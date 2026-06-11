use crate::db::Database;
use crate::errors::ApiError;
use crate::db::repositories::ParentRepository;
use crate::models::Parent;
use crate::models::auth::CreateParentWithUserRequest;
use crate::models::users::User;
use chrono::Utc;
use uuid::Uuid;

pub struct ParentService;

impl ParentService {
    async fn ensure_school_admin(db: &Database, actor: Uuid, school_id: Uuid) -> Result<(), ApiError> {
        let school_admin_role_id = sqlx::query_scalar::<sqlx::Postgres, Uuid>(
            "SELECT id FROM roles WHERE name = 'SCHOOL_ADMIN' AND is_active = true"
        )
        .fetch_optional(db.pool())
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))?
        .ok_or_else(|| ApiError::DatabaseError("SCHOOL_ADMIN role not found".to_string()))?;

        let is_admin = crate::db::repositories::UserSchoolRoleRepository::exists(
            db.pool(),
            actor,
            school_id,
            school_admin_role_id,
        )
        .await?;

        if !is_admin {
            return Err(ApiError::Unauthorized(
                "Caller is not a SCHOOL_ADMIN for this school".to_string(),
            ));
        }

        Ok(())
    }

    pub async fn create_parent(db: &Database, payload: Parent) -> Result<Parent, ApiError> {
        ParentRepository::create(db.pool(), &payload).await
    }

    pub async fn get_parent_by_id(db: &Database, id: Uuid) -> Result<Parent, ApiError> {
        ParentRepository::get_by_id(db.pool(), id).await
    }

    pub async fn update_parent(db: &Database, id: Uuid, payload: Parent) -> Result<Parent, ApiError> {
        // ParentRepository currently has no update; reuse create pattern or return NotImplemented
        ParentRepository::create(db.pool(), &payload).await
    }

    /// Update a parent's contact details (name, email, phone).
    /// Operates on the linked `users` row since parent data lives there.
    pub async fn update_parent_contact(
        db: &Database,
        parent_id: Uuid,
        payload: crate::handlers::community::parents::UpdateParentRequest,
    ) -> Result<crate::models::ParentListResponse, ApiError> {
        // Split full_name → first_name + last_name
        let name_parts: Vec<&str> = payload.full_name.trim().splitn(2, ' ').collect();
        let first_name = name_parts.first().copied().unwrap_or("").to_string();
        let last_name = name_parts.get(1).copied().unwrap_or("").to_string();

        // Update the linked user row
        sqlx::query(
            r#"
            UPDATE users
            SET first_name = $1,
                last_name   = $2,
                email       = $3,
                phone_number = $4,
                updated_at  = NOW()
            WHERE id = (SELECT user_id FROM parents WHERE id = $5 AND is_active = true)
            "#,
        )
        .bind(&first_name)
        .bind(&last_name)
        .bind(&payload.email)
        .bind(&payload.phone_number)
        .bind(parent_id)
        .execute(db.pool())
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

        // Return the updated record via the existing list query (single row)
        let result = ParentRepository::list_parents(
            db.pool(),
            // school_id: fetch it from the parents row
            sqlx::query_scalar::<sqlx::Postgres, Uuid>("SELECT school_id FROM parents WHERE id = $1")
                .bind(parent_id)
                .fetch_one(db.pool())
                .await
                .map_err(|e| ApiError::DatabaseError(e.to_string()))?,
            1,
            1,
            Some(payload.email.clone()),
            None,
            None,
        )
        .await?;

        result
            .data
            .into_iter()
            .next()
            .ok_or_else(|| ApiError::NotFound("Updated parent not found".to_string()))
    }


    pub async fn delete_parent(db: &Database, id: Uuid) -> Result<(), ApiError> {
        // Soft delete via direct query
        sqlx::query("UPDATE parents SET is_active = false, updated_at = NOW() WHERE id = $1")
            .bind(id)
            .execute(db.pool())
            .await
            .map_err(|e| ApiError::DatabaseError(e.to_string()))?;
        Ok(())
    }

    pub async fn soft_delete_parent_student_relationship(
        db: &Database,
        relationship_id: Uuid,
        performed_by: Option<Uuid>,
    ) -> Result<(), ApiError> {
        let school_id = sqlx::query_scalar::<sqlx::Postgres, Uuid>(
            "SELECT school_id FROM parent_student_relationships WHERE id = $1 AND is_active = true"
        )
        .bind(relationship_id)
        .fetch_optional(db.pool())
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))?
        .ok_or_else(|| ApiError::NotFound("Parent-student relationship assignment not found".to_string()))?;

        if let Some(actor) = performed_by {
            Self::ensure_school_admin(db, actor, school_id).await?;
        }

        sqlx::query(
            "UPDATE parent_student_relationships SET is_active = false, updated_at = NOW() WHERE id = $1"
        )
        .bind(relationship_id)
        .execute(db.pool())
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

        Ok(())
    }

    /// Create parent role data for a user identified by email.
    /// If user already exists, reuse it; if parent role already exists for school, return existing record.
    pub async fn create_parent_with_user(
        db: &Database,
        req: CreateParentWithUserRequest,
    ) -> Result<Parent, ApiError> {
        let email = req.email.trim().to_lowercase();
        if email.is_empty() {
            return Err(ApiError::ValidationError("email is required".to_string()));
        }

        let user = match crate::db::repositories::UserRepository::get_by_email(db.pool(), &email).await {
            Ok(existing) => existing,
            Err(_) => {
                let new_user = User {
                    id: Uuid::new_v4(),
                    email: email.clone(),
                    phone_number: req.phone_number.clone(),
                    phone_country_code: None,
                    password_hash: None,
                    first_name: Some(req.first_name.clone()),
                    last_name: Some(req.last_name.clone()),
                    middle_name: req.middle_name.clone(),
                    date_of_birth: req.date_of_birth,
                    gender: req.gender.clone(),
                    profile_picture_url: None,
                    address_line1: None,
                    address_line2: None,
                    city: None,
                    state: None,
                    postal_code: None,
                    country: "Unknown".to_string(),
                    status: "ACTIVE".to_string(),
                    is_verified: false,
                    is_approved: Some(false),
                    verified_at: None,
                    approved_at: None,
                    approved_by: None,
                    last_login_at: None,
                    otp_code: None,
                    otp_expires: None,
                    last_otp_sent: None,
                    created_at: Utc::now(),
                    updated_at: Utc::now(),
                    is_active: true,
                };

                crate::db::repositories::UserRepository::create(db.pool(), &new_user).await?
            }
        };

        let parent = if let Some(existing_parent) = sqlx::query_as::<sqlx::Postgres, Parent>(
            "SELECT * FROM parents WHERE user_id = $1 AND school_id = $2 AND is_active = true"
        )
        .bind(user.id)
        .bind(req.school_id)
        .fetch_optional(db.pool())
        .await
        .map_err(|e: sqlx::Error| ApiError::DatabaseError(e.to_string()))? {
            existing_parent
        } else {
            let parent = Parent {
                id: Uuid::new_v4(),
                created_at: Utc::now().naive_utc(),
                is_active: true,
                updated_at: Utc::now().naive_utc(),
                school_id: req.school_id,
                is_emergency_contact: Some(true),
                is_financially_responsible: Some(true),
                is_primary_contact: Some(true),
                receive_academic_updates: Some(true),
                receive_disciplinary_updates: Some(true),
                receive_financial_updates: Some(true),
                user_id: user.id,
                payment_distribution_type: req.parent.occupation.clone(),
                payment_priority_order: req.parent.business_address.clone(),
            };

            ParentRepository::create(db.pool(), &parent).await?
        };

        if let Some(rels) = req.parent_student_relationships {
            for rel in rels.into_iter() {
                sqlx::query(
                    "INSERT INTO parent_student_relationships (id, school_id, parent_id, student_id, relationship_type, created_at, updated_at, is_active) VALUES ($1,$2,$3,$4,$5,NOW(),NOW(),true) ON CONFLICT (parent_id, student_id, school_id) DO UPDATE SET relationship_type = EXCLUDED.relationship_type, updated_at = NOW(), is_active = true"
                )
                .bind(Uuid::new_v4())
                .bind(req.school_id)
                .bind(parent.id)
                .bind(rel.student_id)
                .bind(rel.relationship.unwrap_or_else(|| "GUARDIAN".to_string()))
                .execute(db.pool())
                .await
                .map_err(|e| ApiError::DatabaseError(e.to_string()))?;
            }
        }

        Ok(parent)
    }

    pub async fn list_parents(
        db: &Database,
        school_id: Uuid,
        page: i64,
        per_page: i64,
        search: Option<String>,
        track_id: Option<Uuid>,
        class_id: Option<Uuid>,
    ) -> Result<crate::models::PaginatedResponse<crate::models::ParentListResponse>, ApiError> {
        ParentRepository::list_parents(
            db.pool(),
            school_id,
            page,
            per_page,
            search,
            track_id,
            class_id,
        )
        .await
    }
}

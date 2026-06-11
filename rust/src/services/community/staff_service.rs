use crate::db::Database;
use crate::errors::ApiError;
use crate::db::repositories::StaffRepository;
use crate::models::Staff;
use crate::models::auth::CreateStaffWithUserRequest;
use crate::models::users::User;
use chrono::Utc;
use uuid::Uuid;

pub struct StaffService;

impl StaffService {
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

    pub async fn create_staff(db: &Database, payload: Staff) -> Result<Staff, ApiError> {
        StaffRepository::create(db.pool(), &payload).await
    }

    pub async fn get_staff_by_id(db: &Database, id: Uuid) -> Result<Staff, ApiError> {
        StaffRepository::get_by_id(db.pool(), id).await
    }

    pub async fn update_staff(db: &Database, id: Uuid, payload: Staff) -> Result<Staff, ApiError> {
        // Verify staff exists first
        StaffRepository::get_by_id(db.pool(), id).await?;
        StaffRepository::update(db.pool(), id, &payload).await
    }

    pub async fn delete_staff(db: &Database, id: Uuid) -> Result<(), ApiError> {
        sqlx::query("UPDATE staff SET is_active = false, updated_at = NOW() WHERE id = $1")
            .bind(id)
            .execute(db.pool())
            .await
            .map_err(|e| ApiError::DatabaseError(e.to_string()))?;
        Ok(())
    }

    pub async fn soft_delete_staff_class_assignment(
        db: &Database,
        assignment_id: Uuid,
        performed_by: Option<Uuid>,
    ) -> Result<(), ApiError> {
        let school_id = sqlx::query_scalar::<sqlx::Postgres, Uuid>(
            "SELECT school_id FROM class_teachers WHERE id = $1 AND is_active = true"
        )
        .bind(assignment_id)
        .fetch_optional(db.pool())
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))?
        .ok_or_else(|| ApiError::NotFound("Staff-class assignment not found".to_string()))?;

        if let Some(actor) = performed_by {
            Self::ensure_school_admin(db, actor, school_id).await?;
        }

        sqlx::query("UPDATE class_teachers SET is_active = false, updated_at = NOW() WHERE id = $1")
            .bind(assignment_id)
            .execute(db.pool())
            .await
            .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

        Ok(())
    }

    pub async fn soft_delete_staff_subject_assignment(
        db: &Database,
        assignment_id: Uuid,
        performed_by: Option<Uuid>,
    ) -> Result<(), ApiError> {
        let school_id = sqlx::query_scalar::<sqlx::Postgres, Uuid>(
            "SELECT school_id FROM subject_teachers WHERE id = $1 AND is_active = true"
        )
        .bind(assignment_id)
        .fetch_optional(db.pool())
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))?
        .ok_or_else(|| ApiError::NotFound("Staff-subject assignment not found".to_string()))?;

        if let Some(actor) = performed_by {
            Self::ensure_school_admin(db, actor, school_id).await?;
        }

        sqlx::query("UPDATE subject_teachers SET is_active = false, updated_at = NOW() WHERE id = $1")
            .bind(assignment_id)
            .execute(db.pool())
            .await
            .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

        Ok(())
    }

    /// Create staff role data for a user identified by email.
    /// If user already exists, reuse it; if staff role already exists for school, return existing record.
    pub async fn create_staff_with_user(
        db: &Database,
        req: CreateStaffWithUserRequest,
    ) -> Result<Staff, ApiError> {
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

        let staff = if let Some(existing_staff) = sqlx::query_as::<sqlx::Postgres, Staff>(
            "SELECT * FROM staff WHERE user_id = $1 AND school_id = $2 AND is_active = true"
        )
        .bind(user.id)
        .bind(req.school_id)
        .fetch_optional(db.pool())
        .await
        .map_err(|e: sqlx::Error| ApiError::DatabaseError(e.to_string()))? {
            existing_staff
        } else {
            let generated_staff_id = format!(
                "STF-{}",
                Uuid::new_v4().to_string().split('-').next().unwrap_or("0000")
            );

            let staff = Staff {
                id: Uuid::new_v4(),
                school_id: req.school_id,
                user_id: user.id,
                staff_id: req.staff.staff_id.clone().unwrap_or(generated_staff_id),
                employee_number: req.staff.employee_number.clone(),
                designation: req.staff.designation.clone().unwrap_or_else(|| "Teacher".to_string()),
                hire_date: req.staff.hire_date.unwrap_or_else(|| Utc::now().date_naive()),
                termination_date: None,
                employment_status: Some(req.staff.employment_status.clone().unwrap_or_else(|| "ACTIVE".to_string())),
                employment_type: Some(req.staff.employment_type.clone().unwrap_or_else(|| "FULL_TIME".to_string())),
                highest_degree: req.staff.highest_degree.clone(),
                department: req.staff.department.clone(),
                is_class_teacher: Some(req.staff.is_class_teacher.unwrap_or(false)),
                is_subject_teacher: Some(req.staff.is_subject_teacher.unwrap_or(false)),
                bank_name: req.staff.bank_name.clone(),
                account_name: req.staff.account_name.clone(),
                account_number: req.staff.account_number.clone(),
                monthly_deduction: Some(req.staff.monthly_deduction.unwrap_or(0.0)),
                class_teacher_for: req.staff.class_teacher_for,
                years_of_experience: Some(req.staff.years_of_experience.unwrap_or(0)),
                created_at: Utc::now().naive_utc(),
                updated_at: Utc::now().naive_utc(),
                is_active: true,
            };

            StaffRepository::create(db.pool(), &staff).await?
        };

        if let Some(assignments) = req.staff_class_assignments {
            for a in assignments.into_iter() {
                sqlx::query(
                    "INSERT INTO class_teachers (id, school_id, class_id, staff_id, academic_session_id, term_id, created_at, updated_at, is_active) VALUES ($1,$2,$3,$4,$5,$6,NOW(),NOW(),true) ON CONFLICT (staff_id, class_id, academic_session_id, term_id, school_id) DO UPDATE SET updated_at = NOW(), is_active = true"
                )
                .bind(Uuid::new_v4())
                .bind(req.school_id)
                .bind(a.class_id)
                .bind(staff.id)
                .bind(a.session_id)
                .bind(a.term_id)
                .execute(db.pool())
                .await
                .map_err(|e| ApiError::DatabaseError(e.to_string()))?;
            }
        }

        if let Some(assignments) = req.staff_subject_assignments {
            for a in assignments.into_iter() {
                sqlx::query(
                    "INSERT INTO subject_teachers (id, school_id, subject_id, class_id, staff_id, academic_session_id, term_id, created_at, updated_at, is_active) VALUES ($1,$2,$3,$4,$5,$6,$7,NOW(),NOW(),true) ON CONFLICT (staff_id, subject_id, class_id, academic_session_id, term_id, school_id) DO UPDATE SET updated_at = NOW(), is_active = true"
                )
                .bind(Uuid::new_v4())
                .bind(req.school_id)
                .bind(a.subject_id)
                .bind(a.class_id)
                .bind(staff.id)
                .bind(a.session_id)
                .bind(a.term_id)
                .execute(db.pool())
                .await
                .map_err(|e| ApiError::DatabaseError(e.to_string()))?;
            }
        }

        Ok(staff)
    }

    pub async fn list_staff(
        db: &Database,
        school_id: Uuid,
        page: i64,
        per_page: i64,
        search: Option<String>,
        track_id: Option<Uuid>,
        department_id: Option<Uuid>,
        class_id: Option<Uuid>,
        designation: Option<String>,
    ) -> Result<crate::models::PaginatedResponse<crate::models::StaffListResponse>, ApiError> {
        StaffRepository::list_staff(
            db.pool(),
            school_id,
            page,
            per_page,
            search,
            track_id,
            department_id,
            class_id,
            designation,
        )
        .await
    }
}

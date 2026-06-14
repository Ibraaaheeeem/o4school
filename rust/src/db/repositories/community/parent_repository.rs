use uuid::Uuid;
use sqlx::PgPool;

use crate::errors::ApiError;
use crate::models::Parent;

pub struct ParentRepository;

impl ParentRepository {
    /// Get parent by ID
    pub async fn get_by_id(pool: &PgPool, id: Uuid) -> Result<Parent, ApiError> {
        sqlx::query_as::<sqlx::Postgres, Parent>(
            "SELECT * FROM parents WHERE id = $1"
        )
        .bind(id)
        .fetch_one(pool)
        .await
        .map_err(|e| {
            if e.to_string().contains("no rows") {
                ApiError::NotFound(format!("Parent with id {} not found", id))
            } else {
                ApiError::DatabaseError(e.to_string())
            }
        })
    }

    /// Create a new parent record
    pub async fn create(pool: &PgPool, parent: &Parent) -> Result<Parent, ApiError> {
        sqlx::query_as::<sqlx::Postgres, Parent>(
            r#"
            INSERT INTO parents (
                id, created_at, is_active, updated_at, school_id, is_emergency_contact,
                is_financially_responsible, is_primary_contact, receive_academic_updates,
                receive_disciplinary_updates, receive_financial_updates, user_id,
                payment_distribution_type, payment_priority_order
            ) VALUES (
                $1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14
            )
            RETURNING *
            "#
        )
        .bind(parent.id)
        .bind(parent.created_at)
        .bind(parent.is_active)
        .bind(parent.updated_at)
        .bind(parent.school_id)
        .bind(parent.is_emergency_contact)
        .bind(parent.is_financially_responsible)
        .bind(parent.is_primary_contact)
        .bind(parent.receive_academic_updates)
        .bind(parent.receive_disciplinary_updates)
        .bind(parent.receive_financial_updates)
        .bind(parent.user_id)
        .bind(&parent.payment_distribution_type)
        .bind(&parent.payment_priority_order)
        .fetch_one(pool)
        .await
        .map_err(|e| {
            log::error!("ParentRepository::create failed: {}", e);
            ApiError::DatabaseError(e.to_string())
        })
    }

    /// Create a new parent record within an existing transaction
        // transactional helpers removed; use `create` method on pool instead

    /// Get parent by user_id
    pub async fn get_by_user_id(pool: &PgPool, user_id: Uuid) -> Result<Parent, ApiError> {
        sqlx::query_as::<sqlx::Postgres, Parent>(
            "SELECT * FROM parents WHERE user_id = $1"
        )
        .bind(user_id)
        .fetch_one(pool)
        .await
        .map_err(|e| {
            if e.to_string().contains("no rows") {
                ApiError::NotFound(format!("Parent for user_id {} not found", user_id))
            } else {
                ApiError::DatabaseError(e.to_string())
            }
        })
    }

    /// List parents with pagination and filters
    pub async fn list_parents(
        pool: &PgPool,
        school_id: Uuid,
        page: i64,
        per_page: i64,
        search: Option<String>,
        track_id: Option<Uuid>,
        class_id: Option<Uuid>,
    ) -> Result<crate::models::PaginatedResponse<crate::models::ParentListResponse>, ApiError> {
        let limit = per_page;
        let offset = (page - 1) * per_page;
        
        let search_pattern = search.map(|s| format!("%{}%", s));

        // 1. Fetch total count
        let total = sqlx::query_scalar::<sqlx::Postgres, i64>(
            r#"
            SELECT COUNT(DISTINCT p.id)
            FROM parents p
            JOIN users u ON p.user_id = u.id
            LEFT JOIN parent_student_relationships psr ON p.id = psr.parent_id AND psr.is_active = true
            LEFT JOIN students s ON psr.student_id = s.id AND s.is_active = true
            LEFT JOIN student_classes sc ON s.id = sc.student_id AND sc.is_active = true
            LEFT JOIN classes c ON sc.class_id = c.id AND c.is_active = true
            LEFT JOIN departments d ON c.department_id = d.id AND d.is_active = true
            WHERE p.school_id = $1 
              AND p.is_active = true
              AND ($2::text IS NULL OR u.first_name ILIKE $2 OR u.last_name ILIKE $2 OR u.email ILIKE $2 OR u.phone_number ILIKE $2)
              AND ($3::uuid IS NULL OR d.track_id = $3)
              AND ($4::uuid IS NULL OR sc.class_id = $4)
            "#
        )
        .bind(school_id)
        .bind(search_pattern.clone())
        .bind(track_id)
        .bind(class_id)
        .fetch_one(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

        // 2. Fetch parent rows
        #[derive(Debug, Clone, sqlx::FromRow)]
        struct FlatParentRow {
            id: Uuid,
            parent_id: String,
            full_name: String,
            email: String,
            phone_number: String,
            is_verified: bool,
            profile_image_url: Option<String>,
        }

        let parents = sqlx::query_as::<sqlx::Postgres, FlatParentRow>(
            r#"
            SELECT DISTINCT
                p.id,
                'PAR-' || TO_CHAR(p.created_at, 'YYYY') || '-' || SUBSTR(p.id::text, 1, 3) AS parent_id,
                u.first_name || ' ' || COALESCE(u.middle_name || ' ', '') || u.last_name AS full_name,
                u.email,
                COALESCE(u.phone_number, '') AS phone_number,
                u.is_verified AS is_verified,
                u.profile_picture_url AS profile_image_url
            FROM parents p
            JOIN users u ON p.user_id = u.id
            LEFT JOIN parent_student_relationships psr ON p.id = psr.parent_id AND psr.is_active = true
            LEFT JOIN students s ON psr.student_id = s.id AND s.is_active = true
            LEFT JOIN student_classes sc ON s.id = sc.student_id AND sc.is_active = true
            LEFT JOIN classes c ON sc.class_id = c.id AND c.is_active = true
            LEFT JOIN departments d ON c.department_id = d.id AND d.is_active = true
            WHERE p.school_id = $1 
              AND p.is_active = true
              AND ($2::text IS NULL OR u.first_name ILIKE $2 OR u.last_name ILIKE $2 OR u.email ILIKE $2 OR u.phone_number ILIKE $2)
              AND ($3::uuid IS NULL OR d.track_id = $3)
              AND ($4::uuid IS NULL OR sc.class_id = $4)
            ORDER BY full_name ASC
            LIMIT $5 OFFSET $6
            "#
        )
        .bind(school_id)
        .bind(search_pattern)
        .bind(track_id)
        .bind(class_id)
        .bind(limit)
        .bind(offset)
        .fetch_all(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

        // 3. Fetch linked students for these parents
        let mut parent_list = Vec::new();
        if !parents.is_empty() {
            let parent_ids: Vec<Uuid> = parents.iter().map(|p| p.id).collect();

            #[derive(Debug, Clone, sqlx::FromRow)]
            struct FlatRelRow {
                id: Uuid,
                parent_id: Uuid,
                student_id: String,
                student_name: String,
                class_name: Option<String>,
                profile_image_url: Option<String>,
            }

            let rels = sqlx::query_as::<sqlx::Postgres, FlatRelRow>(
                r#"
                SELECT 
                    psr.id,
                    psr.parent_id,
                    COALESCE(NULLIF(s.admission_number, ''), s.student_id) AS student_id,
                    su.first_name || ' ' || su.last_name AS student_name,
                    (
                        SELECT c.class_name 
                        FROM student_classes sc
                        JOIN classes c ON sc.class_id = c.id
                        WHERE sc.student_id = s.id AND sc.is_active = true
                        LIMIT 1
                    ) AS class_name,
                    su.profile_picture_url AS profile_image_url
                FROM parent_student_relationships psr
                JOIN students s ON psr.student_id = s.id
                JOIN users su ON s.user_id = su.id
                WHERE psr.parent_id = ANY($1) 
                  AND psr.is_active = true 
                  AND s.is_active = true
                "#,
            )
            .bind(&parent_ids)
            .fetch_all(pool)
            .await
            .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

            for p in parents {
                let linked: Vec<crate::models::ParentLinkedStudent> = rels
                    .iter()
                    .filter(|r| r.parent_id == p.id)
                    .map(|r| crate::models::ParentLinkedStudent {
                        id: r.id,
                        student_id: r.student_id.clone(),
                        full_name: r.student_name.clone(),
                        class_name: r.class_name.clone(),
                        profile_image_url: r.profile_image_url.clone(),
                    })
                    .collect();

                parent_list.push(crate::models::ParentListResponse {
                    id: p.id,
                    parent_id: p.parent_id,
                    full_name: p.full_name,
                    email: p.email,
                    phone_number: p.phone_number,
                    is_verified: p.is_verified,
                    profile_image_url: p.profile_image_url,
                    linked_students: linked,
                });
            }
        }

        let total_pages = (total as f64 / per_page as f64).ceil() as i64;
        let has_next = page < total_pages;
        let has_previous = page > 1;

        Ok(crate::models::PaginatedResponse {
            success: true,
            message: "Parent list retrieved successfully".to_string(),
            data: parent_list,
            pagination: crate::models::Pagination {
                current_page: page,
                per_page,
                total,
                total_pages,
                has_next,
                has_previous,
            },
            errors: None,
        })
    }
}

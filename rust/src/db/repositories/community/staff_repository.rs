use uuid::Uuid;
use sqlx::PgPool;
use sqlx::{Transaction, Postgres, Executor};
use crate::errors::ApiError;
use crate::models::Staff;

pub struct StaffRepository;

impl StaffRepository {
    /// Get staff by ID
    pub async fn get_by_id(pool: &PgPool, id: Uuid) -> Result<Staff, ApiError> {
        sqlx::query_as::<sqlx::Postgres, Staff>(
            "SELECT * FROM staff WHERE id = $1"
        )
        .bind(id)
        .fetch_one(pool)
        .await
        .map_err(|e| {
            if e.to_string().contains("no rows") {
                ApiError::NotFound(format!("Staff with id {} not found", id))
            } else {
                ApiError::DatabaseError(e.to_string())
            }
        })
    }

    /// Get staff by user_id
    pub async fn get_by_user_id(pool: &PgPool, user_id: Uuid) -> Result<Staff, ApiError> {
        sqlx::query_as::<sqlx::Postgres, Staff>(
            "SELECT * FROM staff WHERE user_id = $1"
        )
        .bind(user_id)
        .fetch_one(pool)
        .await
        .map_err(|e| {
            if e.to_string().contains("no rows") {
                ApiError::NotFound(format!("Staff for user_id {} not found", user_id))
            } else {
                ApiError::DatabaseError(e.to_string())
            }
        })
    }

    /// Create a new staff record
    pub async fn create(pool: &PgPool, staff: &Staff) -> Result<Staff, ApiError> {
        sqlx::query_as::<sqlx::Postgres, Staff>(
            r#"
            INSERT INTO staff (
                id, school_id, user_id, staff_id, employee_number, designation, hire_date, termination_date,
                employment_status, employment_type, highest_degree, department, is_class_teacher, is_subject_teacher,
                bank_name, account_name, account_number, monthly_deduction, class_teacher_for, years_of_experience,
                created_at, updated_at, is_active
            ) VALUES (
                $1, $2, $3, $4, $5, $6, $7, $8,
                $9, $10, $11, $12, $13, $14,
                $15, $16, $17, $18, $19, $20,
                $21, $22, $23
            )
            RETURNING *
            "#
        )
        .bind(staff.id)
        .bind(staff.school_id)
        .bind(staff.user_id)
        .bind(&staff.staff_id)
        .bind(&staff.employee_number)
        .bind(&staff.designation)
        .bind(staff.hire_date)
        .bind(staff.termination_date)
        .bind(&staff.employment_status)
        .bind(&staff.employment_type)
        .bind(&staff.highest_degree)
        .bind(&staff.department)
        .bind(staff.is_class_teacher)
        .bind(staff.is_subject_teacher)
        .bind(&staff.bank_name)
        .bind(&staff.account_name)
        .bind(&staff.account_number)
        .bind(staff.monthly_deduction)
        .bind(staff.class_teacher_for)
        .bind(staff.years_of_experience)
        .bind(staff.created_at)
        .bind(staff.updated_at)
        .bind(staff.is_active)
        .fetch_one(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))
    }

    /// List staff members with pagination and filters
    pub async fn list_staff(
        pool: &PgPool,
        school_id: Uuid,
        page: i64,
        per_page: i64,
        search: Option<String>,
        track_id: Option<Uuid>,
        department_id: Option<Uuid>,
        class_id: Option<Uuid>,
        designation: Option<String>,
    ) -> Result<crate::models::PaginatedResponse<crate::models::StaffListResponse>, ApiError> {
        let limit = per_page;
        let offset = (page - 1) * per_page;
        
        let search_pattern = search.map(|s| format!("%{}%", s));
        let designation_pattern = designation.map(|d| format!("%{}%", d));

        // 1. Fetch total count
        let total = sqlx::query_scalar::<sqlx::Postgres, i64>(
            r#"
            SELECT COUNT(DISTINCT s.id)
            FROM staff s
            JOIN users u ON s.user_id = u.id
            WHERE s.school_id = $1 
              AND s.is_active = true
              AND ($2::text IS NULL OR u.first_name ILIKE $2 OR u.last_name ILIKE $2 OR s.staff_id ILIKE $2 OR u.email ILIKE $2)
              AND ($3::uuid IS NULL OR EXISTS (
                  SELECT 1 
                  FROM class_teachers ct
                  JOIN classes c ON ct.class_id = c.id
                  JOIN departments d ON c.department_id = d.id
                  WHERE ct.staff_id = s.id AND d.track_id = $3 AND ct.is_active = true
                  UNION ALL
                  SELECT 1
                  FROM subject_teachers st
                  JOIN classes c ON st.class_id = c.id
                  JOIN departments d ON c.department_id = d.id
                  WHERE st.staff_id = s.id AND d.track_id = $3 AND st.is_active = true
              ))
              AND ($4::uuid IS NULL OR EXISTS (
                  SELECT 1 
                  FROM class_teachers ct
                  JOIN classes c ON ct.class_id = c.id
                  WHERE ct.staff_id = s.id AND c.department_id = $4 AND ct.is_active = true
                  UNION ALL
                  SELECT 1
                  FROM subject_teachers st
                  JOIN classes c ON st.class_id = c.id
                  WHERE st.staff_id = s.id AND c.department_id = $4 AND st.is_active = true
              ))
              AND ($5::uuid IS NULL OR EXISTS (
                  SELECT 1 
                  FROM class_teachers ct
                  WHERE ct.staff_id = s.id AND ct.class_id = $5 AND ct.is_active = true
                  UNION ALL
                  SELECT 1
                  FROM subject_teachers st
                  WHERE st.staff_id = s.id AND st.class_id = $5 AND st.is_active = true
              ))
              AND ($6::text IS NULL OR 
                   s.designation ILIKE $6 OR
                   ($6 = 'Class Teacher' AND (s.is_class_teacher = true OR EXISTS (
                       SELECT 1 FROM class_teachers ct WHERE ct.staff_id = s.id AND ct.is_active = true
                   ))) OR
                   ($6 = 'Subject Teacher' AND (s.is_subject_teacher = true OR EXISTS (
                       SELECT 1 FROM subject_teachers st WHERE st.staff_id = s.id AND st.is_active = true
                   )))
              )
            "#
        )
        .bind(school_id)
        .bind(search_pattern.clone())
        .bind(track_id)
        .bind(department_id)
        .bind(class_id)
        .bind(designation_pattern.clone())
        .fetch_one(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

        // 2. Fetch page items
        let mut data = sqlx::query_as::<sqlx::Postgres, crate::models::StaffListResponse>(
            r#"
            SELECT 
                s.id,
                s.staff_id,
                u.first_name || ' ' || COALESCE(u.middle_name || ' ', '') || u.last_name AS full_name,
                u.email,
                COALESCE(u.phone_number, '') AS phone_number,
                COALESCE(s.department, '') AS department,
                s.designation AS position,
                s.hire_date::text AS hire_date,
                0.0::FLOAT8 AS salary,
                u.profile_picture_url AS profile_image_url,
                s.is_active,
                (COALESCE(s.is_class_teacher, false) OR EXISTS (
                    SELECT 1 FROM class_teachers ct WHERE ct.staff_id = s.id AND ct.is_active = true
                )) AS is_class_teacher,
                (COALESCE(s.is_subject_teacher, false) OR EXISTS (
                    SELECT 1 FROM subject_teachers st WHERE st.staff_id = s.id AND st.is_active = true
                )) AS is_subject_teacher,
                (
                    SELECT string_agg(DISTINCT c.class_name, ', ' ORDER BY c.class_name)
                    FROM class_teachers ct
                    JOIN classes c ON ct.class_id = c.id
                    JOIN academic_sessions acs ON ct.academic_session_id = acs.id
                    JOIN terms t ON ct.term_id = t.id
                    WHERE ct.staff_id = s.id AND ct.is_active = true
                      AND acs.is_current_session = true
                      AND t.is_current_term = true
                ) AS class_teacher_class_name,
                (
                    SELECT string_agg(DISTINCT sub.subject_name || ' (' || c.class_name || ')', ', ')
                    FROM subject_teachers st
                    JOIN subjects sub ON st.subject_id = sub.id
                    JOIN classes c ON st.class_id = c.id
                    JOIN academic_sessions acs ON st.academic_session_id = acs.id
                    JOIN terms t ON st.term_id = t.id
                    WHERE st.staff_id = s.id AND st.is_active = true
                      AND acs.is_current_session = true
                      AND t.is_current_term = true
                ) AS subject_teacher_subjects
            FROM staff s
            JOIN users u ON s.user_id = u.id
            WHERE s.school_id = $1 
              AND s.is_active = true
              AND ($2::text IS NULL OR u.first_name ILIKE $2 OR u.last_name ILIKE $2 OR s.staff_id ILIKE $2 OR u.email ILIKE $2)
              AND ($3::uuid IS NULL OR EXISTS (
                  SELECT 1 
                  FROM class_teachers ct
                  JOIN classes c ON ct.class_id = c.id
                  JOIN departments d ON c.department_id = d.id
                  WHERE ct.staff_id = s.id AND d.track_id = $3 AND ct.is_active = true
                  UNION ALL
                  SELECT 1
                  FROM subject_teachers st
                  JOIN classes c ON st.class_id = c.id
                  JOIN departments d ON c.department_id = d.id
                  WHERE st.staff_id = s.id AND d.track_id = $3 AND st.is_active = true
              ))
              AND ($4::uuid IS NULL OR EXISTS (
                  SELECT 1 
                  FROM class_teachers ct
                  JOIN classes c ON ct.class_id = c.id
                  WHERE ct.staff_id = s.id AND c.department_id = $4 AND ct.is_active = true
                  UNION ALL
                  SELECT 1
                  FROM subject_teachers st
                  JOIN classes c ON st.class_id = c.id
                  WHERE st.staff_id = s.id AND c.department_id = $4 AND st.is_active = true
              ))
              AND ($5::uuid IS NULL OR EXISTS (
                  SELECT 1 
                  FROM class_teachers ct
                  WHERE ct.staff_id = s.id AND ct.class_id = $5 AND ct.is_active = true
                  UNION ALL
                  SELECT 1
                  FROM subject_teachers st
                  WHERE st.staff_id = s.id AND st.class_id = $5 AND st.is_active = true
              ))
              AND ($6::text IS NULL OR 
                   s.designation ILIKE $6 OR
                   ($6 = 'Class Teacher' AND (s.is_class_teacher = true OR EXISTS (
                       SELECT 1 FROM class_teachers ct WHERE ct.staff_id = s.id AND ct.is_active = true
                   ))) OR
                   ($6 = 'Subject Teacher' AND (s.is_subject_teacher = true OR EXISTS (
                       SELECT 1 FROM subject_teachers st WHERE st.staff_id = s.id AND st.is_active = true
                   )))
              )
            ORDER BY full_name ASC
            LIMIT $7 OFFSET $8
            "#
        )
        .bind(school_id)
        .bind(search_pattern)
        .bind(track_id)
        .bind(department_id)
        .bind(class_id)
        .bind(designation_pattern)
        .bind(limit)
        .bind(offset)
        .fetch_all(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

        if !data.is_empty() {
            let staff_ids: Vec<Uuid> = data.iter().map(|s| s.id).collect();

            #[derive(Debug, Clone, sqlx::FromRow)]
            struct FlatClassRelRow {
                id: Uuid,
                staff_id: Uuid,
                class_id: Uuid,
                class_name: String,
            }

            let class_rels = sqlx::query_as::<sqlx::Postgres, FlatClassRelRow>(
                r#"
                SELECT 
                    ct.id,
                    ct.staff_id,
                    ct.class_id,
                    c.class_name
                FROM class_teachers ct
                JOIN classes c ON ct.class_id = c.id
                JOIN academic_sessions acs ON ct.academic_session_id = acs.id
                JOIN terms t ON ct.term_id = t.id
                WHERE ct.staff_id = ANY($1) 
                  AND ct.is_active = true
                  AND acs.is_current_session = true
                  AND t.is_current_term = true
                "#
            )
            .bind(&staff_ids)
            .fetch_all(pool)
            .await
            .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

            #[derive(Debug, Clone, sqlx::FromRow)]
            struct FlatSubjectRelRow {
                id: Uuid,
                staff_id: Uuid,
                class_id: Uuid,
                class_name: String,
                subject_id: Uuid,
                subject_name: String,
            }

            let subject_rels = sqlx::query_as::<sqlx::Postgres, FlatSubjectRelRow>(
                r#"
                SELECT 
                    st.id,
                    st.staff_id,
                    st.class_id,
                    c.class_name,
                    st.subject_id,
                    sub.subject_name
                FROM subject_teachers st
                JOIN subjects sub ON st.subject_id = sub.id
                JOIN classes c ON st.class_id = c.id
                JOIN academic_sessions acs ON st.academic_session_id = acs.id
                JOIN terms t ON st.term_id = t.id
                WHERE st.staff_id = ANY($1) 
                  AND st.is_active = true
                  AND acs.is_current_session = true
                  AND t.is_current_term = true
                "#
            )
            .bind(&staff_ids)
            .fetch_all(pool)
            .await
            .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

            for s in &mut data {
                s.class_assignments = class_rels
                    .iter()
                    .filter(|r| r.staff_id == s.id)
                    .map(|r| crate::models::StaffClassAssignmentResponse {
                        id: r.id,
                        class_id: r.class_id,
                        class_name: r.class_name.clone(),
                    })
                    .collect();

                s.subject_assignments = subject_rels
                    .iter()
                    .filter(|r| r.staff_id == s.id)
                    .map(|r| crate::models::StaffSubjectAssignmentResponse {
                        id: r.id,
                        class_id: r.class_id,
                        class_name: r.class_name.clone(),
                        subject_id: r.subject_id,
                        subject_name: r.subject_name.clone(),
                    })
                    .collect();
            }
        }

        let total_pages = (total as f64 / per_page as f64).ceil() as i64;
        let has_next = page < total_pages;
        let has_previous = page > 1;

        Ok(crate::models::PaginatedResponse {
            success: true,
            message: "Staff list retrieved successfully".to_string(),
            data,
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

    /// Update an existing staff record
    pub async fn update(pool: &PgPool, id: Uuid, updates: &Staff) -> Result<Staff, ApiError> {
        sqlx::query_as::<sqlx::Postgres, Staff>(
            r#"
            UPDATE staff SET
                employee_number = $1, designation = $2, hire_date = $3, termination_date = $4,
                employment_status = $5, employment_type = $6, highest_degree = $7, department = $8,
                is_class_teacher = $9, is_subject_teacher = $10, bank_name = $11, account_name = $12,
                account_number = $13, monthly_deduction = $14, class_teacher_for = $15, years_of_experience = $16,
                updated_at = $17, is_active = $18
            WHERE id = $19
            RETURNING *
            "#
        )
        .bind(&updates.employee_number)
        .bind(&updates.designation)
        .bind(updates.hire_date)
        .bind(updates.termination_date)
        .bind(&updates.employment_status)
        .bind(&updates.employment_type)
        .bind(&updates.highest_degree)
        .bind(&updates.department)
        .bind(updates.is_class_teacher)
        .bind(updates.is_subject_teacher)
        .bind(&updates.bank_name)
        .bind(&updates.account_name)
        .bind(&updates.account_number)
        .bind(updates.monthly_deduction)
        .bind(updates.class_teacher_for)
        .bind(updates.years_of_experience)
        .bind(updates.updated_at)
        .bind(updates.is_active)
        .bind(id)
        .fetch_one(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))
    }

    /// Fetch a unified StaffListResponse by ID
    pub async fn get_list_response_by_id(pool: &sqlx::PgPool, id: uuid::Uuid) -> Result<crate::models::StaffListResponse, ApiError> {
        let mut list_resp = sqlx::query_as::<sqlx::Postgres, crate::models::StaffListResponse>(
            r#"
            SELECT 
                s.id,
                s.staff_id,
                u.first_name || ' ' || COALESCE(u.middle_name || ' ', '') || u.last_name AS full_name,
                u.email,
                COALESCE(u.phone_number, '') AS phone_number,
                COALESCE(s.department, '') AS department,
                s.designation AS position,
                s.hire_date::text AS hire_date,
                0.0::FLOAT8 AS salary,
                u.profile_picture_url AS profile_image_url,
                s.is_active,
                (COALESCE(s.is_class_teacher, false) OR EXISTS (
                    SELECT 1 FROM class_teachers ct WHERE ct.staff_id = s.id AND ct.is_active = true
                )) AS is_class_teacher,
                (COALESCE(s.is_subject_teacher, false) OR EXISTS (
                    SELECT 1 FROM subject_teachers st WHERE st.staff_id = s.id AND st.is_active = true
                )) AS is_subject_teacher,
                (
                    SELECT string_agg(DISTINCT c.class_name, ', ' ORDER BY c.class_name)
                    FROM class_teachers ct
                    JOIN classes c ON ct.class_id = c.id
                    JOIN academic_sessions acs ON ct.academic_session_id = acs.id
                    JOIN terms t ON ct.term_id = t.id
                    WHERE ct.staff_id = s.id AND ct.is_active = true
                      AND acs.is_current_session = true
                      AND t.is_current_term = true
                ) AS class_teacher_class_name,
                (
                    SELECT string_agg(DISTINCT sub.subject_name || ' (' || c.class_name || ')', ', ')
                    FROM subject_teachers st
                    JOIN subjects sub ON st.subject_id = sub.id
                    JOIN classes c ON st.class_id = c.id
                    JOIN academic_sessions acs ON st.academic_session_id = acs.id
                    JOIN terms t ON st.term_id = t.id
                    WHERE st.staff_id = s.id AND st.is_active = true
                      AND acs.is_current_session = true
                      AND t.is_current_term = true
                ) AS subject_teacher_subjects
            FROM staff s
            JOIN users u ON s.user_id = u.id
            WHERE s.id = $1
            "#
        )
        .bind(id)
        .fetch_one(pool)
        .await
        .map_err(|e: sqlx::Error| {
            if e.to_string().contains("no rows") {
                ApiError::NotFound(format!("Staff with id {} not found", id))
            } else {
                ApiError::DatabaseError(e.to_string())
            }
        })?;

        #[derive(Debug, Clone, sqlx::FromRow)]
        struct FlatClassRelRow {
            id: Uuid,
            class_id: Uuid,
            class_name: String,
        }

        let class_rels = sqlx::query_as::<sqlx::Postgres, FlatClassRelRow>(
            r#"
            SELECT 
                ct.id,
                ct.class_id,
                c.class_name
            FROM class_teachers ct
            JOIN classes c ON ct.class_id = c.id
            JOIN academic_sessions acs ON ct.academic_session_id = acs.id
            JOIN terms t ON ct.term_id = t.id
            WHERE ct.staff_id = $1 
              AND ct.is_active = true
              AND acs.is_current_session = true
              AND t.is_current_term = true
            "#
        )
        .bind(id)
        .fetch_all(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

        #[derive(Debug, Clone, sqlx::FromRow)]
        struct FlatSubjectRelRow {
            id: Uuid,
            class_id: Uuid,
            class_name: String,
            subject_id: Uuid,
            subject_name: String,
        }

        let subject_rels = sqlx::query_as::<sqlx::Postgres, FlatSubjectRelRow>(
            r#"
            SELECT 
                st.id,
                st.class_id,
                c.class_name,
                st.subject_id,
                sub.subject_name
            FROM subject_teachers st
            JOIN subjects sub ON st.subject_id = sub.id
            JOIN classes c ON st.class_id = c.id
            JOIN academic_sessions acs ON st.academic_session_id = acs.id
            JOIN terms t ON st.term_id = t.id
            WHERE st.staff_id = $1 
              AND st.is_active = true
              AND acs.is_current_session = true
              AND t.is_current_term = true
            "#
        )
        .bind(id)
        .fetch_all(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

        list_resp.class_assignments = class_rels
            .into_iter()
            .map(|r| crate::models::StaffClassAssignmentResponse {
                id: r.id,
                class_id: r.class_id,
                class_name: r.class_name,
            })
            .collect();

        list_resp.subject_assignments = subject_rels
            .into_iter()
            .map(|r| crate::models::StaffSubjectAssignmentResponse {
                id: r.id,
                class_id: r.class_id,
                class_name: r.class_name,
                subject_id: r.subject_id,
                subject_name: r.subject_name,
            })
            .collect();

        Ok(list_resp)
    }
}


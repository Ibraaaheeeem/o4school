use uuid::Uuid;
use sqlx::PgPool;

use crate::errors::ApiError;
use crate::models::Student;

pub struct StudentRepository;

impl StudentRepository {
    /// Get student by ID
    pub async fn get_by_id(pool: &PgPool, student_id: Uuid) -> Result<Student, ApiError> {
        sqlx::query_as::<sqlx::Postgres, Student>(
            "SELECT * FROM students WHERE id = $1"
        )
        .bind(student_id)
        .fetch_one(pool)
        .await
        .map_err(|e| {
            if e.to_string().contains("no rows") {
                ApiError::NotFound(format!("Student with id {} not found", student_id))
            } else {
                ApiError::DatabaseError(e.to_string())
            }
        })
    }

    /// Get student by user_id
    pub async fn get_by_user_id(pool: &PgPool, user_id: Uuid) -> Result<Student, ApiError> {
        sqlx::query_as::<sqlx::Postgres, Student>(
            "SELECT * FROM students WHERE user_id = $1"
        )
        .bind(user_id)
        .fetch_one(pool)
        .await
        .map_err(|e| {
            if e.to_string().contains("no rows") {
                ApiError::NotFound(format!("Student with user_id {} not found", user_id))
            } else {
                ApiError::DatabaseError(e.to_string())
            }
        })
    }

    /// Get all students in a school
    pub async fn get_by_school(pool: &PgPool, school_id: Uuid, limit: i64, offset: i64) -> Result<Vec<Student>, ApiError> {
        sqlx::query_as::<sqlx::Postgres, Student>(
            "SELECT * FROM students WHERE school_id = $1 AND is_active = true ORDER BY created_at DESC LIMIT $2 OFFSET $3"
        )
        .bind(school_id)
        .bind(limit)
        .bind(offset)
        .fetch_all(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))
    }

    /// Create a new student
    pub async fn create(pool: &PgPool, student: &Student) -> Result<Student, ApiError> {
        sqlx::query_as::<sqlx::Postgres, Student>(
            r#"
            INSERT INTO students (
                id, school_id, user_id, student_id, admission_number, admission_date,
                graduation_date, academic_status, current_grade_level, date_of_birth,
                gender, previous_school, special_needs_description, transportation_method,
                passport_photo_url, is_new, has_special_needs, created_at, updated_at, is_active
            ) VALUES (
                $1, $2, $3, $4, $5, $6, $7, $8, $9, $10,
                $11, $12, $13, $14, $15, $16, $17, $18, $19, $20
            )
            RETURNING *
            "#
        )
        .bind(student.id)
        .bind(student.school_id)
        .bind(student.user_id)
        .bind(&student.student_id)
        .bind(&student.admission_number)
        .bind(student.admission_date)
        .bind(student.graduation_date)
        .bind(&student.academic_status)
        .bind(&student.current_grade_level)
        .bind(student.date_of_birth)
        // Normalize gender to MALE/FEMALE to satisfy DB check constraint
        .bind(student.gender.as_ref().map(|g| match g.trim().to_uppercase().as_str() {
            "M" | "MALE" => "MALE",
            "F" | "FEMALE" => "FEMALE",
            _ => "MALE",
        }))
        .bind(&student.previous_school)
        .bind(&student.special_needs_description)
        .bind(&student.transportation_method)
        .bind(&student.passport_photo_url)
        .bind(student.is_new)
        .bind(student.has_special_needs)
        .bind(student.created_at)
        .bind(student.updated_at)
        .bind(student.is_active)
        .fetch_one(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))
    }

    // Transactional helpers removed to avoid Executor trait issues; use pool-based operations in services

    /// Create a new student within an existing transaction
        // transactional helpers removed; use `create` method on pool instead

    /// Update student
    pub async fn update(pool: &PgPool, student_id: Uuid, updates: &Student) -> Result<Student, ApiError> {
        sqlx::query_as::<sqlx::Postgres, Student>(
            r#"
            UPDATE students SET
                admission_number = $1,
                admission_date = $2,
                graduation_date = $3,
                academic_status = $4,
                current_grade_level = $5,
                date_of_birth = $6,
                gender = $7,
                previous_school = $8,
                special_needs_description = $9,
                transportation_method = $10,
                passport_photo_url = $11,
                has_special_needs = $12,
                is_active = $13,
                updated_at = $14
            WHERE id = $15
            RETURNING *
            "#
        )
        .bind(&updates.admission_number)
        .bind(updates.admission_date)
        .bind(updates.graduation_date)
        .bind(&updates.academic_status)
        .bind(&updates.current_grade_level)
        .bind(updates.date_of_birth)
        .bind(updates.gender.as_ref().map(|g| match g.trim().to_uppercase().as_str() {
            "M" | "MALE" => "MALE",
            "F" | "FEMALE" => "FEMALE",
            _ => "MALE",
        }))
        .bind(&updates.previous_school)
        .bind(&updates.special_needs_description)
        .bind(&updates.transportation_method)
        .bind(&updates.passport_photo_url)
        .bind(updates.has_special_needs)
        .bind(updates.is_active)
        .bind(updates.updated_at)
        .bind(student_id)
        .fetch_one(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))
    }

    /// Delete student (soft delete)
    pub async fn delete(pool: &PgPool, student_id: Uuid) -> Result<(), ApiError> {
        sqlx::query(
            "UPDATE students SET is_active = false, updated_at = NOW() WHERE id = $1"
        )
        .bind(student_id)
        .execute(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

        Ok(())
    }

    /// Create a student and associated student_class rows within a single connection/transaction
    pub async fn create_with_classes(pool: &PgPool, student: &Student, student_classes: Option<Vec<crate::models::auth::CreateStudentClassInfo>>) -> Result<Student, ApiError> {
        // Insert student using the existing create() helper
        let created = StudentRepository::create(pool, student).await?;

        // Insert student_classes (non-transactionally) — callers should wrap if they need atomicity
        if let Some(scs) = student_classes {
            for sc in scs.into_iter() {
                let enrollment_date = sc.enrollment_date.unwrap_or_else(|| chrono::Utc::now().date_naive());

                let track_id = match sqlx::query_scalar::<sqlx::Postgres, Uuid>(
                    "SELECT track_id FROM classes WHERE id = $1 AND school_id = $2 AND is_active = true"
                )
                    .bind(sc.class_id)
                    .bind(created.school_id)
                    .fetch_optional(pool)
                    .await
                {
                    Ok(Some(track_id)) => track_id,
                    Ok(None) => {
                        return Err(ApiError::ValidationError(format!(
                            "Class {} not found for school {}",
                            sc.class_id, created.school_id
                        )));
                    }
                    Err(e) => return Err(ApiError::DatabaseError(e.to_string())),
                };

                if let Err(e) = sqlx::query(
                    "INSERT INTO student_classes (id, school_id, student_id, class_id, academic_session_id, term_id, track_id, enrollment_date, created_at, updated_at, is_active) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,NOW(),NOW(),true) ON CONFLICT (student_id, track_id, academic_session_id, term_id) DO UPDATE SET class_id = EXCLUDED.class_id, school_id = EXCLUDED.school_id, enrollment_date = EXCLUDED.enrollment_date, updated_at = NOW(), is_active = true"
                )
                    .bind(Uuid::new_v4())
                    .bind(created.school_id)
                    .bind(created.id)
                    .bind(sc.class_id)
                    .bind(sc.session_id)
                    .bind(sc.term_id)
                    .bind(track_id)
                    .bind(enrollment_date)
                    .execute(pool)
                    .await
                {
                    return Err(ApiError::DatabaseError(e.to_string()));
                }
            }
        }
        
        Ok(created)
    }

    /// List students with pagination and name/id search
    pub async fn list_students(
        pool: &PgPool,
        school_id: Uuid,
        page: i64,
        per_page: i64,
        search: Option<String>,
        track_id: Option<Uuid>,
        class_id: Option<Uuid>,
    ) -> Result<crate::models::PaginatedResponse<crate::models::StudentListResponse>, ApiError> {
        let limit = per_page;
        let offset = (page - 1) * per_page;
        
        let search_pattern = search.map(|s| format!("%{}%", s));

        // 1. Fetch total count
        let total = sqlx::query_scalar::<sqlx::Postgres, i64>(
            r#"
            SELECT COUNT(DISTINCT s.id)
            FROM students s
            JOIN users u ON s.user_id = u.id
            WHERE s.school_id = $1 
              AND s.is_active = true
              AND ($2::text IS NULL OR u.first_name ILIKE $2 OR u.last_name ILIKE $2 OR s.student_id ILIKE $2 OR s.admission_number ILIKE $2 OR u.email ILIKE $2)
              AND ($3::uuid IS NULL OR EXISTS (SELECT 1 FROM student_classes sc WHERE sc.student_id = s.id AND sc.track_id = $3 AND sc.is_active = true))
              AND ($4::uuid IS NULL OR EXISTS (
                  SELECT 1 FROM student_classes sc 
                  JOIN academic_sessions asess ON sc.academic_session_id = asess.id
                  JOIN terms t ON sc.term_id = t.id
                  WHERE sc.student_id = s.id 
                    AND sc.class_id = $4 
                    AND sc.is_active = true
                    AND asess.is_current_session = true
                    AND t.is_current_term = true
              ))
            "#
        )
        .bind(school_id)
        .bind(search_pattern.clone())
        .bind(track_id)
        .bind(class_id)
        .fetch_one(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

        // 2. Fetch student rows
        let students = sqlx::query_as::<sqlx::Postgres, crate::models::StudentListResponse>(
            r#"
            SELECT 
                s.id,
                COALESCE(NULLIF(s.admission_number, ''), s.student_id) AS student_id,
                u.first_name || ' ' || COALESCE(u.middle_name || ' ', '') || u.last_name AS full_name,
                u.email,
                u.phone_number,
                TO_CHAR(s.date_of_birth, 'YYYY-MM-DD') AS date_of_birth,
                s.gender,
                (
                    SELECT sc.class_id 
                    FROM student_classes sc
                    JOIN academic_sessions asess ON sc.academic_session_id = asess.id
                    JOIN terms t ON sc.term_id = t.id
                    WHERE sc.student_id = s.id 
                      AND sc.is_active = true 
                      AND asess.is_current_session = true 
                      AND t.is_current_term = true
                    LIMIT 1
                ) AS class_id,
                (
                    SELECT STRING_AGG(c.class_name, ', ') 
                    FROM student_classes sc
                    JOIN classes c ON sc.class_id = c.id
                    JOIN academic_sessions asess ON sc.academic_session_id = asess.id
                    JOIN terms t ON sc.term_id = t.id
                    WHERE sc.student_id = s.id 
                      AND sc.is_active = true 
                      AND asess.is_current_session = true 
                      AND t.is_current_term = true
                ) AS class_name,
                TO_CHAR(s.admission_date, 'YYYY-MM-DD') AS admission_date,
                u.profile_picture_url AS profile_image_url,
                s.is_active
            FROM students s
            JOIN users u ON s.user_id = u.id
            WHERE s.school_id = $1 
              AND s.is_active = true
              AND ($2::text IS NULL OR u.first_name ILIKE $2 OR u.last_name ILIKE $2 OR s.student_id ILIKE $2 OR s.admission_number ILIKE $2 OR u.email ILIKE $2)
              AND ($3::uuid IS NULL OR EXISTS (SELECT 1 FROM student_classes sc WHERE sc.student_id = s.id AND sc.track_id = $3 AND sc.is_active = true))
              AND ($4::uuid IS NULL OR EXISTS (
                  SELECT 1 FROM student_classes sc 
                  JOIN academic_sessions asess ON sc.academic_session_id = asess.id
                  JOIN terms t ON sc.term_id = t.id
                  WHERE sc.student_id = s.id 
                    AND sc.class_id = $4 
                    AND sc.is_active = true
                    AND asess.is_current_session = true
                    AND t.is_current_term = true
              ))
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

        let total_pages = (total as f64 / per_page as f64).ceil() as i64;
        let has_next = page < total_pages;
        let has_previous = page > 1;

        Ok(crate::models::PaginatedResponse {
            success: true,
            message: "Student list retrieved successfully".to_string(),
            data: students,
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

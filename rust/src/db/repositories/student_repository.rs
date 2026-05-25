use uuid::Uuid;
use sqlx::PgPool;
use sqlx::{Transaction, Postgres, Executor};
use crate::errors::ApiError;
use crate::models::Student;

pub struct StudentRepository;

impl StudentRepository {
    /// Get student by ID
    pub async fn get_by_id(pool: &PgPool, student_id: Uuid) -> Result<Student, ApiError> {
        sqlx::query_as::<_, Student>(
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
        sqlx::query_as::<_, Student>(
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
        sqlx::query_as::<_, Student>(
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
        sqlx::query_as::<_, Student>(
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
        .bind(&student.gender)
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

    /// Create a new student within an existing transaction
        // transactional helpers removed; use `create` method on pool instead

    /// Update student
    pub async fn update(pool: &PgPool, student_id: Uuid, updates: &Student) -> Result<Student, ApiError> {
        sqlx::query_as::<_, Student>(
            r#"
            UPDATE students SET
                current_grade_level = $1, academic_status = $2, graduation_date = $3,
                updated_at = $4, is_active = $5
            WHERE id = $6
            RETURNING *
            "#
        )
        .bind(&updates.current_grade_level)
        .bind(&updates.academic_status)
        .bind(updates.graduation_date)
        .bind(updates.updated_at)
        .bind(updates.is_active)
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
}

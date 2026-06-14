use sqlx::{PgPool, Postgres, Transaction};
use uuid::Uuid;

use crate::errors::ApiError;
use crate::models::assessments::Examination;

pub struct ExaminationRepository;

impl ExaminationRepository {
    pub async fn get_by_id(
        pool: &PgPool,
        school_id: Uuid,
        examination_id: Uuid,
    ) -> Result<Option<Examination>, ApiError> {
        sqlx::query_as::<sqlx::Postgres, Examination>(
            r#"
            SELECT
                id, created_at, is_active, updated_at, school_id, created_by,
                duration_minutes, end_time, exam_type, is_published, start_time,
                title, total_marks, class_id, subject_id, is_online, session_id, term_id,
                questions_json
            FROM examinations
            WHERE id = $1 AND school_id = $2 AND is_active = true
            "#,
        )
        .bind(examination_id)
        .bind(school_id)
        .fetch_optional(pool)
        .await
        .map_err(|error| ApiError::DatabaseError(error.to_string()))
    }

    pub async fn create_in_transaction(
        tx: &mut Transaction<'_, Postgres>,
        examination: &Examination,
    ) -> Result<Examination, ApiError> {
        sqlx::query_as::<sqlx::Postgres, Examination>(
            r#"
            INSERT INTO examinations (
                id, created_at, is_active, updated_at, school_id, created_by,
                duration_minutes, end_time, exam_type, is_published, start_time,
                title, total_marks, class_id, subject_id, is_online, session_id, term_id,
                questions_json
            ) VALUES (
                $1, $2, $3, $4, $5, $6,
                $7, $8, $9, $10, $11,
                $12, $13, $14, $15, $16, $17, $18, $19
            )
            RETURNING
                id, created_at, is_active, updated_at, school_id, created_by,
                duration_minutes, end_time, exam_type, is_published, start_time,
                title, total_marks, class_id, subject_id, is_online, session_id, term_id,
                questions_json
            "#,
        )
        .bind(examination.id)
        .bind(examination.created_at)
        .bind(examination.is_active)
        .bind(examination.updated_at)
        .bind(examination.school_id)
        .bind(examination.created_by)
        .bind(examination.duration_minutes)
        .bind(examination.end_time)
        .bind(&examination.exam_type)
        .bind(examination.is_published)
        .bind(examination.start_time)
        .bind(&examination.title)
        .bind(examination.total_marks)
        .bind(examination.class_id)
        .bind(examination.subject_id)
        .bind(examination.is_online)
        .bind(examination.session_id)
        .bind(examination.term_id)
        .bind(&examination.questions_json)
        .fetch_one(&mut **tx)
        .await
        .map_err(|error| ApiError::DatabaseError(error.to_string()))
    }

    pub async fn update_in_transaction(
        tx: &mut Transaction<'_, Postgres>,
        examination: &Examination,
    ) -> Result<Examination, ApiError> {
        sqlx::query_as::<sqlx::Postgres, Examination>(
            r#"
            UPDATE examinations
            SET
                updated_at = $1,
                duration_minutes = $2,
                end_time = $3,
                exam_type = $4,
                is_published = $5,
                start_time = $6,
                title = $7,
                total_marks = $8,
                class_id = $9,
                subject_id = $10,
                is_online = $11,
                session_id = $12,
                term_id = $13,
                questions_json = $14
            WHERE id = $15 AND school_id = $16 AND is_active = true
            RETURNING
                id, created_at, is_active, updated_at, school_id, created_by,
                duration_minutes, end_time, exam_type, is_published, start_time,
                title, total_marks, class_id, subject_id, is_online, session_id, term_id,
                questions_json
            "#,
        )
        .bind(examination.updated_at)
        .bind(examination.duration_minutes)
        .bind(examination.end_time)
        .bind(&examination.exam_type)
        .bind(examination.is_published)
        .bind(examination.start_time)
        .bind(&examination.title)
        .bind(examination.total_marks)
        .bind(examination.class_id)
        .bind(examination.subject_id)
        .bind(examination.is_online)
        .bind(examination.session_id)
        .bind(examination.term_id)
        .bind(&examination.questions_json)
        .bind(examination.id)
        .bind(examination.school_id)
        .fetch_one(&mut **tx)
        .await
        .map_err(|error| ApiError::DatabaseError(error.to_string()))
    }

    pub async fn list(
        pool: &PgPool,
        school_id: Uuid,
        session_id: Option<Uuid>,
        term_id: Option<Uuid>,
        class_id: Option<Uuid>,
        subject_id: Option<Uuid>,
        page: i64,
        per_page: i64,
    ) -> Result<crate::models::PaginatedResponse<Examination>, ApiError> {
        let limit = per_page;
        let offset = (page - 1) * per_page;

        let total = sqlx::query_scalar::<sqlx::Postgres, i64>(
            r#"
            SELECT COUNT(*)
            FROM examinations
            WHERE school_id = $1
              AND is_active = true
              AND ($2::uuid IS NULL OR session_id = $2)
              AND ($3::uuid IS NULL OR term_id = $3)
              AND ($4::uuid IS NULL OR class_id = $4)
              AND ($5::uuid IS NULL OR subject_id = $5)
            "#,
        )
        .bind(school_id)
        .bind(session_id)
        .bind(term_id)
        .bind(class_id)
        .bind(subject_id)
        .fetch_one(pool)
        .await
        .map_err(|error| ApiError::DatabaseError(error.to_string()))?;

        let examinations = sqlx::query_as::<sqlx::Postgres, Examination>(
            r#"
            SELECT
                id, created_at, is_active, updated_at, school_id, created_by,
                duration_minutes, end_time, exam_type, is_published, start_time,
                title, total_marks, class_id, subject_id, is_online, session_id, term_id,
                questions_json
            FROM examinations
            WHERE school_id = $1
              AND is_active = true
              AND ($2::uuid IS NULL OR session_id = $2)
              AND ($3::uuid IS NULL OR term_id = $3)
              AND ($4::uuid IS NULL OR class_id = $4)
              AND ($5::uuid IS NULL OR subject_id = $5)
            ORDER BY created_at DESC
            LIMIT $6 OFFSET $7
            "#,
        )
        .bind(school_id)
        .bind(session_id)
        .bind(term_id)
        .bind(class_id)
        .bind(subject_id)
        .bind(limit)
        .bind(offset)
        .fetch_all(pool)
        .await
        .map_err(|error| ApiError::DatabaseError(error.to_string()))?;

        let total_pages = if total == 0 { 1 } else { (total as f64 / per_page as f64).ceil() as i64 };
        let has_next = page < total_pages;
        let has_previous = page > 1;

        Ok(crate::models::PaginatedResponse {
            success: true,
            message: "Examinations retrieved successfully".to_string(),
            data: examinations,
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

    pub async fn delete(
        pool: &PgPool,
        school_id: Uuid,
        examination_id: Uuid,
    ) -> Result<(), ApiError> {
        let rows_affected = sqlx::query(
            r#"
            UPDATE examinations
            SET is_active = false, updated_at = NOW()
            WHERE id = $1 AND school_id = $2 AND is_active = true
            "#,
        )
        .bind(examination_id)
        .bind(school_id)
        .execute(pool)
        .await
        .map_err(|error| ApiError::DatabaseError(error.to_string()))?
        .rows_affected();

        if rows_affected == 0 {
            return Err(ApiError::NotFound("Examination not found or already deleted".to_string()));
        }

        Ok(())
    }
}

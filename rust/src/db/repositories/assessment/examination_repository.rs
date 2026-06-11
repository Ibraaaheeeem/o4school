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
                title, total_marks, class_id, subject_id, is_online, session_id, term_id
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
                title, total_marks, class_id, subject_id, is_online, session_id, term_id
            ) VALUES (
                $1, $2, $3, $4, $5, $6,
                $7, $8, $9, $10, $11,
                $12, $13, $14, $15, $16, $17, $18
            )
            RETURNING
                id, created_at, is_active, updated_at, school_id, created_by,
                duration_minutes, end_time, exam_type, is_published, start_time,
                title, total_marks, class_id, subject_id, is_online, session_id, term_id
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
                term_id = $13
            WHERE id = $14 AND school_id = $15 AND is_active = true
            RETURNING
                id, created_at, is_active, updated_at, school_id, created_by,
                duration_minutes, end_time, exam_type, is_published, start_time,
                title, total_marks, class_id, subject_id, is_online, session_id, term_id
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
        .bind(examination.id)
        .bind(examination.school_id)
        .fetch_one(&mut **tx)
        .await
        .map_err(|error| ApiError::DatabaseError(error.to_string()))
    }
}

use chrono::Utc;
use sqlx::{Postgres, Transaction};
use uuid::Uuid;

use crate::errors::ApiError;
use crate::models::assessments::SubjectScore;

#[derive(Debug, Clone, sqlx::FromRow)]
pub struct SubjectScoreTarget {
    pub class_subject_id: Uuid,
    pub subject_id: Uuid,
}

pub struct SubjectScoreRepository;

impl SubjectScoreRepository {
    pub async fn list_targets_for_class_subject_ids(
        pool: &sqlx::PgPool,
        school_id: Uuid,
        class_subject_ids: &[Uuid],
    ) -> Result<Vec<SubjectScoreTarget>, ApiError> {
        if class_subject_ids.is_empty() {
            return Ok(vec![]);
        }

        sqlx::query_as::<sqlx::Postgres, SubjectScoreTarget>(
            r#"
                        SELECT
                                cs.id AS class_subject_id,
                ss.subject_id AS subject_id
                        FROM class_subjects cs
            INNER JOIN school_subjects ss ON ss.id = cs.school_subject_id
                        WHERE cs.school_id = $1
                            AND cs.id = ANY($2)
                            AND cs.is_active = true
                        ORDER BY cs.created_at ASC
            "#,
        )
        .bind(school_id)
        .bind(class_subject_ids.to_vec())
        .fetch_all(pool)
        .await
        .map_err(|error| ApiError::DatabaseError(error.to_string()))
    }

    pub async fn upsert_in_transaction(
        tx: &mut Transaction<'_, Postgres>,
        school_id: Uuid,
        assessment_id: Uuid,
        target: &SubjectScoreTarget,
        grade: Option<String>,
        position: Option<i32>,
        remark: Option<String>,
        scores_json: Option<String>,
    ) -> Result<SubjectScore, ApiError> {
        let now = Utc::now().naive_utc();
        sqlx::query_as::<sqlx::Postgres, SubjectScore>(
            r#"
            INSERT INTO subject_scores (
                id, created_at, is_active, updated_at, school_id, grade,
                position, remark, scores_json, assessment_id, class_subject_id, subject_id
            ) VALUES (
                $1, $2, true, $3, $4, $5,
                $6, $7, $8, $9, $10, $11
            )
            ON CONFLICT (assessment_id, class_subject_id)
            DO UPDATE SET
                updated_at = EXCLUDED.updated_at,
                is_active = true,
                grade = EXCLUDED.grade,
                position = EXCLUDED.position,
                remark = EXCLUDED.remark,
                scores_json = EXCLUDED.scores_json,
                subject_id = EXCLUDED.subject_id
            RETURNING *
            "#,
        )
        .bind(Uuid::new_v4())
        .bind(now)
        .bind(now)
        .bind(school_id)
        .bind(grade)
        .bind(position)
        .bind(remark)
        .bind(scores_json)
        .bind(assessment_id)
        .bind(target.class_subject_id)
        .bind(target.subject_id)
        .fetch_one(&mut **tx)
        .await
        .map_err(|error| ApiError::DatabaseError(error.to_string()))
    }
}

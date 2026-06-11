use chrono::Utc;
use sqlx::{Postgres, Transaction};
use uuid::Uuid;

use crate::errors::ApiError;
use crate::models::assessments::Assessment;

#[derive(Debug, Clone, sqlx::FromRow)]
pub struct AssessmentTarget {
    pub student_id: Uuid,
    pub track_id: Uuid,
    pub admission_number: Option<String>,
    pub academic_session_id: Option<Uuid>,
    pub term_id: Option<Uuid>,
}

pub struct AssessmentRecordRepository;

impl AssessmentRecordRepository {
    #[allow(clippy::too_many_arguments)]
    pub async fn update_assessment(
        pool: &sqlx::PgPool,
        school_id: Uuid,
        assessment_id: Uuid,
        attendance: Option<i32>,
        attentiveness: Option<i32>,
        class_teacher_comment: Option<String>,
        critical_thinking: Option<i32>,
        fluency: Option<i32>,
        game: Option<i32>,
        handwriting: Option<i32>,
        head_teacher_comment: Option<String>,
        initiative: Option<i32>,
        neatness: Option<i32>,
        politeness: Option<i32>,
        punctuality: Option<i32>,
        self_discipline: Option<i32>,
    ) -> Result<Option<Assessment>, ApiError> {
        let now = Utc::now().naive_utc();
        sqlx::query_as::<sqlx::Postgres, Assessment>(
            r#"
            UPDATE assessments
            SET
                updated_at = $1,
                attendance = COALESCE($2, attendance),
                attentiveness = COALESCE($3, attentiveness),
                class_teacher_comment = COALESCE($4, class_teacher_comment),
                critical_thinking = CASE WHEN $5 IS NULL THEN critical_thinking ELSE $5 END,
                fluency = COALESCE($6, fluency),
                game = COALESCE($7, game),
                handwriting = COALESCE($8, handwriting),
                head_teacher_comment = COALESCE($9, head_teacher_comment),
                initiative = COALESCE($10, initiative),
                neatness = COALESCE($11, neatness),
                politeness = COALESCE($12, politeness),
                punctuality = COALESCE($13, punctuality),
                self_discipline = CASE WHEN $14 IS NULL THEN self_discipline ELSE $14 END
            WHERE id = $15
              AND school_id = $16
              AND is_active = true
            RETURNING *
            "#,
        )
        .bind(now)
        .bind(attendance)
        .bind(attentiveness)
        .bind(class_teacher_comment)
        .bind(critical_thinking)
        .bind(fluency)
        .bind(game)
        .bind(handwriting)
        .bind(head_teacher_comment)
        .bind(initiative)
        .bind(neatness)
        .bind(politeness)
        .bind(punctuality)
        .bind(self_discipline)
        .bind(assessment_id)
        .bind(school_id)
        .fetch_optional(pool)
        .await
        .map_err(|error| ApiError::DatabaseError(error.to_string()))
    }

    pub async fn list_by_student_session_term_track(
        pool: &sqlx::PgPool,
        school_id: Uuid,
        student_id: Uuid,
        academic_session_id: Uuid,
        term_id: Uuid,
        track_id: Uuid,
    ) -> Result<Vec<Assessment>, ApiError> {
        sqlx::query_as::<sqlx::Postgres, Assessment>(
            r#"
            SELECT *
            FROM assessments
            WHERE school_id = $1
              AND student_id = $2
              AND academic_session_id = $3
              AND term_id = $4
              AND track_id = $5
              AND is_active = true
            ORDER BY created_at ASC
            "#,
        )
        .bind(school_id)
        .bind(student_id)
        .bind(academic_session_id)
        .bind(term_id)
        .bind(track_id)
        .fetch_all(pool)
        .await
        .map_err(|error| ApiError::DatabaseError(error.to_string()))
    }

    pub async fn list_targets_for_classes(
        pool: &sqlx::PgPool,
        school_id: Uuid,
        class_ids: &[Uuid],
        academic_session_id: Option<Uuid>,
        term_id: Option<Uuid>,
    ) -> Result<Vec<AssessmentTarget>, ApiError> {
        if class_ids.is_empty() {
            return Ok(vec![]);
        }

        let mut sql = String::from(
            r#"
            SELECT DISTINCT
                sc.student_id,
                sc.track_id,
                st.admission_number,
                sc.academic_session_id,
                sc.term_id
            FROM student_classes sc
            INNER JOIN students st ON st.id = sc.student_id
            WHERE sc.school_id = $1
              AND sc.class_id = ANY($2)
              AND sc.is_active = true
              AND st.is_active = true
            "#,
        );

        if academic_session_id.is_some() {
            sql.push_str(" AND sc.academic_session_id = $3");
        }
        if term_id.is_some() {
            let term_position = if academic_session_id.is_some() { 4 } else { 3 };
            sql.push_str(&format!(" AND sc.term_id = ${}", term_position));
        }

        sql.push_str(" ORDER BY sc.student_id ASC");

        let mut query = sqlx::query_as::<sqlx::Postgres, AssessmentTarget>(&sql)
            .bind(school_id)
            .bind(class_ids.to_vec());

        if let Some(academic_session_id) = academic_session_id {
            query = query.bind(academic_session_id);
        }
        if let Some(term_id) = term_id {
            query = query.bind(term_id);
        }

        query
            .fetch_all(pool)
            .await
            .map_err(|error| ApiError::DatabaseError(error.to_string()))
    }

    pub async fn create_in_transaction(
        tx: &mut Transaction<'_, Postgres>,
        school_id: Uuid,
        target: &AssessmentTarget,
        academic_session_id: Option<Uuid>,
        term_id: Option<Uuid>,
    ) -> Result<Assessment, ApiError> {
        let now = Utc::now().naive_utc();
        let assessment = sqlx::query_as::<sqlx::Postgres, Assessment>(
            r#"
            INSERT INTO assessments (
                id, created_at, is_active, updated_at, school_id, track_id,
                admission_number, attendance, attentiveness, class_teacher_comment,
                critical_thinking, fluency, game, handwriting, head_teacher_comment,
                initiative, neatness, politeness, punctuality, self_discipline,
                student_id, academic_session_id, term_id
            ) VALUES (
                $1, $2, true, $3, $4, $5,
                $6, 0, 0, NULL,
                NULL, 0, 0, 0, NULL,
                0, 0, 0, 0, NULL,
                $7, $8, $9
            )
            RETURNING *
            "#,
        )
        .bind(Uuid::new_v4())
        .bind(now)
        .bind(now)
        .bind(school_id)
        .bind(target.track_id)
        .bind(
            target
                .admission_number
                .clone()
                .unwrap_or_else(|| target.student_id.to_string()),
        )
        .bind(target.student_id)
        .bind(academic_session_id.or(target.academic_session_id))
        .bind(term_id.or(target.term_id))
        .fetch_one(&mut **tx)
        .await
        .map_err(|error| ApiError::DatabaseError(error.to_string()))?;

        Ok(assessment)
    }
}

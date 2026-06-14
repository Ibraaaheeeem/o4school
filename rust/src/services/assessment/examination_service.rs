use std::collections::HashSet;

use chrono::Utc;
use uuid::Uuid;

use crate::db::repositories::ExaminationRepository;
use crate::db::Database;
use crate::errors::ApiError;
use crate::models::assessments::{CreateExaminationRequest, Examination, UpdateExaminationRequest};

pub struct ExaminationService;

impl ExaminationService {
    fn validate_create_request(request: &CreateExaminationRequest) -> Result<(), ApiError> {
        if request.class_ids.is_empty() {
            return Err(ApiError::ValidationError(
                "At least one class_id is required".to_string(),
            ));
        }

        if request.subject_ids.is_empty() {
            return Err(ApiError::ValidationError(
                "At least one subject_id is required".to_string(),
            ));
        }

        if let Some(duration_minutes) = request.duration_minutes {
            if duration_minutes <= 0 {
                return Err(ApiError::ValidationError(
                    "duration_minutes must be greater than zero".to_string(),
                ));
            }
        }

        Ok(())
    }

    fn validate_update_request(request: &UpdateExaminationRequest) -> Result<(), ApiError> {
        if request.examination_ids.is_empty() {
            return Err(ApiError::ValidationError(
                "At least one examination_id is required".to_string(),
            ));
        }

        if request.duration_minutes == Some(0) {
            return Err(ApiError::ValidationError(
                "duration_minutes must be greater than zero".to_string(),
            ));
        }

        if request.duration_minutes.is_none()
            && request.end_time.is_none()
            && request.exam_type.is_none()
            && request.is_published.is_none()
            && request.start_time.is_none()
            && request.title.is_none()
            && request.total_marks.is_none()
            && request.is_online.is_none()
            && request.session_id.is_none()
            && request.term_id.is_none()
        {
            return Err(ApiError::ValidationError(
                "At least one field must be provided to update".to_string(),
            ));
        }

        Ok(())
    }

    fn dedupe_ids(ids: &[Uuid], field_name: &str) -> Result<Vec<Uuid>, ApiError> {
        let mut seen = HashSet::new();
        let mut unique_ids = Vec::with_capacity(ids.len());

        for id in ids {
            if seen.insert(*id) {
                unique_ids.push(*id);
            }
        }

        if unique_ids.is_empty() {
            return Err(ApiError::ValidationError(format!(
                "At least one {} is required",
                field_name
            )));
        }

        Ok(unique_ids)
    }

    pub async fn create_examinations(
        db: &Database,
        request: &CreateExaminationRequest,
    ) -> Result<Vec<Examination>, ApiError> {
        Self::validate_create_request(request)?;

        let class_ids = Self::dedupe_ids(&request.class_ids, "class_id")?;
        let subject_ids = Self::dedupe_ids(&request.subject_ids, "subject_id")?;
        let now = Utc::now().naive_utc();

        let mut tx = db
            .pool()
            .begin()
            .await
            .map_err(|error| ApiError::DatabaseError(error.to_string()))?;

        let mut created = Vec::with_capacity(class_ids.len() * subject_ids.len());

        for class_id in class_ids {
            for subject_id in &subject_ids {
                let exam = Examination {
                    id: Uuid::new_v4(),
                    created_at: now,
                    is_active: true,
                    updated_at: now,
                    school_id: request.school_id,
                    created_by: request.created_by,
                    duration_minutes: request.duration_minutes,
                    end_time: request.end_time,
                    exam_type: request.exam_type.clone(),
                    is_published: request.is_published,
                    start_time: request.start_time,
                    title: request.title.clone(),
                    total_marks: request.total_marks,
                    class_id,
                    subject_id: *subject_id,
                    is_online: request.is_online,
                    session_id: request.session_id,
                    term_id: request.term_id,
                    questions_json: request.questions_json.clone(),
                };

                let inserted = ExaminationRepository::create_in_transaction(&mut tx, &exam).await?;
                created.push(inserted);
            }
        }

        tx.commit()
            .await
            .map_err(|error| ApiError::DatabaseError(error.to_string()))?;

        Ok(created)
    }

    pub async fn update_examinations(
        db: &Database,
        request: &UpdateExaminationRequest,
    ) -> Result<Vec<Examination>, ApiError> {
        Self::validate_update_request(request)?;
        let examination_ids = Self::dedupe_ids(&request.examination_ids, "examination_id")?;

        let mut tx = db
            .pool()
            .begin()
            .await
            .map_err(|error| ApiError::DatabaseError(error.to_string()))?;

        let mut updated = Vec::with_capacity(examination_ids.len());

        for examination_id in examination_ids {
            let existing: Examination = ExaminationRepository::get_by_id(db.pool(), request.school_id, examination_id)
                .await?
                .ok_or_else(|| ApiError::NotFound("Examination not found".to_string()))?;

            let next = Examination {
                id: existing.id,
                created_at: existing.created_at,
                is_active: existing.is_active,
                updated_at: Utc::now().naive_utc(),
                school_id: existing.school_id,
                created_by: existing.created_by,
                duration_minutes: request.duration_minutes.or(existing.duration_minutes),
                end_time: request.end_time.or(existing.end_time),
                exam_type: request
                    .exam_type
                    .clone()
                    .unwrap_or_else(|| existing.exam_type.clone()),
                is_published: request.is_published.or(existing.is_published),
                start_time: request.start_time.or(existing.start_time),
                title: request
                    .title
                    .clone()
                    .unwrap_or_else(|| existing.title.clone()),
                total_marks: request.total_marks.or(existing.total_marks),
                class_id: existing.class_id,
                subject_id: existing.subject_id,
                is_online: request.is_online.unwrap_or(existing.is_online),
                session_id: request.session_id.unwrap_or(existing.session_id),
                term_id: request.term_id.unwrap_or(existing.term_id),
                questions_json: request.questions_json.clone().or(existing.questions_json),
            };

            let saved = ExaminationRepository::update_in_transaction(&mut tx, &next).await?;
            updated.push(saved);
        }

        tx.commit()
            .await
            .map_err(|error| ApiError::DatabaseError(error.to_string()))?;

        Ok(updated)
    }

    pub async fn list_examinations(
        db: &Database,
        school_id: Uuid,
        session_id: Option<Uuid>,
        term_id: Option<Uuid>,
        class_id: Option<Uuid>,
        subject_id: Option<Uuid>,
        page: i64,
        per_page: i64,
    ) -> Result<crate::models::PaginatedResponse<Examination>, ApiError> {
        let p = if page <= 0 { 1 } else { page };
        let pp = if per_page <= 0 { 20 } else { per_page };
        ExaminationRepository::list(db.pool(), school_id, session_id, term_id, class_id, subject_id, p, pp).await
    }

    pub async fn delete_examination(
        db: &Database,
        school_id: Uuid,
        examination_id: Uuid,
    ) -> Result<(), ApiError> {
        ExaminationRepository::delete(db.pool(), school_id, examination_id).await
    }
}

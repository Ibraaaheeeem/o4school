use crate::db::repositories::assessment::SubjectScoreRepository;
use crate::db::repositories::assessment::subject_score_repository::SubjectScoreTarget;
use crate::db::repositories::AssessmentRecordRepository;
use crate::db::repositories::ScoringSchemeRepository;
use crate::db::Database;
use crate::errors::ApiError;
use crate::models::assessments::{
    Assessment, ClassAssessmentContextResponse, CreateAssessmentRequest, SaveSubjectScoresRequest,
    SubjectScore, UpdateAssessmentRequest,
};
use uuid::Uuid;

pub mod examination_service;

pub use examination_service::ExaminationService;

pub struct AssessmentService;

impl AssessmentService {
    pub async fn save_modified_assessment(
        db: &Database,
        request: &UpdateAssessmentRequest,
    ) -> Result<Assessment, ApiError> {
        let numeric_fields = [
            ("attendance", request.attendance),
            ("attentiveness", request.attentiveness),
            ("critical_thinking", request.critical_thinking),
            ("fluency", request.fluency),
            ("game", request.game),
            ("handwriting", request.handwriting),
            ("initiative", request.initiative),
            ("neatness", request.neatness),
            ("politeness", request.politeness),
            ("punctuality", request.punctuality),
            ("self_discipline", request.self_discipline),
        ];

        for (field_name, value) in numeric_fields {
            if let Some(value) = value {
                if value < 0 {
                    return Err(ApiError::ValidationError(format!(
                        "{} cannot be negative",
                        field_name
                    )));
                }
            }
        }

        let updated: Option<Assessment> = AssessmentRecordRepository::update_assessment(
            db.pool(),
            request.school_id,
            request.assessment_id,
            request.attendance,
            request.attentiveness,
            request.class_teacher_comment.clone(),
            request.critical_thinking,
            request.fluency,
            request.game,
            request.handwriting,
            request.head_teacher_comment.clone(),
            request.initiative,
            request.neatness,
            request.politeness,
            request.punctuality,
            request.self_discipline,
        )
        .await?;

        updated.ok_or_else(|| {
            ApiError::NotFound("Assessment not found for this school".to_string())
        })
    }

    pub async fn save_subject_scores(
        db: &Database,
        request: &SaveSubjectScoresRequest,
    ) -> Result<Vec<SubjectScore>, ApiError> {
        if request.subject_scores.is_empty() {
            return Err(ApiError::ValidationError(
                "At least one subject score is required".to_string(),
            ));
        }

        let mut unique_class_subject_ids = std::collections::BTreeSet::new();
        for item in &request.subject_scores {
            unique_class_subject_ids.insert(item.class_subject_id);
        }
        let class_subject_ids = unique_class_subject_ids.into_iter().collect::<Vec<_>>();

        let targets: Vec<SubjectScoreTarget> = SubjectScoreRepository::list_targets_for_class_subject_ids(
            db.pool(),
            request.school_id,
            &class_subject_ids,
        )
        .await?;

        if targets.len() != class_subject_ids.len() {
            return Err(ApiError::NotFound(
                "One or more class_subject_ids were not found for this school".to_string(),
            ));
        }

        let target_map = targets
            .into_iter()
            .map(|target| (target.class_subject_id, target))
            .collect::<std::collections::HashMap<_, _>>();

        let mut tx = db
            .pool()
            .begin()
            .await
            .map_err(|error| ApiError::DatabaseError(error.to_string()))?;

        let mut saved = Vec::with_capacity(request.subject_scores.len());
        for item in &request.subject_scores {
            let target = target_map.get(&item.class_subject_id).ok_or_else(|| {
                ApiError::NotFound(format!(
                    "Class subject {} was not found for this school",
                    item.class_subject_id
                ))
            })?;

            let scores_json = item
                .scores_json
                .as_ref()
                .map(|value| serde_json::to_string(value))
                .transpose()
                .map_err(|error| {
                    ApiError::ValidationError(format!("Invalid scores_json payload: {}", error))
                })?;

            let subject_score = SubjectScoreRepository::upsert_in_transaction(
                &mut tx,
                request.school_id,
                request.assessment_id,
                target,
                item.grade.clone(),
                item.position,
                item.remark.clone(),
                scores_json,
            )
            .await?;
            saved.push(subject_score);
        }

        tx.commit()
            .await
            .map_err(|error| ApiError::DatabaseError(error.to_string()))?;

        Ok(saved)
    }

    pub async fn get_class_assessment_context(
        db: &Database,
        school_id: Uuid,
        class_id: Uuid,
        academic_session_id: Uuid,
        term_id: Uuid,
    ) -> Result<ClassAssessmentContextResponse, ApiError> {
        let class_subjects =
            ScoringSchemeRepository::list_class_subjects_for_class(db.pool(), school_id, class_id)
                .await?;

        let scoring_scheme = ScoringSchemeRepository::get_by_class_session_term(
            db.pool(),
            school_id,
            class_id,
            Some(academic_session_id),
            Some(term_id),
        )
        .await?;

        Ok(ClassAssessmentContextResponse {
            class_subjects,
            scoring_scheme,
        })
    }

    pub async fn get_student_assessments_for_context(
        db: &Database,
        school_id: Uuid,
        student_id: Uuid,
        academic_session_id: Uuid,
        term_id: Uuid,
        track_id: Uuid,
    ) -> Result<Vec<Assessment>, ApiError> {
        AssessmentRecordRepository::list_by_student_session_term_track(
            db.pool(),
            school_id,
            student_id,
            academic_session_id,
            term_id,
            track_id,
        )
        .await
    }

    pub async fn create_assessments_for_classes(
        db: &Database,
        request: &CreateAssessmentRequest,
    ) -> Result<Vec<Assessment>, ApiError> {
        if request.class_ids.is_empty() {
            return Err(ApiError::ValidationError(
                "At least one class_id is required".to_string(),
            ));
        }

        let class_ids = request
            .class_ids
            .iter()
            .copied()
            .collect::<std::collections::BTreeSet<_>>()
            .into_iter()
            .collect::<Vec<_>>();

        let targets = AssessmentRecordRepository::list_targets_for_classes(
            db.pool(),
            request.school_id,
            &class_ids,
            request.academic_session_id,
            request.term_id,
        )
        .await?;

        let mut tx = db
            .pool()
            .begin()
            .await
            .map_err(|error| ApiError::DatabaseError(error.to_string()))?;

        let mut created = Vec::with_capacity(targets.len());
        for target in targets.iter() {
            let assessment = AssessmentRecordRepository::create_in_transaction(
                &mut tx,
                request.school_id,
                target,
                request.academic_session_id,
                request.term_id,
            )
            .await?;
            created.push(assessment);
        }

        tx.commit()
            .await
            .map_err(|error| ApiError::DatabaseError(error.to_string()))?;

        Ok(created)
    }
}

use actix_web::{web, HttpResponse};
use crate::errors::ApiError;
use crate::db::Database;
use crate::models::assessments::{
    CreateAssessmentRequest, SaveSubjectScoresRequest, UpdateAssessmentRequest,
};
use crate::services::assessment::AssessmentService;
use serde::Deserialize;

pub mod examination;
pub mod scoring_scheme;

#[derive(Debug, Clone, Deserialize)]
pub struct ClassAssessmentContextQuery {
    pub school_id: uuid::Uuid,
    pub class_id: uuid::Uuid,
    pub academic_session_id: uuid::Uuid,
    pub term_id: uuid::Uuid,
}

#[derive(Debug, Clone, Deserialize)]
pub struct StudentAssessmentQuery {
    pub school_id: uuid::Uuid,
    pub student_id: uuid::Uuid,
    pub academic_session_id: uuid::Uuid,
    pub term_id: uuid::Uuid,
    pub track_id: uuid::Uuid,
}

pub async fn get_class_assessment_context(
    db: web::Data<Database>,
    query: web::Query<ClassAssessmentContextQuery>,
) -> Result<HttpResponse, ApiError> {
    let q = query.into_inner();
    let response = AssessmentService::get_class_assessment_context(
        &db,
        q.school_id,
        q.class_id,
        q.academic_session_id,
        q.term_id,
    )
    .await?;

    Ok(HttpResponse::Ok().json(response))
}

pub async fn get_student_assessments_for_context(
    db: web::Data<Database>,
    query: web::Query<StudentAssessmentQuery>,
) -> Result<HttpResponse, ApiError> {
    let q = query.into_inner();
    let response = AssessmentService::get_student_assessments_for_context(
        &db,
        q.school_id,
        q.student_id,
        q.academic_session_id,
        q.term_id,
        q.track_id,
    )
    .await?;

    Ok(HttpResponse::Ok().json(response))
}

pub async fn create_assessments(
    db: web::Data<Database>,
    request: web::Json<CreateAssessmentRequest>,
) -> Result<HttpResponse, ApiError> {
    let assessments = AssessmentService::create_assessments_for_classes(&db, &request.into_inner()).await?;
    Ok(HttpResponse::Created().json(assessments))
}

pub async fn save_modified_assessment(
    db: web::Data<Database>,
    request: web::Json<UpdateAssessmentRequest>,
) -> Result<HttpResponse, ApiError> {
    let assessment = AssessmentService::save_modified_assessment(&db, &request.into_inner()).await?;
    Ok(HttpResponse::Ok().json(assessment))
}

pub async fn save_subject_scores(
    db: web::Data<Database>,
    request: web::Json<SaveSubjectScoresRequest>,
) -> Result<HttpResponse, ApiError> {
    let subject_scores = AssessmentService::save_subject_scores(&db, &request.into_inner()).await?;
    Ok(HttpResponse::Created().json(subject_scores))
}

pub fn configure(cfg: &mut web::ServiceConfig) {
    cfg.configure(scoring_scheme::configure)
        .configure(examination::configure)
        .route("/assessments/class-context", web::get().to(get_class_assessment_context))
        .route("/assessments/student", web::get().to(get_student_assessments_for_context))
        .route("/assessments", web::put().to(save_modified_assessment))
        .route("/subject-scores", web::post().to(save_subject_scores))
        .route("/assessments", web::post().to(create_assessments));
}

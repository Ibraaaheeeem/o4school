use actix_web::{web, HttpResponse};
use serde::Deserialize;
use uuid::Uuid;

use crate::db::Database;
use crate::errors::ApiError;
use crate::models::assessments::{CreateExaminationRequest, UpdateExaminationRequest};
use crate::services::assessment::ExaminationService;

#[derive(Debug, Deserialize)]
pub struct ListExaminationsQuery {
    pub school_id: Uuid,
    pub session_id: Option<Uuid>,
    pub term_id: Option<Uuid>,
    pub class_id: Option<Uuid>,
    pub subject_id: Option<Uuid>,
    pub page: Option<i64>,
    pub per_page: Option<i64>,
}

#[derive(Debug, Deserialize)]
pub struct DeleteExaminationQuery {
    pub school_id: Uuid,
}

pub async fn create_examinations(
    db: web::Data<Database>,
    request: web::Json<CreateExaminationRequest>,
) -> Result<HttpResponse, ApiError> {
    let examinations = ExaminationService::create_examinations(&db, &request.into_inner()).await?;
    Ok(HttpResponse::Created().json(examinations))
}

pub async fn update_examinations(
    db: web::Data<Database>,
    request: web::Json<UpdateExaminationRequest>,
) -> Result<HttpResponse, ApiError> {
    let examinations = ExaminationService::update_examinations(&db, &request.into_inner()).await?;
    Ok(HttpResponse::Ok().json(examinations))
}

pub async fn list_examinations(
    db: web::Data<Database>,
    query: web::Query<ListExaminationsQuery>,
) -> Result<HttpResponse, ApiError> {
    let q = query.into_inner();
    let res = ExaminationService::list_examinations(
        &db,
        q.school_id,
        q.session_id,
        q.term_id,
        q.class_id,
        q.subject_id,
        q.page.unwrap_or(1),
        q.per_page.unwrap_or(20),
    ).await?;
    Ok(HttpResponse::Ok().json(res))
}

pub async fn delete_examination(
    db: web::Data<Database>,
    examination_id: web::Path<Uuid>,
    query: web::Query<DeleteExaminationQuery>,
) -> Result<HttpResponse, ApiError> {
    ExaminationService::delete_examination(&db, query.school_id, examination_id.into_inner()).await?;
    Ok(HttpResponse::Ok().json(serde_json::json!({
        "success": true,
        "message": "Examination deleted successfully"
    })))
}

pub fn configure(cfg: &mut web::ServiceConfig) {
    cfg.route("/examinations", web::post().to(create_examinations))
        .route("/examinations", web::put().to(update_examinations))
        .route("/examinations", web::get().to(list_examinations))
        .route("/examinations/{id}", web::delete().to(delete_examination));
}

use actix_web::{web, HttpResponse};

use crate::db::Database;
use crate::errors::ApiError;
use crate::models::assessments::{CreateExaminationRequest, UpdateExaminationRequest};
use crate::services::assessment::ExaminationService;

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

pub fn configure(cfg: &mut web::ServiceConfig) {
    cfg.route("/examinations", web::post().to(create_examinations))
        .route("/examinations", web::put().to(update_examinations));
}

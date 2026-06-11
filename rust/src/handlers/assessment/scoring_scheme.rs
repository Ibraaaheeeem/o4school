use actix_web::{web, HttpResponse};
use uuid::Uuid;

use crate::db::Database;
use crate::errors::ApiError;
use crate::models::academic::{CreateScoringSchemeRequest, UpdateScoringSchemeRequest};
use crate::services::academic::AcademicService;

/// Get a scoring scheme by ID
pub async fn get_scoring_scheme(
    db: web::Data<Database>,
    path: web::Path<Uuid>,
    school_id: web::Query<String>,
) -> Result<HttpResponse, ApiError> {
    let scoring_scheme_id = path.into_inner();
    let school_id = Uuid::parse_str(&school_id.into_inner())
        .map_err(|_| ApiError::ValidationError("Invalid school ID".to_string()))?;

    match AcademicService::get_scoring_scheme(&db, school_id, scoring_scheme_id).await? {
        Some(scheme) => Ok(HttpResponse::Ok().json(scheme)),
        None => Err(ApiError::NotFound("Scoring scheme not found".to_string())),
    }
}

/// Get scoring scheme for a class in a specific session and term
pub async fn get_scoring_scheme_for_class(
    db: web::Data<Database>,
    query: web::Query<std::collections::HashMap<String, String>>,
) -> Result<HttpResponse, ApiError> {
    let school_id = Uuid::parse_str(query.get("school_id").ok_or_else(|| {
        ApiError::ValidationError("school_id is required".to_string())
    })?)
    .map_err(|_| ApiError::ValidationError("Invalid school ID".to_string()))?;

    let class_id = Uuid::parse_str(query.get("class_id").ok_or_else(|| {
        ApiError::ValidationError("class_id is required".to_string())
    })?)
    .map_err(|_| ApiError::ValidationError("Invalid class ID".to_string()))?;

    let academic_session_id = query
        .get("academic_session_id")
        .and_then(|id| Uuid::parse_str(id).ok());

    let term_id = query
        .get("term_id")
        .and_then(|id| Uuid::parse_str(id).ok());

    match AcademicService::get_scoring_scheme_for_class(
        &db,
        school_id,
        class_id,
        academic_session_id,
        term_id,
    )
    .await?
    {
        Some(scheme) => Ok(HttpResponse::Ok().json(scheme)),
        None => Err(ApiError::NotFound(
            "Scoring scheme not found for this class".to_string(),
        )),
    }
}

/// List all scoring schemes for a class
pub async fn list_scoring_schemes_for_class(
    db: web::Data<Database>,
    query: web::Query<std::collections::HashMap<String, String>>,
) -> Result<HttpResponse, ApiError> {
    let school_id = Uuid::parse_str(query.get("school_id").ok_or_else(|| {
        ApiError::ValidationError("school_id is required".to_string())
    })?)
    .map_err(|_| ApiError::ValidationError("Invalid school ID".to_string()))?;

    let class_id = Uuid::parse_str(query.get("class_id").ok_or_else(|| {
        ApiError::ValidationError("class_id is required".to_string())
    })?)
    .map_err(|_| ApiError::ValidationError("Invalid class ID".to_string()))?;

    let schemes = AcademicService::list_scoring_schemes_for_class(&db, school_id, class_id).await?;

    Ok(HttpResponse::Ok().json(schemes))
}

/// Create a new scoring scheme
pub async fn create_scoring_scheme(
    db: web::Data<Database>,
    request: web::Json<CreateScoringSchemeRequest>,
) -> Result<HttpResponse, ApiError> {
    let scheme = AcademicService::create_scoring_scheme(&db, &request.into_inner()).await?;
    Ok(HttpResponse::Created().json(scheme))
}

/// Update a scoring scheme
pub async fn update_scoring_scheme(
    db: web::Data<Database>,
    path: web::Path<Uuid>,
    query: web::Query<std::collections::HashMap<String, String>>,
    request: web::Json<UpdateScoringSchemeRequest>,
) -> Result<HttpResponse, ApiError> {
    let scoring_scheme_id = path.into_inner();
    let school_id = Uuid::parse_str(query.get("school_id").ok_or_else(|| {
        ApiError::ValidationError("school_id is required".to_string())
    })?)
    .map_err(|_| ApiError::ValidationError("Invalid school ID".to_string()))?;

    let scheme =
        AcademicService::update_scoring_scheme(&db, school_id, scoring_scheme_id, &request.into_inner()).await?;

    Ok(HttpResponse::Ok().json(scheme))
}

/// Delete a scoring scheme
pub async fn delete_scoring_scheme(
    db: web::Data<Database>,
    path: web::Path<Uuid>,
    query: web::Query<std::collections::HashMap<String, String>>,
) -> Result<HttpResponse, ApiError> {
    let scoring_scheme_id = path.into_inner();
    let school_id = Uuid::parse_str(query.get("school_id").ok_or_else(|| {
        ApiError::ValidationError("school_id is required".to_string())
    })?)
    .map_err(|_| ApiError::ValidationError("Invalid school ID".to_string()))?;

    AcademicService::delete_scoring_scheme(&db, school_id, scoring_scheme_id).await?;

    Ok(HttpResponse::NoContent().finish())
}

pub fn configure(cfg: &mut web::ServiceConfig) {
    cfg.route(
        "/scoring-schemes/{id}",
        web::get().to(get_scoring_scheme),
    )
    .route(
        "/scoring-schemes/class",
        web::get().to(get_scoring_scheme_for_class),
    )
    .route(
        "/scoring-schemes",
        web::get().to(list_scoring_schemes_for_class),
    )
    .route(
        "/scoring-schemes",
        web::post().to(create_scoring_scheme),
    )
    .route(
        "/scoring-schemes/{id}",
        web::put().to(update_scoring_scheme),
    )
    .route(
        "/scoring-schemes/{id}",
        web::delete().to(delete_scoring_scheme),
    );
}

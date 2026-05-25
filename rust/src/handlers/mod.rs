use actix_web::{web, HttpResponse};
use serde_json::json;

use crate::db::Database;
use crate::errors::ApiError;
use crate::services::HealthService;

pub mod auth;

pub async fn health_check(db: web::Data<Database>) -> Result<HttpResponse, ApiError> {
    let health = HealthService::check_system_health(&db).await?;
    Ok(HttpResponse::Ok().json(health))
}

pub async fn root() -> HttpResponse {
    HttpResponse::Ok().json(json!({
        "message": "School Backend API",
        "version": "0.1.0",
        "endpoints": {
            "health": "/api/health",
            "auth": {
                "sign-up": "POST /api/auth/sign-up",
                "sign-in": "POST /api/auth/sign-in",
                "verify-email": "POST /api/auth/verify-email",
                "activate": "POST /api/auth/activate",
                "forgot-password": "POST /api/auth/forgot-password",
                "reset-password": "POST /api/auth/reset-password",
                "logout": "POST /api/auth/logout",
            },
            "users": "/api/users",
            "schools": "/api/schools",
            "students": "/api/students",
        }
    }))
}

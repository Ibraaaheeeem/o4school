use actix_web::{web, HttpResponse};
use uuid::Uuid;
use chrono::Utc;
use serde::{Deserialize, Serialize};

use crate::db::Database;
use crate::errors::ApiError;
use crate::middleware::UserContext;
use crate::models::{PaginatedResponse, Pagination};
use crate::services::EmailService;

#[derive(Debug, Deserialize)]
pub struct ListUsersQuery {
    pub page: Option<i64>,
    pub per_page: Option<i64>,
    pub search: Option<String>,
    pub school_id: Uuid,
    pub role: Option<String>,
}

#[derive(Debug, Serialize, sqlx::FromRow)]
pub struct SchoolUserResponse {
    pub id: Uuid,
    pub email: String,
    pub phone_number: Option<String>,
    pub first_name: Option<String>,
    pub last_name: Option<String>,
    pub status: String,
    pub is_verified: bool,
    pub is_approved: Option<bool>,
    pub last_login_at: Option<chrono::DateTime<chrono::Utc>>,
    pub created_at: chrono::DateTime<chrono::Utc>,
    pub is_active: bool,
    pub role_name: String,
}

/// GET /api/auth/users
/// List all users in a school with pagination, filtering by role, and search
pub async fn list_school_users(
    db: web::Data<Database>,
    query: web::Query<ListUsersQuery>,
    _user_ctx: UserContext,
) -> Result<HttpResponse, ApiError> {
    let q = query.into_inner();
    let page = q.page.unwrap_or(1);
    let per_page = q.per_page.unwrap_or(20);
    let limit = per_page;
    let offset = (page - 1) * per_page;

    let search_pattern = q.search.as_ref().map(|s| format!("%{}%", s));
    let role_upper = q.role.as_ref().map(|r| r.trim().to_uppercase());

    let pool = db.pool();

    // 1. Fetch total count
    let total = sqlx::query_scalar::<sqlx::Postgres, i64>(
        r#"
        SELECT COUNT(DISTINCT u.id)
        FROM users u
        JOIN user_school_roles usr ON usr.user_id = u.id AND usr.is_active = true
        JOIN roles r ON r.id = usr.role_id
        WHERE usr.school_id = $1
          AND ($2::text IS NULL OR u.email ILIKE $2 OR u.first_name ILIKE $2 OR u.last_name ILIKE $2)
          AND ($3::text IS NULL OR r.name = $3)
        "#
    )
    .bind(q.school_id)
    .bind(search_pattern.clone())
    .bind(role_upper.clone())
    .fetch_one(pool)
    .await
    .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

    // 2. Fetch user rows
    let users = sqlx::query_as::<sqlx::Postgres, SchoolUserResponse>(
        r#"
        SELECT 
            u.id, 
            u.email, 
            u.phone_number, 
            u.first_name, 
            u.last_name, 
            u.status, 
            u.is_verified, 
            u.is_approved, 
            u.last_login_at, 
            u.created_at, 
            u.is_active,
            r.name as role_name
        FROM users u
        JOIN user_school_roles usr ON usr.user_id = u.id AND usr.is_active = true
        JOIN roles r ON r.id = usr.role_id
        WHERE usr.school_id = $1
          AND ($2::text IS NULL OR u.email ILIKE $2 OR u.first_name ILIKE $2 OR u.last_name ILIKE $2)
          AND ($3::text IS NULL OR r.name = $3)
        ORDER BY u.created_at DESC
        LIMIT $4 OFFSET $5
        "#
    )
    .bind(q.school_id)
    .bind(search_pattern)
    .bind(role_upper)
    .bind(limit)
    .bind(offset)
    .fetch_all(pool)
    .await
    .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

    let total_pages = (total as f64 / per_page as f64).ceil() as i64;
    let has_next = page < total_pages;
    let has_previous = page > 1;

    Ok(HttpResponse::Ok().json(PaginatedResponse {
        success: true,
        message: "Users list retrieved successfully".to_string(),
        data: users,
        pagination: Pagination {
            current_page: page,
            per_page,
            total,
            total_pages,
            has_next,
            has_previous,
        },
        errors: None,
    }))
}

/// PUT /api/auth/users/{id}/deactivate
/// Deactivate a user account
pub async fn deactivate_user(
    db: web::Data<Database>,
    path: web::Path<(Uuid,)>,
    _user_ctx: UserContext,
) -> Result<HttpResponse, ApiError> {
    let id = path.into_inner().0;
    let pool = db.pool();

    sqlx::query("UPDATE users SET is_active = false, status = 'INACTIVE', updated_at = NOW() WHERE id = $1")
        .bind(id)
        .execute(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

    Ok(HttpResponse::Ok().json(serde_json::json!({
        "success": true,
        "message": "User deactivated successfully"
    })))
}

/// PUT /api/auth/users/{id}/activate
/// Activate a user account
pub async fn activate_user(
    db: web::Data<Database>,
    path: web::Path<(Uuid,)>,
    _user_ctx: UserContext,
) -> Result<HttpResponse, ApiError> {
    let id = path.into_inner().0;
    let pool = db.pool();

    sqlx::query("UPDATE users SET is_active = true, status = 'ACTIVE', updated_at = NOW() WHERE id = $1")
        .bind(id)
        .execute(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

    Ok(HttpResponse::Ok().json(serde_json::json!({
        "success": true,
        "message": "User activated successfully"
    })))
}

/// PUT /api/auth/users/{id}/deverify
/// Deverify a user account's email verification status
pub async fn deverify_user(
    db: web::Data<Database>,
    path: web::Path<(Uuid,)>,
    _user_ctx: UserContext,
) -> Result<HttpResponse, ApiError> {
    let id = path.into_inner().0;
    let pool = db.pool();

    sqlx::query("UPDATE users SET is_verified = false, verified_at = NULL, updated_at = NOW() WHERE id = $1")
        .bind(id)
        .execute(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

    Ok(HttpResponse::Ok().json(serde_json::json!({
        "success": true,
        "message": "User email deverified successfully"
    })))
}

/// POST /api/auth/users/{id}/activation-reminder
/// Send an activation email/OTP reminder to a user
pub async fn send_activation_reminder(
    db: web::Data<Database>,
    path: web::Path<(Uuid,)>,
    _user_ctx: UserContext,
) -> Result<HttpResponse, ApiError> {
    let id = path.into_inner().0;
    
    // Fetch user details
    let user = crate::db::repositories::UserRepository::get_by_id(db.pool(), id).await?;
    
    // Generate activation OTP reminder code
    let otp_code = crate::services::AuthService::generate_otp();
    let otp_expires = Utc::now() + chrono::Duration::minutes(15);
    
    let mut updated_user = user.clone();
    updated_user.otp_code = Some(otp_code.clone());
    updated_user.otp_expires = Some(otp_expires);
    updated_user.last_otp_sent = Some(Utc::now());
    updated_user.updated_at = Utc::now();
    
    crate::db::repositories::UserRepository::update(db.pool(), id, &updated_user).await?;
    
    EmailService::from_env()?
        .send_code_email(
            &user.email,
            "Your account activation code reminder",
            "account activation reminder",
            &otp_code,
            15,
        )
        .await?;
        
    Ok(HttpResponse::Ok().json(serde_json::json!({
        "success": true,
        "message": "Activation code sent successfully"
    })))
}

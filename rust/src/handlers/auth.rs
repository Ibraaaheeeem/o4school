// ============================================================================
// AUTH HANDLERS
// ============================================================================
// HTTP endpoint handlers for authentication

use uuid::Uuid;
use actix_web::{web, HttpResponse};

use crate::db::Database;
use crate::errors::ApiError;
use crate::models::{
    SignUpRequest, SignInRequest, ActivationRequest,
    VerifyEmailRequest, ForgotPasswordRequest,
    ResetPasswordRequest, LogoutRequest, SendOtpRequest, VerifyOtpRequest,
};
use crate::models::auth::CreateRoleUserRequest;
use crate::services::AuthService;
use crate::middleware::UserContext;
use crate::models::auth::{UpdateStudentClassesRequest, UpdateParentStudentsRequest, UpdateClassTeacherRequest, UpdateSubjectTeacherRequest};

// ============================================================================
// SIGN UP HANDLER
// ============================================================================
/// POST /api/auth/sign-up
/// Create a new user account
pub async fn sign_up(
    db: web::Data<Database>,
    req: web::Json<SignUpRequest>,
) -> Result<HttpResponse, ApiError> {
    let response = AuthService::sign_up(&db, req.into_inner()).await?;

    log::info!("Sign up handler: user created {}", response.user_id);

    Ok(HttpResponse::Created().json(response))
}

/// POST /api/auth/create-role-user
/// Internal endpoint to create a user and assign a role (bypasses public signup)
pub async fn create_role_user(
    db: web::Data<Database>,
    req: web::Json<CreateRoleUserRequest>,
    user_ctx: UserContext,
) -> Result<HttpResponse, ApiError> {
    // Extract payload
    let payload = req.into_inner();

    // Ensure caller is SCHOOL_ADMIN for the requested school
    let school_admin_role_id = AuthService::get_role_id_for_name(&db, "SCHOOL_ADMIN").await?;
    let is_admin = crate::db::repositories::UserSchoolRoleRepository::exists(db.pool(), user_ctx.user_id, payload.school_id, school_admin_role_id).await?;
    if !is_admin {
        return Err(ApiError::Unauthorized("Caller is not a SCHOOL_ADMIN for the specified school".to_string()));
    }

    let response = AuthService::create_user_with_role(&db, payload, Some(user_ctx.user_id)).await?;

    log::info!("Create role user handler: caller={} created user={} role={}", user_ctx.user_id, response.user_id, response.role);

    Ok(HttpResponse::Created().json(response))
}

// ============================================================================
// VERIFY EMAIL HANDLER
// ============================================================================
/// POST /api/auth/verify-email
/// Verify user email with verification code
pub async fn verify_email(
    db: web::Data<Database>,
    req: web::Json<VerifyEmailRequest>,
) -> Result<HttpResponse, ApiError> {
    let response = AuthService::verify_email(&db, req.into_inner()).await?;

    log::info!("Verify email handler: OTP sent to {}", response.email);

    Ok(HttpResponse::Ok().json(response))
}

// ============================================================================
// ACTIVATE ACCOUNT HANDLER
// ============================================================================
/// POST /api/auth/activate
/// Activate user account after email verification
pub async fn activate_account(
    db: web::Data<Database>,
    req: web::Json<ActivationRequest>,
) -> Result<HttpResponse, ApiError> {
    let response = AuthService::activate_account(&db, req.into_inner()).await?;

    log::info!(
        "Activate handler: status={}, email={}, next_route={}",
        response.status,
        response.email,
        response.next_route
    );

    Ok(HttpResponse::Ok().json(response))
}

// ============================================================================
// SIGN IN HANDLER
// ============================================================================
/// POST /api/auth/sign-in
/// Authenticate user and return JWT token
pub async fn sign_in(
    db: web::Data<Database>,
    req: web::Json<SignInRequest>,
) -> Result<HttpResponse, ApiError> {
    let response = AuthService::sign_in(&db, req.into_inner()).await?;

    log::info!("Sign in handler: user authenticated {}", response.user_id);

    Ok(HttpResponse::Ok().json(response))
}

// ============================================================================
// FORGOT PASSWORD HANDLER
// ============================================================================
/// POST /api/auth/forgot-password
/// Initiate password reset process
pub async fn forgot_password(
    db: web::Data<Database>,
    req: web::Json<ForgotPasswordRequest>,
) -> Result<HttpResponse, ApiError> {
    let response = AuthService::forgot_password(&db, req.into_inner()).await?;

    log::info!(
        "Forgot password handler: reset initiated for {}",
        response.email
    );

    Ok(HttpResponse::Ok().json(response))
}

// ============================================================================
// RESET PASSWORD HANDLER
// ============================================================================
/// POST /api/auth/reset-password
/// Reset user password with reset token
pub async fn reset_password(
    db: web::Data<Database>,
    req: web::Json<ResetPasswordRequest>,
) -> Result<HttpResponse, ApiError> {
    let response = AuthService::reset_password(&db, req.into_inner()).await?;

    log::info!(
        "Reset password handler: password reset for {}",
        response.user_id
    );

    Ok(HttpResponse::Ok().json(response))
}

// ============================================================================
// LOGOUT HANDLER
// ============================================================================
/// POST /api/auth/logout
/// Logout user and invalidate session
pub async fn logout(
    db: web::Data<Database>,
    req: web::Json<LogoutRequest>,
) -> Result<HttpResponse, ApiError> {
    let user_id = req.user_id;
    let response = AuthService::logout(&db, req.into_inner()).await?;

    log::info!("Logout handler: user logged out {}", user_id);

    Ok(HttpResponse::Ok().json(response))
}

// ============================================================================
// SEND OTP HANDLER
// ============================================================================
/// POST /api/auth/send-otp
/// Send OTP code to user email
pub async fn send_otp(
    db: web::Data<Database>,
    req: web::Json<SendOtpRequest>,
) -> Result<HttpResponse, ApiError> {
    let response = AuthService::send_otp(&db, req.into_inner()).await?;

    log::info!("Send OTP handler: OTP sent to {}", response.email);

    Ok(HttpResponse::Ok().json(response))
}

// ============================================================================
// VERIFY OTP HANDLER
// ============================================================================
/// POST /api/auth/verify-otp
/// Verify OTP code sent to user email
pub async fn verify_otp(
    db: web::Data<Database>,
    req: web::Json<VerifyOtpRequest>,
) -> Result<HttpResponse, ApiError> {
    let response = AuthService::verify_otp(&db, req.into_inner()).await?;

    log::info!("Verify OTP handler: OTP verified for {}", response.user_id);

    Ok(HttpResponse::Ok().json(response))
}

/// PUT /api/auth/role/student-classes
pub async fn update_student_classes(
    db: web::Data<Database>,
    req: web::Json<UpdateStudentClassesRequest>,
    user_ctx: UserContext,
) -> Result<HttpResponse, ApiError> {
    let payload = req.into_inner();
    AuthService::update_student_classes(&db, payload, Some(user_ctx.user_id)).await?;
    Ok(HttpResponse::Ok().json(serde_json::json!({"status":"ok"})))
}

/// PUT /api/auth/role/parent-students
pub async fn update_parent_students(
    db: web::Data<Database>,
    req: web::Json<UpdateParentStudentsRequest>,
    user_ctx: UserContext,
) -> Result<HttpResponse, ApiError> {
    let payload = req.into_inner();
    AuthService::update_parent_student_relationships(&db, payload, Some(user_ctx.user_id)).await?;
    Ok(HttpResponse::Ok().json(serde_json::json!({"status":"ok"})))
}

/// PUT /api/auth/role/class-teachers
pub async fn update_class_teachers(
    db: web::Data<Database>,
    req: web::Json<UpdateClassTeacherRequest>,
    user_ctx: UserContext,
) -> Result<HttpResponse, ApiError> {
    let payload = req.into_inner();
    AuthService::update_class_teacher_assignments(&db, payload, Some(user_ctx.user_id)).await?;
    Ok(HttpResponse::Ok().json(serde_json::json!({"status":"ok"})))
}

/// PUT /api/auth/role/subject-teachers
pub async fn update_subject_teachers(
    db: web::Data<Database>,
    req: web::Json<UpdateSubjectTeacherRequest>,
    user_ctx: UserContext,
) -> Result<HttpResponse, ApiError> {
    let payload = req.into_inner();
    AuthService::update_subject_teacher_assignments(&db, payload, Some(user_ctx.user_id)).await?;
    Ok(HttpResponse::Ok().json(serde_json::json!({"status":"ok"})))
}

// ============================================================================
// REQUEST PARAMETER EXTRACTORS
// ============================================================================

/// Extract user_id from path parameter
/// Used as: /api/auth/verify-email/{user_id}
#[derive(serde::Deserialize)]
pub struct UserIdParam {
    pub user_id: Uuid,
}

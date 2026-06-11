// ============================================================================
// AUTH REQUEST/RESPONSE MODELS
// ============================================================================
// Authentication and authorization DTOs

use serde::{Deserialize, Serialize};
use uuid::Uuid;
use chrono::NaiveDate;
use std::fmt;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum AuthNextRoute {
    None,
    Activate,
    VerifyOtp,
    VerifyEmail,
    ResetPassword,
    SignIn,
    SignUp,
    Dashboard,
    ProfileComplete,
    SupportContact,
    SetPassword,
}

impl AuthNextRoute {
    pub fn as_path(self) -> &'static str {
        match self {
            Self::None => "",
            Self::Activate => "/auth/activate",
            Self::VerifyOtp => "/auth/verify-otp",
            Self::VerifyEmail => "/auth/verify-email",
            Self::ResetPassword => "/auth/reset-password",
            Self::SignIn => "/auth/sign-in",
            Self::SignUp => "/auth/sign-up",
            Self::Dashboard => "/dashboard",
            Self::ProfileComplete => "/profile/complete",
            Self::SupportContact => "/support/contact",
            Self::SetPassword => "/set-password",
        }
    }
}

impl fmt::Display for AuthNextRoute {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        let value = match self {
            Self::None => "NONE",
            Self::Activate => "ACTIVATE",
            Self::VerifyOtp => "VERIFY_OTP",
            Self::VerifyEmail => "VERIFY_EMAIL",
            Self::ResetPassword => "RESET_PASSWORD",
            Self::SignIn => "SIGN_IN",
            Self::SignUp => "SIGN_UP",
            Self::Dashboard => "DASHBOARD",
            Self::ProfileComplete => "PROFILE_COMPLETE",
            Self::SupportContact => "SUPPORT_CONTACT",
            Self::SetPassword => "SET_PASSWORD",
        };

        f.write_str(value)
    }
}

// ============================================================================
// SIGN UP REQUEST/RESPONSE
// ============================================================================

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SignUpRequest {
    pub email: String,
    pub password: String,
    pub first_name: String,
    pub last_name: String,
    pub phone_number: Option<String>,
    pub phone_country_code: Option<String>,
    pub address_line1: Option<String>,
    pub address_line2: Option<String>,
    pub city: Option<String>,
    pub state: Option<String>,
    pub country: Option<String>,
    pub role: String, // STAFF, PARENT, ADMIN, SCHOOL_ADMIN
    pub school_code: Option<String>, // Required for STAFF, PARENT, ADMIN; ignored for SCHOOL_ADMIN
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SignUpResponse {
    pub user_id: Uuid,
    pub email: String,
    pub role: String,
    pub school_id: Option<Uuid>,
    pub school_name: Option<String>,
    pub user_school_role_id: Uuid,
    pub message: String,
    pub next_route: AuthNextRoute,
}

// DTOs for service-to-service creation of role-assigned users
// V2 shape (detailed role payloads) is now the canonical `CreateRoleUserRequest`.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CreateRoleUserRequest {
    pub email: String,
    pub first_name: String,
    pub last_name: String,
    pub phone_number: Option<String>,
    pub role: String,
    pub school_id: uuid::Uuid,
    pub student: Option<CreateStudentInfo>,
    pub student_classes: Option<Vec<CreateStudentClassInfo>>,
    pub parent: Option<CreateParentInfo>,
    pub parent_student_relationships: Option<Vec<CreateParentStudentInfo>>,
    pub staff: Option<CreateStaffInfo>,
    pub staff_class_assignments: Option<Vec<CreateClassTeacherInfo>>,
    pub staff_subject_assignments: Option<Vec<CreateSubjectTeacherInfo>>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CreateStudentInfo {
    pub student_id: Option<String>,
    pub school_id: Uuid,
    pub admission_number: Option<String>,
    pub admission_date: Option<NaiveDate>,
    pub graduation_date: Option<NaiveDate>,
    pub academic_status: Option<String>,
    pub current_grade_level: Option<String>,
    pub date_of_birth: Option<NaiveDate>,
    pub gender: Option<String>,
    pub previous_school: Option<String>,
    pub special_needs_description: Option<String>,
    pub transportation_method: Option<String>,
    pub passport_photo_url: Option<String>,
    pub has_special_needs: Option<bool>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CreateUserInfo {
    pub email: Option<String>,
    pub phone_number: Option<String>,
    pub first_name: String,
    pub middle_name: Option<String>,
    pub last_name: String,
    pub gender: Option<String>,
    pub date_of_birth: Option<NaiveDate>,
    pub school_slug: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CreateStudentClassInfo {
    pub class_id: Uuid,
    pub session_id: Uuid,
    pub term_id: Uuid,
    pub enrollment_date: Option<NaiveDate>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CreateParentInfo {
    pub occupation: Option<String>,
    pub employer_name: Option<String>,
    pub business_address: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CreateParentWithUserRequest {
    pub school_id: Uuid,
    pub email: String,
    pub phone_number: Option<String>,
    pub first_name: String,
    pub middle_name: Option<String>,
    pub last_name: String,
    pub gender: Option<String>,
    pub date_of_birth: Option<NaiveDate>,
    pub parent: CreateParentInfo,
    pub parent_student_relationships: Option<Vec<CreateParentStudentInfo>>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CreateParentStudentInfo {
    pub student_id: Uuid,
    pub relationship: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CreateStaffInfo {
    pub staff_id: Option<String>,
    pub employee_number: Option<String>,
    pub designation: Option<String>,
    pub hire_date: Option<NaiveDate>,
    pub employment_status: Option<String>,
    pub employment_type: Option<String>,
    pub highest_degree: Option<String>,
    pub department: Option<String>,
    pub is_class_teacher: Option<bool>,
    pub is_subject_teacher: Option<bool>,
    pub bank_name: Option<String>,
    pub account_name: Option<String>,
    pub account_number: Option<String>,
    pub monthly_deduction: Option<f64>,
    pub class_teacher_for: Option<Uuid>,
    pub years_of_experience: Option<i32>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CreateStaffWithUserRequest {
    pub school_id: Uuid,
    pub email: String,
    pub phone_number: Option<String>,
    pub first_name: String,
    pub middle_name: Option<String>,
    pub last_name: String,
    pub gender: Option<String>,
    pub date_of_birth: Option<NaiveDate>,
    pub staff: CreateStaffInfo,
    pub staff_class_assignments: Option<Vec<CreateClassTeacherInfo>>,
    pub staff_subject_assignments: Option<Vec<CreateSubjectTeacherInfo>>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CreateClassTeacherInfo {
    pub class_id: Uuid,
    pub session_id: Uuid,
    pub term_id: Uuid,
    pub assigned_date: Option<NaiveDate>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CreateSubjectTeacherInfo {
    pub subject_id: Uuid,
    pub class_id: Uuid,
    pub session_id: Uuid,
    pub term_id: Uuid,
    pub assigned_date: Option<NaiveDate>,
}

// Update request shapes for role relationship editing via API
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UpdateStudentClassesRequest {
    pub school_id: Uuid,
    pub student_user_id: Uuid,
    pub student_classes: Vec<CreateStudentClassInfo>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UpdateParentStudentsRequest {
    pub school_id: Uuid,
    pub parent_user_id: Uuid,
    pub parent_student_relationships: Vec<CreateParentStudentInfo>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UpdateClassTeacherRequest {
    pub school_id: Uuid,
    pub staff_user_id: Uuid,
    pub staff_class_assignments: Vec<CreateClassTeacherInfo>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UpdateSubjectTeacherRequest {
    pub school_id: Uuid,
    pub staff_user_id: Uuid,
    pub staff_subject_assignments: Vec<CreateSubjectTeacherInfo>,
}

// (Old CreateRoleUserRequestV2 removed; V2 fields merged into canonical struct above.)

// For convenience reuse SignUpResponse as the create response

// ============================================================================
// SIGN IN REQUEST/RESPONSE
// ============================================================================

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SignInRequest {
    pub email: String,
    pub password: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UserRoleInfo {
    pub id: Uuid,
    pub name: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UserSchoolWithRoles {
    pub id: Uuid,
    pub name: String,
    pub roles: Vec<UserRoleInfo>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SignInResponse {
    pub user_id: Uuid,
    pub email: String,
    pub first_name: Option<String>,
    pub last_name: Option<String>,
    pub access_token: String,
    pub refresh_token: Option<String>,
    pub token_type: String,
    pub expires_in: i64, // seconds
    pub message: String,
    pub next_route: AuthNextRoute,
    pub status: String,    // ACTIVE, PENDING_VERIFICATION, PENDING_ACTIVATION, etc.
    pub schools: Vec<UserSchoolWithRoles>, // List of schools with their associated roles
}

// ============================================================================
// ACTIVATION REQUEST/RESPONSE
// ============================================================================

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ActivationRequest {
    pub email: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ActivationResponse {
    pub email: String,
    pub user_id: Option<Uuid>,
    pub status: String, // "email_not_found", "otp_sent", "email_already_active"
    pub message: String,
    pub next_route: AuthNextRoute,
    pub otp_sent: bool,
}

// ============================================================================
// VERIFY EMAIL REQUEST/RESPONSE
// ============================================================================

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct VerifyEmailRequest {
    pub email: String,
    pub next_route: AuthNextRoute,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct VerifyEmailResponse {
    pub email: String,
    pub message: String,
    pub next_route: AuthNextRoute,
    pub otp_sent: bool,
}

// ============================================================================
// FORGOT PASSWORD REQUEST/RESPONSE
// ============================================================================

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ForgotPasswordRequest {
    pub email: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ForgotPasswordResponse {
    pub email: String,
    pub message: String,
    pub next_route: AuthNextRoute,
    pub reset_token_sent: bool,
}

// ============================================================================
// RESET PASSWORD REQUEST/RESPONSE
// ============================================================================

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ResetPasswordRequest {
    pub email: String,
    pub otp: String,
    pub new_password: String,
    pub confirm_password: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ResetPasswordResponse {
    pub user_id: Uuid,
    pub email: String,
    pub message: String,
    pub next_route: AuthNextRoute,
    pub reset_at: String,
}

// ============================================================================
// REFRESH TOKEN REQUEST/RESPONSE
// ============================================================================

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RefreshTokenRequest {
    pub refresh_token: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RefreshTokenResponse {
    pub access_token: String,
    pub refresh_token: Option<String>,
    pub token_type: String,
    pub expires_in: i64,
}

// ============================================================================
// LOGOUT REQUEST/RESPONSE
// ============================================================================

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct LogoutRequest {
    pub user_id: Uuid,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct LogoutResponse {
    pub message: String,
    pub next_route: AuthNextRoute,
}

// ============================================================================
// SEND OTP REQUEST/RESPONSE
// ============================================================================

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SendOtpRequest {
    pub email: String,
    pub next_route: AuthNextRoute,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SendOtpResponse {
    pub email: String,
    pub message: String,
    pub otp_sent: bool,
    pub expires_in_seconds: i64, // e.g., 900 for 15 minutes
    pub next_route: AuthNextRoute,
}

// ============================================================================
// VERIFY OTP REQUEST/RESPONSE
// ============================================================================

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct VerifyOtpRequest {
    pub email: String,
    pub otp_code: String,
    pub next_route: AuthNextRoute,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct VerifyOtpResponse {
    pub user_id: Uuid,
    pub email: String,
    pub message: String,
    pub otp_verified: bool,
    pub reset_token: String,
    pub next_route: AuthNextRoute,
    pub verified_at: String,
}

// ============================================================================
// AUTH ERROR RESPONSE
// ============================================================================

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AuthErrorResponse {
    pub error: String,
    pub message: String,
    pub next_route: Option<AuthNextRoute>,
    pub status_code: u16,
}

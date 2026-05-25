use uuid::Uuid;
use chrono::Utc;
use bcrypt::{hash, verify, DEFAULT_COST};
use jsonwebtoken::{encode, decode, Header, EncodingKey, DecodingKey, Validation};
use serde::{Deserialize, Serialize};

// JWT claims used for encoding/decoding tokens across the crate
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct JwtClaims {
    pub user_id: Uuid,
    pub email: String,
    pub iat: i64,
    pub exp: i64,
}

use crate::db::Database;
use crate::db::repositories::{UserRepository, SchoolRepository, UserSchoolRoleRepository, StudentRepository, ParentRepository, StaffRepository};
use crate::errors::ApiError;
use crate::models::{
    User, SignUpRequest, SignUpResponse, SignInRequest, SignInResponse, ActivationRequest,
    ActivationResponse, VerifyEmailRequest, VerifyEmailResponse, ForgotPasswordRequest,
    ForgotPasswordResponse, ResetPasswordRequest, ResetPasswordResponse,
    LogoutRequest, LogoutResponse, SendOtpRequest, SendOtpResponse, VerifyOtpRequest,
    VerifyOtpResponse, UserSchoolRole, School, Student, Parent, Staff,
};
use crate::models::auth::{UserRoleInfo, UserSchoolWithRoles};

pub struct AuthService;

impl AuthService {

    /// Public sign-up flow used by end-users.
    pub async fn sign_up(db: &Database, req: SignUpRequest) -> Result<SignUpResponse, ApiError> {
        // Basic validation
        if req.email.is_empty() || !req.email.contains('@') {
            return Err(ApiError::ValidationError("Valid email is required".to_string()));
        }
        if req.password.len() < 8 {
            return Err(ApiError::ValidationError("Password must be at least 8 characters".to_string()));
        }
        if req.first_name.is_empty() || req.last_name.is_empty() {
            return Err(ApiError::ValidationError("First and last names are required".to_string()));
        }

        // Validate role
        let valid_roles = vec!["STAFF", "PARENT", "SCHOOL_ADMIN"];
        if !valid_roles.contains(&req.role.as_str()) {
            return Err(ApiError::ValidationError(format!("Invalid role. Must be one of: {}", valid_roles.join(", "))));
        }

        // Resolve school
        let school = if req.role == "SCHOOL_ADMIN" {
            // create a minimal school record for school admin
            let prefix = Uuid::new_v4().simple().to_string()[..6].to_string().to_uppercase();
            let new_school = School {
                id: Uuid::new_v4(),
                name: format!("School Admin - {}", req.email),
                slug: format!("admin-{}", Uuid::new_v4().simple()),
                address_line1: String::new(),
                address_line2: None,
                city: String::new(),
                state: String::new(),
                postal_code: None,
                country: String::new(),
                status: Some("ACTIVE".to_string()),
                timezone: Some("UTC".to_string()),
                currency: Some("USD".to_string()),
                language: Some("en".to_string()),
                website: None,
                admin_name: String::new(),
                admin_email: req.email.clone(),
                admin_phone: String::new(),
                banner_url: None,
                logo_url: None,
                primary_color: None,
                secondary_color: None,
                school_motto: None,
                admission_prefix: Some(format!("ADM-{}", prefix)),
                staff_id_prefix: Some(format!("STF-{}", prefix)),
                created_at: Utc::now().naive_utc(),
                updated_at: Utc::now().naive_utc(),
                is_active: true,
            };
            SchoolRepository::create(db.pool(), &new_school).await?
        } else {
            if req.school_code.is_none() || req.school_code.as_ref().unwrap().is_empty() {
                return Err(ApiError::ValidationError(format!("school_code is required for {} role", req.role)));
            }
            SchoolRepository::get_by_slug(db.pool(), req.school_code.as_ref().unwrap()).await?
        };

        // Get role id
        let role_id = Self::get_role_id_for_name(db, &req.role).await?;

        // Check if user exists. If so, allow assigning the role if missing.
        match UserRepository::get_by_email(db.pool(), &req.email).await {
            Ok(mut existing_user) => {
                // Check if the user already has this role for the school
                let role_exists = UserSchoolRoleRepository::exists(db.pool(), existing_user.id, school.id, role_id).await?;
                if role_exists {
                    return Err(ApiError::BadRequest(format!("User {} already has {} role at this school", req.email, req.role)));
                }

                    // Phone consistency: if request provides a phone different from existing, reject
                    if let Some(req_phone) = &req.phone_number {
                        if let Some(existing_phone) = &existing_user.phone_number {
                            if !existing_phone.is_empty() && existing_phone != req_phone {
                                return Err(ApiError::BadRequest("Provided phone number does not match existing user phone".to_string()));
                            }
                        } else {
                            // set phone when none exists
                            existing_user.phone_number = Some(req_phone.clone());
                            existing_user.updated_at = Utc::now();
                            let _ = UserRepository::update(db.pool(), existing_user.id, &existing_user).await?;
                        }
                    }

                // Create user_school_role for existing user
                let user_school_role = UserSchoolRole {
                    id: Uuid::new_v4(),
                    school_id: school.id,
                    user_id: existing_user.id,
                    role_id,
                    created_at: Utc::now().naive_utc(),
                    updated_at: Utc::now().naive_utc(),
                    is_active: true,
                };
                let created_role = UserSchoolRoleRepository::create(db.pool(), &user_school_role).await?;

                // Optionally create minimal type-specific record for student/parent/staff
                match req.role.as_str() {
                    "STUDENT" => {
                        let student = Student {
                            id: Uuid::new_v4(),
                            school_id: school.id,
                            user_id: existing_user.id,
                            student_id: format!("STU-{}", Uuid::new_v4().simple().to_string()),
                            admission_number: None,
                            admission_date: Utc::now().date_naive(),
                            graduation_date: None,
                            academic_status: "ENROLLED".to_string(),
                            current_grade_level: None,
                            date_of_birth: None,
                            gender: None,
                            previous_school: None,
                            special_needs_description: None,
                            transportation_method: None,
                            passport_photo_url: None,
                            is_new: true,
                            has_special_needs: false,
                            created_at: Utc::now().naive_utc(),
                            updated_at: Utc::now().naive_utc(),
                            is_active: false,
                        };
                        let _ = StudentRepository::create(db.pool(), &student).await?;
                    }
                    "PARENT" => {
                        let parent = Parent {
                            id: Uuid::new_v4(),
                            created_at: Utc::now().naive_utc(),
                            is_active: false,
                            updated_at: Utc::now().naive_utc(),
                            school_id: school.id,
                            is_emergency_contact: None,
                            is_financially_responsible: None,
                            is_primary_contact: None,
                            receive_academic_updates: None,
                            receive_disciplinary_updates: None,
                            receive_financial_updates: None,
                            user_id: existing_user.id,
                            payment_distribution_type: None,
                            payment_priority_order: None,
                        };
                        let _ = ParentRepository::create(db.pool(), &parent).await?;
                    }
                    "STAFF" => {
                        let staff = Staff {
                            id: Uuid::new_v4(),
                            school_id: school.id,
                            user_id: existing_user.id,
                            staff_id: format!("STF-{}", Uuid::new_v4().simple().to_string()),
                            employee_number: None,
                            designation: "Staff".to_string(),
                            hire_date: Utc::now().date_naive(),
                            termination_date: None,
                            employment_status: "ACTIVE".to_string(),
                            employment_type: "FULL_TIME".to_string(),
                            highest_degree: None,
                            department: None,
                            is_class_teacher: false,
                            is_subject_teacher: false,
                            bank_name: None,
                            account_name: None,
                            account_number: None,
                            monthly_deduction: 0.0,
                            class_teacher_for: None,
                            years_of_experience: 0,
                            created_at: Utc::now().naive_utc(),
                            updated_at: Utc::now().naive_utc(),
                            is_active: false,
                        };
                        let _ = StaffRepository::create(db.pool(), &staff).await?;
                    }
                    _ => {}
                }

                return Ok(SignUpResponse {
                    user_id: existing_user.id,
                    email: existing_user.email.clone(),
                    role: req.role.clone(),
                    school_id: Some(school.id),
                    school_name: Some(school.name.clone()),
                    user_school_role_id: created_role.id,
                    message: format!("Role {} assigned to existing user {}", req.role, existing_user.email.clone()),
                    next_route: "".to_string(),
                    verification_token: "".to_string(),
                });
            }
            Err(_) => {
                // proceed with new user creation below
            }
        }

        // Hash password and create user
        let password_hash = hash(&req.password, DEFAULT_COST)
            .map_err(|_| ApiError::InternalServerError("Password hashing failed".to_string()))?;

        let user = User {
            id: Uuid::new_v4(),
            email: req.email.clone(),
            phone_number: req.phone_number.clone(),
            password_hash: Some(password_hash),
            first_name: Some(req.first_name.clone()),
            last_name: Some(req.last_name.clone()),
            middle_name: None,
            date_of_birth: None,
            gender: None,
            profile_picture_url: None,
            address_line1: None,
            address_line2: None,
            city: None,
            state: None,
            postal_code: None,
            country: "Unknown".to_string(),
            status: "PENDING".to_string(),
            is_verified: false,
            is_approved: Some(false),
            verified_at: Some(Utc::now()),
            approved_at: None,
            approved_by: None,
            last_login_at: None,
            otp_code: None,
            otp_expires: None,
            last_otp_sent: None,
            created_at: Utc::now(),
            updated_at: Utc::now(),
            is_active: false,
        };

        let created_user = UserRepository::create(db.pool(), &user).await?;

        // Create user_school_role
        let user_school_role = UserSchoolRole {
            id: Uuid::new_v4(),
            school_id: school.id,
            user_id: created_user.id,
            role_id,
            created_at: Utc::now().naive_utc(),
            updated_at: Utc::now().naive_utc(),
            is_active: true,
        };
        let created_role = UserSchoolRoleRepository::create(db.pool(), &user_school_role).await?;

        // Create basic type-specific record where applicable
        match req.role.as_str() {
            "STUDENT" => {
                let student = Student {
                    id: Uuid::new_v4(),
                    school_id: school.id,
                    user_id: created_user.id,
                    student_id: format!("STU-{}", Uuid::new_v4().simple().to_string()),
                    admission_number: None,
                    admission_date: Utc::now().date_naive(),
                    graduation_date: None,
                    academic_status: "ENROLLED".to_string(),
                    current_grade_level: None,
                    date_of_birth: None,
                    gender: None,
                    previous_school: None,
                    special_needs_description: None,
                    transportation_method: None,
                    passport_photo_url: None,
                    is_new: true,
                    has_special_needs: false,
                    created_at: Utc::now().naive_utc(),
                    updated_at: Utc::now().naive_utc(),
                    is_active: false,
                };
                let _ = StudentRepository::create(db.pool(), &student).await?;
            }
            "PARENT" => {
                let parent = Parent {
                    id: Uuid::new_v4(),
                    created_at: Utc::now().naive_utc(),
                    is_active: false,
                    updated_at: Utc::now().naive_utc(),
                    school_id: school.id,
                    is_emergency_contact: None,
                    is_financially_responsible: None,
                    is_primary_contact: None,
                    receive_academic_updates: None,
                    receive_disciplinary_updates: None,
                    receive_financial_updates: None,
                    user_id: created_user.id,
                    payment_distribution_type: None,
                    payment_priority_order: None,
                };
                let _ = ParentRepository::create(db.pool(), &parent).await?;
            }
            "STAFF" => {
                let staff = Staff {
                    id: Uuid::new_v4(),
                    school_id: school.id,
                    user_id: created_user.id,
                    staff_id: format!("STF-{}", Uuid::new_v4().simple().to_string()),
                    employee_number: None,
                    designation: "Staff".to_string(),
                    hire_date: Utc::now().date_naive(),
                    termination_date: None,
                    employment_status: "ACTIVE".to_string(),
                    employment_type: "FULL_TIME".to_string(),
                    highest_degree: None,
                    department: None,
                    is_class_teacher: false,
                    is_subject_teacher: false,
                    bank_name: None,
                    account_name: None,
                    account_number: None,
                    monthly_deduction: 0.0,
                    class_teacher_for: None,
                    years_of_experience: 0,
                    created_at: Utc::now().naive_utc(),
                    updated_at: Utc::now().naive_utc(),
                    is_active: false,
                };
                let _ = StaffRepository::create(db.pool(), &staff).await?;
            }
            _ => {}
        }

        Ok(SignUpResponse {
            user_id: created_user.id,
            email: created_user.email,
            role: req.role.clone(),
            school_id: Some(school.id),
            school_name: Some(school.name.clone()),
            user_school_role_id: created_role.id,
            message: format!("Sign up successful as {} for {}. Please verify your email to continue by entering the OTP received in your email.", req.role, school.name),
            next_route: "/auth/activate".to_string(),
            verification_token: "".to_string(), // For email verification flow, we will use OTP instead of token
        })
    }

    /// Create a user and assign a role (V2) — accepts detailed role payloads for creating type-specific records
    pub async fn create_user_with_role(db: &Database, req: crate::models::auth::CreateRoleUserRequest, performed_by: Option<Uuid>) -> Result<SignUpResponse, ApiError> {
        // Basic validation
        if req.email.is_empty() || !req.email.contains('@') {
            return Err(ApiError::ValidationError("Valid email is required".to_string()));
        }
        if req.first_name.is_empty() || req.last_name.is_empty() {
            return Err(ApiError::ValidationError("First and last names are required".to_string()));
        }

        let valid_roles = vec!["STAFF", "PARENT", "STUDENT"];
        if !valid_roles.contains(&req.role.as_str()) {
            return Err(ApiError::ValidationError(format!("Invalid role. Must be one of: {}", valid_roles.join(", "))));
        }

        let school = SchoolRepository::get_by_id(db.pool(), req.school_id).await?;
        let role_id = Self::get_role_id_for_name(db, &req.role).await?;

        // Check if user exists
        let user_opt = match UserRepository::get_by_email(db.pool(), &req.email).await {
            Ok(u) => Some(u),
            Err(_) => None,
        };

        if let Some(mut user) = user_opt {
            // Ensure role not already assigned
            let role_exists = UserSchoolRoleRepository::exists(db.pool(), user.id, school.id, role_id).await?;
            log::info!("create_user_with_role: existing user check: email={} user_id={} school_id={} role_id={} role_exists={}", req.email, user.id, school.id, role_id, role_exists);
            if role_exists {
                return Err(ApiError::BadRequest(format!("User {} already has {} role at this school", req.email, req.role)));
            }

            // Existing user: ensure phone number consistency (after checking role existence)
            if let Some(req_phone) = &req.phone_number {
                // Only set phone when none exists. Do not overwrite an existing phone
                // with a new value during role assignment to avoid unexpected side-effects
                // when callers reuse an email with different generated phone numbers.
                if let Some(existing_phone) = &user.phone_number {
                    if !existing_phone.is_empty() && existing_phone != req_phone {
                        log::warn!("create_user_with_role: phone mismatch for {}: existing={}, request={}. Not updating record.", req.email, existing_phone, req_phone);
                    }
                } else {
                    user.phone_number = Some(req_phone.clone());
                    user.updated_at = Utc::now();
                    let _ = UserRepository::update(db.pool(), user.id, &user).await?;
                }
            }

            // Create role record (non-transactional path)
            let user_school_role = UserSchoolRole {
                id: Uuid::new_v4(),
                school_id: school.id,
                user_id: user.id,
                role_id,
                created_at: Utc::now().naive_utc(),
                updated_at: Utc::now().naive_utc(),
                is_active: true,
            };
            log::debug!("create_user_with_role: creating user_school_role for existing user user_id={} school_id={} role_id={}", user.id, school.id, role_id);
            println!("DEBUG create_user_with_role existing: user_id={} school_id={} role_id={}", user.id, school.id, role_id);
            let created_role = UserSchoolRoleRepository::create(db.pool(), &user_school_role).await?;

                log::info!(
                    "create_user_with_role: created role for existing user: email={}, user_id={}, role={}, user_school_role_id={}, role_id={}, school_id={}",
                    req.email,
                    user.id,
                    req.role,
                    created_role.id,
                    created_role.role_id,
                    school.id
                );

            // Create type-specific record if payload provided
            match req.role.as_str() {
                "STUDENT" => {
                    if let Some(student_info) = req.student {
                        let student = Student {
                            id: Uuid::new_v4(),
                            school_id: school.id,
                            user_id: user.id,
                            student_id: student_info.student_id.unwrap_or_else(|| format!("STU-{}", Uuid::new_v4().simple().to_string())),
                            admission_number: student_info.admission_number,
                            admission_date: student_info.admission_date.unwrap_or_else(|| Utc::now().date_naive()),
                            graduation_date: student_info.graduation_date,
                            academic_status: student_info.academic_status.unwrap_or_else(|| "ENROLLED".to_string()),
                            current_grade_level: student_info.current_grade_level,
                            date_of_birth: student_info.date_of_birth,
                            gender: Self::normalize_gender(student_info.gender),
                            previous_school: student_info.previous_school,
                            special_needs_description: student_info.special_needs_description,
                            transportation_method: student_info.transportation_method,
                            passport_photo_url: student_info.passport_photo_url,
                            is_new: true,
                            has_special_needs: false,
                            created_at: Utc::now().naive_utc(),
                            updated_at: Utc::now().naive_utc(),
                            is_active: true,
                        };
                        let _ = StudentRepository::create(db.pool(), &student).await?;
                    }
                }
                "PARENT" => {
                    if let Some(_parent_info) = req.parent {
                        let parent = Parent {
                            id: Uuid::new_v4(),
                            created_at: Utc::now().naive_utc(),
                            is_active: true,
                            updated_at: Utc::now().naive_utc(),
                            school_id: school.id,
                            is_emergency_contact: None,
                            is_financially_responsible: None,
                            is_primary_contact: None,
                            receive_academic_updates: None,
                            receive_disciplinary_updates: None,
                            receive_financial_updates: None,
                            user_id: user.id,
                            payment_distribution_type: None,
                            payment_priority_order: None,
                        };
                        let _ = ParentRepository::create(db.pool(), &parent).await?;
                    }
                }
                "STAFF" => {
                    if let Some(staff_info) = req.staff {
                        let staff = Staff {
                            id: Uuid::new_v4(),
                            school_id: school.id,
                            user_id: user.id,
                            staff_id: staff_info.staff_id.unwrap_or_else(|| format!("STF-{}", Uuid::new_v4().simple().to_string())),
                            employee_number: staff_info.employee_number,
                            designation: staff_info.designation.unwrap_or_else(|| "Staff".to_string()),
                            hire_date: staff_info.hire_date.unwrap_or_else(|| Utc::now().date_naive()),
                            termination_date: None,
                            employment_status: staff_info.employment_status.unwrap_or_else(|| "ACTIVE".to_string()),
                            employment_type: staff_info.employment_type.unwrap_or_else(|| "FULL_TIME".to_string()),
                            highest_degree: staff_info.highest_degree,
                            department: staff_info.department,
                            is_class_teacher: staff_info.is_class_teacher.unwrap_or(false),
                            is_subject_teacher: staff_info.is_subject_teacher.unwrap_or(false),
                            bank_name: staff_info.bank_name,
                            account_name: staff_info.account_name,
                            account_number: staff_info.account_number,
                            monthly_deduction: staff_info.monthly_deduction.unwrap_or(0.0),
                            class_teacher_for: staff_info.class_teacher_for,
                            years_of_experience: staff_info.years_of_experience.unwrap_or(0),
                            created_at: Utc::now().naive_utc(),
                            updated_at: Utc::now().naive_utc(),
                            is_active: true,
                        };
                        let _ = StaffRepository::create(db.pool(), &staff).await?;
                    }
                }
                _ => {}
            }

            // Update audit info on user (performed_by)
            if let Some(actor) = performed_by {
                user.approved_by = Some(actor);
                let _ = UserRepository::update(db.pool(), user.id, &user).await?;
            }

            return Ok(SignUpResponse {
                user_id: user.id,
                email: user.email.clone(),
                role: req.role.clone(),
                school_id: Some(school.id),
                school_name: Some(school.name.clone()),
                user_school_role_id: created_role.id,
                message: format!("Role {} assigned to existing user {}", req.role, user.email.clone()),
                next_route: "".to_string(),
                verification_token: "".to_string(),
            });
        }

        // New user flow
        let user = User {
            id: Uuid::new_v4(),
            email: req.email.clone(),
            phone_number: req.phone_number.clone(),
            password_hash: None,
            first_name: Some(req.first_name.clone()),
            last_name: Some(req.last_name.clone()),
            middle_name: None,
            date_of_birth: None,
            gender: None,
            profile_picture_url: None,
            address_line1: None,
            address_line2: None,
            city: None,
            state: None,
            postal_code: None,
            country: "Unknown".to_string(),
            status: "PENDING".to_string(),
            is_verified: false,
            is_approved: Some(false),
            verified_at: Some(Utc::now()),
            approved_at: None,
            approved_by: performed_by,
            last_login_at: None,
            otp_code: None,
            otp_expires: None,
            last_otp_sent: None,
            created_at: Utc::now(),
            updated_at: Utc::now(),
            is_active: false,
        };

        let created_user = UserRepository::create(db.pool(), &user).await?;

        let user_school_role = UserSchoolRole {
            id: Uuid::new_v4(),
            school_id: school.id,
            user_id: created_user.id,
            role_id,
            created_at: Utc::now().naive_utc(),
            updated_at: Utc::now().naive_utc(),
            is_active: true,
        };
        log::debug!("create_user_with_role: creating user_school_role for new user user_id={} school_id={} role_id={}", created_user.id, school.id, role_id);
        println!("DEBUG create_user_with_role new: user_id={} school_id={} role_id={}", created_user.id, school.id, role_id);
        let created_role = UserSchoolRoleRepository::create(db.pool(), &user_school_role).await?;

        match req.role.as_str() {
            "STUDENT" => {
                if let Some(student_info) = req.student {
                    let student = Student {
                        id: Uuid::new_v4(),
                        school_id: school.id,
                        user_id: created_user.id,
                        student_id: student_info.student_id.unwrap_or_else(|| format!("STU-{}", Uuid::new_v4().simple().to_string())),
                        admission_number: student_info.admission_number,
                        admission_date: student_info.admission_date.unwrap_or_else(|| Utc::now().date_naive()),
                        graduation_date: student_info.graduation_date,
                        academic_status: student_info.academic_status.unwrap_or_else(|| "ENROLLED".to_string()),
                        current_grade_level: student_info.current_grade_level,
                        date_of_birth: student_info.date_of_birth,
                        gender: Self::normalize_gender(student_info.gender),
                        previous_school: student_info.previous_school,
                        special_needs_description: student_info.special_needs_description,
                        transportation_method: student_info.transportation_method,
                        passport_photo_url: student_info.passport_photo_url,
                        is_new: true,
                        has_special_needs: false,
                        created_at: Utc::now().naive_utc(),
                        updated_at: Utc::now().naive_utc(),
                        is_active: false,
                    };
                    let _ = StudentRepository::create(db.pool(), &student).await?;
                }
            }
            "PARENT" => {
                if let Some(_parent_info) = req.parent {
                    let parent = Parent {
                        id: Uuid::new_v4(),
                        created_at: Utc::now().naive_utc(),
                        is_active: false,
                        updated_at: Utc::now().naive_utc(),
                        school_id: school.id,
                        is_emergency_contact: None,
                        is_financially_responsible: None,
                        is_primary_contact: None,
                        receive_academic_updates: None,
                        receive_disciplinary_updates: None,
                        receive_financial_updates: None,
                        user_id: created_user.id,
                        payment_distribution_type: None,
                        payment_priority_order: None,
                    };
                    let _ = ParentRepository::create(db.pool(), &parent).await?;
                }
            }
            "STAFF" => {
                if let Some(staff_info) = req.staff {
                    let staff = Staff {
                        id: Uuid::new_v4(),
                        school_id: school.id,
                        user_id: created_user.id,
                        staff_id: staff_info.staff_id.unwrap_or_else(|| format!("STF-{}", Uuid::new_v4().simple().to_string())),
                        employee_number: staff_info.employee_number,
                        designation: staff_info.designation.unwrap_or_else(|| "Staff".to_string()),
                        hire_date: staff_info.hire_date.unwrap_or_else(|| Utc::now().date_naive()),
                        termination_date: None,
                        employment_status: staff_info.employment_status.unwrap_or_else(|| "ACTIVE".to_string()),
                        employment_type: staff_info.employment_type.unwrap_or_else(|| "FULL_TIME".to_string()),
                        highest_degree: staff_info.highest_degree,
                        department: staff_info.department,
                        is_class_teacher: staff_info.is_class_teacher.unwrap_or(false),
                        is_subject_teacher: staff_info.is_subject_teacher.unwrap_or(false),
                        bank_name: staff_info.bank_name,
                        account_name: staff_info.account_name,
                        account_number: staff_info.account_number,
                        monthly_deduction: staff_info.monthly_deduction.unwrap_or(0.0),
                        class_teacher_for: staff_info.class_teacher_for,
                        years_of_experience: staff_info.years_of_experience.unwrap_or(0),
                        created_at: Utc::now().naive_utc(),
                        updated_at: Utc::now().naive_utc(),
                        is_active: false,
                    };
                    let _ = StaffRepository::create(db.pool(), &staff).await?;
                }
            }
            _ => {}
        }

        // Non-transactional path used here (no explicit transaction commit)

        Ok(SignUpResponse {
            user_id: created_user.id,
            email: created_user.email.clone(),
            role: req.role.clone(),
            school_id: Some(school.id),
            school_name: Some(school.name.clone()),
            user_school_role_id: created_role.id,
            message: format!("User {} added as a {}. Now, they will have to activate and set their login password", created_user.email.clone(), req.role),
            next_route: "/auth/activate".to_string(),
            verification_token: Self::generate_token(),
        })
    }

    /// Helper to get role ID from role name
    pub async fn get_role_id_for_name(_db: &Database, role_name: &str) -> Result<Uuid, ApiError> {
        // Query the roles table for the role name. This avoids hardcoded UUIDs
        // and ensures we only use role IDs that actually exist in the database.
        let pool = _db.pool();
        match sqlx::query_scalar::<_, Uuid>("SELECT id FROM roles WHERE name = $1 LIMIT 1")
            .bind(role_name)
            .fetch_optional(pool)
            .await
        {
            Ok(Some(id)) => Ok(id),
            Ok(None) => Err(ApiError::ValidationError(format!("Unknown role: {}", role_name))),
            Err(e) => Err(ApiError::DatabaseError(e.to_string())),
        }
    }

    /// Helper to get role name from role ID
    pub async fn get_role_name_for_id(role_id: Uuid) -> Result<String, ApiError> {
        // Match role ID to role name
        // TODO: Implement proper role lookup from database when roles table is available
        match role_id.to_string().as_str() {
            "c990228f-2f50-4301-a73b-53457d608507" => Ok("STAFF".to_string()),
            "66b88d78-ccaa-452c-8fb4-8c744ffa4b64" => Ok("PARENT".to_string()),
            "2f1d3c4b-5a6e-47b8-9cde-1234567890ab" => Ok("STUDENT".to_string()),
            "b1262b13-16bf-4ea0-aeb1-844a06b0e402" => Ok("ADMIN".to_string()),
            "045c0177-9085-4833-aa35-a6346c71e0e3" => Ok("SCHOOL_ADMIN".to_string()),
            _ => Err(ApiError::ValidationError(format!("Unknown role ID: {}", role_id))),
        }
    }

    fn normalize_gender(g: Option<String>) -> Option<String> {
        g.map(|mut s| {
            let up = s.trim().to_uppercase();
            match up.as_str() {
                "M" | "MALE" => "MALE".to_string(),
                "F" | "FEMALE" => "FEMALE".to_string(),
                "O" | "OTHER" => "OTHER".to_string(),
                _ => up,
            }
        })
    }


    
    // ========================================================================
    // VERIFY EMAIL
    // ========================================================================

    pub async fn verify_email(
        db: &Database,
        req: VerifyEmailRequest,
    ) -> Result<VerifyEmailResponse, ApiError> {
        // Validate input
        if req.email.is_empty() || !req.email.contains('@') {
            return Err(ApiError::ValidationError("Valid email is required".to_string()));
        }

        // Check if email exists in database
        let mut user = UserRepository::get_by_email(db.pool(), &req.email).await?;

        // Generate 6-digit OTP
        let otp_code = Self::generate_otp();

        // Set OTP with 15-minute expiration
        let otp_expires = Utc::now() + chrono::Duration::minutes(15);
        user.otp_code = Some(otp_code.clone());
        user.otp_expires = Some(otp_expires);
        user.last_otp_sent = Some(Utc::now());
        user.updated_at = Utc::now();

        UserRepository::update(db.pool(), user.id, &user).await?;

        // TODO: Send OTP to email via email service
        log::info!("OTP sent to email: {}", user.email);

        // Generate conditional message based on next_route
        let message = if req.next_route == "/auth/reset-password" {
            "OTP sent successfully to your email. Enter the OTP to reset your password".to_string()
        } else {
            "OTP sent successfully to your email. Enter the OTP received to verify your email".to_string()
        };

        Ok(VerifyEmailResponse {
            email: user.email,
            message,
            next_route: req.next_route.to_string(),
            otp_sent: true,
        })
    }

    // ========================================================================
    // ACTIVATE ACCOUNT
    // ========================================================================

    pub async fn activate_account(
        db: &Database,
        req: ActivationRequest,
    ) -> Result<ActivationResponse, ApiError> {
        // Validate email
        if req.email.is_empty() || !req.email.contains('@') {
            return Err(ApiError::ValidationError("Valid email is required".to_string()));
        }

        // Try to get user by email
        let user_result = UserRepository::get_by_email(db.pool(), &req.email).await;

        match user_result {
            Err(_) => {
                // Email not found - suggest sign up
                log::info!("Activation attempt for non-existent email: {}", req.email);
                Ok(ActivationResponse {
                    email: req.email,
                    user_id: None,
                    status: "email_not_found".to_string(),
                    message: "Email address not found in our records".to_string(),
                    next_route: "/auth/sign-up".to_string(),
                    otp_sent: false,
                })
            }
            Ok(mut user) => {
                // Email found - check if already active
                if user.is_active {
                    log::info!("Activation attempt for already active email: {}", req.email);
                    return Ok(ActivationResponse {
                        email: user.email,
                        user_id: Some(user.id),
                        status: "email_already_active".to_string(),
                        message: "Email address is already active. Please sign in to continue or use \"Forgot password\" link if you can't remember your password.".to_string(),
                        next_route: "/auth/sign-in".to_string(),
                        otp_sent: false,
                    });
                }

                // Email found but not active - send OTP
                // Generate 6-digit OTP
                let otp_code = Self::generate_otp();

                // Set OTP with 15-minute expiration
                let otp_expires = Utc::now() + chrono::Duration::minutes(15);
                user.otp_code = Some(otp_code.clone());
                user.otp_expires = Some(otp_expires);
                user.last_otp_sent = Some(Utc::now());
                user.updated_at = Utc::now();

                UserRepository::update(db.pool(), user.id, &user).await?;

                // TODO: Send OTP to email via email service
                log::info!("OTP sent for account activation: {}", user.email);

                Ok(ActivationResponse {
                    email: user.email,
                    user_id: Some(user.id),
                    status: "otp_sent".to_string(),
                    message: format!("OTP sent to {}. Please enter the OTP to verify your account.", req.email),
                    next_route: "/auth/verify-otp".to_string(),
                    otp_sent: true,
                })
            }
        }
    }

    // ========================================================================
    // SIGN IN
    // ========================================================================

    pub async fn sign_in(db: &Database, req: SignInRequest) -> Result<SignInResponse, ApiError> {
        // Validate input
        if req.email.is_empty() || req.password.is_empty() {
            return Err(ApiError::BadRequest(
                "Email and password are required".to_string(),
            ));
        }

        // Get user by email
        let user = UserRepository::get_by_email(db.pool(), &req.email).await?;

        log::info!("sign_in: fetched user id={} email={} password_hash_present={}", user.id, user.email, user.password_hash.is_some());

        // Verify password
        if let Some(hash) = &user.password_hash {
            if !verify(&req.password, hash)
                .map_err(|_| ApiError::Unauthorized("Invalid email or password".to_string()))?
            {
                return Err(ApiError::Unauthorized("Invalid email or password".to_string()));
            }
        } else {
            return Err(ApiError::Unauthorized(
                "Invalid email or password".to_string(),
            ));
        }

        // Check user status
        if user.status != "ACTIVE" {
            let next_route = match user.status.as_str() {
                "PENDING" => "/auth/verify-email",
                "SUSPENDED" => "/support/contact",
                _ => "/dashboard",
            };

            return Err(ApiError::Unauthorized(format!(
                "User account is {}. Status: {}",
                user.status, next_route
            )));
        }

        // Generate JWT token
        let access_token = Self::generate_jwt(&user)?;

        // Update last login
        let mut updated_user = user.clone();
        updated_user.last_login_at = Some(Utc::now());
        updated_user.updated_at = Utc::now();
        UserRepository::update(db.pool(), user.id, &updated_user).await?;

        // Retrieve user's schools and roles
        let user_school_roles = UserSchoolRoleRepository::get_by_user_id(db.pool(), user.id).await?;
        let role_ids: Vec<String> = user_school_roles.iter().map(|r| r.role_id.to_string()).collect();
        log::info!("sign_in: user_id={} found {} user_school_roles: {:?}", user.id, user_school_roles.len(), role_ids);
        
        // Group roles by school
        let mut schools_map: std::collections::HashMap<Uuid, (School, Vec<UserRoleInfo>)> = std::collections::HashMap::new();
        
        for user_school_role in user_school_roles {
            let school = SchoolRepository::get_by_id(db.pool(), user_school_role.school_id).await?;
            let role = UserRoleInfo {
                id: user_school_role.role_id,
                name: Self::get_role_name_for_id(user_school_role.role_id).await?,
            };
            
            schools_map
                .entry(school.id)
                .or_insert_with(|| (school.clone(), vec![]))
                .1
                .push(role);
        }
        
        // Convert to UserSchoolWithRoles
        let schools = schools_map
            .into_values()
            .map(|(school, roles)| UserSchoolWithRoles {
                id: school.id,
                name: school.name,
                roles,
            })
            .collect();

        log::info!("Sign in successful for user: {}", user.email);

        let next_route = if let Some(first_name) = &user.first_name {
            if first_name.is_empty() {
                "/profile/complete"
            } else {
                "/dashboard"
            }
        } else {
            "/profile/complete"
        };

        Ok(SignInResponse {
            user_id: user.id,
            email: user.email,
            first_name: user.first_name,
            last_name: user.last_name,
            access_token,
            refresh_token: None,
            token_type: "Bearer".to_string(),
            expires_in: 3600, // 1 hour
            message: "Sign in successful".to_string(),
            next_route: next_route.to_string(),
            status: "ACTIVE".to_string(),
            schools,
        })
    }

    // ========================================================================
    // FORGOT PASSWORD
    // ========================================================================

    pub async fn forgot_password(
        db: &Database,
        req: ForgotPasswordRequest,
    ) -> Result<ForgotPasswordResponse, ApiError> {
        // Get user by email
        let mut user = UserRepository::get_by_email(db.pool(), &req.email).await?;

        // Generate reset token
        let reset_token = Self::generate_token();

        // Update user with reset token
        user.otp_code = Some(reset_token.clone());
        user.otp_expires = Some(Utc::now() + chrono::Duration::hours(1));
        user.last_otp_sent = Some(Utc::now());
        user.updated_at = Utc::now();

        UserRepository::update(db.pool(), user.id, &user).await?;

        // TODO: Send password reset email here with the reset_token

        log::info!("Password reset requested for user: {}", user.email);

        Ok(ForgotPasswordResponse {
            email: user.email,
            message: "OTP sent to your email".to_string(),
            next_route: "/auth/reset-password".to_string(),
            reset_token_sent: true,
        })
    }

    // ========================================================================
    // RESET PASSWORD
    // ========================================================================

    pub async fn reset_password(
        db: &Database,
        req: ResetPasswordRequest,
    ) -> Result<ResetPasswordResponse, ApiError> {
        // Validate input
        if req.new_password != req.confirm_password {
            return Err(ApiError::BadRequest(
                "Passwords do not match".to_string(),
            ));
        }

        if req.new_password.len() < 8 {
            return Err(ApiError::BadRequest(
                "Password must be at least 8 characters".to_string(),
            ));
        }

        // Get user by email
        let mut user = UserRepository::get_by_email(db.pool(), &req.email).await?;

        // Verify reset token
        if user.otp_code != Some(req.otp.clone()) {
            return Err(ApiError::BadRequest("Invalid OTP".to_string()));
        }

        // Check if token has expired
        if let Some(expires_at) = user.otp_expires {
            if Utc::now() > expires_at {
                return Err(ApiError::BadRequest("OTP has expired".to_string()));
            }
        }

        // Hash new password
        let password_hash = hash(&req.new_password, DEFAULT_COST)
            .map_err(|_| ApiError::InternalServerError("Password hashing failed".to_string()))?;

        // Update user
        user.password_hash = Some(password_hash);
        user.otp_code = None;
        user.otp_expires = None;
        user.updated_at = Utc::now();

        UserRepository::update(db.pool(), user.id, &user).await?;

        log::info!("Password reset successful for user: {}. You can now proceed to login", user.email);

        Ok(ResetPasswordResponse {
            user_id: user.id,
            email: user.email,
            message: "Password reset successfully".to_string(),
            next_route: "/auth/sign-in".to_string(),
            reset_at: Utc::now().to_rfc3339(),
        })
    }

    // ========================================================================
    // LOGOUT
    // ========================================================================
    // ========================================================================
    // LOGOUT
    // ========================================================================

    pub async fn logout(
        db: &Database,
        req: LogoutRequest,
    ) -> Result<LogoutResponse, ApiError> {
        // Verify user exists
        UserRepository::get_by_id(db.pool(), req.user_id).await?;

        // TODO: Invalidate token in cache/database if needed

        log::info!("User logged out: {}", req.user_id);

        Ok(LogoutResponse {
            message: "Logged out successfully".to_string(),
            next_route: "/auth/sign-in".to_string(),
        })
    }

    // ========================================================================
    // SEND OTP
    // ========================================================================

    pub async fn send_otp(
        db: &Database,
        req: SendOtpRequest,
    ) -> Result<SendOtpResponse, ApiError> {
        // Validate input
        if req.email.is_empty() || !req.email.contains('@') {
            return Err(ApiError::ValidationError("Valid email is required".to_string()));
        }

        // Get user by email
        let mut user = UserRepository::get_by_email(db.pool(), &req.email).await?;

        // Generate 6-digit OTP
        let otp_code = Self::generate_otp();

        // Set OTP with 15-minute expiration
        let otp_expires = Utc::now() + chrono::Duration::minutes(15);
        user.otp_code = Some(otp_code.clone());
        user.otp_expires = Some(otp_expires);
        user.last_otp_sent = Some(Utc::now());
        user.updated_at = Utc::now();

        UserRepository::update(db.pool(), user.id, &user).await?;

        // TODO: Send OTP to email via email service
        log::info!("OTP sent to email: {}", user.email);

        Ok(SendOtpResponse {
            email: user.email,
            message: "OTP sent successfully to your email".to_string(),
            otp_sent: true,
            expires_in_seconds: 900, // 15 minutes
            next_route: "/auth/verify-otp".to_string(),
        })
    }

    // ========================================================================
    // VERIFY OTP
    // ========================================================================

    pub async fn verify_otp(
        db: &Database,
        req: VerifyOtpRequest,
    ) -> Result<VerifyOtpResponse, ApiError> {
        // Validate input
        if req.email.is_empty() || req.otp_code.is_empty() {
            return Err(ApiError::ValidationError(
                "Email and OTP code are required".to_string(),
            ));
        }

        // Get user by email
        let mut user = UserRepository::get_by_email(db.pool(), &req.email).await?;

        // Check if OTP code matches
        if user.otp_code != Some(req.otp_code) {
            return Err(ApiError::BadRequest("Invalid OTP code".to_string()));
        }

        // Check if OTP has expired
        if let Some(expires_at) = user.otp_expires {
            if Utc::now() > expires_at {
                return Err(ApiError::BadRequest("OTP has expired. Please request a new one.".to_string()));
            }
        } else {
            return Err(ApiError::BadRequest("No OTP found for this email".to_string()));
        }

        // Clear OTP after successful verification
        user.otp_code = None;
        user.otp_expires = None;
        user.is_active = true;
        user.status = "ACTIVE".to_string();
        user.approved_at = Some(Utc::now());
        user.updated_at = Utc::now();

        UserRepository::update(db.pool(), user.id, &user).await?;

        log::info!("OTP verified and account activated for user: {}", user.email);

        // Generate conditional message based on next_route
        let message = if req.next_route == "/set-password" {
            "OTP verified successfully. Now, proceed to change password".to_string()
        } else {
            "OTP verified successfully. Your account is now active".to_string()
        };

        // Generate reset token for password change if needed
        let reset_token = if req.next_route == "/set-password" {
            Self::generate_token()
        } else {
            String::new()
        };

        Ok(VerifyOtpResponse {
            user_id: user.id,
            email: user.email,
            message,
            otp_verified: true,
            reset_token,
            next_route: req.next_route.to_string(),
            verified_at: Utc::now().to_rfc3339(),
        })
    }

    // ========================================================================
    // HELPER FUNCTIONS
    // ========================================================================

    fn generate_token() -> String {
        use uuid::Uuid;
        Uuid::new_v4().to_string().replace("-", "")[..32].to_string()
    }

    fn generate_otp() -> String {
        use rand::Rng;
        let mut rng = rand::thread_rng();
        format!("{:06}", rng.gen_range(0..1000000))
    }

    fn generate_jwt(user: &User) -> Result<String, ApiError> {
        let now = Utc::now().timestamp();
        let claims = JwtClaims {
            user_id: user.id,
            email: user.email.clone(),
            iat: now,
            exp: now + 3600, // 1 hour expiry
        };

        let secret = std::env::var("JWT_SECRET")
            .unwrap_or_else(|_| "your-secret-key-change-in-production".to_string());

        encode(
            &Header::default(),
            &claims,
            &EncodingKey::from_secret(secret.as_ref()),
        )
        .map_err(|_| ApiError::InternalServerError("Token generation failed".to_string()))
    }

    /// Authorize a bearer JWT token and ensure the caller is SCHOOL_ADMIN for `school_id`.
    pub async fn authorize_school_admin_by_token(db: &Database, token: &str, school_id: Uuid) -> Result<Uuid, ApiError> {
        // Decode token
        let secret = std::env::var("JWT_SECRET").unwrap_or_else(|_| "your-secret-key-change-in-production".to_string());
        let token_data = decode::<JwtClaims>(
            token,
            &DecodingKey::from_secret(secret.as_ref()),
            &Validation::default(),
        ).map_err(|_| ApiError::Unauthorized("Invalid or expired token".to_string()))?;

        let claims = token_data.claims;

        // Ensure the caller is SCHOOL_ADMIN for the provided school_id
        let school_admin_role_id = Self::get_role_id_for_name(db, "SCHOOL_ADMIN").await?;
        let is_admin = UserSchoolRoleRepository::exists(db.pool(), claims.user_id, school_id, school_admin_role_id).await?;

        if !is_admin {
            return Err(ApiError::Unauthorized("Caller is not a SCHOOL_ADMIN for the specified school".to_string()));
        }

        Ok(claims.user_id)
    }
}

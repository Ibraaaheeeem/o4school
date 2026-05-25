use uuid::Uuid;
use chrono::Utc;
use crate::db::Database;
use crate::db::repositories::UserRepository;
use crate::errors::ApiError;
use crate::models::{User, CreateTenantRequest};

pub struct UserService;

impl UserService {
    /// Get user by ID
    pub async fn get_user(db: &Database, user_id: Uuid) -> Result<User, ApiError> {
        UserRepository::get_by_id(db.pool(), user_id).await
    }

    /// Get user by email
    pub async fn get_user_by_email(db: &Database, email: &str) -> Result<User, ApiError> {
        UserRepository::get_by_email(db.pool(), email).await
    }

    /// List all users with pagination
    pub async fn list_users(db: &Database, limit: i64, offset: i64) -> Result<Vec<User>, ApiError> {
        if limit > 100 {
            return Err(ApiError::BadRequest("Limit cannot exceed 100".to_string()));
        }
        UserRepository::get_all(db.pool(), limit, offset).await
    }

    /// Create a new user
    pub async fn create_user(db: &Database, user: User) -> Result<User, ApiError> {
        // Validation: check if email already exists
        if let Ok(_) = UserRepository::get_by_email(db.pool(), &user.email).await {
            return Err(ApiError::BadRequest(format!("User with email {} already exists", user.email)));
        }

        // Create the user
        UserRepository::create(db.pool(), &user).await
    }

    /// Create a tenant with initial admin user
    pub async fn create_tenant(db: &Database, req: CreateTenantRequest) -> Result<User, ApiError> {
        // Validate email
        if !req.email.contains('@') {
            return Err(ApiError::ValidationError("Invalid email format".to_string()));
        }

        // Check if email already exists
        if let Ok(_) = UserRepository::get_by_email(db.pool(), &req.email).await {
            return Err(ApiError::BadRequest(format!("User with email {} already exists", req.email)));
        }

        // Create new user with PENDING status
        let user = User {
            id: Uuid::new_v4(),
            email: req.email,
            phone_number: None,
            password_hash: None,
            first_name: Some(req.name),
            last_name: None,
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
            verified_at: None,
            approved_at: None,
            approved_by: None,
            last_login_at: None,
            otp_code: None,
            otp_expires: None,
            last_otp_sent: None,
            created_at: Utc::now(),
            updated_at: Utc::now(),
            is_active: true,
        };

        UserRepository::create(db.pool(), &user).await
    }

    /// Update user information
    pub async fn update_user(db: &Database, user_id: Uuid, updates: User) -> Result<User, ApiError> {
        // Verify user exists
        UserRepository::get_by_id(db.pool(), user_id).await?;

        UserRepository::update(db.pool(), user_id, &updates).await
    }

    /// Delete user (soft delete)
    pub async fn delete_user(db: &Database, user_id: Uuid) -> Result<(), ApiError> {
        // Verify user exists
        UserRepository::get_by_id(db.pool(), user_id).await?;

        UserRepository::delete(db.pool(), user_id).await
    }

    /// Verify user email
    pub async fn verify_email(db: &Database, user_id: Uuid) -> Result<User, ApiError> {
        let mut user = UserRepository::get_by_id(db.pool(), user_id).await?;
        
        user.is_verified = true;
        user.verified_at = Some(Utc::now());
        user.updated_at = Utc::now();

        UserRepository::update(db.pool(), user_id, &user).await
    }

    /// Approve user
    pub async fn approve_user(db: &Database, user_id: Uuid, approved_by: Uuid) -> Result<User, ApiError> {
        let mut user = UserRepository::get_by_id(db.pool(), user_id).await?;
        
        user.status = "ACTIVE".to_string();
        user.approved_at = Some(Utc::now());
        user.approved_by = Some(approved_by);
        user.updated_at = Utc::now();

        UserRepository::update(db.pool(), user_id, &user).await
    }
}

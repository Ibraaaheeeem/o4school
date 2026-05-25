use chrono::{DateTime, NaiveDate, NaiveDateTime, Utc};
use serde::{Deserialize, Serialize};
use uuid::Uuid;

/// Users table - Represents all users in the system (teachers, students, parents, admins)
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct User {
    pub id: Uuid,
    pub email: String,
    pub phone_number: Option<String>,
    pub password_hash: Option<String>,
    pub first_name: Option<String>,
    pub last_name: Option<String>,
    pub middle_name: Option<String>,
    pub date_of_birth: Option<NaiveDate>,
    pub gender: Option<String>,
    pub profile_picture_url: Option<String>,
    pub address_line1: Option<String>,
    pub address_line2: Option<String>,
    pub city: Option<String>,
    pub state: Option<String>,
    pub postal_code: Option<String>,
    pub country: String,
    pub status: String,
    pub is_verified: bool,
    pub is_approved: Option<bool>,
    pub verified_at: Option<DateTime<Utc>>,
    pub approved_at: Option<DateTime<Utc>>,
    pub approved_by: Option<Uuid>,
    pub last_login_at: Option<DateTime<Utc>>,
    pub otp_code: Option<String>,
    pub otp_expires: Option<DateTime<Utc>>,
    pub last_otp_sent: Option<DateTime<Utc>>,
    pub created_at: DateTime<Utc>,
    pub updated_at: DateTime<Utc>,
    pub is_active: bool,
}

/// Roles table - User roles
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct Role {
    pub id: Uuid,
    pub name: String,
    pub role_type: String,
    pub description: Option<String>,
    pub is_system_role: bool,
    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
    pub is_active: bool,
}

/// Permissions table - Granular permissions
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct Permission {
    pub id: Uuid,
    pub name: String,
    pub code: String,
    pub description: Option<String>,
    pub resource: String,
    pub action: String,
    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
    pub is_active: bool,
}

/// UserSchoolRoles table - User roles per school
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct UserSchoolRole {
    pub id: Uuid,
    pub school_id: Uuid,
    pub user_id: Uuid,
    pub role_id: Uuid,
    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
    pub is_active: bool,
}

/// UserGlobalRoles table - Global user roles
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct UserGlobalRole {
    pub id: Uuid,
    pub user_id: Uuid,
    pub role_id: Uuid,
    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
    pub is_active: bool,
}

/// DesignationPermissions table - Permission assignments to designations
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct DesignationPermission {
    pub id: Uuid,
    pub school_id: Uuid,
    pub designation_id: Uuid,
    pub permission_id: Uuid,
    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
    pub is_active: bool,
}

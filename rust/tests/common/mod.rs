/// Common test utilities, helpers, and constants
use chrono::Utc;
use serde_json::json;
use sqlx::{PgPool, postgres::PgPoolOptions};
use uuid::Uuid;

fn with_required_sslmode(database_url: &str) -> String {
    let is_local = database_url.contains("localhost")
        || database_url.contains("127.0.0.1")
        || database_url.contains("0.0.0.0");

    if is_local || database_url.contains("sslmode=") {
        return database_url.to_string();
    }

    if database_url.contains('?') {
        format!("{}&sslmode=require", database_url)
    } else {
        format!("{}?sslmode=require", database_url)
    }
}

fn redact_database_url(database_url: &str) -> String {
    let scheme_sep = match database_url.find("://") {
        Some(i) => i,
        None => return "<redacted>".to_string(),
    };

    let creds_start = scheme_sep + 3;
    let at_pos = match database_url[creds_start..].find('@') {
        Some(i) => creds_start + i,
        None => return database_url.to_string(),
    };

    let colon_pos = match database_url[creds_start..at_pos].find(':') {
        Some(i) => creds_start + i,
        None => return database_url.to_string(),
    };

    let mut redacted = database_url.to_string();
    redacted.replace_range((colon_pos + 1)..at_pos, "***");
    redacted
}

/// Test HTTP client
pub fn get_http_client() -> reqwest::Client {
    reqwest::Client::new()
}

/// Get database pool for tests
/// Each test creates a fresh pool handle (cheap operation)
/// SQLx pools are designed to be cloned and reused efficiently
pub async fn get_db_pool() -> PgPool {
    let database_url = std::env::var("DATABASE_URL")
        .unwrap_or_else(|_| "postgres://postgres:password@localhost:5432/myschool".to_string());
    let database_url = with_required_sslmode(&database_url);
    let redacted_url = redact_database_url(&database_url);

    // Create pool with lenient settings for test environment
    // Tests must run with --test-threads=1 to avoid connection conflicts
    PgPoolOptions::new()
        .max_connections(5)  // Allow multiple connections for retry logic
        .acquire_timeout(std::time::Duration::from_secs(60))  // Give 60 seconds
        .idle_timeout(std::time::Duration::from_secs(300))  // 5 min idle timeout
        .max_lifetime(std::time::Duration::from_secs(1800))  // 30 min max lifetime
        .connect(&database_url)
        .await
        .map_err(|e| {
            eprintln!("❌ Database connection failed: {:?}", e);
            eprintln!("   DATABASE_URL: {}", redacted_url);
            eprintln!("   Make sure PostgreSQL is running and accessible");
            eprintln!("   Hosted PostgreSQL often requires sslmode=require");
            eprintln!("   Ensure server is running: cargo run (in another terminal)");
            eprintln!("   Run tests with: cargo test -- --test-threads=1");
            e
        })
        .expect("Failed to connect to test database")
}

/// Test constants
pub mod constants {
    use uuid::Uuid;

    pub const API_URL: &str = "http://127.0.0.1:8080";
    pub const SIGNUP_ENDPOINT: &str = "/api/auth/sign-up";

    // Test school codes (must exist in database)
    pub const TEST_SCHOOL_CODE_1: &str = "school-722ee764";
    pub const TEST_SCHOOL_CODE_2: &str = "ibrahim-0bf21cd6";

    // Role UUIDs (verified in database)
    pub fn role_id_staff() -> Uuid {
        Uuid::parse_str("c990228f-2f50-4301-a73b-53457d608507").unwrap()
    }

    pub fn role_id_parent() -> Uuid {
        Uuid::parse_str("66b88d78-ccaa-452c-8fb4-8c744ffa4b64").unwrap()
    }

    pub fn role_id_admin() -> Uuid {
        Uuid::parse_str("b1262b13-16bf-4ea0-aeb1-844a06b0e402").unwrap()
    }

    pub fn role_id_school_admin() -> Uuid {
        Uuid::parse_str("045c0177-9085-4833-aa35-a6346c71e0e3").unwrap()
    }
}

/// Generate unique test email with timestamp
pub fn generate_test_email(prefix: &str) -> String {
    let timestamp = Utc::now().timestamp_millis();
    format!("{}-test-{}@example.com", prefix, timestamp)
}

/// Generate unique test phone number
pub fn generate_test_phone() -> String {
    // Use nanosecond timestamp to minimize chance of collision across rapid calls
    let nanos = Utc::now().timestamp_nanos();
    let last_10 = (nanos % 10_000_000_000) as i64;
    format!("+1212555{:010}", last_10)
}

/// Build signup request JSON
pub fn build_signup_request(
    email: &str,
    password: &str,
    first_name: &str,
    last_name: &str,
    phone: &str,
    role: &str,
    school_code: Option<&str>,
) -> serde_json::Value {
    let mut request = json!({
        "email": email,
        "password": password,
        "first_name": first_name,
        "last_name": last_name,
        "phone_number": phone,
        "phone_country_code": "+1",
        "address_line1": "123 Test Street",
        "address_line2": null,
        "city": "Test City",
        "state": "Test State",
        "country": "Test Country",
        "role": role
    });

    if let Some(code) = school_code {
        request["school_code"] = json!(code);
    }

    request
}

/// Response structure for signup endpoint
#[derive(Debug, serde::Deserialize)]
pub struct SignUpResponse {
    pub user_id: String,
    pub email: String,
    pub role: String,
    #[serde(default)]
    pub school_id: String,
    #[serde(default)]
    pub school_name: String,
    pub user_school_role_id: String,
    pub message: String,
    pub next_route: String,
    #[serde(default)]
    pub verification_token: String,
}

/// Error response structure
#[derive(Debug, serde::Deserialize)]
pub struct ErrorResponse {
    pub error: String,
    #[serde(default)]
    pub status: Option<u16>,
}

/// User database record
#[derive(Debug, sqlx::FromRow)]
pub struct DbUser {
    pub id: Uuid,
    pub email: String,
    pub phone_number: String,
    pub phone_country_code: Option<String>,
    pub first_name: String,
    pub last_name: String,
    pub password_hash: String,
    pub is_approved: bool,
    pub is_active: bool,
    pub created_at: chrono::DateTime<Utc>,
}

/// School database record
#[derive(Debug, sqlx::FromRow)]
pub struct DbSchool {
    pub id: Uuid,
    pub name: String,
    pub slug: String,
    pub admission_prefix: Option<String>,
    pub staff_id_prefix: Option<String>,
    pub is_active: bool,
}

/// UserSchoolRole database record
#[derive(Debug, sqlx::FromRow)]
pub struct DbUserSchoolRole {
    pub id: Uuid,
    pub user_id: Uuid,
    pub school_id: Uuid,
    pub role_id: Uuid,
    pub is_active: bool,
}

/// Database helpers
pub mod db {
    use super::*;

    /// Get user by email from database
    pub async fn get_user_by_email(pool: &PgPool, email: &str) -> Option<DbUser> {
        sqlx::query_as::<_, DbUser>(
            "SELECT id, email, phone_number, phone_country_code, first_name, last_name, password_hash, is_approved, is_active, created_at FROM users WHERE email = $1"
        )
        .bind(email)
        .fetch_optional(pool)
        .await
        .ok()?
    }

    /// Get user by ID from database
    pub async fn get_user_by_id(pool: &PgPool, user_id: Uuid) -> Option<DbUser> {
        sqlx::query_as::<_, DbUser>(
            "SELECT id, email, phone_number, phone_country_code, first_name, last_name, password_hash, is_approved, is_active, created_at FROM users WHERE id = $1"
        )
        .bind(user_id)
        .fetch_optional(pool)
        .await
        .ok()?
    }

    /// Get school by slug from database
    pub async fn get_school_by_slug(pool: &PgPool, slug: &str) -> Option<DbSchool> {
        sqlx::query_as::<_, DbSchool>(
            "SELECT id, name, slug, admission_prefix, staff_id_prefix, is_active FROM schools WHERE slug = $1"
        )
        .bind(slug)
        .fetch_optional(pool)
        .await
        .ok()?
    }

    /// Get school by ID from database
    pub async fn get_school_by_id(pool: &PgPool, school_id: Uuid) -> Option<DbSchool> {
        sqlx::query_as::<_, DbSchool>(
            "SELECT id, name, slug, admission_prefix, staff_id_prefix, is_active FROM schools WHERE id = $1"
        )
        .bind(school_id)
        .fetch_optional(pool)
        .await
        .ok()?
    }

    /// Get UserSchoolRole by user_id, school_id, and role_id
    pub async fn get_user_school_role(
        pool: &PgPool,
        user_id: Uuid,
        school_id: Uuid,
        role_id: Uuid,
    ) -> Option<DbUserSchoolRole> {
        sqlx::query_as::<_, DbUserSchoolRole>(
            "SELECT id, user_id, school_id, role_id, is_active FROM user_school_roles WHERE user_id = $1 AND school_id = $2 AND role_id = $3"
        )
        .bind(user_id)
        .bind(school_id)
        .bind(role_id)
        .fetch_optional(pool)
        .await
        .ok()?
    }

    /// Get all UserSchoolRoles for a user
    pub async fn get_user_school_roles(pool: &PgPool, user_id: Uuid) -> Vec<DbUserSchoolRole> {
        sqlx::query_as::<_, DbUserSchoolRole>(
            "SELECT id, user_id, school_id, role_id, is_active FROM user_school_roles WHERE user_id = $1"
        )
        .bind(user_id)
        .fetch_all(pool)
        .await
        .unwrap_or_default()
    }

    /// Get all UserSchoolRoles at a school
    pub async fn get_school_user_roles(pool: &PgPool, school_id: Uuid) -> Vec<DbUserSchoolRole> {
        sqlx::query_as::<_, DbUserSchoolRole>(
            "SELECT id, user_id, school_id, role_id, is_active FROM user_school_roles WHERE school_id = $1"
        )
        .bind(school_id)
        .fetch_all(pool)
        .await
        .unwrap_or_default()
    }

    /// Count users in database
    pub async fn count_users(pool: &PgPool) -> i64 {
        sqlx::query_scalar::<_, i64>("SELECT COUNT(*) FROM users")
            .fetch_one(pool)
            .await
            .unwrap_or(0)
    }

    /// Count schools in database
    pub async fn count_schools(pool: &PgPool) -> i64 {
        sqlx::query_scalar::<_, i64>("SELECT COUNT(*) FROM schools")
            .fetch_one(pool)
            .await
            .unwrap_or(0)
    }

    /// Delete test user by email
    pub async fn delete_test_user(pool: &PgPool, email: &str) -> Result<(), sqlx::Error> {
        sqlx::query("DELETE FROM users WHERE email = $1")
            .bind(email)
            .execute(pool)
            .await?;
        Ok(())
    }

    /// Delete test school by slug
    pub async fn delete_test_school(pool: &PgPool, slug: &str) -> Result<(), sqlx::Error> {
        sqlx::query("DELETE FROM schools WHERE slug = $1")
            .bind(slug)
            .execute(pool)
            .await?;
        Ok(())
    }

    /// Check if email exists
    pub async fn email_exists(pool: &PgPool, email: &str) -> bool {
        sqlx::query_scalar::<_, i64>("SELECT COUNT(*) FROM users WHERE email = $1")
            .bind(email)
            .fetch_one(pool)
            .await
            .map(|count| count > 0)
            .unwrap_or(false)
    }

    /// Check if phone exists
    pub async fn phone_exists(pool: &PgPool, phone: &str) -> bool {
        sqlx::query_scalar::<_, i64>("SELECT COUNT(*) FROM users WHERE phone_number = $1")
            .bind(phone)
            .fetch_one(pool)
            .await
            .map(|count| count > 0)
            .unwrap_or(false)
    }

    /// Check if school exists by code/slug
    pub async fn school_exists(pool: &PgPool, slug: &str) -> bool {
        sqlx::query_scalar::<_, i64>("SELECT COUNT(*) FROM schools WHERE slug = $1")
            .bind(slug)
            .fetch_one(pool)
            .await
            .map(|count| count > 0)
            .unwrap_or(false)
    }
}

/// HTTP helpers
pub mod http {
    use super::*;

    /// Make signup request and return response
    pub async fn signup(client: &reqwest::Client, request_body: serde_json::Value) -> reqwest::Response {
        client
            .post(&format!("{}{}", constants::API_URL, constants::SIGNUP_ENDPOINT))
            .json(&request_body)
            .send()
            .await
            .expect("Failed to make signup request")
    }

    /// Make signup request and parse response as SignUpResponse
    pub async fn signup_expect_success(
        client: &reqwest::Client,
        request_body: serde_json::Value,
    ) -> SignUpResponse {
        let response = signup(client, request_body).await;
        assert!(response.status().is_success(), "Expected successful 2xx status, got {}", response.status());
        response
            .json::<SignUpResponse>()
            .await
            .expect("Failed to parse signup response")
    }

    /// Make signup request and expect error
    pub async fn signup_expect_error(
        client: &reqwest::Client,
        request_body: serde_json::Value,
    ) -> ErrorResponse {
        let response = signup(client, request_body).await;
        assert!(response.status().is_client_error() || response.status().is_server_error());
        response
            .json::<ErrorResponse>()
            .await
            .expect("Failed to parse error response")
    }
}

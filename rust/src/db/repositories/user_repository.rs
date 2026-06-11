use uuid::Uuid;
use sqlx::PgPool;
use crate::errors::ApiError;
use crate::models::User;

pub struct UserRepository;

impl UserRepository {
    /// Get user by ID
    pub async fn get_by_id(pool: &PgPool, user_id: Uuid) -> Result<User, ApiError> {
        sqlx::query_as::<sqlx::Postgres, User>(
            "SELECT * FROM users WHERE id = $1"
        )
        .bind(user_id)
        .fetch_one(pool)
        .await
        .map_err(|e| {
            if e.to_string().contains("no rows") {
                ApiError::NotFound(format!("User with id {} not found", user_id))
            } else {
                ApiError::DatabaseError(e.to_string())
            }
        })
    }

    // transactional helpers removed; use `create`/`update` methods directly

    /// Get user by email
    pub async fn get_by_email(pool: &PgPool, email: &str) -> Result<User, ApiError> {
        log::debug!("UserRepository::get_by_email called with email={}", email);
        let user = sqlx::query_as::<sqlx::Postgres, User>(
            "SELECT * FROM users WHERE email = $1"
        )
        .bind(email)
        .fetch_one(pool)
        .await
        .map_err(|e| {
            if e.to_string().contains("no rows") {
                ApiError::NotFound(format!("User with email {} not found", email))
            } else {
                ApiError::DatabaseError(e.to_string())
            }
        })?;

        log::debug!("UserRepository::get_by_email result email={} id={} phone={:?}", user.email, user.id, user.phone_number);
        Ok(user)
    }

    /// Get all users (with optional pagination)
    pub async fn get_all(pool: &PgPool, limit: i64, offset: i64) -> Result<Vec<User>, ApiError> {
        sqlx::query_as::<sqlx::Postgres, User>(
            "SELECT * FROM users ORDER BY created_at DESC LIMIT $1 OFFSET $2"
        )
        .bind(limit)
        .bind(offset)
        .fetch_all(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))
    }

    /// Create a new user
    pub async fn create(pool: &PgPool, user: &User) -> Result<User, ApiError> {
        sqlx::query_as::<sqlx::Postgres, User>(
            r#"
            INSERT INTO users (
                id, email, phone_number, phone_country_code, password_hash, first_name, last_name, 
                middle_name, date_of_birth, gender, profile_picture_url, 
                address_line1, address_line2, city, state, postal_code, country,
                status, is_verified, is_approved, verified_at, approved_at, approved_by, 
                last_login_at, otp_code, otp_expires, last_otp_sent, created_at, updated_at, is_active
            ) VALUES (
                $1, $2, $3, $4, $5, $6, $7, $8, $9, $10,
                $11, $12, $13, $14, $15, $16, $17, $18, $19, $20,
                $21, $22, $23, $24, $25, $26, $27, $28, $29, $30
            )
            RETURNING *
            "#
        )
        .bind(user.id)
        .bind(&user.email)
        .bind(&user.phone_number)
        .bind(&user.phone_country_code)
        .bind(&user.password_hash)
        .bind(&user.first_name)
        .bind(&user.last_name)
        .bind(&user.middle_name)
        .bind(user.date_of_birth)
        .bind(&user.gender)
        .bind(&user.profile_picture_url)
        .bind(&user.address_line1)
        .bind(&user.address_line2)
        .bind(&user.city)
        .bind(&user.state)
        .bind(&user.postal_code)
        .bind(&user.country)
        .bind(&user.status)
        .bind(user.is_verified)
        .bind(user.is_approved)
        .bind(user.verified_at)
        .bind(user.approved_at)
        .bind(user.approved_by)
        .bind(user.last_login_at)
        .bind(&user.otp_code)
        .bind(user.otp_expires)
        .bind(user.last_otp_sent)
        .bind(user.created_at)
        .bind(user.updated_at)
        .bind(user.is_active)
        .fetch_one(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))
    }

    /// Update user
    pub async fn update(pool: &PgPool, user_id: Uuid, updates: &User) -> Result<User, ApiError> {
        sqlx::query_as::<sqlx::Postgres, User>(
            r#"
            UPDATE users SET
                email = $1,
                phone_number = $2,
                phone_country_code = $3,
                password_hash = $4,
                first_name = $5,
                last_name = $6,
                middle_name = $7,
                date_of_birth = $8,
                gender = $9,
                profile_picture_url = $10,
                address_line1 = $11,
                address_line2 = $12,
                city = $13,
                state = $14,
                postal_code = $15,
                country = $16,
                status = $17,
                is_verified = $18,
                is_approved = $19,
                verified_at = $20,
                approved_at = $21,
                approved_by = $22,
                last_login_at = $23,
                otp_code = $24,
                otp_expires = $25,
                last_otp_sent = $26,
                created_at = $27,
                updated_at = $28,
                is_active = $29
            WHERE id = $30
            RETURNING *
            "#
        )
        .bind(&updates.email)
        .bind(&updates.phone_number)
        .bind(&updates.phone_country_code)
        .bind(&updates.password_hash)
        .bind(&updates.first_name)
        .bind(&updates.last_name)
        .bind(&updates.middle_name)
        .bind(updates.date_of_birth)
        .bind(&updates.gender)
        .bind(&updates.profile_picture_url)
        .bind(&updates.address_line1)
        .bind(&updates.address_line2)
        .bind(&updates.city)
        .bind(&updates.state)
        .bind(&updates.postal_code)
        .bind(&updates.country)
        .bind(&updates.status)
        .bind(updates.is_verified)
        .bind(updates.is_approved)
        .bind(updates.verified_at)
        .bind(updates.approved_at)
        .bind(updates.approved_by)
        .bind(updates.last_login_at)
        .bind(&updates.otp_code)
        .bind(updates.otp_expires)
        .bind(updates.last_otp_sent)
        .bind(updates.created_at)
        .bind(updates.updated_at)
        .bind(updates.is_active)
        .bind(user_id)
        .fetch_one(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))
    }

    // transactional helpers removed; use `create`/`update` methods directly

    /// Delete user (soft delete)
    pub async fn delete(pool: &PgPool, user_id: Uuid) -> Result<(), ApiError> {
        sqlx::query(
            "UPDATE users SET is_active = false, updated_at = NOW() WHERE id = $1"
        )
        .bind(user_id)
        .execute(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

        Ok(())
    }
}

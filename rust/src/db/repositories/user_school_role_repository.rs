use uuid::Uuid;
use sqlx::PgPool;
use sqlx::{Transaction, Postgres, Executor};
use crate::errors::ApiError;
use crate::models::UserSchoolRole;

pub struct UserSchoolRoleRepository;

impl UserSchoolRoleRepository {
    /// Get UserSchoolRole by ID
    pub async fn get_by_id(pool: &PgPool, id: Uuid) -> Result<UserSchoolRole, ApiError> {
        sqlx::query_as::<_, UserSchoolRole>(
            "SELECT * FROM user_school_roles WHERE id = $1"
        )
        .bind(id)
        .fetch_one(pool)
        .await
        .map_err(|e| {
            if e.to_string().contains("no rows") {
                ApiError::NotFound(format!("UserSchoolRole with id {} not found", id))
            } else {
                ApiError::DatabaseError(e.to_string())
            }
        })
    }

    /// Create a new UserSchoolRole within an existing transaction
    // transactional helpers removed; use `create` method on pool instead

    /// Check if a UserSchoolRole exists for a specific user, school, and role
    pub async fn exists(
        pool: &PgPool,
        user_id: Uuid,
        school_id: Uuid,
        role_id: Uuid,
    ) -> Result<bool, ApiError> {
        // Log inputs for debugging role existence issues
        log::info!("UserSchoolRoleRepository::exists called with user_id={} school_id={} role_id={}", user_id, school_id, role_id);
        let result = sqlx::query_scalar::<_, i64>(
            "SELECT COUNT(*) FROM user_school_roles WHERE user_id = $1 AND school_id = $2 AND role_id = $3 AND is_active = true"
        )
        .bind(user_id)
        .bind(school_id)
        .bind(role_id)
        .fetch_one(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

        log::info!("UserSchoolRoleRepository::exists result count={} for user_id={} school_id={} role_id={}", result, user_id, school_id, role_id);

        Ok(result > 0)
    }

    /// Get all UserSchoolRoles for a specific user
    pub async fn get_by_user_id(pool: &PgPool, user_id: Uuid) -> Result<Vec<UserSchoolRole>, ApiError> {
        sqlx::query_as::<_, UserSchoolRole>(
            "SELECT * FROM user_school_roles WHERE user_id = $1 AND is_active = true ORDER BY created_at DESC"
        )
        .bind(user_id)
        .fetch_all(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))
    }

    /// Get all UserSchoolRoles for a specific school
    pub async fn get_by_school_id(pool: &PgPool, school_id: Uuid) -> Result<Vec<UserSchoolRole>, ApiError> {
        sqlx::query_as::<_, UserSchoolRole>(
            "SELECT * FROM user_school_roles WHERE school_id = $1 AND is_active = true ORDER BY created_at DESC"
        )
        .bind(school_id)
        .fetch_all(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))
    }

    /// Create a new UserSchoolRole
    pub async fn create(pool: &PgPool, user_school_role: &UserSchoolRole) -> Result<UserSchoolRole, ApiError> {
        sqlx::query_as::<_, UserSchoolRole>(
            r#"
            INSERT INTO user_school_roles (
                id, school_id, user_id, role_id, created_at, updated_at, is_active
            ) VALUES (
                $1, $2, $3, $4, $5, $6, $7
            )
            RETURNING *
            "#
        )
        .bind(user_school_role.id)
        .bind(user_school_role.school_id)
        .bind(user_school_role.user_id)
        .bind(user_school_role.role_id)
        .bind(user_school_role.created_at)
        .bind(user_school_role.updated_at)
        .bind(user_school_role.is_active)
        .fetch_one(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))
    }

    /// Update a UserSchoolRole
    pub async fn update(pool: &PgPool, id: Uuid, updates: &UserSchoolRole) -> Result<UserSchoolRole, ApiError> {
        sqlx::query_as::<_, UserSchoolRole>(
            r#"
            UPDATE user_school_roles SET
                school_id = $1, user_id = $2, role_id = $3, updated_at = $4, is_active = $5
            WHERE id = $6
            RETURNING *
            "#
        )
        .bind(updates.school_id)
        .bind(updates.user_id)
        .bind(updates.role_id)
        .bind(updates.updated_at)
        .bind(updates.is_active)
        .bind(id)
        .fetch_one(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))
    }

    /// Delete a UserSchoolRole (soft delete)
    pub async fn delete(pool: &PgPool, id: Uuid) -> Result<(), ApiError> {
        sqlx::query(
            "UPDATE user_school_roles SET is_active = false, updated_at = NOW() WHERE id = $1"
        )
        .bind(id)
        .execute(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

        Ok(())
    }
}

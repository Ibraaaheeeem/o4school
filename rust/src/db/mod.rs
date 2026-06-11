// ============================================================================
// DATABASE LAYER
// ============================================================================
// Repository layer - handles all database interactions

pub mod repositories;

use sqlx::postgres::PgPool;
use crate::errors::ApiError;

#[derive(Clone)]
pub struct Database {
    pool: PgPool,
}

impl Database {
    pub async fn new(database_url: &str) -> Result<Self, ApiError> {
        let pool = PgPool::connect(database_url)
            .await
            .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

        // Remove stale migration records from old file naming (version < 202605290001).
        // Migrations use IF NOT EXISTS so re-applying them is safe.
        let _ = sqlx::query("DELETE FROM _sqlx_migrations WHERE version < 202605290001")
            .execute(&pool)
            .await;

        sqlx::migrate!("./migrations")
            .run(&pool)
            .await
            .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

        Ok(Database { pool })
    }

    pub fn pool(&self) -> &PgPool {
        &self.pool
    }

    pub async fn health_check(&self) -> Result<(), ApiError> {
        sqlx::query("SELECT 1")
            .execute(&self.pool)
            .await
            .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

        Ok(())
    }
}

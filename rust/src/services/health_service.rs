use chrono::Utc;
use crate::db::Database;
use crate::errors::ApiError;
use crate::models::HealthResponse;

pub struct HealthService;

impl HealthService {
    pub async fn check_system_health(db: &Database) -> Result<HealthResponse, ApiError> {
        db.health_check().await?;

        Ok(HealthResponse {
            status: "ok".to_string(),
            database: "connected".to_string(),
            timestamp: Utc::now().naive_utc(),
        })
    }
}

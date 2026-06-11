// ============================================================================
// DASHBOARD HANDLER
// ============================================================================

use actix_web::{web, HttpResponse};
use uuid::Uuid;

use crate::db::Database;
use crate::errors::ApiError;
use crate::middleware::UserContext;
use crate::services::dashboard_service::DashboardService;

#[derive(serde::Deserialize)]
pub struct DashboardQuery {
    /// Optional: specify which school's dashboard to load.
    /// If omitted, the user's first active school is used.
    pub school_id: Option<Uuid>,
}

/// GET /api/auth/dashboard
///
/// Returns role-scoped dashboard data for the authenticated user.
/// Requires a valid Bearer JWT in the `Authorization` header.
pub async fn get_dashboard(
    db: web::Data<Database>,
    user_ctx: UserContext,
    query: web::Query<DashboardQuery>,
) -> Result<HttpResponse, ApiError> {
    let data =
        DashboardService::get_dashboard(&db, user_ctx.user_id, query.school_id).await?;
    Ok(HttpResponse::Ok().json(data))
}

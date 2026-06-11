use actix_web::{web, HttpResponse};

use crate::db::Database;
use crate::errors::ApiError;
use crate::middleware::UserContext;
use crate::models::{
	CreateAcademicSessionRequest, CreateCalendarEventRequest, CreateTermRequest,
	CreateSchoolTimetableRequest, UpdateAcademicSessionRequest, UpdateCalendarEventRequest,
	UpdateSchoolTimetableRequest, UpdateTermRequest, TimetableSuccessResponse,
};
use crate::services::ScheduleService;

#[derive(Debug, Clone, serde::Deserialize)]
pub struct TermsInSessionQuery {
	pub school_id: uuid::Uuid,
}

#[derive(Debug, Clone, serde::Deserialize)]
pub struct SchoolScopeQuery {
	pub school_id: uuid::Uuid,
}

pub async fn create_academic_session(
	db: web::Data<Database>,
	payload: web::Json<CreateAcademicSessionRequest>,
	user_ctx: UserContext,
) -> Result<HttpResponse, ApiError> {
	let created =
		ScheduleService::create_academic_session(&db, user_ctx.user_id, payload.into_inner()).await?;
	Ok(HttpResponse::Created().json(created))
}

pub async fn list_academic_sessions(
	db: web::Data<Database>,
	query: web::Query<SchoolScopeQuery>,
	user_ctx: UserContext,
) -> Result<HttpResponse, ApiError> {
	let q = query.into_inner();
	let sessions = ScheduleService::list_academic_sessions(&db, user_ctx.user_id, q.school_id).await?;
	Ok(HttpResponse::Ok().json(sessions))
}

pub async fn update_academic_session(
	db: web::Data<Database>,
	path: web::Path<(uuid::Uuid,)>,
	payload: web::Json<UpdateAcademicSessionRequest>,
	user_ctx: UserContext,
) -> Result<HttpResponse, ApiError> {
	let session_id = path.into_inner().0;
	let updated =
		ScheduleService::update_academic_session(&db, user_ctx.user_id, session_id, payload.into_inner())
			.await?;
	Ok(HttpResponse::Ok().json(updated))
}

pub async fn delete_academic_session(
	db: web::Data<Database>,
	path: web::Path<(uuid::Uuid,)>,
	query: web::Query<SchoolScopeQuery>,
	user_ctx: UserContext,
) -> Result<HttpResponse, ApiError> {
	let session_id = path.into_inner().0;
	let q = query.into_inner();
	ScheduleService::delete_academic_session(&db, user_ctx.user_id, q.school_id, session_id).await?;
	Ok(HttpResponse::Ok().json(serde_json::json!({"status":"deleted"})))
}

pub async fn create_calendar_event(
	db: web::Data<Database>,
	payload: web::Json<CreateCalendarEventRequest>,
	user_ctx: UserContext,
) -> Result<HttpResponse, ApiError> {
	let created = ScheduleService::create_calendar_event(&db, user_ctx.user_id, payload.into_inner()).await?;
	Ok(HttpResponse::Created().json(created))
}

pub async fn list_calendar_events(
	db: web::Data<Database>,
	query: web::Query<SchoolScopeQuery>,
	user_ctx: UserContext,
) -> Result<HttpResponse, ApiError> {
	let q = query.into_inner();
	let events = ScheduleService::list_calendar_events(&db, user_ctx.user_id, q.school_id).await?;
	Ok(HttpResponse::Ok().json(events))
}

pub async fn get_calendar_event(
	db: web::Data<Database>,
	path: web::Path<(uuid::Uuid,)>,
	query: web::Query<SchoolScopeQuery>,
	user_ctx: UserContext,
) -> Result<HttpResponse, ApiError> {
	let event_id = path.into_inner().0;
	let q = query.into_inner();
	let event = ScheduleService::get_calendar_event(&db, user_ctx.user_id, q.school_id, event_id).await?;
	Ok(HttpResponse::Ok().json(event))
}

pub async fn update_calendar_event(
	db: web::Data<Database>,
	path: web::Path<(uuid::Uuid,)>,
	payload: web::Json<UpdateCalendarEventRequest>,
	user_ctx: UserContext,
) -> Result<HttpResponse, ApiError> {
	let event_id = path.into_inner().0;
	let updated =
		ScheduleService::update_calendar_event(&db, user_ctx.user_id, event_id, payload.into_inner())
			.await?;
	Ok(HttpResponse::Ok().json(updated))
}

pub async fn delete_calendar_event(
	db: web::Data<Database>,
	path: web::Path<(uuid::Uuid,)>,
	query: web::Query<SchoolScopeQuery>,
	user_ctx: UserContext,
) -> Result<HttpResponse, ApiError> {
	let event_id = path.into_inner().0;
	let q = query.into_inner();
	ScheduleService::delete_calendar_event(&db, user_ctx.user_id, q.school_id, event_id).await?;
	Ok(HttpResponse::Ok().json(serde_json::json!({"status":"deleted"})))
}

pub async fn create_school_timetable_item(
	db: web::Data<Database>,
	payload: web::Json<CreateSchoolTimetableRequest>,
	user_ctx: UserContext,
) -> Result<HttpResponse, ApiError> {
	let created_items =
		ScheduleService::create_school_timetable_item(&db, user_ctx.user_id, payload.into_inner()).await?;
	let response = TimetableSuccessResponse {
		success: true,
		message: "School timetable activity created successfully".to_string(),
		data: created_items,
	};
	Ok(HttpResponse::Created().json(response))
}

pub async fn list_school_timetable_items(
	db: web::Data<Database>,
	query: web::Query<SchoolScopeQuery>,
	user_ctx: UserContext,
) -> Result<HttpResponse, ApiError> {
	let q = query.into_inner();
	let items = ScheduleService::list_school_timetable_items(&db, user_ctx.user_id, q.school_id).await?;
	Ok(HttpResponse::Ok().json(items))
}

pub async fn get_school_timetable_item(
	db: web::Data<Database>,
	path: web::Path<(uuid::Uuid,)>,
	query: web::Query<SchoolScopeQuery>,
	user_ctx: UserContext,
) -> Result<HttpResponse, ApiError> {
	let item_id = path.into_inner().0;
	let q = query.into_inner();
	let item =
		ScheduleService::get_school_timetable_item(&db, user_ctx.user_id, q.school_id, item_id).await?;
	Ok(HttpResponse::Ok().json(item))
}

pub async fn update_school_timetable_item(
	db: web::Data<Database>,
	path: web::Path<(uuid::Uuid,)>,
	payload: web::Json<UpdateSchoolTimetableRequest>,
	user_ctx: UserContext,
) -> Result<HttpResponse, ApiError> {
	let item_id = path.into_inner().0;
	let updated = ScheduleService::update_school_timetable_item(
		&db,
		user_ctx.user_id,
		item_id,
		payload.into_inner(),
	)
	.await?;
	let response = TimetableSuccessResponse {
		success: true,
		message: "School timetable activity updated successfully".to_string(),
		data: updated,
	};
	Ok(HttpResponse::Ok().json(response))
}

pub async fn delete_school_timetable_item(
	db: web::Data<Database>,
	path: web::Path<(uuid::Uuid,)>,
	query: web::Query<SchoolScopeQuery>,
	user_ctx: UserContext,
) -> Result<HttpResponse, ApiError> {
	let item_id = path.into_inner().0;
	let q = query.into_inner();
	ScheduleService::delete_school_timetable_item(&db, user_ctx.user_id, q.school_id, item_id).await?;
	let response = TimetableSuccessResponse {
		success: true,
		message: "School timetable activity deleted successfully".to_string(),
		data: (),
	};
	Ok(HttpResponse::Ok().json(response))
}

pub async fn get_terms_in_session(
	db: web::Data<Database>,
	path: web::Path<(uuid::Uuid,)>,
	query: web::Query<TermsInSessionQuery>,
	user_ctx: UserContext,
) -> Result<HttpResponse, ApiError> {
	let session_id = path.into_inner().0;
	let q = query.into_inner();
	let terms =
		ScheduleService::get_terms_in_session(&db, user_ctx.user_id, q.school_id, session_id).await?;
	Ok(HttpResponse::Ok().json(terms))
}

pub async fn create_term(
	db: web::Data<Database>,
	payload: web::Json<CreateTermRequest>,
	user_ctx: UserContext,
) -> Result<HttpResponse, ApiError> {
	let created = ScheduleService::create_term(&db, user_ctx.user_id, payload.into_inner()).await?;
	Ok(HttpResponse::Created().json(created))
}

pub async fn update_term(
	db: web::Data<Database>,
	path: web::Path<(uuid::Uuid,)>,
	payload: web::Json<UpdateTermRequest>,
	user_ctx: UserContext,
) -> Result<HttpResponse, ApiError> {
	let term_id = path.into_inner().0;
	let updated = ScheduleService::update_term(&db, user_ctx.user_id, term_id, payload.into_inner()).await?;
	Ok(HttpResponse::Ok().json(updated))
}

pub async fn delete_term(
	db: web::Data<Database>,
	path: web::Path<(uuid::Uuid,)>,
	query: web::Query<SchoolScopeQuery>,
	user_ctx: UserContext,
) -> Result<HttpResponse, ApiError> {
	let term_id = path.into_inner().0;
	let q = query.into_inner();
	ScheduleService::delete_term(&db, user_ctx.user_id, q.school_id, term_id).await?;
	Ok(HttpResponse::Ok().json(serde_json::json!({"status":"deleted"})))
}

pub async fn get_current_schedule(
	db: web::Data<Database>,
	query: web::Query<SchoolScopeQuery>,
	_user_ctx: UserContext,
) -> Result<HttpResponse, ApiError> {
	let q = query.into_inner();
	
	let session_id = sqlx::query_scalar::<sqlx::Postgres, uuid::Uuid>(
		"SELECT id FROM academic_sessions WHERE school_id = $1 AND is_current_session = true LIMIT 1"
	)
	.bind(q.school_id)
	.fetch_optional(db.pool())
	.await
	.map_err(|e| ApiError::DatabaseError(e.to_string()))?
	.ok_or_else(|| ApiError::NotFound("Current academic session not found".to_string()))?;

	let term_id = sqlx::query_scalar::<sqlx::Postgres, uuid::Uuid>(
		"SELECT id FROM terms WHERE academic_session_id = $1 AND is_current_term = true LIMIT 1"
	)
	.bind(session_id)
	.fetch_optional(db.pool())
	.await
	.map_err(|e| ApiError::DatabaseError(e.to_string()))?
	.ok_or_else(|| ApiError::NotFound("Current term not found".to_string()))?;

	Ok(HttpResponse::Ok().json(serde_json::json!({
		"session_id": session_id,
		"term_id": term_id,
	})))
}
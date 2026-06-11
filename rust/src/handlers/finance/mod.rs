use actix_web::{web, HttpResponse};
use chrono::NaiveDateTime;
use serde::Deserialize;
use uuid::Uuid;

use crate::db::Database;
use crate::errors::ApiError;
use crate::middleware::UserContext;
use crate::services::FinanceService;

#[derive(Debug, Clone, Deserialize)]
pub struct SchoolScopeQuery {
	pub school_id: Uuid,
}

#[derive(Debug, Clone, Deserialize)]
pub struct FeeItemRequest {
	pub school_id: Uuid,
	pub amount: f64,
	pub description: Option<String>,
	pub is_mandatory: Option<bool>,
	pub name: String,
	pub gender_eligibility: Option<String>,
	pub student_status_eligibility: Option<String>,
	pub staff_discount_amount: Option<f64>,
	pub staff_discount_type: Option<String>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct UpsertClassFeeItemAssignmentRequest {
	pub school_id: Uuid,
	pub class_id: Uuid,
	pub academic_year: String,
	pub custom_amount: Option<f64>,
	pub is_applicable: Option<bool>,
	pub notes: Option<String>,
	pub term: Option<String>,
	pub academic_session_id: Option<Uuid>,
	pub term_id: Option<Uuid>,
	pub is_locked: Option<bool>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct ApplyOptionalFeeItemToStudentRequest {
	pub school_id: Uuid,
	pub student_id: Uuid,
	pub class_fee_item_id: Uuid,
	pub academic_session_id: Option<Uuid>,
	pub term_id: Option<Uuid>,
	pub custom_amount: Option<f64>,
	pub notes: Option<String>,
	pub is_locked: Option<bool>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct StudentOptionalFeeActionRequest {
	pub school_id: Uuid,
}

#[derive(Debug, Clone, Deserialize)]
pub struct ManualSettlementRequest {
	pub school_id: Uuid,
	pub amount: f64,
	pub currency: String,
	pub payer_email: Option<String>,
	pub payment_channel: Option<String>,
	pub raw_payload: Option<String>,
	pub reference: String,
	pub status: String,
	pub transaction_date: Option<NaiveDateTime>,
	pub wallet_id: Option<Uuid>,
	pub academic_session_year: Option<String>,
	pub term: Option<String>,
	pub academic_session_id: Option<Uuid>,
	pub term_id: Option<Uuid>,
	pub paystack_wallet_id: Option<Uuid>,
	pub squad_wallet_id: Option<Uuid>,
	pub provider: Option<String>,
	pub parent_id: Option<Uuid>,
}

pub async fn list_fee_items(
	db: web::Data<Database>,
	query: web::Query<SchoolScopeQuery>,
	user_ctx: UserContext,
) -> Result<HttpResponse, ApiError> {
	let q = query.into_inner();
	let items = FinanceService::list_fee_items(&db, user_ctx.user_id, q.school_id).await?;
	Ok(HttpResponse::Ok().json(items))
}

pub async fn create_fee_item(
	db: web::Data<Database>,
	payload: web::Json<FeeItemRequest>,
	user_ctx: UserContext,
) -> Result<HttpResponse, ApiError> {
	let req = payload.into_inner();
	let created = FinanceService::create_fee_item(
		&db,
		user_ctx.user_id,
		req.school_id,
		req.amount,
		req.description,
		req.is_mandatory,
		req.name,
		req.gender_eligibility,
		req.student_status_eligibility,
		req.staff_discount_amount,
		req.staff_discount_type,
	)
	.await?;

	Ok(HttpResponse::Created().json(created))
}

pub async fn update_fee_item(
	db: web::Data<Database>,
	path: web::Path<(Uuid,)>,
	payload: web::Json<FeeItemRequest>,
	user_ctx: UserContext,
) -> Result<HttpResponse, ApiError> {
	let fee_item_id = path.into_inner().0;
	let req = payload.into_inner();
	let updated = FinanceService::update_fee_item(
		&db,
		user_ctx.user_id,
		fee_item_id,
		req.school_id,
		req.amount,
		req.description,
		req.is_mandatory,
		req.name,
		req.gender_eligibility,
		req.student_status_eligibility,
		req.staff_discount_amount,
		req.staff_discount_type,
	)
	.await?;

	Ok(HttpResponse::Ok().json(updated))
}

pub async fn delete_fee_item(
	db: web::Data<Database>,
	path: web::Path<(Uuid,)>,
	query: web::Query<SchoolScopeQuery>,
	user_ctx: UserContext,
) -> Result<HttpResponse, ApiError> {
	let fee_item_id = path.into_inner().0;
	let q = query.into_inner();
	FinanceService::delete_fee_item(&db, user_ctx.user_id, q.school_id, fee_item_id).await?;
	Ok(HttpResponse::Ok().json(serde_json::json!({"status": "deleted"})))
}

pub async fn upsert_class_fee_item_assignment(
	db: web::Data<Database>,
	path: web::Path<(Uuid,)>,
	payload: web::Json<UpsertClassFeeItemAssignmentRequest>,
	user_ctx: UserContext,
) -> Result<HttpResponse, ApiError> {
	let fee_item_id = path.into_inner().0;
	let req = payload.into_inner();
	let assigned = FinanceService::upsert_class_fee_item_assignment(
		&db,
		user_ctx.user_id,
		fee_item_id,
		req.school_id,
		req.class_id,
		req.academic_year,
		req.custom_amount,
		req.is_applicable,
		req.notes,
		req.term,
		req.academic_session_id,
		req.term_id,
		req.is_locked,
	)
	.await?;

	Ok(HttpResponse::Ok().json(assigned))
}

pub async fn list_class_fee_item_assignments(
	db: web::Data<Database>,
	path: web::Path<(Uuid,)>,
	query: web::Query<SchoolScopeQuery>,
	user_ctx: UserContext,
) -> Result<HttpResponse, ApiError> {
	let fee_item_id = path.into_inner().0;
	let q = query.into_inner();
	let assignments = FinanceService::list_class_fee_item_assignments(
		&db,
		user_ctx.user_id,
		q.school_id,
		fee_item_id,
	)
	.await?;

	Ok(HttpResponse::Ok().json(assignments))
}

pub async fn delete_class_fee_item_assignment(
	db: web::Data<Database>,
	path: web::Path<(Uuid, Uuid)>,
	query: web::Query<SchoolScopeQuery>,
	user_ctx: UserContext,
) -> Result<HttpResponse, ApiError> {
	let (fee_item_id, class_id) = path.into_inner();
	let q = query.into_inner();
	FinanceService::delete_class_fee_item_assignment(&db, user_ctx.user_id, q.school_id, fee_item_id, class_id).await?;
	Ok(HttpResponse::Ok().json(serde_json::json!({"status": "deleted"})))
}

pub async fn apply_optional_fee_item_to_student(
	db: web::Data<Database>,
	path: web::Path<(Uuid,)>,
	payload: web::Json<ApplyOptionalFeeItemToStudentRequest>,
	user_ctx: UserContext,
) -> Result<HttpResponse, ApiError> {
	let fee_item_id = path.into_inner().0;
	let req = payload.into_inner();

	let applied = FinanceService::apply_optional_fee_item_to_student(
		&db,
		user_ctx.user_id,
		fee_item_id,
		req.school_id,
		req.student_id,
		req.class_fee_item_id,
		req.academic_session_id,
		req.term_id,
		req.custom_amount,
		req.notes,
		req.is_locked,
	)
	.await?;

	Ok(HttpResponse::Ok().json(applied))
}

pub async fn lock_student_optional_fee(
	db: web::Data<Database>,
	path: web::Path<(Uuid,)>,
	payload: web::Json<StudentOptionalFeeActionRequest>,
	user_ctx: UserContext,
) -> Result<HttpResponse, ApiError> {
	let student_optional_fee_id = path.into_inner().0;
	let req = payload.into_inner();
	let updated = FinanceService::lock_student_optional_fee(
		&db,
		user_ctx.user_id,
		req.school_id,
		student_optional_fee_id,
	)
	.await?;

	Ok(HttpResponse::Ok().json(updated))
}

pub async fn create_manual_settlement(
	db: web::Data<Database>,
	payload: web::Json<ManualSettlementRequest>,
	user_ctx: UserContext,
) -> Result<HttpResponse, ApiError> {
	let req = payload.into_inner();

	let created = FinanceService::create_manual_settlement(
		&db,
		user_ctx.user_id,
		req.school_id,
		req.amount,
		req.currency,
		req.payer_email,
		req.payment_channel,
		req.raw_payload,
		req.reference,
		req.status,
		req.transaction_date,
		req.wallet_id,
		req.academic_session_year,
		req.term,
		req.academic_session_id,
		req.term_id,
		req.paystack_wallet_id,
		req.squad_wallet_id,
		req.provider,
		req.parent_id,
	)
	.await?;

	Ok(HttpResponse::Created().json(created))
}

pub async fn unlock_student_optional_fee(
	db: web::Data<Database>,
	path: web::Path<(Uuid,)>,
	payload: web::Json<StudentOptionalFeeActionRequest>,
	user_ctx: UserContext,
) -> Result<HttpResponse, ApiError> {
	let student_optional_fee_id = path.into_inner().0;
	let req = payload.into_inner();
	let updated = FinanceService::unlock_student_optional_fee(
		&db,
		user_ctx.user_id,
		req.school_id,
		student_optional_fee_id,
	)
	.await?;

	Ok(HttpResponse::Ok().json(updated))
}

pub async fn delete_student_optional_fee(
	db: web::Data<Database>,
	path: web::Path<(Uuid,)>,
	query: web::Query<SchoolScopeQuery>,
	user_ctx: UserContext,
) -> Result<HttpResponse, ApiError> {
	let student_optional_fee_id = path.into_inner().0;
	let q = query.into_inner();
	FinanceService::delete_student_optional_fee(&db, user_ctx.user_id, q.school_id, student_optional_fee_id)
		.await?;

	Ok(HttpResponse::Ok().json(serde_json::json!({"status": "deleted"})))
}

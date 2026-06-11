use uuid::Uuid;
use serde_json::json;

use crate::db::repositories::FinanceRepository;
use crate::db::Database;
use crate::errors::ApiError;
use crate::models::{Bill, ClassFeeItem, FeeItem, Settlement, StudentOptionalFee};

pub struct FinanceService;

impl FinanceService {
    async fn ensure_school_admin(db: &Database, actor: Uuid, school_id: Uuid) -> Result<(), ApiError> {
        let school_admin_role_id = sqlx::query_scalar::<sqlx::Postgres, Uuid>(
            "SELECT id FROM roles WHERE name = 'SCHOOL_ADMIN' AND is_active = true",
        )
        .fetch_optional(db.pool())
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))?
        .ok_or_else(|| ApiError::DatabaseError("SCHOOL_ADMIN role not found".to_string()))?;

        let is_admin = crate::db::repositories::UserSchoolRoleRepository::exists(
            db.pool(),
            actor,
            school_id,
            school_admin_role_id,
        )
        .await?;

        if !is_admin {
            return Err(ApiError::Unauthorized(
                "Caller is not a SCHOOL_ADMIN for this school".to_string(),
            ));
        }

        Ok(())
    }

    fn validate_amount(amount: f64, field_name: &str) -> Result<(), ApiError> {
        if !amount.is_finite() || amount < 0.0 {
            return Err(ApiError::ValidationError(format!(
                "{} must be a non-negative number",
                field_name
            )));
        }

        Ok(())
    }

    pub async fn list_fee_items(
        db: &Database,
        actor: Uuid,
        school_id: Uuid,
    ) -> Result<Vec<FeeItem>, ApiError> {
        Self::ensure_school_admin(db, actor, school_id).await?;
        FinanceRepository::list_fee_items_by_school(db.pool(), school_id).await
    }

    async fn recalculate_student_bill_internal(
        db: &Database,
        school_id: Uuid,
        student_id: Uuid,
        academic_session_id: Option<Uuid>,
        term_id: Option<Uuid>,
    ) -> Result<Bill, ApiError> {
        let _ = FinanceRepository::get_student_by_id_and_school(db.pool(), school_id, student_id).await?;

        let class_total = FinanceRepository::calculate_total_class_fee_for_student(
            db.pool(),
            school_id,
            student_id,
            academic_session_id,
            term_id,
        )
        .await?;

        let optional_total = FinanceRepository::calculate_total_optional_fee_for_student(
            db.pool(),
            school_id,
            student_id,
            academic_session_id,
            term_id,
        )
        .await?;

        let mandatory_breakdown = FinanceRepository::list_mandatory_fee_breakdown_for_student(
            db.pool(),
            school_id,
            student_id,
            academic_session_id,
            term_id,
        )
        .await?;

        let optional_breakdown = FinanceRepository::list_optional_fee_breakdown_for_student(
            db.pool(),
            school_id,
            student_id,
            academic_session_id,
            term_id,
        )
        .await?;

        let mut breakdown_entries = Vec::with_capacity(mandatory_breakdown.len() + optional_breakdown.len());
        for item in mandatory_breakdown {
            breakdown_entries.push(json!({
                "type": "MANDATORY",
                "fee_name": item.fee_name,
                "amount": item.amount,
            }));
        }
        for item in optional_breakdown {
            breakdown_entries.push(json!({
                "type": "OPTIONAL",
                "fee_name": item.fee_name,
                "amount": item.amount,
            }));
        }

        let breakdown = Some(
            serde_json::to_string(&breakdown_entries)
                .map_err(|e| ApiError::ValidationError(format!("Failed to build bill breakdown: {}", e)))?,
        );

        let total_amount = class_total + optional_total;
        Self::validate_amount(total_amount, "total_amount")?;

        FinanceRepository::upsert_student_bill(
            db.pool(),
            school_id,
            student_id,
            academic_session_id,
            term_id,
            total_amount,
            breakdown,
        )
        .await
    }

    pub async fn recalculate_student_bill(
        db: &Database,
        actor: Uuid,
        school_id: Uuid,
        student_id: Uuid,
        academic_session_id: Option<Uuid>,
        term_id: Option<Uuid>,
    ) -> Result<Bill, ApiError> {
        Self::ensure_school_admin(db, actor, school_id).await?;
        Self::recalculate_student_bill_internal(
            db,
            school_id,
            student_id,
            academic_session_id,
            term_id,
        )
        .await
    }

    pub async fn recalculate_bills_for_class_fee_item(
        db: &Database,
        actor: Uuid,
        school_id: Uuid,
        class_fee_item_id: Uuid,
    ) -> Result<Vec<Bill>, ApiError> {
        Self::ensure_school_admin(db, actor, school_id).await?;

        let class_fee_item = FinanceRepository::get_class_fee_item_assignment_by_id(
            db.pool(),
            school_id,
            class_fee_item_id,
        )
        .await?;

        let student_ids = FinanceRepository::list_student_ids_for_class(
            db.pool(),
            school_id,
            class_fee_item.class_id,
            class_fee_item.academic_session_id,
            class_fee_item.term_id,
        )
        .await?;

        let mut bills = Vec::with_capacity(student_ids.len());
        for student_id in student_ids {
            let bill = Self::recalculate_student_bill_internal(
                db,
                school_id,
                student_id,
                class_fee_item.academic_session_id,
                class_fee_item.term_id,
            )
            .await?;
            bills.push(bill);
        }

        Ok(bills)
    }

    #[allow(clippy::too_many_arguments)]
    pub async fn create_manual_settlement(
        db: &Database,
        actor: Uuid,
        school_id: Uuid,
        amount: f64,
        currency: String,
        payer_email: Option<String>,
        payment_channel: Option<String>,
        raw_payload: Option<String>,
        reference: String,
        status: String,
        transaction_date: Option<chrono::NaiveDateTime>,
        wallet_id: Option<Uuid>,
        academic_session_year: Option<String>,
        term: Option<String>,
        academic_session_id: Option<Uuid>,
        term_id: Option<Uuid>,
        paystack_wallet_id: Option<Uuid>,
        squad_wallet_id: Option<Uuid>,
        provider: Option<String>,
        parent_id: Option<Uuid>,
    ) -> Result<Settlement, ApiError> {
        Self::ensure_school_admin(db, actor, school_id).await?;
        Self::validate_amount(amount, "amount")?;

        if currency.trim().is_empty() {
            return Err(ApiError::ValidationError("currency is required".to_string()));
        }

        if reference.trim().is_empty() {
            return Err(ApiError::ValidationError("reference is required".to_string()));
        }

        if status.trim().is_empty() {
            return Err(ApiError::ValidationError("status is required".to_string()));
        }

        FinanceRepository::create_settlement(
            db.pool(),
            school_id,
            amount,
            currency.trim().to_string(),
            payer_email,
            payment_channel,
            raw_payload,
            reference.trim().to_string(),
            status.trim().to_string(),
            transaction_date,
            wallet_id,
            academic_session_year,
            term,
            academic_session_id,
            term_id,
            paystack_wallet_id,
            squad_wallet_id,
            provider,
            parent_id,
        )
        .await
    }

    pub async fn create_fee_item(
        db: &Database,
        actor: Uuid,
        school_id: Uuid,
        amount: f64,
        description: Option<String>,
        is_mandatory: Option<bool>,
        name: String,
        gender_eligibility: Option<String>,
        student_status_eligibility: Option<String>,
        staff_discount_amount: Option<f64>,
        staff_discount_type: Option<String>,
    ) -> Result<FeeItem, ApiError> {
        Self::ensure_school_admin(db, actor, school_id).await?;
        Self::validate_amount(amount, "amount")?;

        if let Some(discount) = staff_discount_amount {
            Self::validate_amount(discount, "staff_discount_amount")?;
        }

        FinanceRepository::create_fee_item(
            db.pool(),
            school_id,
            amount,
            description,
            is_mandatory,
            name,
            gender_eligibility,
            student_status_eligibility,
            staff_discount_amount,
            staff_discount_type,
        )
        .await
    }

    pub async fn update_fee_item(
        db: &Database,
        actor: Uuid,
        fee_item_id: Uuid,
        school_id: Uuid,
        amount: f64,
        description: Option<String>,
        is_mandatory: Option<bool>,
        name: String,
        gender_eligibility: Option<String>,
        student_status_eligibility: Option<String>,
        staff_discount_amount: Option<f64>,
        staff_discount_type: Option<String>,
    ) -> Result<FeeItem, ApiError> {
        Self::ensure_school_admin(db, actor, school_id).await?;
        Self::validate_amount(amount, "amount")?;

        if let Some(discount) = staff_discount_amount {
            Self::validate_amount(discount, "staff_discount_amount")?;
        }

        FinanceRepository::update_fee_item(
            db.pool(),
            fee_item_id,
            school_id,
            amount,
            description,
            is_mandatory,
            name,
            gender_eligibility,
            student_status_eligibility,
            staff_discount_amount,
            staff_discount_type,
        )
        .await
    }

    pub async fn delete_fee_item(
        db: &Database,
        actor: Uuid,
        school_id: Uuid,
        fee_item_id: Uuid,
    ) -> Result<(), ApiError> {
        Self::ensure_school_admin(db, actor, school_id).await?;

        let mut tx = db
            .pool()
            .begin()
            .await
            .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

        FinanceRepository::soft_delete_fee_item_in_tx(&mut tx, school_id, fee_item_id).await?;
        FinanceRepository::soft_delete_class_fee_items_for_fee_item_in_tx(&mut tx, school_id, fee_item_id)
            .await?;

        tx.commit()
            .await
            .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

        Ok(())
    }

    pub async fn upsert_class_fee_item_assignment(
        db: &Database,
        actor: Uuid,
        fee_item_id: Uuid,
        school_id: Uuid,
        class_id: Uuid,
        academic_year: String,
        custom_amount: Option<f64>,
        is_applicable: Option<bool>,
        notes: Option<String>,
        term: Option<String>,
        academic_session_id: Option<Uuid>,
        term_id: Option<Uuid>,
        is_locked: Option<bool>,
    ) -> Result<ClassFeeItem, ApiError> {
        Self::ensure_school_admin(db, actor, school_id).await?;

        if academic_year.trim().is_empty() {
            return Err(ApiError::ValidationError(
                "academic_year is required".to_string(),
            ));
        }

        if let Some(amount) = custom_amount {
            Self::validate_amount(amount, "custom_amount")?;
        }

        let _ = FinanceRepository::get_fee_item_by_id(db.pool(), school_id, fee_item_id).await?;

        sqlx::query_scalar::<sqlx::Postgres, Uuid>(
            "SELECT id FROM classes WHERE id = $1 AND school_id = $2 AND is_active = true",
        )
        .bind(class_id)
        .bind(school_id)
        .fetch_optional(db.pool())
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))?
        .ok_or_else(|| ApiError::ValidationError("Class not found for this school".to_string()))?;

        FinanceRepository::upsert_class_fee_item_assignment(
            db.pool(),
            school_id,
            fee_item_id,
            class_id,
            academic_year,
            custom_amount,
            is_applicable,
            notes,
            term,
            academic_session_id,
            term_id,
            is_locked,
        )
        .await
    }

    pub async fn list_class_fee_item_assignments(
        db: &Database,
        actor: Uuid,
        school_id: Uuid,
        fee_item_id: Uuid,
    ) -> Result<Vec<ClassFeeItem>, ApiError> {
        Self::ensure_school_admin(db, actor, school_id).await?;
        let _ = FinanceRepository::get_fee_item_by_id(db.pool(), school_id, fee_item_id).await?;
        FinanceRepository::list_class_fee_item_assignments_by_fee_item(db.pool(), school_id, fee_item_id).await
    }

    pub async fn delete_class_fee_item_assignment(
        db: &Database,
        actor: Uuid,
        school_id: Uuid,
        fee_item_id: Uuid,
        class_id: Uuid,
    ) -> Result<(), ApiError> {
        Self::ensure_school_admin(db, actor, school_id).await?;
        let _ = FinanceRepository::get_fee_item_by_id(db.pool(), school_id, fee_item_id).await?;
        FinanceRepository::soft_delete_class_fee_item_assignment(
            db.pool(),
            school_id,
            fee_item_id,
            class_id,
        )
        .await
    }

    pub async fn apply_optional_fee_item_to_student(
        db: &Database,
        actor: Uuid,
        fee_item_id: Uuid,
        school_id: Uuid,
        student_id: Uuid,
        class_fee_item_id: Uuid,
        academic_session_id: Option<Uuid>,
        term_id: Option<Uuid>,
        custom_amount: Option<f64>,
        notes: Option<String>,
        is_locked: Option<bool>,
    ) -> Result<StudentOptionalFee, ApiError> {
        Self::ensure_school_admin(db, actor, school_id).await?;

        if let Some(amount) = custom_amount {
            Self::validate_amount(amount, "custom_amount")?;
        }

        let fee_item = FinanceRepository::get_fee_item_by_id(db.pool(), school_id, fee_item_id).await?;
        if fee_item.is_mandatory.unwrap_or(false) {
            return Err(ApiError::ValidationError(
                "Cannot apply a mandatory fee item as optional".to_string(),
            ));
        }

        let class_assignment = FinanceRepository::get_class_fee_item_assignment_by_id(
            db.pool(),
            school_id,
            class_fee_item_id,
        )
        .await?;

        if class_assignment.fee_item_id != fee_item_id {
            return Err(ApiError::ValidationError(
                "class_fee_item_id does not belong to the provided fee_item_id".to_string(),
            ));
        }

        let student = FinanceRepository::get_student_by_id_and_school(db.pool(), school_id, student_id).await?;

        let gender_eligibility = fee_item
            .gender_eligibility
            .unwrap_or_else(|| "ALL".to_string())
            .trim()
            .to_uppercase();
        let student_gender = student
            .gender
            .unwrap_or_else(|| "UNKNOWN".to_string())
            .trim()
            .to_uppercase();

        if gender_eligibility != "ALL" && gender_eligibility != student_gender {
            return Err(ApiError::ValidationError(format!(
                "Student is not eligible by gender. Required={}, student={}",
                gender_eligibility, student_gender
            )));
        }

        let status_eligibility = fee_item
            .student_status_eligibility
            .unwrap_or_else(|| "ALL".to_string())
            .trim()
            .to_uppercase();
        let student_status = if student.is_new { "NEW" } else { "RETURNING" };

        if status_eligibility != "ALL" && status_eligibility != student_status {
            return Err(ApiError::ValidationError(format!(
                "Student is not eligible by status. Required={}, student={}",
                status_eligibility, student_status
            )));
        }

        FinanceRepository::upsert_student_optional_fee(
            db.pool(),
            school_id,
            student_id,
            class_fee_item_id,
            actor,
            academic_session_id,
            term_id,
            custom_amount,
            notes,
            is_locked,
        )
        .await
    }

    pub async fn lock_student_optional_fee(
        db: &Database,
        actor: Uuid,
        school_id: Uuid,
        student_optional_fee_id: Uuid,
    ) -> Result<StudentOptionalFee, ApiError> {
        Self::ensure_school_admin(db, actor, school_id).await?;
        FinanceRepository::set_student_optional_fee_lock_state(
            db.pool(),
            school_id,
            student_optional_fee_id,
            true,
        )
        .await
    }

    pub async fn unlock_student_optional_fee(
        db: &Database,
        actor: Uuid,
        school_id: Uuid,
        student_optional_fee_id: Uuid,
    ) -> Result<StudentOptionalFee, ApiError> {
        Self::ensure_school_admin(db, actor, school_id).await?;
        FinanceRepository::set_student_optional_fee_lock_state(
            db.pool(),
            school_id,
            student_optional_fee_id,
            false,
        )
        .await
    }

    pub async fn delete_student_optional_fee(
        db: &Database,
        actor: Uuid,
        school_id: Uuid,
        student_optional_fee_id: Uuid,
    ) -> Result<(), ApiError> {
        Self::ensure_school_admin(db, actor, school_id).await?;
        FinanceRepository::soft_delete_student_optional_fee(
            db.pool(),
            school_id,
            student_optional_fee_id,
        )
        .await
    }
}

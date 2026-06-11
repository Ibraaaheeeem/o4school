use chrono::Utc;
use sqlx::{PgPool, Postgres, Transaction};
use uuid::Uuid;

use crate::errors::ApiError;
use crate::models::{Bill, ClassFeeItem, FeeItem, Settlement, Student, StudentOptionalFee};

pub struct FinanceRepository;

#[derive(sqlx::FromRow)]
pub struct FeeBreakdownRow {
    pub fee_name: String,
    pub amount: f64,
}

impl FinanceRepository {
    pub async fn list_student_ids_for_class(
        pool: &PgPool,
        school_id: Uuid,
        class_id: Uuid,
        academic_session_id: Option<Uuid>,
        term_id: Option<Uuid>,
    ) -> Result<Vec<Uuid>, ApiError> {
        sqlx::query_scalar::<sqlx::Postgres, Uuid>(
            r#"
            SELECT DISTINCT student_id
            FROM student_classes
            WHERE school_id = $1
              AND class_id = $2
              AND is_active = true
              AND ($3::uuid IS NULL OR academic_session_id = $3)
              AND ($4::uuid IS NULL OR term_id = $4)
            "#,
        )
        .bind(school_id)
        .bind(class_id)
        .bind(academic_session_id)
        .bind(term_id)
        .fetch_all(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))
    }

    pub async fn calculate_total_class_fee_for_student(
        pool: &PgPool,
        school_id: Uuid,
        student_id: Uuid,
        academic_session_id: Option<Uuid>,
        term_id: Option<Uuid>,
    ) -> Result<f64, ApiError> {
        sqlx::query_scalar::<sqlx::Postgres, f64>(
            r#"
            WITH active_student_classes AS (
                SELECT DISTINCT class_id, academic_session_id, term_id
                FROM student_classes
                WHERE school_id = $1
                  AND student_id = $2
                  AND is_active = true
                  AND ($3::uuid IS NULL OR academic_session_id = $3)
                  AND ($4::uuid IS NULL OR term_id = $4)
            )
            SELECT COALESCE(SUM(COALESCE(cfi.custom_amount, fi.amount)::float8), 0.0)::float8
            FROM active_student_classes ascx
            JOIN class_fee_items cfi
              ON cfi.school_id = $1
             AND cfi.class_id = ascx.class_id
             AND cfi.is_active = true
             AND COALESCE(cfi.is_applicable, true) = true
             AND (cfi.academic_session_id IS NULL OR cfi.academic_session_id = ascx.academic_session_id)
             AND (cfi.term_id IS NULL OR cfi.term_id = ascx.term_id)
            JOIN fee_items fi
              ON fi.id = cfi.fee_item_id
             AND fi.school_id = $1
             AND fi.is_active = true
                         AND COALESCE(fi.is_mandatory, false) = true
            "#,
        )
        .bind(school_id)
        .bind(student_id)
        .bind(academic_session_id)
        .bind(term_id)
        .fetch_one(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))
    }

    pub async fn calculate_total_optional_fee_for_student(
        pool: &PgPool,
        school_id: Uuid,
        student_id: Uuid,
        academic_session_id: Option<Uuid>,
        term_id: Option<Uuid>,
    ) -> Result<f64, ApiError> {
        sqlx::query_scalar::<sqlx::Postgres, f64>(
            r#"
                        SELECT COALESCE(SUM(COALESCE(cfi.custom_amount, fi.amount)::float8), 0.0)::float8
            FROM student_optional_fees sof
            JOIN class_fee_items cfi
              ON cfi.id = sof.class_fee_item_id
             AND cfi.school_id = $1
             AND cfi.is_active = true
            JOIN fee_items fi
              ON fi.id = cfi.fee_item_id
             AND fi.school_id = $1
             AND fi.is_active = true
                         AND COALESCE(fi.is_mandatory, false) = false
            WHERE sof.school_id = $1
              AND sof.student_id = $2
              AND sof.is_active = true
              AND ($3::uuid IS NULL OR sof.academic_session_id = $3 OR sof.academic_session_id IS NULL)
              AND ($4::uuid IS NULL OR sof.term_id = $4 OR sof.term_id IS NULL)
            "#,
        )
        .bind(school_id)
        .bind(student_id)
        .bind(academic_session_id)
        .bind(term_id)
        .fetch_one(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))
    }

    pub async fn list_mandatory_fee_breakdown_for_student(
        pool: &PgPool,
        school_id: Uuid,
        student_id: Uuid,
        academic_session_id: Option<Uuid>,
        term_id: Option<Uuid>,
    ) -> Result<Vec<FeeBreakdownRow>, ApiError> {
        sqlx::query_as::<sqlx::Postgres, FeeBreakdownRow>(
            r#"
            WITH active_student_classes AS (
                SELECT DISTINCT class_id, academic_session_id, term_id
                FROM student_classes
                WHERE school_id = $1
                  AND student_id = $2
                  AND is_active = true
                  AND ($3::uuid IS NULL OR academic_session_id = $3)
                  AND ($4::uuid IS NULL OR term_id = $4)
            )
            SELECT
                fi.name AS fee_name,
                COALESCE(cfi.custom_amount, fi.amount)::float8 AS amount
            FROM active_student_classes ascx
            JOIN class_fee_items cfi
              ON cfi.school_id = $1
             AND cfi.class_id = ascx.class_id
             AND cfi.is_active = true
             AND COALESCE(cfi.is_applicable, true) = true
             AND (cfi.academic_session_id IS NULL OR cfi.academic_session_id = ascx.academic_session_id)
             AND (cfi.term_id IS NULL OR cfi.term_id = ascx.term_id)
            JOIN fee_items fi
              ON fi.id = cfi.fee_item_id
             AND fi.school_id = $1
             AND fi.is_active = true
             AND COALESCE(fi.is_mandatory, false) = true
            ORDER BY fi.name ASC
            "#,
        )
        .bind(school_id)
        .bind(student_id)
        .bind(academic_session_id)
        .bind(term_id)
        .fetch_all(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))
    }

    pub async fn list_optional_fee_breakdown_for_student(
        pool: &PgPool,
        school_id: Uuid,
        student_id: Uuid,
        academic_session_id: Option<Uuid>,
        term_id: Option<Uuid>,
    ) -> Result<Vec<FeeBreakdownRow>, ApiError> {
        sqlx::query_as::<sqlx::Postgres, FeeBreakdownRow>(
            r#"
            SELECT
                fi.name AS fee_name,
                COALESCE(cfi.custom_amount, fi.amount)::float8 AS amount
            FROM student_optional_fees sof
            JOIN class_fee_items cfi
              ON cfi.id = sof.class_fee_item_id
             AND cfi.school_id = $1
             AND cfi.is_active = true
            JOIN fee_items fi
              ON fi.id = cfi.fee_item_id
             AND fi.school_id = $1
             AND fi.is_active = true
             AND COALESCE(fi.is_mandatory, false) = false
            WHERE sof.school_id = $1
              AND sof.student_id = $2
              AND sof.is_active = true
              AND ($3::uuid IS NULL OR sof.academic_session_id = $3 OR sof.academic_session_id IS NULL)
              AND ($4::uuid IS NULL OR sof.term_id = $4 OR sof.term_id IS NULL)
            ORDER BY fi.name ASC
            "#,
        )
        .bind(school_id)
        .bind(student_id)
        .bind(academic_session_id)
        .bind(term_id)
        .fetch_all(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))
    }

    pub async fn upsert_student_bill(
        pool: &PgPool,
        school_id: Uuid,
        student_id: Uuid,
        academic_session_id: Option<Uuid>,
        term_id: Option<Uuid>,
        amount: f64,
        breakdown: Option<String>,
    ) -> Result<Bill, ApiError> {
        let existing = sqlx::query_as::<sqlx::Postgres, Bill>(
            r#"
            SELECT
                id,
                created_at,
                is_active,
                updated_at,
                school_id,
                student_id,
                academic_session_id,
                term_id,
                                amount::float8 AS amount,
                                breakdown
            FROM bills
            WHERE school_id = $1
              AND student_id = $2
              AND is_active = true
              AND academic_session_id IS NOT DISTINCT FROM $3
              AND term_id IS NOT DISTINCT FROM $4
            ORDER BY updated_at DESC
            LIMIT 1
            "#,
        )
        .bind(school_id)
        .bind(student_id)
        .bind(academic_session_id)
        .bind(term_id)
        .fetch_optional(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

        if let Some(existing_bill) = existing {
            return sqlx::query_as::<sqlx::Postgres, Bill>(
                r#"
                UPDATE bills
                SET amount = $1,
                    breakdown = $2,
                    updated_at = NOW(),
                    is_active = true
                WHERE id = $3
                RETURNING
                    id,
                    created_at,
                    is_active,
                    updated_at,
                    school_id,
                    student_id,
                    academic_session_id,
                    term_id,
                    amount::float8 AS amount,
                    breakdown
                "#,
            )
            .bind(amount)
            .bind(breakdown)
            .bind(existing_bill.id)
            .fetch_one(pool)
            .await
            .map_err(|e| ApiError::DatabaseError(e.to_string()));
        }

        let now = Utc::now().naive_utc();
        sqlx::query_as::<sqlx::Postgres, Bill>(
            r#"
            INSERT INTO bills (
                id,
                created_at,
                is_active,
                updated_at,
                school_id,
                student_id,
                academic_session_id,
                term_id,
                amount,
                breakdown
            ) VALUES (
                $1,
                $2,
                true,
                $3,
                $4,
                $5,
                $6,
                $7,
                $8,
                $9
            )
            RETURNING
                id,
                created_at,
                is_active,
                updated_at,
                school_id,
                student_id,
                academic_session_id,
                term_id,
                amount::float8 AS amount,
                breakdown
            "#,
        )
        .bind(Uuid::new_v4())
        .bind(now)
        .bind(now)
        .bind(school_id)
        .bind(student_id)
        .bind(academic_session_id)
        .bind(term_id)
        .bind(amount)
        .bind(breakdown)
        .fetch_one(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))
    }

    pub async fn create_settlement(
        pool: &PgPool,
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
        let now = Utc::now().naive_utc();

        sqlx::query_as::<sqlx::Postgres, Settlement>(
            r#"
            INSERT INTO settlements (
                id, created_at, is_active, updated_at,
                school_id, amount, currency, payer_email, payment_channel, raw_payload,
                reference, status, transaction_date, wallet_id, academic_session_year,
                term, academic_session_id, term_id, reimbursed, settlement_type,
                paystack_wallet_id, squad_wallet_id, provider, parent_id
            ) VALUES (
                $1, $2, true, $3,
                $4, $5, $6, $7, $8, $9,
                $10, $11, $12, $13, $14,
                $15, $16, $17, false, 'MANUAL',
                $18, $19, $20, $21
            )
            RETURNING
                id,
                created_at,
                is_active,
                updated_at,
                school_id,
                amount::float8 AS amount,
                currency,
                payer_email,
                payment_channel,
                raw_payload,
                reference,
                status,
                transaction_date,
                wallet_id,
                academic_session_year,
                term,
                academic_session_id,
                term_id,
                reimbursed,
                settlement_type,
                paystack_wallet_id,
                squad_wallet_id,
                provider,
                parent_id
            "#,
        )
        .bind(Uuid::new_v4())
        .bind(now)
        .bind(now)
        .bind(school_id)
        .bind(amount)
        .bind(currency)
        .bind(payer_email)
        .bind(payment_channel)
        .bind(raw_payload)
        .bind(reference)
        .bind(status)
        .bind(transaction_date.or(Some(now)))
        .bind(wallet_id)
        .bind(academic_session_year)
        .bind(term)
        .bind(academic_session_id)
        .bind(term_id)
        .bind(paystack_wallet_id)
        .bind(squad_wallet_id)
        .bind(provider)
        .bind(parent_id)
        .fetch_one(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))
    }

    pub async fn list_fee_items_by_school(pool: &PgPool, school_id: Uuid) -> Result<Vec<FeeItem>, ApiError> {
        sqlx::query_as::<sqlx::Postgres, FeeItem>(
            r#"
            SELECT
                id,
                created_at,
                is_active,
                updated_at,
                school_id,
                amount::float8 AS amount,
                description,
                is_mandatory,
                name,
                gender_eligibility,
                student_status_eligibility,
                staff_discount_amount::float8 AS staff_discount_amount,
                staff_discount_type
            FROM fee_items
            WHERE school_id = $1 AND is_active = true
            ORDER BY created_at DESC
            "#,
        )
        .bind(school_id)
        .fetch_all(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))
    }

    pub async fn get_fee_item_by_id(
        pool: &PgPool,
        school_id: Uuid,
        fee_item_id: Uuid,
    ) -> Result<FeeItem, ApiError> {
        sqlx::query_as::<sqlx::Postgres, FeeItem>(
            r#"
            SELECT
                id,
                created_at,
                is_active,
                updated_at,
                school_id,
                amount::float8 AS amount,
                description,
                is_mandatory,
                name,
                gender_eligibility,
                student_status_eligibility,
                staff_discount_amount::float8 AS staff_discount_amount,
                staff_discount_type
            FROM fee_items
            WHERE id = $1 AND school_id = $2 AND is_active = true
            "#,
        )
        .bind(fee_item_id)
        .bind(school_id)
        .fetch_optional(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))?
        .ok_or_else(|| ApiError::NotFound("Fee item not found for this school".to_string()))
    }

    pub async fn create_fee_item(
        pool: &PgPool,
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
        let now = Utc::now().naive_utc();

        sqlx::query_as::<sqlx::Postgres, FeeItem>(
            r#"
            INSERT INTO fee_items (
                id, created_at, is_active, updated_at, school_id, amount, description,
                is_mandatory, name, gender_eligibility, student_status_eligibility,
                staff_discount_amount, staff_discount_type
            ) VALUES (
                $1, $2, true, $3, $4, $5, $6,
                $7, $8, $9, $10,
                $11, $12
            )
            RETURNING
                id,
                created_at,
                is_active,
                updated_at,
                school_id,
                amount::float8 AS amount,
                description,
                is_mandatory,
                name,
                gender_eligibility,
                student_status_eligibility,
                staff_discount_amount::float8 AS staff_discount_amount,
                staff_discount_type
            "#,
        )
        .bind(Uuid::new_v4())
        .bind(now)
        .bind(now)
        .bind(school_id)
        .bind(amount)
        .bind(description)
        .bind(is_mandatory)
        .bind(name)
        .bind(gender_eligibility)
        .bind(student_status_eligibility)
        .bind(staff_discount_amount)
        .bind(staff_discount_type)
        .fetch_one(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))
    }

    pub async fn update_fee_item(
        pool: &PgPool,
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
        sqlx::query_as::<sqlx::Postgres, FeeItem>(
            r#"
            UPDATE fee_items
            SET amount = $1,
                description = $2,
                is_mandatory = $3,
                name = $4,
                gender_eligibility = $5,
                student_status_eligibility = $6,
                staff_discount_amount = $7,
                staff_discount_type = $8,
                updated_at = NOW()
            WHERE id = $9 AND school_id = $10 AND is_active = true
            RETURNING
                id,
                created_at,
                is_active,
                updated_at,
                school_id,
                amount::float8 AS amount,
                description,
                is_mandatory,
                name,
                gender_eligibility,
                student_status_eligibility,
                staff_discount_amount::float8 AS staff_discount_amount,
                staff_discount_type
            "#,
        )
        .bind(amount)
        .bind(description)
        .bind(is_mandatory)
        .bind(name)
        .bind(gender_eligibility)
        .bind(student_status_eligibility)
        .bind(staff_discount_amount)
        .bind(staff_discount_type)
        .bind(fee_item_id)
        .bind(school_id)
        .fetch_optional(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))?
        .ok_or_else(|| ApiError::NotFound("Fee item not found for this school".to_string()))
    }

    pub async fn soft_delete_fee_item_in_tx(
        tx: &mut Transaction<'_, Postgres>,
        school_id: Uuid,
        fee_item_id: Uuid,
    ) -> Result<(), ApiError> {
        let result = sqlx::query(
            "UPDATE fee_items SET is_active = false, updated_at = NOW() WHERE id = $1 AND school_id = $2 AND is_active = true",
        )
        .bind(fee_item_id)
        .bind(school_id)
        .execute(&mut **tx)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

        if result.rows_affected() == 0 {
            return Err(ApiError::NotFound("Fee item not found for this school".to_string()));
        }

        Ok(())
    }

    pub async fn soft_delete_class_fee_items_for_fee_item_in_tx(
        tx: &mut Transaction<'_, Postgres>,
        school_id: Uuid,
        fee_item_id: Uuid,
    ) -> Result<(), ApiError> {
        sqlx::query(
            "UPDATE class_fee_items SET is_active = false, updated_at = NOW() WHERE fee_item_id = $1 AND school_id = $2 AND is_active = true",
        )
        .bind(fee_item_id)
        .bind(school_id)
        .execute(&mut **tx)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

        Ok(())
    }

    pub async fn list_class_fee_item_assignments_by_fee_item(
        pool: &PgPool,
        school_id: Uuid,
        fee_item_id: Uuid,
    ) -> Result<Vec<ClassFeeItem>, ApiError> {
        sqlx::query_as::<sqlx::Postgres, ClassFeeItem>(
            r#"
            SELECT
                id,
                created_at,
                is_active,
                updated_at,
                school_id,
                academic_year,
                custom_amount::float8 AS custom_amount,
                is_applicable,
                notes,
                term,
                academic_session_id,
                fee_item_id,
                class_id,
                term_id,
                is_locked
            FROM class_fee_items
            WHERE school_id = $1 AND fee_item_id = $2 AND is_active = true
            ORDER BY academic_year DESC, created_at DESC
            "#,
        )
        .bind(school_id)
        .bind(fee_item_id)
        .fetch_all(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))
    }

    pub async fn get_class_fee_item_assignment(
        pool: &PgPool,
        school_id: Uuid,
        fee_item_id: Uuid,
        class_id: Uuid,
    ) -> Result<Option<ClassFeeItem>, ApiError> {
        sqlx::query_as::<sqlx::Postgres, ClassFeeItem>(
            r#"
            SELECT
                id,
                created_at,
                is_active,
                updated_at,
                school_id,
                academic_year,
                custom_amount::float8 AS custom_amount,
                is_applicable,
                notes,
                term,
                academic_session_id,
                fee_item_id,
                class_id,
                term_id,
                is_locked
            FROM class_fee_items
            WHERE school_id = $1 AND fee_item_id = $2 AND class_id = $3 AND is_active = true
            LIMIT 1
            "#,
        )
        .bind(school_id)
        .bind(fee_item_id)
        .bind(class_id)
        .fetch_optional(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))
    }

    pub async fn get_class_fee_item_assignment_by_id(
        pool: &PgPool,
        school_id: Uuid,
        class_fee_item_id: Uuid,
    ) -> Result<ClassFeeItem, ApiError> {
        sqlx::query_as::<sqlx::Postgres, ClassFeeItem>(
            r#"
            SELECT
                id,
                created_at,
                is_active,
                updated_at,
                school_id,
                academic_year,
                custom_amount::float8 AS custom_amount,
                is_applicable,
                notes,
                term,
                academic_session_id,
                fee_item_id,
                class_id,
                term_id,
                is_locked
            FROM class_fee_items
            WHERE id = $1 AND school_id = $2 AND is_active = true
            "#,
        )
        .bind(class_fee_item_id)
        .bind(school_id)
        .fetch_optional(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))?
        .ok_or_else(|| ApiError::NotFound("Class fee item assignment not found for this school".to_string()))
    }

    pub async fn get_student_by_id_and_school(
        pool: &PgPool,
        school_id: Uuid,
        student_id: Uuid,
    ) -> Result<Student, ApiError> {
        sqlx::query_as::<sqlx::Postgres, Student>(
            "SELECT * FROM students WHERE id = $1 AND school_id = $2 AND is_active = true",
        )
        .bind(student_id)
        .bind(school_id)
        .fetch_optional(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))?
        .ok_or_else(|| ApiError::NotFound("Student not found for this school".to_string()))
    }

    pub async fn upsert_student_optional_fee(
        pool: &PgPool,
        school_id: Uuid,
        student_id: Uuid,
        class_fee_item_id: Uuid,
        actor: Uuid,
        academic_session_id: Option<Uuid>,
        term_id: Option<Uuid>,
        custom_amount: Option<f64>,
        notes: Option<String>,
        is_locked: Option<bool>,
    ) -> Result<StudentOptionalFee, ApiError> {
        let now = Utc::now().naive_utc();

        sqlx::query_as::<sqlx::Postgres, StudentOptionalFee>(
            r#"
            INSERT INTO student_optional_fees (
                id, created_at, is_active, updated_at, school_id,
                opted_in_at, opted_in_by, class_fee_item_id, student_id,
                is_locked, academic_session_id, term_id, custom_amount, notes
            ) VALUES (
                $1, $2, true, $3, $4,
                $5, $6, $7, $8,
                $9, $10, $11, $12, $13
            )
            ON CONFLICT (student_id, class_fee_item_id)
            DO UPDATE SET
                updated_at = NOW(),
                is_active = true,
                opted_in_at = EXCLUDED.opted_in_at,
                opted_in_by = EXCLUDED.opted_in_by,
                is_locked = EXCLUDED.is_locked,
                academic_session_id = EXCLUDED.academic_session_id,
                term_id = EXCLUDED.term_id,
                custom_amount = EXCLUDED.custom_amount,
                notes = EXCLUDED.notes
            RETURNING
                id,
                created_at,
                is_active,
                updated_at,
                school_id,
                opted_in_at,
                opted_in_by,
                class_fee_item_id,
                student_id,
                is_locked,
                academic_session_id,
                term_id,
                custom_amount::float8 AS custom_amount,
                notes
            "#,
        )
        .bind(Uuid::new_v4())
        .bind(now)
        .bind(now)
        .bind(school_id)
        .bind(Some(now))
        .bind(Some(actor.to_string()))
        .bind(class_fee_item_id)
        .bind(student_id)
        .bind(is_locked)
        .bind(academic_session_id)
        .bind(term_id)
        .bind(custom_amount)
        .bind(notes)
        .fetch_one(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))
    }

    pub async fn set_student_optional_fee_lock_state(
        pool: &PgPool,
        school_id: Uuid,
        student_optional_fee_id: Uuid,
        lock_state: bool,
    ) -> Result<StudentOptionalFee, ApiError> {
        sqlx::query_as::<sqlx::Postgres, StudentOptionalFee>(
            r#"
            UPDATE student_optional_fees
            SET is_locked = $1,
                updated_at = NOW()
            WHERE id = $2 AND school_id = $3 AND is_active = true
            RETURNING
                id,
                created_at,
                is_active,
                updated_at,
                school_id,
                opted_in_at,
                opted_in_by,
                class_fee_item_id,
                student_id,
                is_locked,
                academic_session_id,
                term_id,
                custom_amount::float8 AS custom_amount,
                notes
            "#,
        )
        .bind(Some(lock_state))
        .bind(student_optional_fee_id)
        .bind(school_id)
        .fetch_optional(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))?
        .ok_or_else(|| ApiError::NotFound("Student optional fee not found for this school".to_string()))
    }

    pub async fn soft_delete_student_optional_fee(
        pool: &PgPool,
        school_id: Uuid,
        student_optional_fee_id: Uuid,
    ) -> Result<(), ApiError> {
        let result = sqlx::query(
            "UPDATE student_optional_fees SET is_active = false, updated_at = NOW() WHERE id = $1 AND school_id = $2 AND is_active = true",
        )
        .bind(student_optional_fee_id)
        .bind(school_id)
        .execute(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

        if result.rows_affected() == 0 {
            return Err(ApiError::NotFound(
                "Student optional fee not found for this school".to_string(),
            ));
        }

        Ok(())
    }

    pub async fn upsert_class_fee_item_assignment(
        pool: &PgPool,
        school_id: Uuid,
        fee_item_id: Uuid,
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
        if let Some(existing) = Self::get_class_fee_item_assignment(pool, school_id, fee_item_id, class_id).await? {
            sqlx::query_as::<sqlx::Postgres, ClassFeeItem>(
                r#"
                UPDATE class_fee_items
                SET academic_year = $1,
                    custom_amount = $2,
                    is_applicable = $3,
                    notes = $4,
                    term = $5,
                    academic_session_id = $6,
                    term_id = $7,
                    is_locked = $8,
                    updated_at = NOW(),
                    is_active = true
                WHERE id = $9 AND school_id = $10 AND fee_item_id = $11 AND class_id = $12
                RETURNING
                    id,
                    created_at,
                    is_active,
                    updated_at,
                    school_id,
                    academic_year,
                    custom_amount::float8 AS custom_amount,
                    is_applicable,
                    notes,
                    term,
                    academic_session_id,
                    fee_item_id,
                    class_id,
                    term_id,
                    is_locked
                "#,
            )
            .bind(academic_year)
            .bind(custom_amount)
            .bind(is_applicable)
            .bind(notes)
            .bind(term)
            .bind(academic_session_id)
            .bind(term_id)
            .bind(is_locked)
            .bind(existing.id)
            .bind(school_id)
            .bind(fee_item_id)
            .bind(class_id)
            .fetch_one(pool)
            .await
            .map_err(|e| ApiError::DatabaseError(e.to_string()))
        } else {
            let now = Utc::now().naive_utc();

            sqlx::query_as::<sqlx::Postgres, ClassFeeItem>(
                r#"
                INSERT INTO class_fee_items (
                    id, created_at, is_active, updated_at, school_id, academic_year,
                    custom_amount, is_applicable, notes, term, academic_session_id,
                    fee_item_id, class_id, term_id, is_locked
                ) VALUES (
                    $1, $2, true, $3, $4, $5,
                    $6, $7, $8, $9, $10,
                    $11, $12, $13, $14
                )
                RETURNING
                    id,
                    created_at,
                    is_active,
                    updated_at,
                    school_id,
                    academic_year,
                    custom_amount::float8 AS custom_amount,
                    is_applicable,
                    notes,
                    term,
                    academic_session_id,
                    fee_item_id,
                    class_id,
                    term_id,
                    is_locked
                "#,
            )
            .bind(Uuid::new_v4())
            .bind(now)
            .bind(now)
            .bind(school_id)
            .bind(academic_year)
            .bind(custom_amount)
            .bind(is_applicable)
            .bind(notes)
            .bind(term)
            .bind(academic_session_id)
            .bind(fee_item_id)
            .bind(class_id)
            .bind(term_id)
            .bind(is_locked)
            .fetch_one(pool)
            .await
            .map_err(|e| ApiError::DatabaseError(e.to_string()))
        }
    }

    pub async fn soft_delete_class_fee_item_assignment(
        pool: &PgPool,
        school_id: Uuid,
        fee_item_id: Uuid,
        class_id: Uuid,
    ) -> Result<(), ApiError> {
        let result = sqlx::query(
            "UPDATE class_fee_items SET is_active = false, updated_at = NOW() WHERE school_id = $1 AND fee_item_id = $2 AND class_id = $3 AND is_active = true",
        )
        .bind(school_id)
        .bind(fee_item_id)
        .bind(class_id)
        .execute(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))?;

        if result.rows_affected() == 0 {
            return Err(ApiError::NotFound(
                "Class fee item assignment not found for this fee item and class".to_string(),
            ));
        }

        Ok(())
    }
}

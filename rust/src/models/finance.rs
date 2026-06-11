use chrono::{NaiveDate, NaiveDateTime, Utc};
use serde::{Deserialize, Serialize};
use uuid::Uuid;

/// Invoices table - Financial invoices
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct Invoice {
    pub id: Uuid,
    pub school_id: Uuid,
    pub student_id: Option<Uuid>,
    pub parent_id: Option<Uuid>,
    pub invoice_number: String,
    pub status: String,
    pub total_amount: i64,
    pub paid_amount: i64,
    pub due_date: NaiveDate,
    pub issued_date: NaiveDate,
    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
    pub is_active: bool,
}

/// InvoiceItems table - Individual items in invoices
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct InvoiceItem {
    pub id: Uuid,
    pub school_id: Uuid,
    pub invoice_id: Uuid,
    pub description: String,
    pub quantity: i32,
    pub unit_price: i64,
    pub total_price: i64,
    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
    pub is_active: bool,
}

/// FeeStructures table - Fee structure templates
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct FeeStructure {
    pub id: Uuid,
    pub school_id: Uuid,
    pub name: String,
    pub class_id: Uuid,
    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
    pub is_active: bool,
}

/// FeeItems table - Individual fee items
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct FeeItem {
    pub id: Uuid,
    pub created_at: NaiveDateTime,
    pub is_active: bool,
    pub updated_at: NaiveDateTime,
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

/// ClassFeeItems table - Fee items per class
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct ClassFeeItem {
    pub id: Uuid,
    pub created_at: NaiveDateTime,
    pub is_active: bool,
    pub updated_at: NaiveDateTime,
    pub school_id: Uuid,
    pub academic_year: String,
    pub custom_amount: Option<f64>,
    pub is_applicable: Option<bool>,
    pub notes: Option<String>,
    pub term: Option<String>,
    pub academic_session_id: Option<Uuid>,
    pub fee_item_id: Uuid,
    pub class_id: Uuid,
    pub term_id: Option<Uuid>,
    pub is_locked: Option<bool>,
}

/// StudentOptionalFees table - Optional fees for students
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct StudentOptionalFee {
    pub id: Uuid,
    pub created_at: NaiveDateTime,
    pub is_active: bool,
    pub updated_at: NaiveDateTime,
    pub school_id: Uuid,
    pub opted_in_at: Option<NaiveDateTime>,
    pub opted_in_by: Option<String>,
    pub class_fee_item_id: Uuid,
    pub student_id: Uuid,
    pub is_locked: Option<bool>,
    pub academic_session_id: Option<Uuid>,
    pub term_id: Option<Uuid>,
    pub custom_amount: Option<f64>,
    pub notes: Option<String>,
}

/// Settlements table - Payment settlements
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct Settlement {
    pub id: Uuid,
    pub created_at: NaiveDateTime,
    pub is_active: bool,
    pub updated_at: NaiveDateTime,
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
    pub reimbursed: bool,
    pub settlement_type: Option<String>,
    pub paystack_wallet_id: Option<Uuid>,
    pub squad_wallet_id: Option<Uuid>,
    pub provider: Option<String>,
    pub parent_id: Option<Uuid>,
}

/// Bills table - Student billing records
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct Bill {
    pub id: Uuid,
    pub created_at: NaiveDateTime,
    pub is_active: bool,
    pub updated_at: NaiveDateTime,
    pub school_id: Uuid,
    pub student_id: Uuid,
    pub academic_session_id: Option<Uuid>,
    pub term_id: Option<Uuid>,
    pub amount: f64,
    pub breakdown: Option<String>,
}

/// PaymentAllocations table - Payment allocation records
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct PaymentAllocation {
    pub id: Uuid,
    pub created_at: NaiveDateTime,
    pub is_active: bool,
    pub updated_at: NaiveDateTime,
    pub school_id: Uuid,
    pub allocated_amount: f64,
    pub allocation_date: Option<NaiveDateTime>,
    pub allocation_method: String,
    pub allocation_order: i32,
    pub notes: Option<String>,
    pub remaining_balance_after: f64,
    pub remaining_balance_before: f64,
    pub settlement_id: Uuid,
    pub student_id: Uuid,
}

/// PaymentNotifications table - Payment notification logs
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct PaymentNotification {
    pub id: Uuid,
    pub school_id: Uuid,
    pub invoice_id: Uuid,
    pub notification_type: String,
    pub sent_at: NaiveDateTime,
    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
    pub is_active: bool,
}

/// SchoolReimbursements table - Reimbursement tracking
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct SchoolReimbursement {
    pub id: Uuid,
    pub created_at: NaiveDateTime,
    pub is_active: bool,
    pub updated_at: NaiveDateTime,
    pub amount: f64,
    pub currency: String,
    pub notes: Option<String>,
    pub reference: String,
    pub reimbursement_date: Option<NaiveDateTime>,
    pub status: String,
    pub academic_session_id: Option<Uuid>,
    pub recorded_by_id: Option<Uuid>,
    pub school_id: Uuid,
    pub term_id: Option<Uuid>,
}

/// SchoolBankAccounts table - School bank account information
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct SchoolBankAccount {
    pub id: Uuid,
    pub created_at: NaiveDateTime,
    pub is_active: bool,
    pub updated_at: NaiveDateTime,
    pub account_name: String,
    pub account_number: String,
    pub bank_code: Option<String>,
    pub bank_name: String,
    pub recipient_code: Option<String>,
    pub school_id: Uuid,
}

/// SchoolWallets table - School wallet records
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct SchoolWallet {
    pub id: Uuid,
    pub created_at: NaiveDateTime,
    pub is_active: bool,
    pub updated_at: NaiveDateTime,
    pub account_name: String,
    pub account_number: String,
    pub assigned_at: Option<NaiveDateTime>,
    pub balance: f64,
    pub bank_id: Option<i32>,
    pub bank_name: String,
    pub bank_slug: Option<String>,
    pub currency: String,
    pub customer_code: String,
    pub paystack_account_id: Option<i64>,
    pub school_id: Uuid,
}

/// SchoolSubscriptions table - School subscription plans
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct SchoolSubscription {
    pub id: Uuid,
    pub school_id: Uuid,
    pub fee_collection_active: bool,
    pub whatsapp_balance: i32,
    pub sms_balance: i32,
    pub ai_token_balance: i32,
    pub last_updated: Option<NaiveDateTime>,
    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
    pub is_active: bool,
    pub account_number: Option<String>,
    pub bank_name: Option<String>,
    pub terms_accepted: bool,
    pub subscription_status: String,
    pub valid_until: Option<NaiveDateTime>,
}

/// PaystackParentWallets table - Paystack parent wallet records
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct PaystackParentWallet {
    pub id: Uuid,
    pub created_at: NaiveDateTime,
    pub is_active: bool,
    pub updated_at: NaiveDateTime,
    pub school_id: Uuid,
    pub account_name: Option<String>,
    pub account_number: Option<String>,
    pub assigned_at: Option<NaiveDateTime>,
    pub balance: f64,
    pub bank_id: Option<i32>,
    pub bank_name: Option<String>,
    pub bank_slug: Option<String>,
    pub currency: String,
    pub customer_code: String,
    pub paystack_account_id: Option<i64>,
    pub parent_id: Uuid,
}

/// SquadParentWallets table - Squad parent wallet records
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct SquadParentWallet {
    pub id: Uuid,
    pub created_at: NaiveDateTime,
    pub is_active: bool,
    pub updated_at: NaiveDateTime,
    pub school_id: Uuid,
    pub account_name: Option<String>,
    pub account_number: Option<String>,
    pub assigned_at: Option<NaiveDateTime>,
    pub balance: f64,
    pub bank_name: Option<String>,
    pub currency: String,
    pub customer_identifier: Option<String>,
    pub parent_id: Uuid,
}

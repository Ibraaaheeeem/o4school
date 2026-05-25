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
    pub school_id: Uuid,
    pub name: String,
    pub code: String,
    pub amount: i64,
    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
    pub is_active: bool,
}

/// ClassFeeItems table - Fee items per class
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct ClassFeeItem {
    pub id: Uuid,
    pub school_id: Uuid,
    pub class_id: Uuid,
    pub fee_item_id: Uuid,
    pub amount: i64,
    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
    pub is_active: bool,
}

/// StudentOptionalFees table - Optional fees for students
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct StudentOptionalFee {
    pub id: Uuid,
    pub school_id: Uuid,
    pub student_id: Uuid,
    pub fee_item_id: Uuid,
    pub amount: i64,
    pub status: String,
    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
    pub is_active: bool,
}

/// Settlements table - Payment settlements
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct Settlement {
    pub id: Uuid,
    pub school_id: Uuid,
    pub amount: i64,
    pub settlement_date: NaiveDate,
    pub reference_number: String,
    pub status: String,
    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
    pub is_active: bool,
}

/// PaymentAllocations table - Payment allocation records
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct PaymentAllocation {
    pub id: Uuid,
    pub school_id: Uuid,
    pub invoice_id: Uuid,
    pub payment_amount: i64,
    pub allocation_date: NaiveDate,
    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
    pub is_active: bool,
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
    pub school_id: Uuid,
    pub staff_id: Option<Uuid>,
    pub amount: i64,
    pub description: String,
    pub status: String,
    pub submitted_date: NaiveDate,
    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
    pub is_active: bool,
}

/// SchoolBankAccounts table - School bank account information
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct SchoolBankAccount {
    pub id: Uuid,
    pub school_id: Uuid,
    pub bank_name: String,
    pub account_name: String,
    pub account_number: String,
    pub account_type: String,
    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
    pub is_active: bool,
}

/// SchoolWallets table - School wallet records
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct SchoolWallet {
    pub id: Uuid,
    pub school_id: Uuid,
    pub balance: i64,
    pub total_received: i64,
    pub total_spent: i64,
    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
    pub is_active: bool,
}

/// SchoolSubscriptions table - School subscription plans
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct SchoolSubscription {
    pub id: Uuid,
    pub school_id: Uuid,
    pub plan_name: String,
    pub start_date: NaiveDate,
    pub end_date: Option<NaiveDate>,
    pub status: String,
    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
    pub is_active: bool,
}

/// PaystackParentWallets table - Paystack parent wallet records
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct PaystackParentWallet {
    pub id: Uuid,
    pub school_id: Uuid,
    pub parent_id: Uuid,
    pub balance: i64,
    pub reference_code: String,
    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
    pub is_active: bool,
}

/// SquadParentWallets table - Squad parent wallet records
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct SquadParentWallet {
    pub id: Uuid,
    pub school_id: Uuid,
    pub parent_id: Uuid,
    pub balance: i64,
    pub reference_code: String,
    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
    pub is_active: bool,
}

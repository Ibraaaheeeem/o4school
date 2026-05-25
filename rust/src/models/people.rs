use chrono::{NaiveDate, NaiveDateTime, Utc};
use serde::{Deserialize, Serialize};
use uuid::Uuid;

/// Students table - Student enrollment records
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct Student {
    pub id: Uuid,
    pub school_id: Uuid,
    pub user_id: Uuid,
    pub student_id: String,
    pub admission_number: Option<String>,
    pub admission_date: NaiveDate,
    pub graduation_date: Option<NaiveDate>,
    pub academic_status: String,
    pub current_grade_level: Option<String>,
    pub date_of_birth: Option<NaiveDate>,
    pub gender: Option<String>,
    pub previous_school: Option<String>,
    pub special_needs_description: Option<String>,
    pub transportation_method: Option<String>,
    pub passport_photo_url: Option<String>,
    pub is_new: bool,
    pub has_special_needs: bool,
    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
    pub is_active: bool,
}

/// Staff table - Staff/Teacher records
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct Staff {
    pub id: Uuid,
    pub school_id: Uuid,
    pub user_id: Uuid,
    pub staff_id: String,
    pub employee_number: Option<String>,
    pub designation: String,
    pub hire_date: NaiveDate,
    pub termination_date: Option<NaiveDate>,
    pub employment_status: String,
    pub employment_type: String,
    pub highest_degree: Option<String>,
    pub department: Option<String>,
    pub is_class_teacher: bool,
    pub is_subject_teacher: bool,
    pub bank_name: Option<String>,
    pub account_name: Option<String>,
    pub account_number: Option<String>,
    pub monthly_deduction: f64,
    pub class_teacher_for: Option<Uuid>,
    pub years_of_experience: i32,
    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
    pub is_active: bool,
}

/// Parents table - Parent/Guardian records
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct Parent {
    pub id: Uuid,
    pub created_at: NaiveDateTime,
    pub is_active: bool,
    pub updated_at: NaiveDateTime,
    pub school_id: Uuid,
    pub is_emergency_contact: Option<bool>,
    pub is_financially_responsible: Option<bool>,
    pub is_primary_contact: Option<bool>,
    pub receive_academic_updates: Option<bool>,
    pub receive_disciplinary_updates: Option<bool>,
    pub receive_financial_updates: Option<bool>,
    pub user_id: Uuid,
    pub payment_distribution_type: Option<String>,
    pub payment_priority_order: Option<String>,
}

/// ParentStudents table - Parent-Student relationships
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct ParentStudent {
    pub id: Uuid,
    pub school_id: Uuid,
    pub parent_id: Uuid,
    pub student_id: Uuid,
    pub relationship: String,
    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
    pub is_active: bool,
}

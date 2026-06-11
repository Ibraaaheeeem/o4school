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
    pub employment_status: Option<String>,
    pub employment_type: Option<String>,
    pub highest_degree: Option<String>,
    pub department: Option<String>,
    pub is_class_teacher: Option<bool>,
    pub is_subject_teacher: Option<bool>,
    pub bank_name: Option<String>,
    pub account_name: Option<String>,
    pub account_number: Option<String>,
    pub monthly_deduction: Option<f64>,
    pub class_teacher_for: Option<Uuid>,
    pub years_of_experience: Option<i32>,
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

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct StaffClassAssignmentResponse {
    pub id: Uuid,
    pub class_id: Uuid,
    pub class_name: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct StudentClassAssignmentResponse {
    pub id: Uuid,
    pub school_id: Uuid,
    pub student_id: Uuid,
    pub class_id: Uuid,
    pub class_name: String,
    pub session_id: Uuid,
    pub term_id: Uuid,
    pub enrollment_date: Option<chrono::NaiveDate>,
    pub is_active: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct StaffSubjectAssignmentResponse {
    pub id: Uuid,
    pub class_id: Uuid,
    pub class_name: String,
    pub subject_id: Uuid,
    pub subject_name: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct StaffListResponse {
    pub id: Uuid,
    pub staff_id: String,
    pub full_name: String,
    pub email: String,
    pub phone_number: String,
    pub department: String,
    pub position: String,
    pub hire_date: String,
    pub salary: Option<f64>,
    pub profile_image_url: Option<String>,
    pub is_active: bool,
    pub is_class_teacher: bool,
    pub is_subject_teacher: bool,
    pub class_teacher_class_name: Option<String>,
    pub subject_teacher_subjects: Option<String>,
    #[sqlx(skip)]
    pub class_assignments: Vec<StaffClassAssignmentResponse>,
    #[sqlx(skip)]
    pub subject_assignments: Vec<StaffSubjectAssignmentResponse>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PaginatedResponse<T> {
    pub success: bool,
    pub message: String,
    pub data: Vec<T>,
    pub pagination: Pagination,
    pub errors: Option<Vec<String>>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Pagination {
    pub current_page: i64,
    pub per_page: i64,
    pub total: i64,
    pub total_pages: i64,
    pub has_next: bool,
    pub has_previous: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct ParentLinkedStudent {
    pub id: Uuid,
    pub student_id: String,
    pub full_name: String,
    pub class_name: Option<String>,
    pub profile_image_url: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ParentListResponse {
    pub id: Uuid,
    pub parent_id: String,
    pub full_name: String,
    pub email: String,
    pub phone_number: String,
    pub is_verified: bool,
    pub profile_image_url: Option<String>,
    pub linked_students: Vec<ParentLinkedStudent>,
}

#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct StudentListResponse {
    pub id: Uuid,
    pub student_id: String,
    pub full_name: String,
    pub email: Option<String>,
    pub phone_number: Option<String>,
    pub date_of_birth: Option<String>,
    pub gender: Option<String>,
    pub class_id: Option<Uuid>,
    pub class_name: Option<String>,
    pub admission_date: String,
    pub profile_image_url: Option<String>,
    pub is_active: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct StudentDetailResponse {
    pub id: Uuid,
    pub school_id: Uuid,
    pub user_id: Uuid,
    pub student_id: String,
    pub admission_number: Option<String>,
    pub admission_date: String,
    pub graduation_date: Option<String>,
    pub academic_status: String,
    pub current_grade_level: Option<String>,
    pub date_of_birth: Option<String>,
    pub gender: Option<String>,
    pub previous_school: Option<String>,
    pub special_needs_description: Option<String>,
    pub transportation_method: Option<String>,
    pub passport_photo_url: Option<String>,
    pub is_new: bool,
    pub has_special_needs: bool,
    pub created_at: chrono::NaiveDateTime,
    pub updated_at: chrono::NaiveDateTime,
    pub is_active: bool,

    // Joined User fields
    pub first_name: Option<String>,
    pub last_name: Option<String>,
    pub email: Option<String>,
    pub phone_number: Option<String>,

    pub guardian_name: Option<String>,
    pub guardian_phone: Option<String>,
    pub guardian_email: Option<String>,
    pub guardian_relationship: Option<String>,
}


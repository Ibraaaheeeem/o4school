use uuid::Uuid;
use sqlx::PgPool;
use sqlx::{Transaction, Postgres, Executor};
use crate::errors::ApiError;
use crate::models::Staff;

pub struct StaffRepository;

impl StaffRepository {
    /// Get staff by ID
    pub async fn get_by_id(pool: &PgPool, id: Uuid) -> Result<Staff, ApiError> {
        sqlx::query_as::<_, Staff>(
            "SELECT * FROM staff WHERE id = $1"
        )
        .bind(id)
        .fetch_one(pool)
        .await
        .map_err(|e| {
            if e.to_string().contains("no rows") {
                ApiError::NotFound(format!("Staff with id {} not found", id))
            } else {
                ApiError::DatabaseError(e.to_string())
            }
        })
    }

    /// Create a new staff record
    pub async fn create(pool: &PgPool, staff: &Staff) -> Result<Staff, ApiError> {
        sqlx::query_as::<_, Staff>(
            r#"
            INSERT INTO staff (
                id, school_id, user_id, staff_id, employee_number, designation, hire_date, termination_date,
                employment_status, employment_type, highest_degree, department, is_class_teacher, is_subject_teacher,
                bank_name, account_name, account_number, monthly_deduction, class_teacher_for, years_of_experience,
                created_at, updated_at, is_active
            ) VALUES (
                $1, $2, $3, $4, $5, $6, $7, $8,
                $9, $10, $11, $12, $13, $14,
                $15, $16, $17, $18, $19, $20,
                $21, $22, $23
            )
            RETURNING *
            "#
        )
        .bind(staff.id)
        .bind(staff.school_id)
        .bind(staff.user_id)
        .bind(&staff.staff_id)
        .bind(&staff.employee_number)
        .bind(&staff.designation)
        .bind(staff.hire_date)
        .bind(staff.termination_date)
        .bind(&staff.employment_status)
        .bind(&staff.employment_type)
        .bind(&staff.highest_degree)
        .bind(&staff.department)
        .bind(staff.is_class_teacher)
        .bind(staff.is_subject_teacher)
        .bind(&staff.bank_name)
        .bind(&staff.account_name)
        .bind(&staff.account_number)
        .bind(staff.monthly_deduction)
        .bind(staff.class_teacher_for)
        .bind(staff.years_of_experience)
        .bind(staff.created_at)
        .bind(staff.updated_at)
        .bind(staff.is_active)
        .fetch_one(pool)
        .await
        .map_err(|e| ApiError::DatabaseError(e.to_string()))
    }

    // transactional helpers removed; use `create` method on pool instead
}

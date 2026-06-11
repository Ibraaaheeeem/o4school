mod common;

use common::*;
use school_backend::db::Database;
use school_backend::models::auth::{CreateStaffInfo, CreateStaffWithUserRequest};
use school_backend::services::StaffService;
use uuid::Uuid;

#[tokio::test]
async fn school_admin_creates_staff_with_existing_user_email_reuses_user_and_skips_duplicate_role() {
    let database_url = std::env::var("DATABASE_URL")
        .unwrap_or_else(|_| "postgres://postgres:password@localhost:5432/myschool".to_string());
    let db = Database::new(&database_url).await.expect("Failed to create Database");

    let school_id = Uuid::parse_str("02ba1e88-cb88-4e10-9d3f-81eb62912e1d").expect("Invalid school_id");

    let test_email = generate_test_email("staff-create-with-user");

    let req = CreateStaffWithUserRequest {
        school_id,
        email: test_email.clone(),
        phone_number: Some(generate_test_phone()),
        first_name: "Staff".to_string(),
        middle_name: None,
        last_name: "Creator".to_string(),
        gender: Some("M".to_string()),
        date_of_birth: None,
        staff: CreateStaffInfo {
            staff_id: None,
            employee_number: Some("EMP-001".to_string()),
            designation: Some("Teacher".to_string()),
            hire_date: None,
            employment_status: Some("ACTIVE".to_string()),
            employment_type: Some("FULL_TIME".to_string()),
            highest_degree: None,
            department: Some("Academics".to_string()),
            is_class_teacher: Some(false),
            is_subject_teacher: Some(true),
            bank_name: None,
            account_name: None,
            account_number: None,
            monthly_deduction: Some(0.0),
            class_teacher_for: None,
            years_of_experience: Some(2),
        },
        staff_class_assignments: None,
        staff_subject_assignments: None,
    };

    let created = StaffService::create_staff_with_user(&db, req.clone())
        .await
        .expect("Failed to create staff with user");

    assert_eq!(created.school_id, school_id, "staff school should match request");

    let created_again = StaffService::create_staff_with_user(&db, req)
        .await
        .expect("Second create should return existing staff role record");

    assert_eq!(created_again.id, created.id, "duplicate role should not create a new staff row");

    let role_count: i64 = sqlx::query_scalar(
        "SELECT COUNT(*) FROM staff WHERE user_id = $1 AND school_id = $2 AND is_active = true"
    )
    .bind(created.user_id)
    .bind(school_id)
    .fetch_one(db.pool())
    .await
    .expect("Failed to query staff role count");

    assert_eq!(role_count, 1, "only one active staff role row should exist for user+school");

    let _ = sqlx::query("DELETE FROM staff WHERE user_id = $1 AND school_id = $2")
        .bind(created.user_id)
        .bind(school_id)
        .execute(db.pool())
        .await;

    let _ = db::delete_test_user(&db.pool(), &test_email).await;
}

mod common;

use common::*;
use uuid::Uuid;
use school_backend::models::auth as auth;

static INIT: std::sync::Once = std::sync::Once::new();

fn init_logger() {
    INIT.call_once(|| {
        let _ = env_logger::builder().is_test(true).try_init();
    });
}

#[tokio::test]
async fn school_admin_can_create_user_with_role_staff() {
    init_logger();

    let client = common::get_http_client();

    let admin_email = common::generate_test_email("school-admin-create-role");
    let admin_phone = common::generate_test_phone();
    let signup_req = common::build_signup_request(
        &admin_email,
        "SecurePassword123!",
        "Admin",
        "Creator",
        &admin_phone,
        "SCHOOL_ADMIN",
        None,
    );

    let resp = common::http::signup_expect_success(&client, signup_req).await;
    let performed_by = uuid::Uuid::parse_str(&resp.user_id).unwrap();
    let school_id = uuid::Uuid::parse_str(&resp.school_id).unwrap();

    let database_url = std::env::var("DATABASE_URL").unwrap_or_else(|_| "postgres://postgres:password@localhost:5432/myschool".to_string());
    let db = school_backend::db::Database::new(&database_url).await.expect("Failed to create Database");

    // Omit phone to avoid collisions in shared test DB

    let req = auth::CreateRoleUserRequest {
        email: format!("staff-created-by-admin-{}@example.com", Uuid::new_v4().simple()),
        first_name: "StaffFirst".to_string(),
        last_name: "StaffLast".to_string(),
        phone_number: None,
        role: "STAFF".to_string(),
        school_id,
        student: None,
        student_classes: None,
        parent: None,
        parent_student_relationships: None,
        staff_class_assignments: None,
        staff_subject_assignments: None,
        staff: Some(auth::CreateStaffInfo {
            staff_id: None,
            employee_number: None,
            designation: Some("Teacher".to_string()),
            hire_date: None,
            employment_status: None,
            employment_type: None,
            highest_degree: None,
            department: None,
            is_class_teacher: None,
            is_subject_teacher: None,
            bank_name: None,
            account_name: None,
            account_number: None,
            monthly_deduction: None,
            class_teacher_for: None,
            years_of_experience: None,
        }),
    };

    let res = school_backend::services::auth_service::AuthService::create_user_with_role(&db, req, Some(performed_by)).await;
    assert!(res.is_ok(), "expected create_user_with_role to succeed, got: {:?}", res);
}

#[tokio::test]
async fn school_admin_can_create_user_with_role_parent() {
    init_logger();

    let client = common::get_http_client();

    let admin_email = common::generate_test_email("school-admin-create-role-parent");
    let admin_phone = common::generate_test_phone();
    let signup_req = common::build_signup_request(
        &admin_email,
        "SecurePassword123!",
        "Admin",
        "Creator",
        &admin_phone,
        "SCHOOL_ADMIN",
        None,
    );

    let resp = common::http::signup_expect_success(&client, signup_req).await;
    let performed_by = uuid::Uuid::parse_str(&resp.user_id).unwrap();
    let school_id = uuid::Uuid::parse_str(&resp.school_id).unwrap();

    let database_url = std::env::var("DATABASE_URL").unwrap_or_else(|_| "postgres://postgres:password@localhost:5432/myschool".to_string());
    let db = school_backend::db::Database::new(&database_url).await.expect("Failed to create Database");

    // Omit phone to avoid collisions in shared test DB

    let req = auth::CreateRoleUserRequest {
        email: format!("parent-created-by-admin-{}@example.com", Uuid::new_v4().simple()),
        first_name: "ParentFirst".to_string(),
        last_name: "ParentLast".to_string(),
        phone_number: None,
        role: "PARENT".to_string(),
        school_id,
        student: None,
        student_classes: None,
        parent_student_relationships: None,
        staff: None,
        staff_class_assignments: None,
        staff_subject_assignments: None,
        parent: Some(auth::CreateParentInfo {
            occupation: None,
            employer_name: None,
            business_address: None,
        }),
    };

    let res = school_backend::services::auth_service::AuthService::create_user_with_role(&db, req, Some(performed_by)).await;
    assert!(res.is_ok(), "expected create_user_with_role to succeed for parent, got: {:?}", res);
}

#[tokio::test]
async fn school_admin_can_create_user_with_role_student() {
    init_logger();

    let client = common::get_http_client();

    let admin_email = common::generate_test_email("school-admin-create-role-student");
    let admin_phone = common::generate_test_phone();
    let signup_req = common::build_signup_request(
        &admin_email,
        "SecurePassword123!",
        "Admin",
        "Creator",
        &admin_phone,
        "SCHOOL_ADMIN",
        None,
    );

    let resp = common::http::signup_expect_success(&client, signup_req).await;
    let performed_by = uuid::Uuid::parse_str(&resp.user_id).unwrap();
    let school_id = uuid::Uuid::parse_str(&resp.school_id).unwrap();

    let database_url = std::env::var("DATABASE_URL").unwrap_or_else(|_| "postgres://postgres:password@localhost:5432/myschool".to_string());
    let db = school_backend::db::Database::new(&database_url).await.expect("Failed to create Database");

    // Omit phone to avoid collisions in shared test DB

    let req = auth::CreateRoleUserRequest {
        email: format!("student-created-by-admin-{}@example.com", Uuid::new_v4().simple()),
        first_name: "StudentFirst".to_string(),
        last_name: "StudentLast".to_string(),
        phone_number: None,
        role: "STUDENT".to_string(),
        school_id,
        staff: None,
        staff_class_assignments: None,
        staff_subject_assignments: None,
        parent: None,
        parent_student_relationships: None,
        student_classes: None,
        student: Some(auth::CreateStudentInfo {
            student_id: None,
            admission_number: None,
            admission_date: None,
            graduation_date: None,
            academic_status: None,
            current_grade_level: Some("Grade 1".to_string()),
            date_of_birth: None,
            gender: Some("F".to_string()),
            previous_school: None,
            special_needs_description: None,
            transportation_method: None,
            passport_photo_url: None,
        }),
    };

    let res = school_backend::services::auth_service::AuthService::create_user_with_role(&db, req, Some(performed_by)).await;
    assert!(res.is_ok(), "expected create_user_with_role to succeed for student, got: {:?}", res);
}

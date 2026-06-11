mod common;

use common::*;
use uuid::Uuid;

const SCHOOL_ID: &str = "02ba1e88-cb88-4e10-9d3f-81eb62912e1d";
const SCHOOL_SLUG: &str = "demohighschool";
const SESSION_ID: &str = "8f47bc12-671a-4a63-b671-3c691b054915";
const TERM_ID: &str = "3273883a-d0e5-4009-b60e-ec30a1dbb8a0";
const STUDENT_ID_TO_LINK: &str = "9acf5acf-682d-42e8-9c8c-773450e4420c";
const CLASS_ID_TO_LINK: &str = "de4d411b-ce1f-42b0-a0a5-95503d24d7d6";
const SUBJECT_ID_TO_LINK: &str = "856d26fd-6878-4458-a240-3d229cff6358";

async fn sign_in_admin_and_get_token(client: &reqwest::Client) -> String {
    let payload = serde_json::json!({
        "email": "admin@demohighschool.edu",
        "password": "admin123"
    });

    let response = client
        .post(&format!("{}{}", constants::API_URL, "/api/auth/sign-in"))
        .json(&payload)
        .send()
        .await
        .expect("Failed to sign in admin");

    assert!(response.status().is_success(), "Admin sign-in should succeed");

    let body = response
        .json::<serde_json::Value>()
        .await
        .expect("Failed to parse sign-in response");

    body.get("access_token")
        .and_then(|v| v.as_str())
        .expect("Missing access_token in sign-in response")
        .to_string()
}

async fn cleanup_by_email(pool: &sqlx::PgPool, email: &str) {
    let user_id = sqlx::query_scalar::<_, Uuid>("SELECT id FROM users WHERE email = $1")
        .bind(email)
        .fetch_optional(pool)
        .await
        .ok()
        .flatten();

    if let Some(uid) = user_id {
        let _ = sqlx::query("DELETE FROM parent_student_relationships WHERE parent_id IN (SELECT id FROM parents WHERE user_id = $1)")
            .bind(uid)
            .execute(pool)
            .await;
        let _ = sqlx::query("DELETE FROM class_teachers WHERE staff_id IN (SELECT id FROM staff WHERE user_id = $1)")
            .bind(uid)
            .execute(pool)
            .await;
        let _ = sqlx::query("DELETE FROM subject_teachers WHERE staff_id IN (SELECT id FROM staff WHERE user_id = $1)")
            .bind(uid)
            .execute(pool)
            .await;
        let _ = sqlx::query("DELETE FROM student_classes WHERE student_id IN (SELECT id FROM students WHERE user_id = $1)")
            .bind(uid)
            .execute(pool)
            .await;
        let _ = sqlx::query("DELETE FROM students WHERE user_id = $1")
            .bind(uid)
            .execute(pool)
            .await;
        let _ = sqlx::query("DELETE FROM parents WHERE user_id = $1")
            .bind(uid)
            .execute(pool)
            .await;
        let _ = sqlx::query("DELETE FROM staff WHERE user_id = $1")
            .bind(uid)
            .execute(pool)
            .await;
    }

    let _ = db::delete_test_user(pool, email).await;
}

#[tokio::test]
async fn dedicated_student_with_user_endpoint_creates_student() {
    let client = get_http_client();
    let token = sign_in_admin_and_get_token(&client).await;
    let pool = get_db_pool().await;

    let email = generate_test_email("dedicated-student-with-user");
    cleanup_by_email(&pool, &email).await;

    let payload = serde_json::json!({
        "user": {
            "email": email,
            "phone_number": generate_test_phone(),
            "first_name": "Dedicated",
            "middle_name": null,
            "last_name": "Student",
            "gender": "F",
            "date_of_birth": null,
            "school_slug": SCHOOL_SLUG
        },
        "student": {
            "student_id": null,
            "school_id": SCHOOL_ID,
            "admission_number": null,
            "admission_date": null,
            "graduation_date": null,
            "academic_status": null,
            "current_grade_level": "Grade 1",
            "date_of_birth": null,
            "gender": "F",
            "previous_school": null,
            "special_needs_description": null,
            "transportation_method": null,
            "passport_photo_url": null
        },
        "student_classes": null
    });

    let response = client
        .post(&format!("{}{}", constants::API_URL, "/api/auth/students/with-user"))
        .bearer_auth(token)
        .json(&payload)
        .send()
        .await
        .expect("Failed to call student dedicated endpoint");

    let status = response.status();
    let body = response
        .json::<serde_json::Value>()
        .await
        .expect("Failed to parse response");

    assert!(status.is_success(), "Expected success, body: {}", body);
    assert_eq!(
        body.get("school_id").and_then(|v| v.as_str()),
        Some(SCHOOL_ID),
        "student should be created in provided school"
    );

    cleanup_by_email(&pool, payload["user"]["email"].as_str().unwrap()).await;
}

#[tokio::test]
async fn dedicated_parent_with_user_endpoint_creates_parent() {
    let client = get_http_client();
    let token = sign_in_admin_and_get_token(&client).await;
    let pool = get_db_pool().await;

    let email = generate_test_email("dedicated-parent-with-user");
    cleanup_by_email(&pool, &email).await;

    let payload = serde_json::json!({
        "school_id": SCHOOL_ID,
        "email": email,
        "phone_number": generate_test_phone(),
        "first_name": "Dedicated",
        "middle_name": null,
        "last_name": "Parent",
        "gender": "F",
        "date_of_birth": null,
        "parent": {
            "occupation": "Trader",
            "employer_name": "Biz Ltd",
            "business_address": "Main Street"
        },
        "parent_student_relationships": [
            {
                "student_id": STUDENT_ID_TO_LINK,
                "relationship": "GUARDIAN"
            }
        ]
    });

    let response = client
        .post(&format!("{}{}", constants::API_URL, "/api/auth/parents/with-user"))
        .bearer_auth(token)
        .json(&payload)
        .send()
        .await
        .expect("Failed to call parent dedicated endpoint");

    let status = response.status();
    let body = response
        .json::<serde_json::Value>()
        .await
        .expect("Failed to parse response");

    assert!(status.is_success(), "Expected success, body: {}", body);
    assert_eq!(
        body.get("school_id").and_then(|v| v.as_str()),
        Some(SCHOOL_ID),
        "parent should be created in provided school"
    );

    let parent_id = Uuid::parse_str(
        body.get("id")
            .and_then(|v| v.as_str())
            .expect("parent id missing in response"),
    )
    .expect("invalid parent id");

    let linked_count: i64 = sqlx::query_scalar(
        "SELECT COUNT(*) FROM parent_student_relationships WHERE parent_id = $1 AND student_id = $2 AND school_id = $3 AND is_active = true"
    )
    .bind(parent_id)
    .bind(Uuid::parse_str(STUDENT_ID_TO_LINK).expect("invalid student id"))
    .bind(Uuid::parse_str(SCHOOL_ID).expect("invalid school id"))
    .fetch_one(&pool)
    .await
    .expect("Failed to query parent_student_relationships");

    assert_eq!(linked_count, 1, "parent should be linked to requested student");

    cleanup_by_email(&pool, payload["email"].as_str().unwrap()).await;
}

#[tokio::test]
async fn dedicated_staff_with_user_endpoint_creates_staff() {
    let client = get_http_client();
    let token = sign_in_admin_and_get_token(&client).await;
    let pool = get_db_pool().await;

    let email = generate_test_email("dedicated-staff-with-user");
    cleanup_by_email(&pool, &email).await;

    let payload = serde_json::json!({
        "school_id": SCHOOL_ID,
        "email": email,
        "phone_number": generate_test_phone(),
        "first_name": "Dedicated",
        "middle_name": null,
        "last_name": "Staff",
        "gender": "M",
        "date_of_birth": null,
        "staff": {
            "staff_id": null,
            "employee_number": "EMP-D01",
            "designation": "Teacher",
            "hire_date": null,
            "employment_status": "ACTIVE",
            "employment_type": "FULL_TIME",
            "highest_degree": null,
            "department": "Academics",
            "is_class_teacher": false,
            "is_subject_teacher": true,
            "bank_name": null,
            "account_name": null,
            "account_number": null,
            "monthly_deduction": 0.0,
            "class_teacher_for": null,
            "years_of_experience": 2
        },
        "staff_class_assignments": [
            {
                "class_id": CLASS_ID_TO_LINK,
                "session_id": SESSION_ID,
                "term_id": TERM_ID,
                "assigned_date": null
            }
        ],
        "staff_subject_assignments": [
            {
                "subject_id": SUBJECT_ID_TO_LINK,
                "class_id": CLASS_ID_TO_LINK,
                "session_id": SESSION_ID,
                "term_id": TERM_ID,
                "assigned_date": null
            }
        ]
    });

    let response = client
        .post(&format!("{}{}", constants::API_URL, "/api/auth/staff/with-user"))
        .bearer_auth(token)
        .json(&payload)
        .send()
        .await
        .expect("Failed to call staff dedicated endpoint");

    let status = response.status();
    let body = response
        .json::<serde_json::Value>()
        .await
        .expect("Failed to parse response");

    assert!(status.is_success(), "Expected success, body: {}", body);
    assert_eq!(
        body.get("school_id").and_then(|v| v.as_str()),
        Some(SCHOOL_ID),
        "staff should be created in provided school"
    );

    let staff_id = Uuid::parse_str(
        body.get("id")
            .and_then(|v| v.as_str())
            .expect("staff id missing in response"),
    )
    .expect("invalid staff id");

    let class_assignment_count: i64 = sqlx::query_scalar(
        "SELECT COUNT(*) FROM class_teachers WHERE staff_id = $1 AND class_id = $2 AND academic_session_id = $3 AND term_id = $4 AND school_id = $5 AND is_active = true"
    )
    .bind(staff_id)
    .bind(Uuid::parse_str(CLASS_ID_TO_LINK).expect("invalid class id"))
    .bind(Uuid::parse_str(SESSION_ID).expect("invalid session id"))
    .bind(Uuid::parse_str(TERM_ID).expect("invalid term id"))
    .bind(Uuid::parse_str(SCHOOL_ID).expect("invalid school id"))
    .fetch_one(&pool)
    .await
    .expect("Failed to query class_teachers");

    assert_eq!(class_assignment_count, 1, "staff should be assigned to requested class");

    let subject_assignment_count: i64 = sqlx::query_scalar(
        "SELECT COUNT(*) FROM subject_teachers WHERE staff_id = $1 AND class_id = $2 AND subject_id = $3 AND academic_session_id = $4 AND term_id = $5 AND school_id = $6 AND is_active = true"
    )
    .bind(staff_id)
    .bind(Uuid::parse_str(CLASS_ID_TO_LINK).expect("invalid class id"))
    .bind(Uuid::parse_str(SUBJECT_ID_TO_LINK).expect("invalid subject id"))
    .bind(Uuid::parse_str(SESSION_ID).expect("invalid session id"))
    .bind(Uuid::parse_str(TERM_ID).expect("invalid term id"))
    .bind(Uuid::parse_str(SCHOOL_ID).expect("invalid school id"))
    .fetch_one(&pool)
    .await
    .expect("Failed to query subject_teachers");

    assert_eq!(subject_assignment_count, 1, "staff should be assigned to requested class+subject");

    cleanup_by_email(&pool, payload["email"].as_str().unwrap()).await;
}

mod common;

use common::*;
use uuid::Uuid;

const SCHOOL_ID: &str = "02ba1e88-cb88-4e10-9d3f-81eb62912e1d";
const SCHOOL_SLUG: &str = "demohighschool";
const SESSION_ID: &str = "8f47bc12-671a-4a63-b671-3c691b054915";
const TERM_ID: &str = "3273883a-d0e5-4009-b60e-ec30a1dbb8a0";
const STUDENT_ID_TO_LINK: &str = "9acf5acf-682d-42e8-9c8c-773450e4420c";
const STUDENT_CLASS_ID_TO_LINK: &str = "3f88af37-81c3-4949-ad78-6395de0b6dfc";
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
async fn soft_delete_student_class_assignment_by_assignment_id() {
    let client = get_http_client();
    let token = sign_in_admin_and_get_token(&client).await;
    let pool = get_db_pool().await;

    let email = generate_test_email("soft-delete-student-assignment");
    cleanup_by_email(&pool, &email).await;

    let create_payload = serde_json::json!({
        "user": {
            "email": email,
            "phone_number": generate_test_phone(),
            "first_name": "Soft",
            "middle_name": null,
            "last_name": "DeleteStudent",
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

    let create_resp = client
        .post(&format!("{}{}", constants::API_URL, "/api/auth/students/with-user"))
        .bearer_auth(&token)
        .json(&create_payload)
        .send()
        .await
        .expect("Failed to create student");

    let create_body = create_resp
        .json::<serde_json::Value>()
        .await
        .expect("Failed to parse create student response");
    let student_id = Uuid::parse_str(
        create_body.get("id").and_then(|v| v.as_str()).expect("missing student id")
    ).expect("invalid student id");

    let assign_payload = serde_json::json!({
        "school_id": SCHOOL_ID,
        "classes": [
            {
                "class_id": STUDENT_CLASS_ID_TO_LINK,
                "session_id": SESSION_ID,
                "term_id": TERM_ID,
                "enrollment_date": null
            }
        ]
    });

    let assign_resp = client
        .post(&format!("{}{}/{}{}", constants::API_URL, "/api/auth/students", student_id, "/classes/assign"))
        .bearer_auth(&token)
        .json(&assign_payload)
        .send()
        .await
        .expect("Failed to assign student class");
    assert!(assign_resp.status().is_success(), "student class assignment should succeed");

    let assignment_id: Uuid = sqlx::query_scalar(
        "SELECT id FROM student_classes WHERE student_id = $1 AND class_id = $2 AND academic_session_id = $3 AND term_id = $4 AND is_active = true ORDER BY created_at DESC LIMIT 1"
    )
    .bind(student_id)
    .bind(Uuid::parse_str(STUDENT_CLASS_ID_TO_LINK).expect("invalid class id"))
    .bind(Uuid::parse_str(SESSION_ID).expect("invalid session id"))
    .bind(Uuid::parse_str(TERM_ID).expect("invalid term id"))
    .fetch_one(&pool)
    .await
    .expect("Failed to fetch student class assignment id");

    let delete_resp = client
        .delete(&format!("{}{}/{}", constants::API_URL, "/api/auth/students/classes/assignments", assignment_id))
        .bearer_auth(&token)
        .send()
        .await
        .expect("Failed to delete student class assignment");
    assert!(delete_resp.status().is_success(), "student class assignment delete should succeed");

    let still_active_count: i64 = sqlx::query_scalar(
        "SELECT COUNT(*) FROM student_classes WHERE id = $1 AND is_active = true"
    )
    .bind(assignment_id)
    .fetch_one(&pool)
    .await
    .expect("Failed to verify student assignment soft delete");

    assert_eq!(still_active_count, 0, "student class assignment should be soft-deleted");

    cleanup_by_email(&pool, create_payload["user"]["email"].as_str().unwrap()).await;
}

#[tokio::test]
async fn soft_delete_parent_student_assignment_by_assignment_id() {
    let client = get_http_client();
    let token = sign_in_admin_and_get_token(&client).await;
    let pool = get_db_pool().await;

    let email = generate_test_email("soft-delete-parent-assignment");
    cleanup_by_email(&pool, &email).await;

    let payload = serde_json::json!({
        "school_id": SCHOOL_ID,
        "email": email,
        "phone_number": generate_test_phone(),
        "first_name": "Soft",
        "middle_name": null,
        "last_name": "DeleteParent",
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

    let create_resp = client
        .post(&format!("{}{}", constants::API_URL, "/api/auth/parents/with-user"))
        .bearer_auth(&token)
        .json(&payload)
        .send()
        .await
        .expect("Failed to create parent with relationship");

    let body = create_resp
        .json::<serde_json::Value>()
        .await
        .expect("Failed to parse parent response");
    let parent_id = Uuid::parse_str(
        body.get("id").and_then(|v| v.as_str()).expect("missing parent id")
    ).expect("invalid parent id");

    let assignment_id: Uuid = sqlx::query_scalar(
        "SELECT id FROM parent_student_relationships WHERE parent_id = $1 AND student_id = $2 AND school_id = $3 AND is_active = true ORDER BY created_at DESC LIMIT 1"
    )
    .bind(parent_id)
    .bind(Uuid::parse_str(STUDENT_ID_TO_LINK).expect("invalid student id"))
    .bind(Uuid::parse_str(SCHOOL_ID).expect("invalid school id"))
    .fetch_one(&pool)
    .await
    .expect("Failed to fetch parent-student assignment id");

    let delete_resp = client
        .delete(&format!("{}{}/{}", constants::API_URL, "/api/auth/parents/students/assignments", assignment_id))
        .bearer_auth(&token)
        .send()
        .await
        .expect("Failed to delete parent-student assignment");

    assert!(delete_resp.status().is_success(), "parent-student assignment delete should succeed");

    let still_active_count: i64 = sqlx::query_scalar(
        "SELECT COUNT(*) FROM parent_student_relationships WHERE id = $1 AND is_active = true"
    )
    .bind(assignment_id)
    .fetch_one(&pool)
    .await
    .expect("Failed to verify parent assignment soft delete");

    assert_eq!(still_active_count, 0, "parent-student assignment should be soft-deleted");

    cleanup_by_email(&pool, payload["email"].as_str().unwrap()).await;
}

#[tokio::test]
async fn soft_delete_staff_class_and_subject_assignments_by_assignment_id() {
    let client = get_http_client();
    let token = sign_in_admin_and_get_token(&client).await;
    let pool = get_db_pool().await;

    let email = generate_test_email("soft-delete-staff-assignment");
    cleanup_by_email(&pool, &email).await;

    let payload = serde_json::json!({
        "school_id": SCHOOL_ID,
        "email": email,
        "phone_number": generate_test_phone(),
        "first_name": "Soft",
        "middle_name": null,
        "last_name": "DeleteStaff",
        "gender": "M",
        "date_of_birth": null,
        "staff": {
            "staff_id": null,
            "employee_number": "EMP-SD-01",
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

    let create_resp = client
        .post(&format!("{}{}", constants::API_URL, "/api/auth/staff/with-user"))
        .bearer_auth(&token)
        .json(&payload)
        .send()
        .await
        .expect("Failed to create staff with assignments");

    let body = create_resp
        .json::<serde_json::Value>()
        .await
        .expect("Failed to parse staff response");
    let staff_id = Uuid::parse_str(
        body.get("id").and_then(|v| v.as_str()).expect("missing staff id")
    ).expect("invalid staff id");

    let class_assignment_id: Uuid = sqlx::query_scalar(
        "SELECT id FROM class_teachers WHERE staff_id = $1 AND class_id = $2 AND academic_session_id = $3 AND term_id = $4 AND school_id = $5 AND is_active = true ORDER BY created_at DESC LIMIT 1"
    )
    .bind(staff_id)
    .bind(Uuid::parse_str(CLASS_ID_TO_LINK).expect("invalid class id"))
    .bind(Uuid::parse_str(SESSION_ID).expect("invalid session id"))
    .bind(Uuid::parse_str(TERM_ID).expect("invalid term id"))
    .bind(Uuid::parse_str(SCHOOL_ID).expect("invalid school id"))
    .fetch_one(&pool)
    .await
    .expect("Failed to fetch class assignment id");

    let subject_assignment_id: Uuid = sqlx::query_scalar(
        "SELECT id FROM subject_teachers WHERE staff_id = $1 AND class_id = $2 AND subject_id = $3 AND academic_session_id = $4 AND term_id = $5 AND school_id = $6 AND is_active = true ORDER BY created_at DESC LIMIT 1"
    )
    .bind(staff_id)
    .bind(Uuid::parse_str(CLASS_ID_TO_LINK).expect("invalid class id"))
    .bind(Uuid::parse_str(SUBJECT_ID_TO_LINK).expect("invalid subject id"))
    .bind(Uuid::parse_str(SESSION_ID).expect("invalid session id"))
    .bind(Uuid::parse_str(TERM_ID).expect("invalid term id"))
    .bind(Uuid::parse_str(SCHOOL_ID).expect("invalid school id"))
    .fetch_one(&pool)
    .await
    .expect("Failed to fetch subject assignment id");

    let del_class_resp = client
        .delete(&format!("{}{}/{}", constants::API_URL, "/api/auth/staff/classes/assignments", class_assignment_id))
        .bearer_auth(&token)
        .send()
        .await
        .expect("Failed to delete staff class assignment");
    assert!(del_class_resp.status().is_success(), "staff class assignment delete should succeed");

    let del_subject_resp = client
        .delete(&format!("{}{}/{}", constants::API_URL, "/api/auth/staff/subjects/assignments", subject_assignment_id))
        .bearer_auth(&token)
        .send()
        .await
        .expect("Failed to delete staff subject assignment");
    assert!(del_subject_resp.status().is_success(), "staff subject assignment delete should succeed");

    let class_active_count: i64 = sqlx::query_scalar(
        "SELECT COUNT(*) FROM class_teachers WHERE id = $1 AND is_active = true"
    )
    .bind(class_assignment_id)
    .fetch_one(&pool)
    .await
    .expect("Failed to verify class assignment soft delete");
    assert_eq!(class_active_count, 0, "staff class assignment should be soft-deleted");

    let subject_active_count: i64 = sqlx::query_scalar(
        "SELECT COUNT(*) FROM subject_teachers WHERE id = $1 AND is_active = true"
    )
    .bind(subject_assignment_id)
    .fetch_one(&pool)
    .await
    .expect("Failed to verify subject assignment soft delete");
    assert_eq!(subject_active_count, 0, "staff subject assignment should be soft-deleted");

    cleanup_by_email(&pool, payload["email"].as_str().unwrap()).await;
}

#[path = "../common/mod.rs"]
mod common;

use common::*;
use school_backend::{db::Database, services::FinanceService};
use std::collections::HashMap;
use uuid::Uuid;

#[derive(Debug, serde::Deserialize)]
struct SignInResponseBody {
    access_token: String,
}

async fn create_school_admin_and_token(
    client: &reqwest::Client,
    pool: &sqlx::PgPool,
) -> (String, Uuid, Uuid, String) {
    let email = generate_test_email("finance-admin");
    let phone = generate_test_phone();
    let password = "SecurePassword123!";

    let signup_request = build_signup_request(
        &email,
        password,
        "Finance",
        "Admin",
        &phone,
        "SCHOOL_ADMIN",
        None,
    );

    let signup_response = http::signup_expect_success(client, signup_request).await;
    let user_id = Uuid::parse_str(&signup_response.user_id).expect("invalid user id");
    let school_id = Uuid::parse_str(&signup_response.school_id).expect("invalid school id");

    sqlx::query("UPDATE users SET is_active = true, is_approved = true, status = 'ACTIVE' WHERE id = $1")
        .bind(user_id)
        .execute(pool)
        .await
        .expect("failed to activate admin user");

    let signin_payload = serde_json::json!({
        "email": email,
        "password": password
    });

    let signin_response = client
        .post(&format!("{}{}", constants::API_URL, "/api/auth/sign-in"))
        .json(&signin_payload)
        .send()
        .await
        .expect("failed to sign in admin");

    assert!(signin_response.status().is_success(), "admin sign-in should succeed");

    let signin_body: SignInResponseBody = signin_response
        .json()
        .await
        .expect("failed to parse sign-in body");

    (signin_body.access_token, school_id, user_id, email)
}

async fn create_staff_and_token_for_school(
    client: &reqwest::Client,
    pool: &sqlx::PgPool,
    school_id: Uuid,
) -> (String, Uuid, String) {
    let school_slug = sqlx::query_scalar::<_, String>("SELECT slug FROM schools WHERE id = $1")
        .bind(school_id)
        .fetch_one(pool)
        .await
        .expect("failed to fetch school slug");

    let email = generate_test_email("finance-staff");
    let phone = generate_test_phone();
    let password = "SecurePassword123!";

    let signup_request = build_signup_request(
        &email,
        password,
        "Finance",
        "Staff",
        &phone,
        "STAFF",
        Some(&school_slug),
    );

    let signup_response = http::signup_expect_success(client, signup_request).await;
    let user_id = Uuid::parse_str(&signup_response.user_id).expect("invalid user id");

    sqlx::query("UPDATE users SET is_active = true, is_approved = true, status = 'ACTIVE' WHERE id = $1")
        .bind(user_id)
        .execute(pool)
        .await
        .expect("failed to activate staff user");

    let signin_payload = serde_json::json!({
        "email": email,
        "password": password
    });

    let signin_response = client
        .post(&format!("{}{}", constants::API_URL, "/api/auth/sign-in"))
        .json(&signin_payload)
        .send()
        .await
        .expect("failed to sign in staff user");

    assert!(signin_response.status().is_success(), "staff sign-in should succeed");

    let signin_body: SignInResponseBody = signin_response
        .json()
        .await
        .expect("failed to parse staff sign-in body");

    (signin_body.access_token, user_id, email)
}

async fn ensure_class_for_school(pool: &sqlx::PgPool, school_id: Uuid) -> Uuid {
    if let Some(existing_id) = sqlx::query_scalar::<_, Uuid>(
        "SELECT id FROM classes WHERE school_id = $1 AND is_active = true ORDER BY created_at ASC LIMIT 1",
    )
    .bind(school_id)
    .fetch_optional(pool)
    .await
    .expect("failed querying existing class")
    {
        return existing_id;
    }

    let class_id = Uuid::new_v4();
    let suffix = &class_id.to_string()[..8];

    sqlx::query(
        r#"
        INSERT INTO classes (
            id, school_id, class_code, class_name, classroom_location, current_enrollment,
            grade_level, max_capacity, scoring_scheme, department_id, track_id,
            class_staff_id, term, created_at, updated_at, is_active
        ) VALUES (
            $1, $2, $3, $4, $5, $6,
            $7, $8, $9, $10, $11,
            $12, $13, NOW(), NOW(), true
        )
        "#,
    )
    .bind(class_id)
    .bind(school_id)
    .bind(format!("FIN-{}", suffix))
    .bind(format!("Finance Test Class {}", suffix))
    .bind(Option::<String>::None)
    .bind(0_i32)
    .bind(Some(1_i32))
    .bind(Some(40_i32))
    .bind(Some("PERCENTAGE".to_string()))
    .bind(Option::<Uuid>::None)
    .bind(Option::<Uuid>::None)
    .bind(Option::<Uuid>::None)
    .bind(Option::<String>::None)
    .execute(pool)
    .await
    .expect("failed creating fallback class");

    class_id
}

async fn get_school_slug(pool: &sqlx::PgPool, school_id: Uuid) -> String {
    sqlx::query_scalar::<_, String>("SELECT slug FROM schools WHERE id = $1")
        .bind(school_id)
        .fetch_one(pool)
        .await
        .expect("failed to fetch school slug")
}

async fn create_student_via_endpoint(
    client: &reqwest::Client,
    pool: &sqlx::PgPool,
    token: &str,
    school_id: Uuid,
    gender: &str,
    student_suffix: &str,
) -> Uuid {
    let school_slug = get_school_slug(pool, school_id).await;

    let payload = serde_json::json!({
        "user": {
            "email": format!("finance-student-{}-{}@example.com", student_suffix, chrono::Utc::now().timestamp_millis()),
            "phone_number": generate_test_phone(),
            "first_name": "Finance",
            "middle_name": null,
            "last_name": format!("Student{}", student_suffix),
            "gender": gender,
            "date_of_birth": "2012-01-01",
            "school_slug": school_slug
        },
        "student": {
            "student_id": null,
            "school_id": school_id,
            "admission_number": null,
            "admission_date": "2026-09-01",
            "graduation_date": null,
            "academic_status": "ENROLLED",
            "current_grade_level": "JSS1",
            "date_of_birth": "2012-01-01",
            "gender": gender,
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
        .expect("failed to create student via endpoint");

    let status = response.status();
    let body = response.text().await.expect("failed to read create student response body");
    assert!(
        status.is_success(),
        "create student failed: status={} body={}",
        status,
        body
    );

    let json: serde_json::Value = serde_json::from_str(&body).expect("failed to parse student response");
    Uuid::parse_str(
        json.get("id")
            .and_then(|v| v.as_str())
            .expect("missing student id in response"),
    )
    .expect("invalid student id")
}

async fn ensure_academic_context_for_school(pool: &sqlx::PgPool, school_id: Uuid) -> (Uuid, Uuid, Uuid) {
    let academic_session_id = if let Some(id) = sqlx::query_scalar::<_, Uuid>(
        "SELECT id FROM academic_sessions WHERE school_id = $1 AND is_active = true ORDER BY created_at ASC LIMIT 1",
    )
    .bind(school_id)
    .fetch_optional(pool)
    .await
    .expect("failed querying academic session")
    {
        id
    } else {
        let id = Uuid::new_v4();
        let suffix = &id.to_string()[..8];
        sqlx::query(
            r#"
            INSERT INTO academic_sessions (
                id, created_at, is_active, updated_at, school_id,
                end_date, is_current_session, notes, session_name,
                session_year, start_date, status
            ) VALUES (
                $1, NOW(), true, NOW(), $2,
                $3, true, $4, $5,
                $6, $7, $8
            )
            "#,
        )
        .bind(id)
        .bind(school_id)
        .bind(chrono::NaiveDate::from_ymd_opt(2027, 7, 31).expect("invalid date"))
        .bind(Some("Finance billing tests".to_string()))
        .bind(format!("Session-{}", suffix))
        .bind("2026/2027")
        .bind(chrono::NaiveDate::from_ymd_opt(2026, 9, 1).expect("invalid date"))
        .bind(Some("ACTIVE".to_string()))
        .execute(pool)
        .await
        .expect("failed creating academic session");
        id
    };

    let term_id = if let Some(id) = sqlx::query_scalar::<_, Uuid>(
        "SELECT id FROM terms WHERE school_id = $1 AND academic_session_id = $2 AND is_active = true ORDER BY created_at ASC LIMIT 1",
    )
    .bind(school_id)
    .bind(academic_session_id)
    .fetch_optional(pool)
    .await
    .expect("failed querying term")
    {
        id
    } else {
        let id = Uuid::new_v4();
        sqlx::query(
            r#"
            INSERT INTO terms (
                id, created_at, is_active, updated_at, school_id,
                description, end_date, is_current_term, start_date, status,
                term_name, academic_session_id, term_number, term_order
            ) VALUES (
                $1, NOW(), true, NOW(), $2,
                $3, $4, true, $5, $6,
                $7, $8, $9, $10
            )
            "#,
        )
        .bind(id)
        .bind(school_id)
        .bind(Some("Finance billing term".to_string()))
        .bind(Some(chrono::NaiveDate::from_ymd_opt(2026, 12, 20).expect("invalid date")))
        .bind(chrono::NaiveDate::from_ymd_opt(2026, 9, 10).expect("invalid date"))
        .bind(Some("ACTIVE".to_string()))
        .bind("FIRST_TERM")
        .bind(academic_session_id)
        .bind(Some(1_i32))
        .bind(Some(1_i32))
        .execute(pool)
        .await
        .expect("failed creating term");
        id
    };

    let track_id = if let Some(id) = sqlx::query_scalar::<_, Uuid>(
        "SELECT id FROM education_tracks WHERE school_id = $1 AND is_active = true ORDER BY created_at ASC LIMIT 1",
    )
    .bind(school_id)
    .fetch_optional(pool)
    .await
    .expect("failed querying education track")
    {
        id
    } else {
        let id = Uuid::new_v4();
        let suffix = &id.to_string()[..8];
        sqlx::query(
            r#"
            INSERT INTO education_tracks (id, created_at, is_active, updated_at, school_id, description, name)
            VALUES ($1, NOW(), true, NOW(), $2, $3, $4)
            "#,
        )
        .bind(id)
        .bind(school_id)
        .bind(Some("Default track for finance billing tests".to_string()))
        .bind(format!("Default Track {}", suffix))
        .execute(pool)
        .await
        .expect("failed creating education track");
        id
    };

    (academic_session_id, term_id, track_id)
}

async fn assign_student_to_class(
    pool: &sqlx::PgPool,
    school_id: Uuid,
    student_id: Uuid,
    class_id: Uuid,
    academic_session_id: Uuid,
    term_id: Uuid,
    track_id: Uuid,
) {
    sqlx::query(
        r#"
        INSERT INTO student_classes (
            id, school_id, student_id, class_id, academic_session_id, term_id,
            track_id, enrollment_date, created_at, updated_at, is_active
        ) VALUES (
            $1, $2, $3, $4, $5, $6,
            $7, $8, NOW(), NOW(), true
        )
        ON CONFLICT (student_id, track_id, academic_session_id, term_id)
        DO UPDATE SET
            class_id = EXCLUDED.class_id,
            school_id = EXCLUDED.school_id,
            enrollment_date = EXCLUDED.enrollment_date,
            updated_at = NOW(),
            is_active = true
        "#,
    )
    .bind(Uuid::new_v4())
    .bind(school_id)
    .bind(student_id)
    .bind(class_id)
    .bind(academic_session_id)
    .bind(term_id)
    .bind(track_id)
    .bind(chrono::NaiveDate::from_ymd_opt(2026, 9, 1).expect("invalid date"))
    .execute(pool)
    .await
    .expect("failed assigning student to class");
}

async fn create_fee_item_and_assignment(
    client: &reqwest::Client,
    token: &str,
    school_id: Uuid,
    class_id: Uuid,
    fee_name: String,
    amount: f64,
    is_mandatory: bool,
    class_custom_amount: Option<f64>,
) -> (Uuid, Uuid) {
    let fee_payload = serde_json::json!({
        "school_id": school_id,
        "amount": amount,
        "description": format!("{} description", fee_name),
        "is_mandatory": is_mandatory,
        "name": fee_name,
        "gender_eligibility": "ALL",
        "student_status_eligibility": "ALL",
        "staff_discount_amount": null,
        "staff_discount_type": "NONE"
    });

    let fee_resp = client
        .post(&format!("{}{}", constants::API_URL, "/api/auth/finance/fee-items"))
        .bearer_auth(token)
        .json(&fee_payload)
        .send()
        .await
        .expect("failed to create fee item");

    assert!(fee_resp.status().is_success(), "fee item create should succeed");
    let fee_json = fee_resp
        .json::<serde_json::Value>()
        .await
        .expect("failed parsing fee item response");
    let fee_item_id = Uuid::parse_str(
        fee_json
            .get("id")
            .and_then(|v| v.as_str())
            .expect("missing fee_item_id"),
    )
    .expect("invalid fee_item_id");

    let assignment_payload = serde_json::json!({
        "school_id": school_id,
        "class_id": class_id,
        "academic_year": "2026/2027",
        "custom_amount": class_custom_amount,
        "is_applicable": true,
        "notes": "Billing test assignment",
        "term": "FIRST_TERM",
        "academic_session_id": null,
        "term_id": null,
        "is_locked": false
    });

    let assignment_resp = client
        .post(&format!(
            "{}{}{}{}",
            constants::API_URL,
            "/api/auth/finance/fee-items/",
            fee_item_id,
            "/class-assignments"
        ))
        .bearer_auth(token)
        .json(&assignment_payload)
        .send()
        .await
        .expect("failed creating class fee assignment");
    assert!(assignment_resp.status().is_success(), "class fee assignment should succeed");

    let assignment_json = assignment_resp
        .json::<serde_json::Value>()
        .await
        .expect("failed parsing class assignment response");
    let class_fee_item_id = Uuid::parse_str(
        assignment_json
            .get("id")
            .and_then(|v| v.as_str())
            .expect("missing class_fee_item_id"),
    )
    .expect("invalid class_fee_item_id");

    (fee_item_id, class_fee_item_id)
}

#[tokio::test]
async fn finance_fee_item_crud_endpoints_work() {
    let pool = get_db_pool().await;
    let client = get_http_client();

    let (token, school_id, _user_id, email) = create_school_admin_and_token(&client, &pool).await;

    let create_payload = serde_json::json!({
        "school_id": school_id,
        "amount": 10000.0,
        "description": "Base school fees",
        "is_mandatory": true,
        "name": format!("Tuition-{}", chrono::Utc::now().timestamp_millis()),
        "gender_eligibility": null,
        "student_status_eligibility": null,
        "staff_discount_amount": 500.0,
        "staff_discount_type": "FLAT_AMOUNT"
    });

    let create_resp = client
        .post(&format!("{}{}", constants::API_URL, "/api/auth/finance/fee-items"))
        .bearer_auth(&token)
        .json(&create_payload)
        .send()
        .await
        .expect("failed to create fee item");

    let create_status = create_resp.status();
    let create_body = create_resp.text().await.expect("failed to read create response body");
    assert!(
        create_status.is_success(),
        "create fee item failed: status={} body={}",
        create_status,
        create_body
    );

    let created_fee_item: serde_json::Value =
        serde_json::from_str(&create_body).expect("failed to parse created fee item");

    let fee_item_id = Uuid::parse_str(
        created_fee_item
            .get("id")
            .and_then(|v| v.as_str())
            .expect("missing fee item id"),
    )
    .expect("invalid fee item id");

    let list_resp = client
        .get(&format!(
            "{}{}?school_id={}",
            constants::API_URL,
            "/api/auth/finance/fee-items",
            school_id
        ))
        .bearer_auth(&token)
        .send()
        .await
        .expect("failed to list fee items");

    assert!(list_resp.status().is_success(), "list fee items should succeed");
    let list_body = list_resp
        .json::<Vec<serde_json::Value>>()
        .await
        .expect("failed to parse fee items list");
    assert!(
        list_body.iter().any(|item| item.get("id").and_then(|v| v.as_str()) == Some(fee_item_id.to_string().as_str())),
        "created fee item should be present in list"
    );

    let update_payload = serde_json::json!({
        "school_id": school_id,
        "amount": 12500.0,
        "description": "Updated base school fees",
        "is_mandatory": true,
        "name": format!("Updated Tuition-{}", chrono::Utc::now().timestamp_millis()),
        "gender_eligibility": "ALL",
        "student_status_eligibility": "ALL",
        "staff_discount_amount": 1000.0,
        "staff_discount_type": "FLAT_AMOUNT"
    });

    let update_resp = client
        .put(&format!(
            "{}{}{}",
            constants::API_URL,
            "/api/auth/finance/fee-items/",
            fee_item_id
        ))
        .bearer_auth(&token)
        .json(&update_payload)
        .send()
        .await
        .expect("failed to update fee item");

    assert!(update_resp.status().is_success(), "update fee item should succeed");
    let updated_fee_item = update_resp
        .json::<serde_json::Value>()
        .await
        .expect("failed to parse updated fee item");
    assert_eq!(
        updated_fee_item.get("amount").and_then(|v| v.as_f64()),
        Some(12500.0)
    );

    let delete_resp = client
        .delete(&format!(
            "{}{}{}?school_id={}",
            constants::API_URL,
            "/api/auth/finance/fee-items/",
            fee_item_id,
            school_id
        ))
        .bearer_auth(&token)
        .send()
        .await
        .expect("failed to delete fee item");

    assert!(delete_resp.status().is_success(), "delete fee item should succeed");

    let list_after_delete_resp = client
        .get(&format!(
            "{}{}?school_id={}",
            constants::API_URL,
            "/api/auth/finance/fee-items",
            school_id
        ))
        .bearer_auth(&token)
        .send()
        .await
        .expect("failed to list fee items after delete");

    assert!(list_after_delete_resp.status().is_success());
    let list_after_delete = list_after_delete_resp
        .json::<Vec<serde_json::Value>>()
        .await
        .expect("failed to parse post-delete list");

    assert!(
        !list_after_delete.iter().any(|item| item.get("id").and_then(|v| v.as_str()) == Some(fee_item_id.to_string().as_str())),
        "deleted fee item should not be present in list"
    );

    let _ = db::delete_test_user(&pool, &email).await;
}

#[tokio::test]
async fn finance_manual_settlement_create_endpoint_uses_manual_type() {
    let pool = get_db_pool().await;
    let client = get_http_client();

    let (token, school_id, _user_id, email) = create_school_admin_and_token(&client, &pool).await;
    let reference = format!("manual-settlement-{}", chrono::Utc::now().timestamp_millis());

    let payload = serde_json::json!({
        "school_id": school_id,
        "amount": 25000.0,
        "currency": "NGN",
        "payer_email": "manualpayer@example.com",
        "payment_channel": "CASH",
        "raw_payload": "{\"note\":\"recorded manually\"}",
        "reference": reference,
        "status": "SUCCESS",
        "transaction_date": null,
        "wallet_id": null,
        "academic_session_year": "2026/2027",
        "term": "FIRST_TERM",
        "academic_session_id": null,
        "term_id": null,
        "paystack_wallet_id": null,
        "squad_wallet_id": null,
        "provider": "OFFLINE",
        "parent_id": null
    });

    let response = client
        .post(&format!(
            "{}{}",
            constants::API_URL,
            "/api/auth/finance/settlements/manual"
        ))
        .bearer_auth(&token)
        .json(&payload)
        .send()
        .await
        .expect("failed to create manual settlement");

    let status = response.status();
    let body = response
        .text()
        .await
        .expect("failed to read manual settlement response body");
    assert!(
        status.is_success(),
        "manual settlement create should succeed: status={} body={}",
        status,
        body
    );

    let json: serde_json::Value = serde_json::from_str(&body).expect("failed to parse manual settlement response");
    assert_eq!(json.get("settlement_type").and_then(|v| v.as_str()), Some("MANUAL"));
    assert_eq!(json.get("reference").and_then(|v| v.as_str()), Some(reference.as_str()));
    assert_eq!(json.get("amount").and_then(|v| v.as_f64()), Some(25000.0));

    let _ = db::delete_test_user(&pool, &email).await;
}

#[tokio::test]
async fn finance_class_fee_item_assignment_endpoints_support_overwrite_and_delete() {
    let pool = get_db_pool().await;
    let client = get_http_client();

    let (token, school_id, _user_id, email) = create_school_admin_and_token(&client, &pool).await;
    let class_id = ensure_class_for_school(&pool, school_id).await;

    let create_fee_item_payload = serde_json::json!({
        "school_id": school_id,
        "amount": 15000.0,
        "description": "Transport fee",
        "is_mandatory": false,
        "name": format!("Transport-{}", chrono::Utc::now().timestamp_millis()),
        "gender_eligibility": null,
        "student_status_eligibility": null,
        "staff_discount_amount": null,
        "staff_discount_type": "NONE"
    });

    let create_fee_item_resp = client
        .post(&format!("{}{}", constants::API_URL, "/api/auth/finance/fee-items"))
        .bearer_auth(&token)
        .json(&create_fee_item_payload)
        .send()
        .await
        .expect("failed to create fee item for assignment test");

    let create_fee_item_status = create_fee_item_resp.status();
    let create_fee_item_body = create_fee_item_resp
        .text()
        .await
        .expect("failed to read create fee item response body");
    assert!(
        create_fee_item_status.is_success(),
        "create fee item should succeed: status={} body={}",
        create_fee_item_status,
        create_fee_item_body
    );
    let created_fee_item = serde_json::from_str::<serde_json::Value>(&create_fee_item_body)
        .expect("failed to parse created fee item");

    let fee_item_id = Uuid::parse_str(
        created_fee_item
            .get("id")
            .and_then(|v| v.as_str())
            .expect("missing fee item id"),
    )
    .expect("invalid fee item id");

    let create_assignment_payload = serde_json::json!({
        "school_id": school_id,
        "class_id": class_id,
        "academic_year": "2026/2027",
        "custom_amount": 18000.0,
        "is_applicable": true,
        "notes": "Science stream premium",
        "term": "FIRST_TERM",
        "academic_session_id": null,
        "term_id": null,
        "is_locked": false
    });

    let create_assignment_resp = client
        .post(&format!(
            "{}{}{}{}",
            constants::API_URL,
            "/api/auth/finance/fee-items/",
            fee_item_id,
            "/class-assignments"
        ))
        .bearer_auth(&token)
        .json(&create_assignment_payload)
        .send()
        .await
        .expect("failed to create class assignment");

    assert!(
        create_assignment_resp.status().is_success(),
        "create assignment should succeed"
    );

    let overwrite_assignment_payload = serde_json::json!({
        "school_id": school_id,
        "class_id": class_id,
        "academic_year": "2026/2027",
        "custom_amount": 20000.0,
        "is_applicable": true,
        "notes": "Overwritten amount",
        "term": "FIRST_TERM",
        "academic_session_id": null,
        "term_id": null,
        "is_locked": false
    });

    let overwrite_assignment_resp = client
        .post(&format!(
            "{}{}{}{}",
            constants::API_URL,
            "/api/auth/finance/fee-items/",
            fee_item_id,
            "/class-assignments"
        ))
        .bearer_auth(&token)
        .json(&overwrite_assignment_payload)
        .send()
        .await
        .expect("failed to overwrite class assignment");

    assert!(
        overwrite_assignment_resp.status().is_success(),
        "overwrite assignment should succeed"
    );

    let list_assignments_resp = client
        .get(&format!(
            "{}{}{}{}?school_id={}",
            constants::API_URL,
            "/api/auth/finance/fee-items/",
            fee_item_id,
            "/class-assignments",
            school_id
        ))
        .bearer_auth(&token)
        .send()
        .await
        .expect("failed to list class assignments");

    assert!(
        list_assignments_resp.status().is_success(),
        "list class assignments should succeed"
    );

    let assignments = list_assignments_resp
        .json::<Vec<serde_json::Value>>()
        .await
        .expect("failed to parse class assignments");

    assert_eq!(assignments.len(), 1, "overwrite should keep a single assignment row");
    assert_eq!(
        assignments[0].get("custom_amount").and_then(|v| v.as_f64()),
        Some(20000.0)
    );

    let delete_assignment_resp = client
        .delete(&format!(
            "{}{}{}{}{}?school_id={}",
            constants::API_URL,
            "/api/auth/finance/fee-items/",
            fee_item_id,
            "/class-assignments/",
            class_id,
            school_id
        ))
        .bearer_auth(&token)
        .send()
        .await
        .expect("failed to delete class assignment");

    assert!(
        delete_assignment_resp.status().is_success(),
        "delete class assignment should succeed"
    );

    let list_after_delete_resp = client
        .get(&format!(
            "{}{}{}{}?school_id={}",
            constants::API_URL,
            "/api/auth/finance/fee-items/",
            fee_item_id,
            "/class-assignments",
            school_id
        ))
        .bearer_auth(&token)
        .send()
        .await
        .expect("failed to list class assignments after delete");

    assert!(list_after_delete_resp.status().is_success());
    let assignments_after_delete = list_after_delete_resp
        .json::<Vec<serde_json::Value>>()
        .await
        .expect("failed to parse post-delete class assignments");
    assert!(
        assignments_after_delete.is_empty(),
        "assignment list should be empty after delete"
    );

    let delete_fee_item_resp = client
        .delete(&format!(
            "{}{}{}?school_id={}",
            constants::API_URL,
            "/api/auth/finance/fee-items/",
            fee_item_id,
            school_id
        ))
        .bearer_auth(&token)
        .send()
        .await
        .expect("failed to delete fee item in assignment test cleanup");

    assert!(delete_fee_item_resp.status().is_success());

    let _ = db::delete_test_user(&pool, &email).await;
}

#[tokio::test]
async fn finance_fee_item_endpoints_require_school_admin_role() {
    let pool = get_db_pool().await;
    let client = get_http_client();

    let (_admin_token, school_id, _admin_user_id, admin_email) =
        create_school_admin_and_token(&client, &pool).await;
    let (staff_token, _staff_user_id, staff_email) =
        create_staff_and_token_for_school(&client, &pool, school_id).await;

    let class_id = ensure_class_for_school(&pool, school_id).await;
    let random_fee_item_id = Uuid::new_v4();

    let create_payload = serde_json::json!({
        "school_id": school_id,
        "amount": 9999.0,
        "description": "Unauthorized create attempt",
        "is_mandatory": true,
        "name": format!("Unauthorized-{}", chrono::Utc::now().timestamp_millis()),
        "gender_eligibility": "ALL",
        "student_status_eligibility": "ALL",
        "staff_discount_amount": 100.0,
        "staff_discount_type": "FLAT_AMOUNT"
    });

    let create_resp = client
        .post(&format!("{}{}", constants::API_URL, "/api/auth/finance/fee-items"))
        .bearer_auth(&staff_token)
        .json(&create_payload)
        .send()
        .await
        .expect("failed to call fee item create endpoint as staff");
    assert_eq!(create_resp.status(), reqwest::StatusCode::UNAUTHORIZED);

    let update_resp = client
        .put(&format!(
            "{}{}{}",
            constants::API_URL,
            "/api/auth/finance/fee-items/",
            random_fee_item_id
        ))
        .bearer_auth(&staff_token)
        .json(&create_payload)
        .send()
        .await
        .expect("failed to call fee item update endpoint as staff");
    assert_eq!(update_resp.status(), reqwest::StatusCode::UNAUTHORIZED);

    let delete_resp = client
        .delete(&format!(
            "{}{}{}?school_id={}",
            constants::API_URL,
            "/api/auth/finance/fee-items/",
            random_fee_item_id,
            school_id
        ))
        .bearer_auth(&staff_token)
        .send()
        .await
        .expect("failed to call fee item delete endpoint as staff");
    assert_eq!(delete_resp.status(), reqwest::StatusCode::UNAUTHORIZED);

    let assignment_payload = serde_json::json!({
        "school_id": school_id,
        "class_id": class_id,
        "academic_year": "2026/2027",
        "custom_amount": 12000.0,
        "is_applicable": true,
        "notes": "Unauthorized assignment",
        "term": "FIRST_TERM",
        "academic_session_id": null,
        "term_id": null,
        "is_locked": false
    });

    let upsert_assignment_resp = client
        .post(&format!(
            "{}{}{}{}",
            constants::API_URL,
            "/api/auth/finance/fee-items/",
            random_fee_item_id,
            "/class-assignments"
        ))
        .bearer_auth(&staff_token)
        .json(&assignment_payload)
        .send()
        .await
        .expect("failed to call class assignment upsert endpoint as staff");
    assert_eq!(
        upsert_assignment_resp.status(),
        reqwest::StatusCode::UNAUTHORIZED
    );

    let list_assignment_resp = client
        .get(&format!(
            "{}{}{}{}?school_id={}",
            constants::API_URL,
            "/api/auth/finance/fee-items/",
            random_fee_item_id,
            "/class-assignments",
            school_id
        ))
        .bearer_auth(&staff_token)
        .send()
        .await
        .expect("failed to call class assignment list endpoint as staff");
    assert_eq!(list_assignment_resp.status(), reqwest::StatusCode::UNAUTHORIZED);

    let delete_assignment_resp = client
        .delete(&format!(
            "{}{}{}{}{}?school_id={}",
            constants::API_URL,
            "/api/auth/finance/fee-items/",
            random_fee_item_id,
            "/class-assignments/",
            class_id,
            school_id
        ))
        .bearer_auth(&staff_token)
        .send()
        .await
        .expect("failed to call class assignment delete endpoint as staff");
    assert_eq!(
        delete_assignment_resp.status(),
        reqwest::StatusCode::UNAUTHORIZED
    );

    let random_student_optional_fee_id = Uuid::new_v4();
    let lock_payload = serde_json::json!({ "school_id": school_id });

    let manual_settlement_payload = serde_json::json!({
        "school_id": school_id,
        "amount": 1000.0,
        "currency": "NGN",
        "payer_email": null,
        "payment_channel": "CASH",
        "raw_payload": null,
        "reference": format!("manual-settlement-staff-{}", chrono::Utc::now().timestamp_millis()),
        "status": "SUCCESS",
        "transaction_date": null,
        "wallet_id": null,
        "academic_session_year": null,
        "term": null,
        "academic_session_id": null,
        "term_id": null,
        "paystack_wallet_id": null,
        "squad_wallet_id": null,
        "provider": "OFFLINE",
        "parent_id": null
    });

    let create_manual_settlement_resp = client
        .post(&format!(
            "{}{}",
            constants::API_URL,
            "/api/auth/finance/settlements/manual"
        ))
        .bearer_auth(&staff_token)
        .json(&manual_settlement_payload)
        .send()
        .await
        .expect("failed to call manual settlement create endpoint as staff");
    assert_eq!(
        create_manual_settlement_resp.status(),
        reqwest::StatusCode::UNAUTHORIZED
    );

    let lock_optional_fee_resp = client
        .put(&format!(
            "{}{}{}{}",
            constants::API_URL,
            "/api/auth/finance/student-optional-fees/",
            random_student_optional_fee_id,
            "/lock"
        ))
        .bearer_auth(&staff_token)
        .json(&lock_payload)
        .send()
        .await
        .expect("failed to call lock optional fee endpoint as staff");
    assert_eq!(
        lock_optional_fee_resp.status(),
        reqwest::StatusCode::UNAUTHORIZED
    );

    let unlock_optional_fee_resp = client
        .put(&format!(
            "{}{}{}{}",
            constants::API_URL,
            "/api/auth/finance/student-optional-fees/",
            random_student_optional_fee_id,
            "/unlock"
        ))
        .bearer_auth(&staff_token)
        .json(&lock_payload)
        .send()
        .await
        .expect("failed to call unlock optional fee endpoint as staff");
    assert_eq!(
        unlock_optional_fee_resp.status(),
        reqwest::StatusCode::UNAUTHORIZED
    );

    let delete_optional_fee_resp = client
        .delete(&format!(
            "{}{}{}?school_id={}",
            constants::API_URL,
            "/api/auth/finance/student-optional-fees/",
            random_student_optional_fee_id,
            school_id
        ))
        .bearer_auth(&staff_token)
        .send()
        .await
        .expect("failed to call delete optional fee endpoint as staff");
    assert_eq!(
        delete_optional_fee_resp.status(),
        reqwest::StatusCode::UNAUTHORIZED
    );

    let _ = db::delete_test_user(&pool, &admin_email).await;
    let _ = db::delete_test_user(&pool, &staff_email).await;
}


#[tokio::test]
async fn finance_apply_optional_fee_item_respects_mandatory_gender_and_status_eligibility() {
    let pool = get_db_pool().await;
    let client = get_http_client();

    let (admin_token, school_id, _admin_user_id, admin_email) =
        create_school_admin_and_token(&client, &pool).await;
    let class_id = ensure_class_for_school(&pool, school_id).await;
    let (_session_id, _term_id, _track_id) = ensure_academic_context_for_school(&pool, school_id).await;

    let male_student_id = create_student_via_endpoint(
        &client,
        &pool,
        &admin_token,
        school_id,
        "MALE",
        "male-eligible",
    )
    .await;

    // Ensure we can test RETURNING status eligibility explicitly.
    sqlx::query("UPDATE students SET is_new = false, updated_at = NOW() WHERE id = $1")
        .bind(male_student_id)
        .execute(&pool)
        .await
        .expect("failed to update student status to returning");

    let female_student_id = create_student_via_endpoint(
        &client,
        &pool,
        &admin_token,
        school_id,
        "FEMALE",
        "female-ineligible",
    )
    .await;

    let optional_fee_payload = serde_json::json!({
        "school_id": school_id,
        "amount": 8500.0,
        "description": "Optional sports fee",
        "is_mandatory": false,
        "name": format!("OptionalSports-{}", chrono::Utc::now().timestamp_millis()),
        "gender_eligibility": "MALE",
        "student_status_eligibility": "RETURNING",
        "staff_discount_amount": null,
        "staff_discount_type": "NONE"
    });

    let optional_fee_resp = client
        .post(&format!("{}{}", constants::API_URL, "/api/auth/finance/fee-items"))
        .bearer_auth(&admin_token)
        .json(&optional_fee_payload)
        .send()
        .await
        .expect("failed to create optional fee item");
    assert!(optional_fee_resp.status().is_success(), "optional fee item create should succeed");
    let optional_fee_json = optional_fee_resp
        .json::<serde_json::Value>()
        .await
        .expect("failed to parse optional fee item");
    let optional_fee_id = Uuid::parse_str(
        optional_fee_json
            .get("id")
            .and_then(|v| v.as_str())
            .expect("missing optional fee item id"),
    )
    .expect("invalid optional fee item id");

    let assignment_payload = serde_json::json!({
        "school_id": school_id,
        "class_id": class_id,
        "academic_year": "2026/2027",
        "custom_amount": 9000.0,
        "is_applicable": true,
        "notes": "Optional sports by class",
        "term": "FIRST_TERM",
        "academic_session_id": null,
        "term_id": null,
        "is_locked": false
    });

    let assignment_resp = client
        .post(&format!(
            "{}{}{}{}",
            constants::API_URL,
            "/api/auth/finance/fee-items/",
            optional_fee_id,
            "/class-assignments"
        ))
        .bearer_auth(&admin_token)
        .json(&assignment_payload)
        .send()
        .await
        .expect("failed to create optional fee class assignment");
    assert!(assignment_resp.status().is_success(), "optional fee class assignment create should succeed");
    let assignment_json = assignment_resp
        .json::<serde_json::Value>()
        .await
        .expect("failed to parse class assignment response");
    let class_fee_item_id = Uuid::parse_str(
        assignment_json
            .get("id")
            .and_then(|v| v.as_str())
            .expect("missing class fee item id"),
    )
    .expect("invalid class fee item id");

    let apply_payload_eligible = serde_json::json!({
        "school_id": school_id,
        "student_id": male_student_id,
        "class_fee_item_id": class_fee_item_id,
        "academic_session_id": null,
        "term_id": null,
        "custom_amount": 8700.0,
        "notes": "Applied to eligible returning male student",
        "is_locked": false
    });

    let apply_eligible_resp = client
        .post(&format!(
            "{}{}{}{}",
            constants::API_URL,
            "/api/auth/finance/fee-items/",
            optional_fee_id,
            "/apply-to-student"
        ))
        .bearer_auth(&admin_token)
        .json(&apply_payload_eligible)
        .send()
        .await
        .expect("failed to apply optional fee to eligible student");
    let apply_eligible_status = apply_eligible_resp.status();
    let apply_eligible_body = apply_eligible_resp
        .text()
        .await
        .expect("failed to read eligible apply response");
    assert!(
        apply_eligible_status.is_success(),
        "apply optional fee (eligible) should succeed: status={} body={}",
        apply_eligible_status,
        apply_eligible_body
    );

    let apply_payload_gender_mismatch = serde_json::json!({
        "school_id": school_id,
        "student_id": female_student_id,
        "class_fee_item_id": class_fee_item_id,
        "academic_session_id": null,
        "term_id": null,
        "custom_amount": null,
        "notes": "Should fail for gender mismatch",
        "is_locked": false
    });

    let apply_gender_mismatch_resp = client
        .post(&format!(
            "{}{}{}{}",
            constants::API_URL,
            "/api/auth/finance/fee-items/",
            optional_fee_id,
            "/apply-to-student"
        ))
        .bearer_auth(&admin_token)
        .json(&apply_payload_gender_mismatch)
        .send()
        .await
        .expect("failed to call apply optional fee for gender mismatch");
    assert_eq!(apply_gender_mismatch_resp.status(), reqwest::StatusCode::BAD_REQUEST);

    let apply_payload_status_mismatch = serde_json::json!({
        "school_id": school_id,
        "student_id": male_student_id,
        "class_fee_item_id": class_fee_item_id,
        "academic_session_id": null,
        "term_id": null,
        "custom_amount": null,
        "notes": "Should fail for status mismatch",
        "is_locked": false
    });

    sqlx::query("UPDATE students SET is_new = true, updated_at = NOW() WHERE id = $1")
        .bind(male_student_id)
        .execute(&pool)
        .await
        .expect("failed to reset student status to new");

    let apply_status_mismatch_resp = client
        .post(&format!(
            "{}{}{}{}",
            constants::API_URL,
            "/api/auth/finance/fee-items/",
            optional_fee_id,
            "/apply-to-student"
        ))
        .bearer_auth(&admin_token)
        .json(&apply_payload_status_mismatch)
        .send()
        .await
        .expect("failed to call apply optional fee for status mismatch");
    assert_eq!(apply_status_mismatch_resp.status(), reqwest::StatusCode::BAD_REQUEST);

    let mandatory_fee_payload = serde_json::json!({
        "school_id": school_id,
        "amount": 7000.0,
        "description": "Mandatory fee",
        "is_mandatory": true,
        "name": format!("MandatoryFee-{}", chrono::Utc::now().timestamp_millis()),
        "gender_eligibility": "ALL",
        "student_status_eligibility": "ALL",
        "staff_discount_amount": null,
        "staff_discount_type": "NONE"
    });

    let mandatory_fee_resp = client
        .post(&format!("{}{}", constants::API_URL, "/api/auth/finance/fee-items"))
        .bearer_auth(&admin_token)
        .json(&mandatory_fee_payload)
        .send()
        .await
        .expect("failed to create mandatory fee item");
    assert!(mandatory_fee_resp.status().is_success(), "mandatory fee item create should succeed");
    let mandatory_fee_json = mandatory_fee_resp
        .json::<serde_json::Value>()
        .await
        .expect("failed to parse mandatory fee item");
    let mandatory_fee_id = Uuid::parse_str(
        mandatory_fee_json
            .get("id")
            .and_then(|v| v.as_str())
            .expect("missing mandatory fee item id"),
    )
    .expect("invalid mandatory fee item id");

    let mandatory_assignment_resp = client
        .post(&format!(
            "{}{}{}{}",
            constants::API_URL,
            "/api/auth/finance/fee-items/",
            mandatory_fee_id,
            "/class-assignments"
        ))
        .bearer_auth(&admin_token)
        .json(&assignment_payload)
        .send()
        .await
        .expect("failed to create mandatory fee class assignment");
    assert!(mandatory_assignment_resp.status().is_success(), "mandatory fee class assignment create should succeed");
    let mandatory_assignment_json = mandatory_assignment_resp
        .json::<serde_json::Value>()
        .await
        .expect("failed to parse mandatory assignment");
    let mandatory_class_fee_item_id = Uuid::parse_str(
        mandatory_assignment_json
            .get("id")
            .and_then(|v| v.as_str())
            .expect("missing mandatory class fee item id"),
    )
    .expect("invalid mandatory class fee item id");

    let apply_payload_mandatory = serde_json::json!({
        "school_id": school_id,
        "student_id": male_student_id,
        "class_fee_item_id": mandatory_class_fee_item_id,
        "academic_session_id": null,
        "term_id": null,
        "custom_amount": null,
        "notes": "Should fail for mandatory fee",
        "is_locked": false
    });

    let apply_mandatory_resp = client
        .post(&format!(
            "{}{}{}{}",
            constants::API_URL,
            "/api/auth/finance/fee-items/",
            mandatory_fee_id,
            "/apply-to-student"
        ))
        .bearer_auth(&admin_token)
        .json(&apply_payload_mandatory)
        .send()
        .await
        .expect("failed to call apply optional fee for mandatory item");
    assert_eq!(apply_mandatory_resp.status(), reqwest::StatusCode::BAD_REQUEST);

    // List optional fees and verify it is returned
    let list_resp = client
        .get(&format!(
            "{}{}?school_id={}&fee_item_id={}",
            constants::API_URL,
            "/api/auth/finance/student-optional-fees",
            school_id,
            optional_fee_id
        ))
        .bearer_auth(&admin_token)
        .send()
        .await
        .expect("failed to list optional fees");
    assert!(list_resp.status().is_success(), "list optional fees should succeed");
    
    let list_json: serde_json::Value = list_resp
        .json()
        .await
        .expect("failed to parse list response");
    
    let data = list_json.get("data").and_then(|d| d.as_array()).expect("missing data array");
    assert!(!data.is_empty(), "optional fees list should not be empty");

    let _ = db::delete_test_user(&pool, &admin_email).await;
}

#[tokio::test]
async fn finance_student_optional_fee_lock_unlock_and_delete_endpoints_work() {
    let pool = get_db_pool().await;
    let client = get_http_client();

    let (admin_token, school_id, _admin_user_id, admin_email) =
        create_school_admin_and_token(&client, &pool).await;
    let class_id = ensure_class_for_school(&pool, school_id).await;

    let student_id = create_student_via_endpoint(
        &client,
        &pool,
        &admin_token,
        school_id,
        "MALE",
        "lock-unlock",
    )
    .await;

    let optional_fee_payload = serde_json::json!({
        "school_id": school_id,
        "amount": 6500.0,
        "description": "Optional clubs fee",
        "is_mandatory": false,
        "name": format!("OptionalClub-{}", chrono::Utc::now().timestamp_millis()),
        "gender_eligibility": "ALL",
        "student_status_eligibility": "ALL",
        "staff_discount_amount": null,
        "staff_discount_type": "NONE"
    });

    let optional_fee_resp = client
        .post(&format!("{}{}", constants::API_URL, "/api/auth/finance/fee-items"))
        .bearer_auth(&admin_token)
        .json(&optional_fee_payload)
        .send()
        .await
        .expect("failed to create optional fee item");
    assert!(optional_fee_resp.status().is_success());
    let optional_fee_json = optional_fee_resp
        .json::<serde_json::Value>()
        .await
        .expect("failed to parse optional fee json");
    let optional_fee_id = Uuid::parse_str(
        optional_fee_json
            .get("id")
            .and_then(|v| v.as_str())
            .expect("missing optional fee id"),
    )
    .expect("invalid optional fee id");

    let assignment_payload = serde_json::json!({
        "school_id": school_id,
        "class_id": class_id,
        "academic_year": "2026/2027",
        "custom_amount": 7000.0,
        "is_applicable": true,
        "notes": "Optional clubs by class",
        "term": "FIRST_TERM",
        "academic_session_id": null,
        "term_id": null,
        "is_locked": false
    });

    let assignment_resp = client
        .post(&format!(
            "{}{}{}{}",
            constants::API_URL,
            "/api/auth/finance/fee-items/",
            optional_fee_id,
            "/class-assignments"
        ))
        .bearer_auth(&admin_token)
        .json(&assignment_payload)
        .send()
        .await
        .expect("failed to create class assignment");
    assert!(assignment_resp.status().is_success());
    let assignment_json = assignment_resp
        .json::<serde_json::Value>()
        .await
        .expect("failed to parse assignment json");
    let class_fee_item_id = Uuid::parse_str(
        assignment_json
            .get("id")
            .and_then(|v| v.as_str())
            .expect("missing class_fee_item_id"),
    )
    .expect("invalid class_fee_item_id");

    let apply_payload = serde_json::json!({
        "school_id": school_id,
        "student_id": student_id,
        "class_fee_item_id": class_fee_item_id,
        "academic_session_id": null,
        "term_id": null,
        "custom_amount": 6800.0,
        "notes": "Apply optional fee for lock/unlock test",
        "is_locked": false
    });

    let apply_resp = client
        .post(&format!(
            "{}{}{}{}",
            constants::API_URL,
            "/api/auth/finance/fee-items/",
            optional_fee_id,
            "/apply-to-student"
        ))
        .bearer_auth(&admin_token)
        .json(&apply_payload)
        .send()
        .await
        .expect("failed to apply optional fee");
    assert!(apply_resp.status().is_success());
    let apply_json = apply_resp
        .json::<serde_json::Value>()
        .await
        .expect("failed to parse apply response");
    let student_optional_fee_id = Uuid::parse_str(
        apply_json
            .get("id")
            .and_then(|v| v.as_str())
            .expect("missing student_optional_fee id"),
    )
    .expect("invalid student_optional_fee id");

    let lock_payload = serde_json::json!({ "school_id": school_id });
    let lock_resp = client
        .put(&format!(
            "{}{}{}{}",
            constants::API_URL,
            "/api/auth/finance/student-optional-fees/",
            student_optional_fee_id,
            "/lock"
        ))
        .bearer_auth(&admin_token)
        .json(&lock_payload)
        .send()
        .await
        .expect("failed to lock student optional fee");
    assert!(lock_resp.status().is_success());
    let lock_json = lock_resp
        .json::<serde_json::Value>()
        .await
        .expect("failed to parse lock response");
    assert_eq!(lock_json.get("is_locked").and_then(|v| v.as_bool()), Some(true));

    let unlock_resp = client
        .put(&format!(
            "{}{}{}{}",
            constants::API_URL,
            "/api/auth/finance/student-optional-fees/",
            student_optional_fee_id,
            "/unlock"
        ))
        .bearer_auth(&admin_token)
        .json(&lock_payload)
        .send()
        .await
        .expect("failed to unlock student optional fee");
    assert!(unlock_resp.status().is_success());
    let unlock_json = unlock_resp
        .json::<serde_json::Value>()
        .await
        .expect("failed to parse unlock response");
    assert_eq!(unlock_json.get("is_locked").and_then(|v| v.as_bool()), Some(false));

    let delete_resp = client
        .delete(&format!(
            "{}{}{}?school_id={}",
            constants::API_URL,
            "/api/auth/finance/student-optional-fees/",
            student_optional_fee_id,
            school_id
        ))
        .bearer_auth(&admin_token)
        .send()
        .await
        .expect("failed to delete student optional fee");
    assert!(delete_resp.status().is_success());

    let lock_after_delete_resp = client
        .put(&format!(
            "{}{}{}{}",
            constants::API_URL,
            "/api/auth/finance/student-optional-fees/",
            student_optional_fee_id,
            "/lock"
        ))
        .bearer_auth(&admin_token)
        .json(&lock_payload)
        .send()
        .await
        .expect("failed to lock deleted student optional fee");
    assert_eq!(lock_after_delete_resp.status(), reqwest::StatusCode::NOT_FOUND);

    let _ = db::delete_test_user(&pool, &admin_email).await;
}

#[tokio::test]
async fn finance_bill_recalculation_functions_return_expected_totals_for_two_students() {
    let pool = get_db_pool().await;
    let client = get_http_client();

    let (admin_token, school_id, admin_user_id, admin_email) =
        create_school_admin_and_token(&client, &pool).await;

    let class_id = ensure_class_for_school(&pool, school_id).await;
    let (academic_session_id, term_id, track_id) =
        ensure_academic_context_for_school(&pool, school_id).await;

    let student_one_id = create_student_via_endpoint(
        &client,
        &pool,
        &admin_token,
        school_id,
        "MALE",
        "bill-one",
    )
    .await;
    let student_two_id = create_student_via_endpoint(
        &client,
        &pool,
        &admin_token,
        school_id,
        "FEMALE",
        "bill-two",
    )
    .await;

    assign_student_to_class(
        &pool,
        school_id,
        student_one_id,
        class_id,
        academic_session_id,
        term_id,
        track_id,
    )
    .await;
    assign_student_to_class(
        &pool,
        school_id,
        student_two_id,
        class_id,
        academic_session_id,
        term_id,
        track_id,
    )
    .await;

    let ts = chrono::Utc::now().timestamp_millis();
    let mandatory_fee_a_name = format!("BillMandatoryA-{}", ts);
    let mandatory_fee_b_name = format!("BillMandatoryB-{}", ts);
    let optional_fee_a_name = format!("BillOptionalA-{}", ts);
    let optional_fee_b_name = format!("BillOptionalB-{}", ts);

    let (_mandatory_a_fee_item_id, mandatory_a_class_fee_item_id) = create_fee_item_and_assignment(
        &client,
        &admin_token,
        school_id,
        class_id,
        mandatory_fee_a_name.clone(),
        1000.0,
        true,
        Some(1200.0),
    )
    .await;

    let _ = create_fee_item_and_assignment(
        &client,
        &admin_token,
        school_id,
        class_id,
        mandatory_fee_b_name.clone(),
        2000.0,
        true,
        None,
    )
    .await;

    let (optional_a_fee_item_id, optional_a_class_fee_item_id) = create_fee_item_and_assignment(
        &client,
        &admin_token,
        school_id,
        class_id,
        optional_fee_a_name.clone(),
        300.0,
        false,
        Some(350.0),
    )
    .await;

    let (optional_b_fee_item_id, optional_b_class_fee_item_id) = create_fee_item_and_assignment(
        &client,
        &admin_token,
        school_id,
        class_id,
        optional_fee_b_name.clone(),
        400.0,
        false,
        None,
    )
    .await;

    let apply_optional_a_student_one = serde_json::json!({
        "school_id": school_id,
        "student_id": student_one_id,
        "class_fee_item_id": optional_a_class_fee_item_id,
        "academic_session_id": null,
        "term_id": null,
        "custom_amount": null,
        "notes": "apply optional A to student one",
        "is_locked": false
    });
    let apply_optional_a_resp = client
        .post(&format!(
            "{}{}{}{}",
            constants::API_URL,
            "/api/auth/finance/fee-items/",
            optional_a_fee_item_id,
            "/apply-to-student"
        ))
        .bearer_auth(&admin_token)
        .json(&apply_optional_a_student_one)
        .send()
        .await
        .expect("failed to apply optional A to student one");
    assert!(apply_optional_a_resp.status().is_success());

    let apply_optional_b_student_two = serde_json::json!({
        "school_id": school_id,
        "student_id": student_two_id,
        "class_fee_item_id": optional_b_class_fee_item_id,
        "academic_session_id": null,
        "term_id": null,
        "custom_amount": null,
        "notes": "apply optional B to student two",
        "is_locked": false
    });
    let apply_optional_b_resp = client
        .post(&format!(
            "{}{}{}{}",
            constants::API_URL,
            "/api/auth/finance/fee-items/",
            optional_b_fee_item_id,
            "/apply-to-student"
        ))
        .bearer_auth(&admin_token)
        .json(&apply_optional_b_student_two)
        .send()
        .await
        .expect("failed to apply optional B to student two");
    assert!(apply_optional_b_resp.status().is_success());

    let database_url = std::env::var("DATABASE_URL")
        .unwrap_or_else(|_| "postgres://postgres:password@localhost:5432/myschool".to_string());
    let db = Database::new(&database_url)
        .await
        .expect("failed to create database handle for service calls");

    let student_one_bill = FinanceService::recalculate_student_bill(
        &db,
        admin_user_id,
        school_id,
        student_one_id,
        None,
        None,
    )
    .await
    .expect("failed to recalculate bill for student one");

    let student_two_bill = FinanceService::recalculate_student_bill(
        &db,
        admin_user_id,
        school_id,
        student_two_id,
        None,
        None,
    )
    .await
    .expect("failed to recalculate bill for student two");

    let expected_student_one_total = 1200.0 + 2000.0 + 350.0;
    let expected_student_two_total = 1200.0 + 2000.0 + 400.0;

    assert!(
        (student_one_bill.amount - expected_student_one_total).abs() < 0.001,
        "student one bill mismatch: expected={}, got={}",
        expected_student_one_total,
        student_one_bill.amount
    );
    assert!(
        (student_two_bill.amount - expected_student_two_total).abs() < 0.001,
        "student two bill mismatch: expected={}, got={}",
        expected_student_two_total,
        student_two_bill.amount
    );

    let student_one_breakdown_text = student_one_bill
        .breakdown
        .as_deref()
        .expect("student one breakdown should exist");
    let student_two_breakdown_text = student_two_bill
        .breakdown
        .as_deref()
        .expect("student two breakdown should exist");

    let student_one_breakdown: Vec<serde_json::Value> =
        serde_json::from_str(student_one_breakdown_text).expect("invalid student one breakdown json");
    let student_two_breakdown: Vec<serde_json::Value> =
        serde_json::from_str(student_two_breakdown_text).expect("invalid student two breakdown json");

    let to_map = |items: &[serde_json::Value]| -> HashMap<String, f64> {
        items
            .iter()
            .map(|item| {
                let name = item
                    .get("fee_name")
                    .and_then(|v| v.as_str())
                    .expect("missing fee_name")
                    .to_string();
                let amount = item
                    .get("amount")
                    .and_then(|v| v.as_f64())
                    .expect("missing amount");
                (name, amount)
            })
            .collect()
    };

    let student_one_breakdown_map = to_map(&student_one_breakdown);
    let student_two_breakdown_map = to_map(&student_two_breakdown);

    assert_eq!(student_one_breakdown_map.get(&mandatory_fee_a_name), Some(&1200.0));
    assert_eq!(student_one_breakdown_map.get(&mandatory_fee_b_name), Some(&2000.0));
    assert_eq!(student_one_breakdown_map.get(&optional_fee_a_name), Some(&350.0));
    assert!(!student_one_breakdown_map.contains_key(&optional_fee_b_name));

    assert_eq!(student_two_breakdown_map.get(&mandatory_fee_a_name), Some(&1200.0));
    assert_eq!(student_two_breakdown_map.get(&mandatory_fee_b_name), Some(&2000.0));
    assert_eq!(student_two_breakdown_map.get(&optional_fee_b_name), Some(&400.0));
    assert!(!student_two_breakdown_map.contains_key(&optional_fee_a_name));

    let affected_bills = FinanceService::recalculate_bills_for_class_fee_item(
        &db,
        admin_user_id,
        school_id,
        mandatory_a_class_fee_item_id,
    )
    .await
    .expect("failed to recalculate bills for class fee item");

    let affected_map: HashMap<Uuid, f64> = affected_bills
        .iter()
        .map(|bill| (bill.student_id, bill.amount))
        .collect();

    assert_eq!(affected_map.get(&student_one_id), Some(&expected_student_one_total));
    assert_eq!(affected_map.get(&student_two_id), Some(&expected_student_two_total));

    let _ = db::delete_test_user(&pool, &admin_email).await;
}

#[path = "../common/mod.rs"]
mod common;

use common::*;
use uuid::Uuid;

#[derive(Debug, serde::Deserialize)]
struct SignInResponseBody {
    access_token: String,
}

async fn create_school_admin_and_token(
    client: &reqwest::Client,
    pool: &sqlx::PgPool,
) -> (String, Uuid, Uuid, String) {
    let email = generate_test_email("schedule-admin");
    let phone = generate_test_phone();
    let password = "SecurePassword123!";

    let signup_request = build_signup_request(
        &email,
        password,
        "Schedule",
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
    .bind(format!("SCHED-{}", suffix))
    .bind(format!("Schedule Test Class {}", suffix))
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

async fn ensure_active_session_for_school(pool: &sqlx::PgPool, school_id: Uuid) -> Uuid {
    if let Some(existing_id) = sqlx::query_scalar::<_, Uuid>(
        r#"
        SELECT id
        FROM academic_sessions
        WHERE school_id = $1 AND is_active = true
        ORDER BY COALESCE(is_current_session, false) DESC, start_date DESC, created_at DESC
        LIMIT 1
        "#,
    )
    .bind(school_id)
    .fetch_optional(pool)
    .await
    .expect("failed querying existing academic session")
    {
        return existing_id;
    }

    let session_id = Uuid::new_v4();
    let now = chrono::Utc::now().naive_utc();

    sqlx::query(
        r#"
        INSERT INTO academic_sessions (
            id, school_id, session_name, session_year, start_date, end_date,
            is_current_session, status, notes, created_at, updated_at, is_active
        ) VALUES (
            $1, $2, $3, $4, $5, $6,
            true, 'ACTIVE', NULL, $7, $8, true
        )
        "#,
    )
    .bind(session_id)
    .bind(school_id)
    .bind("Schedule Calendar Session")
    .bind("Schedule Calendar Session")
    .bind(chrono::NaiveDate::from_ymd_opt(2026, 1, 1).expect("valid start date"))
    .bind(chrono::NaiveDate::from_ymd_opt(2026, 12, 31).expect("valid end date"))
    .bind(now)
    .bind(now)
    .execute(pool)
    .await
    .expect("failed creating fallback academic session");

    session_id
}

#[tokio::test]
async fn schedule_sessions_terms_endpoints_enforce_overlap_and_crud() {
    let pool = get_db_pool().await;
    let client = get_http_client();

    let (token, school_id, _user_id, email) = create_school_admin_and_token(&client, &pool).await;

    let create_session_payload = serde_json::json!({
        "school_id": school_id,
        "name": format!("2026/2027-{}", chrono::Utc::now().timestamp_millis()),
        "start_date": "2026-09-01",
        "end_date": "2027-07-31",
        "is_current": true
    });

    let create_session_resp = client
        .post(&format!("{}{}", constants::API_URL, "/api/auth/schedule/sessions"))
        .bearer_auth(&token)
        .json(&create_session_payload)
        .send()
        .await
        .expect("failed creating session");

    let create_session_status = create_session_resp.status();
    let create_session_body = create_session_resp
        .text()
        .await
        .expect("failed reading create session response body");
    assert!(
        create_session_status.is_success(),
        "create session failed: status={} body={}",
        create_session_status,
        create_session_body
    );

    let created_session: serde_json::Value = serde_json::from_str(&create_session_body)
        .expect("failed parsing session response");

    let session_id = Uuid::parse_str(
        created_session
            .get("id")
            .and_then(|v| v.as_str())
            .expect("missing session id"),
    )
    .expect("invalid session id");

    let create_term_payload = serde_json::json!({
        "school_id": school_id,
        "session_id": session_id,
        "name": "First Term",
        "term_number": 1,
        "start_date": "2026-09-15",
        "end_date": "2026-12-10",
        "is_current": true
    });

    let create_term_resp = client
        .post(&format!("{}{}", constants::API_URL, "/api/auth/schedule/terms"))
        .bearer_auth(&token)
        .json(&create_term_payload)
        .send()
        .await
        .expect("failed creating term");

    let create_term_status = create_term_resp.status();
    let create_term_body = create_term_resp
        .text()
        .await
        .expect("failed reading create term response body");
    assert!(
        create_term_status.is_success(),
        "create term failed: status={} body={}",
        create_term_status,
        create_term_body
    );

    let overlapping_term_payload = serde_json::json!({
        "school_id": school_id,
        "session_id": session_id,
        "name": "Overlap Term",
        "term_number": 2,
        "start_date": "2026-11-01",
        "end_date": "2027-01-15",
        "is_current": false
    });

    let overlap_resp = client
        .post(&format!("{}{}", constants::API_URL, "/api/auth/schedule/terms"))
        .bearer_auth(&token)
        .json(&overlapping_term_payload)
        .send()
        .await
        .expect("failed posting overlap term");

    assert_eq!(overlap_resp.status(), reqwest::StatusCode::BAD_REQUEST);
    let overlap_body = overlap_resp
        .json::<serde_json::Value>()
        .await
        .expect("failed parsing overlap response");
    let overlap_error = overlap_body
        .get("error")
        .and_then(|v| v.as_str())
        .unwrap_or_default()
        .to_lowercase();
    assert!(overlap_error.contains("overlap"), "expected overlap error message");

    let list_terms_resp = client
        .get(&format!(
            "{}{}{}{}?school_id={}",
            constants::API_URL,
            "/api/auth/schedule/sessions/",
            session_id,
            "/terms",
            school_id
        ))
        .bearer_auth(&token)
        .send()
        .await
        .expect("failed listing terms");

    assert!(list_terms_resp.status().is_success(), "list terms failed");
    let terms = list_terms_resp
        .json::<Vec<serde_json::Value>>()
        .await
        .expect("failed parsing terms list");
    assert_eq!(terms.len(), 1, "expected only one non-overlapping term");

    let term_id = Uuid::parse_str(
        terms[0]
            .get("id")
            .and_then(|v| v.as_str())
            .expect("missing term id"),
    )
    .expect("invalid term id");

    let delete_term_resp = client
        .delete(&format!(
            "{}{}{}?school_id={}",
            constants::API_URL,
            "/api/auth/schedule/terms/",
            term_id,
            school_id
        ))
        .bearer_auth(&token)
        .send()
        .await
        .expect("failed deleting term");
    assert!(delete_term_resp.status().is_success(), "delete term failed");

    let delete_session_resp = client
        .delete(&format!(
            "{}{}{}?school_id={}",
            constants::API_URL,
            "/api/auth/schedule/sessions/",
            session_id,
            school_id
        ))
        .bearer_auth(&token)
        .send()
        .await
        .expect("failed deleting session");
    assert!(delete_session_resp.status().is_success(), "delete session failed");

    let _ = db::delete_test_user(&pool, &email).await;
}

#[tokio::test]
async fn schedule_calendar_event_crud_endpoints_work() {
    let pool = get_db_pool().await;
    let client = get_http_client();

    let (token, school_id, _user_id, email) = create_school_admin_and_token(&client, &pool).await;
    let session_id = ensure_active_session_for_school(&pool, school_id).await;

    let create_payload = serde_json::json!({
        "school_id": school_id,
        "session_id": session_id,
        "event_name": "Inter-house Sports",
        "event_type": "EVENT",
        "start_date": "2026-10-10",
        "end_date": "2026-10-10",
        "color": null,
        "description": "Main sports day",
        "is_exam_period": false,
        "is_holiday": false,
        "term_id": null
    });

    let create_resp = client
        .post(&format!("{}{}", constants::API_URL, "/api/auth/schedule/calendar-events"))
        .bearer_auth(&token)
        .json(&create_payload)
        .send()
        .await
        .expect("failed creating calendar event");

    let create_status = create_resp.status();
    let create_body = create_resp
        .text()
        .await
        .expect("failed reading create event response body");
    assert!(
        create_status.is_success(),
        "create calendar event failed: status={} body={}",
        create_status,
        create_body
    );

    let created: serde_json::Value =
        serde_json::from_str(&create_body).expect("failed parsing create event response");

    let event_id = Uuid::parse_str(
        created
            .get("id")
            .and_then(|v| v.as_str())
            .expect("missing event id"),
    )
    .expect("invalid event id");

    let list_resp = client
        .get(&format!(
            "{}{}?school_id={}",
            constants::API_URL,
            "/api/auth/schedule/calendar-events",
            school_id
        ))
        .bearer_auth(&token)
        .send()
        .await
        .expect("failed listing events");
    assert!(list_resp.status().is_success(), "list events failed");

    let get_resp = client
        .get(&format!(
            "{}{}{}?school_id={}",
            constants::API_URL,
            "/api/auth/schedule/calendar-events/",
            event_id,
            school_id
        ))
        .bearer_auth(&token)
        .send()
        .await
        .expect("failed getting event");
    assert!(get_resp.status().is_success(), "get event failed");

    let update_payload = serde_json::json!({
        "school_id": school_id,
        "session_id": session_id,
        "event_name": "Inter-house Sports Updated",
        "event_type": "EVENT",
        "start_date": "2026-10-11",
        "end_date": "2026-10-11",
        "color": null,
        "description": "Updated description",
        "is_exam_period": false,
        "is_holiday": false,
        "term_id": null
    });

    let update_resp = client
        .put(&format!(
            "{}{}{}",
            constants::API_URL,
            "/api/auth/schedule/calendar-events/",
            event_id
        ))
        .bearer_auth(&token)
        .json(&update_payload)
        .send()
        .await
        .expect("failed updating event");
    assert!(update_resp.status().is_success(), "update event failed");

    let delete_resp = client
        .delete(&format!(
            "{}{}{}?school_id={}",
            constants::API_URL,
            "/api/auth/schedule/calendar-events/",
            event_id,
            school_id
        ))
        .bearer_auth(&token)
        .send()
        .await
        .expect("failed deleting event");
    assert!(delete_resp.status().is_success(), "delete event failed");

    let get_deleted_resp = client
        .get(&format!(
            "{}{}{}?school_id={}",
            constants::API_URL,
            "/api/auth/schedule/calendar-events/",
            event_id,
            school_id
        ))
        .bearer_auth(&token)
        .send()
        .await
        .expect("failed getting deleted event");
    assert_eq!(get_deleted_resp.status(), reqwest::StatusCode::NOT_FOUND);

    let _ = db::delete_test_user(&pool, &email).await;
}

#[tokio::test]
async fn schedule_school_timetable_crud_endpoints_work() {
    let pool = get_db_pool().await;
    let client = get_http_client();

    let (token, school_id, _user_id, email) = create_school_admin_and_token(&client, &pool).await;

    let class_id = ensure_class_for_school(&pool, school_id).await;

    let create_payload = serde_json::json!({
        "school_id": school_id,
        "class_id": class_id,
        "days_of_week": ["MONDAY", "WEDNESDAY"],
        "activity_type": "CLASS",
        "start_time": "08:00",
        "end_time": "09:00",
        "title": "Mathematics Period",
        "description": "Morning math session"
    });

    let create_resp = client
        .post(&format!("{}{}", constants::API_URL, "/api/auth/schedule/school-timetable-items"))
        .bearer_auth(&token)
        .json(&create_payload)
        .send()
        .await
        .expect("failed creating timetable item");

    let create_status = create_resp.status();
    let create_body = create_resp
        .text()
        .await
        .expect("failed reading create timetable response body");
    assert!(
        create_status.is_success(),
        "create timetable item failed: status={} body={}",
        create_status,
        create_body
    );

    let success_json: serde_json::Value =
        serde_json::from_str(&create_body).expect("failed parsing create timetable response");
    assert!(success_json.get("success").and_then(|v| v.as_bool()).unwrap_or(false));
    assert_eq!(success_json.get("message").and_then(|v| v.as_str()).unwrap(), "School timetable activity created successfully");
    let created = success_json.get("data").and_then(|v| v.as_array()).expect("missing data array");
    assert_eq!(created.len(), 2, "expected one created item per requested day");

    let item_id = Uuid::parse_str(
        created[0]
            .get("id")
            .and_then(|v| v.as_str())
            .expect("missing item id"),
    )
    .expect("invalid item id");

    let list_resp = client
        .get(&format!(
            "{}{}?school_id={}",
            constants::API_URL,
            "/api/auth/schedule/school-timetable-items",
            school_id
        ))
        .bearer_auth(&token)
        .send()
        .await
        .expect("failed listing timetable items");
    assert!(list_resp.status().is_success(), "list timetable items failed");

    let get_resp = client
        .get(&format!(
            "{}{}{}?school_id={}",
            constants::API_URL,
            "/api/auth/schedule/school-timetable-items/",
            item_id,
            school_id
        ))
        .bearer_auth(&token)
        .send()
        .await
        .expect("failed getting timetable item");
    assert!(get_resp.status().is_success(), "get timetable item failed");

    let update_payload = serde_json::json!({
        "school_id": school_id,
        "class_id": class_id,
        "day_of_week": "TUESDAY",
        "activity_type": "CLASS",
        "start_time": "09:00",
        "end_time": "10:00",
        "title": "Updated Mathematics Period",
        "description": "Updated timetable entry"
    });

    let update_resp = client
        .put(&format!(
            "{}{}{}",
            constants::API_URL,
            "/api/auth/schedule/school-timetable-items/",
            item_id
        ))
        .bearer_auth(&token)
        .json(&update_payload)
        .send()
        .await
        .expect("failed updating timetable item");
    assert!(update_resp.status().is_success(), "update timetable item failed");
    let update_body = update_resp.text().await.expect("failed reading update body");
    let update_json: serde_json::Value = serde_json::from_str(&update_body).expect("failed parsing update response");
    assert!(update_json.get("success").and_then(|v| v.as_bool()).unwrap_or(false));
    assert_eq!(update_json.get("message").and_then(|v| v.as_str()).unwrap(), "School timetable activity updated successfully");

    let delete_resp = client
        .delete(&format!(
            "{}{}{}?school_id={}",
            constants::API_URL,
            "/api/auth/schedule/school-timetable-items/",
            item_id,
            school_id
        ))
        .bearer_auth(&token)
        .send()
        .await
        .expect("failed deleting timetable item");
    assert!(delete_resp.status().is_success(), "delete timetable item failed");
    let delete_body = delete_resp.text().await.expect("failed reading delete body");
    let delete_json: serde_json::Value = serde_json::from_str(&delete_body).expect("failed parsing delete response");
    assert!(delete_json.get("success").and_then(|v| v.as_bool()).unwrap_or(false));
    assert_eq!(delete_json.get("message").and_then(|v| v.as_str()).unwrap(), "School timetable activity deleted successfully");

    let get_deleted_resp = client
        .get(&format!(
            "{}{}{}?school_id={}",
            constants::API_URL,
            "/api/auth/schedule/school-timetable-items/",
            item_id,
            school_id
        ))
        .bearer_auth(&token)
        .send()
        .await
        .expect("failed getting deleted timetable item");
    assert_eq!(get_deleted_resp.status(), reqwest::StatusCode::NOT_FOUND);

    let _ = db::delete_test_user(&pool, &email).await;
}

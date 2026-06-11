#[path = "../common/mod.rs"]
mod common;

use actix_web::{web, App, HttpServer};
use common::*;
use school_backend::{db::Database, handlers, middleware::AuthMiddleware};
use std::net::TcpListener;
use uuid::Uuid;

#[derive(Debug, serde::Deserialize)]
struct SignInResponseBody {
    access_token: String,
}

/// Sign up a school admin, activate the user, sign in, and return (token, school_id).
async fn create_school_admin_and_token(
    client: &reqwest::Client,
    pool: &sqlx::PgPool,
    api_url: &str,
) -> (String, Uuid) {
    let email = generate_test_email("scoring-admin");
    let phone = generate_test_phone();
    let password = "SecurePassword123!";

    let signup_request = build_signup_request(
        &email,
        password,
        "Scoring",
        "Admin",
        &phone,
        "SCHOOL_ADMIN",
        None,
    );

    let signup_response = signup_expect_success(client, api_url, signup_request).await;
    let user_id = Uuid::parse_str(&signup_response.user_id).expect("invalid user_id");
    let school_id = Uuid::parse_str(&signup_response.school_id).expect("invalid school_id");

    sqlx::query(
        "UPDATE users SET is_active = true, is_approved = true, status = 'ACTIVE' WHERE id = $1",
    )
    .bind(user_id)
    .execute(pool)
    .await
    .expect("failed to activate admin user");

    let signin_resp = client
        .post(&format!("{}/api/auth/sign-in", api_url))
        .json(&serde_json::json!({ "email": email, "password": password }))
        .send()
        .await
        .expect("failed to send sign-in request");

    assert!(signin_resp.status().is_success(), "sign-in should succeed");

    let body: SignInResponseBody = signin_resp
        .json()
        .await
        .expect("failed to parse sign-in body");

    (body.access_token, school_id)
}

async fn spawn_test_api() -> String {
    let listener = TcpListener::bind("127.0.0.1:0").expect("failed to bind test listener");
    let addr = listener.local_addr().expect("failed to read local addr");
    let (ready_tx, ready_rx) = std::sync::mpsc::channel();
    let database_url = std::env::var("DATABASE_URL")
        .unwrap_or_else(|_| "postgres://postgres:password@localhost:5432/myschool".to_string());

    std::thread::spawn(move || {
        actix_web::rt::System::new().block_on(async move {
            let db = Database::new(&database_url)
                .await
                .expect("failed to initialize test database");

            let server = HttpServer::new(move || {
                App::new()
                    .app_data(web::Data::new(db.clone()))
                    .wrap(AuthMiddleware::new())
                    .service(
                        web::scope("/api")
                            .service(
                                web::scope("/auth")
                                    .route("/sign-up", web::post().to(handlers::auth::sign_up))
                                    .route("/sign-in", web::post().to(handlers::auth::sign_in))
                                    .configure(handlers::assessment::scoring_scheme::configure),
                            ),
                    )
            })
            .listen(listener)
            .expect("failed to attach listener")
            .run();

            ready_tx.send(()).expect("failed to signal test server ready");
            let _ = server.await;
        });
    });

    ready_rx.recv().expect("failed waiting for test server");
    format!("http://{}", addr)
}

async fn signup_expect_success(
    client: &reqwest::Client,
    api_url: &str,
    request_body: serde_json::Value,
) -> common::SignUpResponse {
    let response = client
        .post(&format!("{}{}", api_url, constants::SIGNUP_ENDPOINT))
        .json(&request_body)
        .send()
        .await
        .expect("Failed to make signup request");

    assert!(
        response.status().is_success(),
        "Expected successful 2xx status, got {}",
        response.status()
    );

    response
        .json::<common::SignUpResponse>()
        .await
        .expect("Failed to parse signup response")
}

/// Return an existing active class for the school, or create a minimal one.
async fn ensure_class(pool: &sqlx::PgPool, school_id: Uuid) -> Uuid {
    if let Some(id) = sqlx::query_scalar::<_, Uuid>(
        "SELECT id FROM classes WHERE school_id = $1 AND is_active = true \
         ORDER BY created_at ASC LIMIT 1",
    )
    .bind(school_id)
    .fetch_optional(pool)
    .await
    .expect("failed querying classes")
    {
        return id;
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
            $1, $2, $3, $4, NULL, 0,
            NULL, NULL, NULL, NULL, NULL,
            NULL, NULL, NOW(), NOW(), true
        )
        "#,
    )
    .bind(class_id)
    .bind(school_id)
    .bind(format!("SC-{}", suffix))
    .bind(format!("Scoring Test Class {}", suffix))
    .execute(pool)
    .await
    .expect("failed creating test class");

    class_id
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

/// POST /api/auth/scoring-schemes — happy path with a single class
#[tokio::test]
async fn create_scoring_scheme_endpoint_creates_single_class_scheme() {
    let client = reqwest::Client::new();
    let pool = get_db_pool().await;
    let api_url = spawn_test_api().await;

    let (token, school_id) = create_school_admin_and_token(&client, &pool, &api_url).await;
    let class_id = ensure_class(&pool, school_id).await;

    let payload = serde_json::json!({
        "school_id": school_id,
        "class_ids": [class_id],
        "academic_session_id": null,
        "term_id": null,
        "scoring_scheme": [
            { "id": 1, "name": "CA", "alias": "ca", "max": 40 },
            { "id": 2, "name": "Exam", "alias": "exam", "max": 60 }
        ],
        "notes": "Created by endpoint test"
    });

    let resp = client
        .post(&format!("{}/api/auth/scoring-schemes", api_url))
        .bearer_auth(&token)
        .json(&payload)
        .send()
        .await
        .expect("failed to send create scoring scheme request");

    let status = resp.status();
    let body = resp.text().await.expect("failed to read response body");

    assert_eq!(status, 201, "expected 201 Created, body: {}", body);

    let json: serde_json::Value =
        serde_json::from_str(&body).expect("response is not valid JSON");

    // The handler returns Vec<ScoringScheme>; there should be exactly one entry.
    let arr = json.as_array().expect("response should be a JSON array");
    assert_eq!(arr.len(), 1, "expected one scheme for one class_id");

    let scheme = &arr[0];
    assert_eq!(
        scheme.get("school_id").and_then(|v| v.as_str()),
        Some(school_id.to_string().as_str()),
        "scheme school_id should match"
    );
    assert_eq!(
        scheme.get("class_id").and_then(|v| v.as_str()),
        Some(class_id.to_string().as_str()),
        "scheme class_id should match"
    );
    assert!(
        scheme.get("id").and_then(|v| v.as_str()).is_some(),
        "scheme should have an id"
    );

    println!("✓ create_scoring_scheme_endpoint_creates_single_class_scheme passed");
}

/// POST /api/auth/scoring-schemes — multiple class_ids creates one scheme per class
#[tokio::test]
async fn create_scoring_scheme_endpoint_creates_multi_class_schemes() {
    let client = reqwest::Client::new();
    let pool = get_db_pool().await;
    let api_url = spawn_test_api().await;

    let (token, school_id) = create_school_admin_and_token(&client, &pool, &api_url).await;

    // Create two distinct classes
    let class_a = ensure_class(&pool, school_id).await;

    let class_b = Uuid::new_v4();
    let suffix_b = &class_b.to_string()[..8];
    sqlx::query(
        r#"
        INSERT INTO classes (
            id, school_id, class_code, class_name, classroom_location, current_enrollment,
            grade_level, max_capacity, scoring_scheme, department_id, track_id,
            class_staff_id, term, created_at, updated_at, is_active
        ) VALUES (
            $1, $2, $3, $4, NULL, 0,
            NULL, NULL, NULL, NULL, NULL,
            NULL, NULL, NOW(), NOW(), true
        )
        "#,
    )
    .bind(class_b)
    .bind(school_id)
    .bind(format!("SC2-{}", suffix_b))
    .bind(format!("Scoring Test Class B {}", suffix_b))
    .execute(&pool)
    .await
    .expect("failed creating second test class");

    let payload = serde_json::json!({
        "school_id": school_id,
        "class_ids": [class_a, class_b],
        "academic_session_id": null,
        "term_id": null,
        "scoring_scheme": [
            { "id": 1, "name": "CA", "alias": "ca", "max": 30 },
            { "id": 2, "name": "Mid", "alias": "mid", "max": 20 },
            { "id": 3, "name": "Exam", "alias": "exam", "max": 50 }
        ],
        "notes": null
    });

    let resp = client
        .post(&format!("{}/api/auth/scoring-schemes", api_url))
        .bearer_auth(&token)
        .json(&payload)
        .send()
        .await
        .expect("failed to send create scoring scheme request");

    let status = resp.status();
    let body = resp.text().await.expect("failed to read response body");

    assert_eq!(status, 201, "expected 201 Created, body: {}", body);

    let arr: Vec<serde_json::Value> =
        serde_json::from_str(&body).expect("response should be a JSON array");
    assert_eq!(arr.len(), 2, "expected two schemes for two class_ids");

    println!("✓ create_scoring_scheme_endpoint_creates_multi_class_schemes passed");
}

/// POST /api/auth/scoring-schemes — components not summing to 100 → 400/422 error
#[tokio::test]
async fn create_scoring_scheme_endpoint_rejects_invalid_total() {
    let client = reqwest::Client::new();
    let pool = get_db_pool().await;
    let api_url = spawn_test_api().await;

    let (token, school_id) = create_school_admin_and_token(&client, &pool, &api_url).await;
    let class_id = ensure_class(&pool, school_id).await;

    let payload = serde_json::json!({
        "school_id": school_id,
        "class_ids": [class_id],
        "academic_session_id": null,
        "term_id": null,
        "scoring_scheme": [
            { "id": 1, "name": "CA", "alias": "ca", "max": 40 },
            { "id": 2, "name": "Exam", "alias": "exam", "max": 40 }
            // total = 80, not 100
        ],
        "notes": null
    });

    let resp = client
        .post(&format!("{}/api/auth/scoring-schemes", api_url))
        .bearer_auth(&token)
        .json(&payload)
        .send()
        .await
        .expect("failed to send create scoring scheme request");

    assert!(
        resp.status().is_client_error(),
        "expected 4xx for invalid total, got {}",
        resp.status()
    );

    println!("✓ create_scoring_scheme_endpoint_rejects_invalid_total passed");
}

/// POST /api/auth/scoring-schemes — no class_ids → 400 error
#[tokio::test]
async fn create_scoring_scheme_endpoint_rejects_empty_class_ids() {
    let client = reqwest::Client::new();
    let pool = get_db_pool().await;
    let api_url = spawn_test_api().await;

    let (token, school_id) = create_school_admin_and_token(&client, &pool, &api_url).await;

    let payload = serde_json::json!({
        "school_id": school_id,
        "class_ids": [],
        "academic_session_id": null,
        "term_id": null,
        "scoring_scheme": [
            { "id": 1, "name": "CA", "alias": "ca", "max": 40 },
            { "id": 2, "name": "Exam", "alias": "exam", "max": 60 }
        ],
        "notes": null
    });

    let resp = client
        .post(&format!("{}/api/auth/scoring-schemes", api_url))
        .bearer_auth(&token)
        .json(&payload)
        .send()
        .await
        .expect("failed to send create scoring scheme request");

    assert!(
        resp.status().is_client_error(),
        "expected 4xx for empty class_ids, got {}",
        resp.status()
    );

    println!("✓ create_scoring_scheme_endpoint_rejects_empty_class_ids passed");
}

/// PUT /api/auth/scoring-schemes/{id}?school_id=... — happy path update
#[tokio::test]
async fn update_scoring_scheme_endpoint_updates_components() {
    let client = reqwest::Client::new();
    let pool = get_db_pool().await;
    let api_url = spawn_test_api().await;

    let (token, school_id) = create_school_admin_and_token(&client, &pool, &api_url).await;
    let class_id = ensure_class(&pool, school_id).await;

    // 1. Create a scheme first
    let create_payload = serde_json::json!({
        "school_id": school_id,
        "class_ids": [class_id],
        "academic_session_id": null,
        "term_id": null,
        "scoring_scheme": [
            { "id": 1, "name": "CA", "alias": "ca", "max": 40 },
            { "id": 2, "name": "Exam", "alias": "exam", "max": 60 }
        ],
        "notes": null
    });

    let create_resp = client
        .post(&format!("{}/api/auth/scoring-schemes", api_url))
        .bearer_auth(&token)
        .json(&create_payload)
        .send()
        .await
        .expect("failed to create scheme");

    assert_eq!(create_resp.status(), 201, "create should succeed");

    let create_body: Vec<serde_json::Value> =
        create_resp.json().await.expect("failed parsing create response");
    let scheme_id = create_body[0]
        .get("id")
        .and_then(|v| v.as_str())
        .expect("missing scheme id");

    // 2. Update the scheme with new components
    let update_payload = serde_json::json!({
        "scoring_scheme": [
            { "id": 1, "name": "CA", "alias": "ca", "max": 20 },
            { "id": 2, "name": "Mid Term", "alias": "mid", "max": 20 },
            { "id": 3, "name": "Exam", "alias": "exam", "max": 60 }
        ]
    });

    let update_resp = client
        .put(&format!(
            "{}/api/auth/scoring-schemes/{}?school_id={}",
            api_url,
            scheme_id,
            school_id
        ))
        .bearer_auth(&token)
        .json(&update_payload)
        .send()
        .await
        .expect("failed to send update scoring scheme request");

    let update_status = update_resp.status();
    let update_body = update_resp
        .text()
        .await
        .expect("failed to read update response body");

    assert_eq!(
        update_status, 200,
        "expected 200 OK from update, body: {}",
        update_body
    );

    let updated: serde_json::Value =
        serde_json::from_str(&update_body).expect("update response is not valid JSON");

    assert_eq!(
        updated.get("id").and_then(|v| v.as_str()),
        Some(scheme_id),
        "updated scheme id should match"
    );

    // Verify the scheme was actually stored with the new components
    let components = updated
        .get("scoring_scheme")
        .expect("scoring_scheme field missing");
    let components_arr = components.as_array().expect("scoring_scheme should be array");
    assert_eq!(components_arr.len(), 3, "updated scheme should have 3 components");

    println!("✓ update_scoring_scheme_endpoint_updates_components passed");
}

/// PUT /api/auth/scoring-schemes/{id}?school_id=... — invalid total → error
#[tokio::test]
async fn update_scoring_scheme_endpoint_rejects_invalid_total() {
    let client = reqwest::Client::new();
    let pool = get_db_pool().await;
    let api_url = spawn_test_api().await;

    let (token, school_id) = create_school_admin_and_token(&client, &pool, &api_url).await;
    let class_id = ensure_class(&pool, school_id).await;

    // Create a valid scheme first
    let create_payload = serde_json::json!({
        "school_id": school_id,
        "class_ids": [class_id],
        "academic_session_id": null,
        "term_id": null,
        "scoring_scheme": [
            { "id": 1, "name": "CA", "alias": "ca", "max": 40 },
            { "id": 2, "name": "Exam", "alias": "exam", "max": 60 }
        ],
        "notes": null
    });

    let create_resp = client
        .post(&format!("{}/api/auth/scoring-schemes", api_url))
        .bearer_auth(&token)
        .json(&create_payload)
        .send()
        .await
        .expect("failed to create scheme");

    assert_eq!(create_resp.status(), 201, "create should succeed");

    let create_body: Vec<serde_json::Value> =
        create_resp.json().await.expect("failed parsing create response");
    let scheme_id = create_body[0]
        .get("id")
        .and_then(|v| v.as_str())
        .expect("missing scheme id");

    // Attempt update with components that don't sum to 100
    let update_payload = serde_json::json!({
        "scoring_scheme": [
            { "id": 1, "name": "CA", "alias": "ca", "max": 30 }
            // total = 30, not 100
        ]
    });

    let update_resp = client
        .put(&format!(
            "{}/api/auth/scoring-schemes/{}?school_id={}",
            api_url,
            scheme_id,
            school_id
        ))
        .bearer_auth(&token)
        .json(&update_payload)
        .send()
        .await
        .expect("failed to send update scoring scheme request");

    assert!(
        update_resp.status().is_client_error(),
        "expected 4xx for invalid total on update, got {}",
        update_resp.status()
    );

    println!("✓ update_scoring_scheme_endpoint_rejects_invalid_total passed");
}

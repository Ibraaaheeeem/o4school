#[path = "common/mod.rs"]
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
        .expect("failed to make signup request");

    assert!(response.status().is_success(), "expected signup to succeed, got {}", response.status());

    response
        .json::<common::SignUpResponse>()
        .await
        .expect("failed to parse signup response")
}

async fn spawn_test_api() -> String {
    let listener = TcpListener::bind("127.0.0.1:0").expect("failed to bind listener");
    let addr = listener.local_addr().expect("failed to read addr");
    let database_url = std::env::var("DATABASE_URL")
        .unwrap_or_else(|_| "postgres://postgres:password@localhost:5432/myschool".to_string());
    let (ready_tx, ready_rx) = std::sync::mpsc::channel();

    std::thread::spawn(move || {
        actix_web::rt::System::new().block_on(async move {
            let db = Database::new(&database_url)
                .await
                .expect("failed to initialize database");

            let server = HttpServer::new(move || {
                App::new()
                    .app_data(web::Data::new(db.clone()))
                    .wrap(AuthMiddleware::new())
                    .service(
                        web::scope("/api").service(
                            web::scope("/auth")
                                .route("/sign-up", web::post().to(handlers::auth::sign_up))
                                .route("/sign-in", web::post().to(handlers::auth::sign_in))
                                .configure(handlers::assessment::configure),
                        ),
                    )
            })
            .listen(listener)
            .expect("failed to attach listener")
            .run();

            ready_tx.send(()).expect("failed to signal server ready");
            let _ = server.await;
        });
    });

    ready_rx.recv().expect("server did not start");
    format!("http://{}", addr)
}

async fn create_school_admin_and_token(
    client: &reqwest::Client,
    api_url: &str,
    pool: &sqlx::PgPool,
) -> (String, Uuid, Uuid) {
    let email = generate_test_email("exam-admin");
    let phone = generate_test_phone();
    let password = "SecurePassword123!";

    let signup_request = build_signup_request(
        &email,
        password,
        "Exam",
        "Admin",
        &phone,
        "SCHOOL_ADMIN",
        None,
    );

    let signup_response = signup_expect_success(client, api_url, signup_request).await;
    let user_id = Uuid::parse_str(&signup_response.user_id).expect("invalid user id");
    let school_id = Uuid::parse_str(&signup_response.school_id).expect("invalid school id");

    sqlx::query("UPDATE users SET is_active = true, is_approved = true, status = 'ACTIVE' WHERE id = $1")
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

    (body.access_token, user_id, school_id)
}

async fn ensure_class(pool: &sqlx::PgPool, school_id: Uuid, suffix_seed: &str) -> Uuid {
    let class_id = Uuid::new_v4();
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
    .bind(format!("EX-{}", suffix_seed))
    .bind(format!("Examination Class {}", suffix_seed))
    .execute(pool)
    .await
    .expect("failed to insert class");

    class_id
}

async fn ensure_subject(pool: &sqlx::PgPool, _school_id: Uuid, suffix_seed: &str) -> Uuid {
    if let Some(existing_id) = sqlx::query_scalar::<_, Uuid>(
        "SELECT id FROM subjects WHERE is_active = true ORDER BY created_at ASC LIMIT 1",
    )
    .fetch_optional(pool)
    .await
    .expect("failed querying subjects")
    {
        return existing_id;
    }

    let subject_id = Uuid::new_v4();
    let columns: Vec<String> = sqlx::query_scalar(
        "SELECT column_name FROM information_schema.columns WHERE table_name = 'subjects' ORDER BY ordinal_position",
    )
    .fetch_all(pool)
    .await
    .expect("failed querying subject columns");

    let has_school_id = columns.iter().any(|column| column == "school_id");
    let has_name = columns.iter().any(|column| column == "name");
    let has_code = columns.iter().any(|column| column == "code");
    let has_subject_name = columns.iter().any(|column| column == "subject_name");
    let has_subject_code = columns.iter().any(|column| column == "subject_code");

    if has_school_id && has_name && has_code {
        sqlx::query(
            r#"
            INSERT INTO subjects (
                id, school_id, name, code, description, credit_hours, created_at, updated_at, is_active
            ) VALUES (
                $1, $2, $3, $4, $5, $6, NOW(), NOW(), true
            )
            "#,
        )
        .bind(subject_id)
        .bind(_school_id)
        .bind(format!("Examination Subject {}", suffix_seed))
        .bind(format!("EXS-{}", suffix_seed))
        .bind(Some("Exam endpoint test subject".to_string()))
        .bind(3_i32)
        .execute(pool)
        .await
        .expect("failed to insert subject");
    } else if has_name && has_code {
        sqlx::query(
            r#"
            INSERT INTO subjects (
                id, name, code, description, credit_hours, created_at, updated_at, is_active
            ) VALUES (
                $1, $2, $3, $4, $5, NOW(), NOW(), true
            )
            "#,
        )
        .bind(subject_id)
        .bind(format!("Examination Subject {}", suffix_seed))
        .bind(format!("EXS-{}", suffix_seed))
        .bind(Some("Exam endpoint test subject".to_string()))
        .bind(3_i32)
        .execute(pool)
        .await
        .expect("failed to insert subject");
    } else if has_subject_name && has_subject_code {
        sqlx::query(
            r#"
            INSERT INTO subjects (
                id, subject_name, subject_code, description, credit_hours, created_at, updated_at, is_active
            ) VALUES (
                $1, $2, $3, $4, $5, NOW(), NOW(), true
            )
            "#,
        )
        .bind(subject_id)
        .bind(format!("Examination Subject {}", suffix_seed))
        .bind(format!("EXS-{}", suffix_seed))
        .bind(Some("Exam endpoint test subject".to_string()))
        .bind(3_i32)
        .execute(pool)
        .await
        .expect("failed to insert subject");
    } else {
        panic!("unsupported subjects table schema: {:?}", columns);
    }

    subject_id
}

async fn ensure_academic_context(pool: &sqlx::PgPool, school_id: Uuid) -> (Uuid, Uuid) {
    let session_id = if let Some(id) = sqlx::query_scalar::<_, Uuid>(
        "SELECT id FROM academic_sessions WHERE school_id = $1 AND is_active = true ORDER BY created_at ASC LIMIT 1",
    )
    .bind(school_id)
    .fetch_optional(pool)
    .await
    .expect("failed to query academic session")
    {
        id
    } else {
        let id = Uuid::new_v4();
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
        .bind(Some("Exam endpoint session".to_string()))
        .bind(format!("Session-{}", &id.to_string()[..8]))
        .bind("2026/2027")
        .bind(chrono::NaiveDate::from_ymd_opt(2026, 9, 1).expect("invalid date"))
        .bind(Some("ACTIVE".to_string()))
        .execute(pool)
        .await
        .expect("failed to insert academic session");
        id
    };

    let term_id = if let Some(id) = sqlx::query_scalar::<_, Uuid>(
        "SELECT id FROM terms WHERE school_id = $1 AND academic_session_id = $2 AND is_active = true ORDER BY created_at ASC LIMIT 1",
    )
    .bind(school_id)
    .bind(session_id)
    .fetch_optional(pool)
    .await
    .expect("failed to query term")
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
        .bind(Some("Exam endpoint term".to_string()))
        .bind(Some(chrono::NaiveDate::from_ymd_opt(2026, 12, 20).expect("invalid date")))
        .bind(chrono::NaiveDate::from_ymd_opt(2026, 9, 10).expect("invalid date"))
        .bind(Some("ACTIVE".to_string()))
        .bind("FIRST_TERM")
        .bind(session_id)
        .bind(Some(1_i32))
        .bind(Some(1_i32))
        .execute(pool)
        .await
        .expect("failed to insert term");
        id
    };

    (session_id, term_id)
}

#[tokio::test]
async fn create_examinations_endpoint_creates_multiple_rows() {
    let client = reqwest::Client::new();
    let pool = get_db_pool().await;
    let api_url = spawn_test_api().await;

    let (_token, admin_user_id, school_id) = create_school_admin_and_token(&client, &api_url, &pool).await;
    let (session_id, term_id) = ensure_academic_context(&pool, school_id).await;
    let class_a = ensure_class(&pool, school_id, "A").await;
    let class_b = ensure_class(&pool, school_id, "B").await;
    let subject_a = ensure_subject(&pool, school_id, "A").await;
    let subject_b = ensure_subject(&pool, school_id, "B").await;

    let payload = serde_json::json!({
        "school_id": school_id,
        "created_by": admin_user_id,
        "class_ids": [class_a, class_b],
        "subject_ids": [subject_a, subject_b],
        "duration_minutes": 90,
        "end_time": null,
        "exam_type": "THEORY",
        "is_published": true,
        "start_time": null,
        "title": "Mid Term Examination",
        "total_marks": 100,
        "is_online": false,
        "session_id": session_id,
        "term_id": term_id
    });

    let resp = client
        .post(&format!("{}/api/auth/examinations", api_url))
        .json(&payload)
        .send()
        .await
        .expect("failed to create examinations");

    assert_eq!(resp.status(), 201, "create should return 201");
    let rows: Vec<serde_json::Value> = resp.json().await.expect("failed to parse response");
    assert_eq!(rows.len(), 4, "2 classes x 2 subjects should create 4 exams");
}

#[tokio::test]
async fn update_examinations_endpoint_updates_multiple_rows() {
    let client = reqwest::Client::new();
    let pool = get_db_pool().await;
    let api_url = spawn_test_api().await;

    let (_token, admin_user_id, school_id) = create_school_admin_and_token(&client, &api_url, &pool).await;
    let (session_id, term_id) = ensure_academic_context(&pool, school_id).await;
    let class_id = ensure_class(&pool, school_id, "U").await;
    let subject_one = ensure_subject(&pool, school_id, "U1").await;
    let subject_two = ensure_subject(&pool, school_id, "U2").await;

    let create_payload = serde_json::json!({
        "school_id": school_id,
        "created_by": admin_user_id,
        "class_ids": [class_id],
        "subject_ids": [subject_one, subject_two],
        "duration_minutes": 60,
        "end_time": null,
        "exam_type": "PRACTICAL",
        "is_published": false,
        "start_time": null,
        "title": "Original Exam",
        "total_marks": 50,
        "is_online": true,
        "session_id": session_id,
        "term_id": term_id
    });

    let create_resp = client
        .post(&format!("{}/api/auth/examinations", api_url))
        .json(&create_payload)
        .send()
        .await
        .expect("failed to create examinations");
    assert_eq!(create_resp.status(), 201, "create should succeed");

    let created: Vec<serde_json::Value> = create_resp.json().await.expect("failed parsing create response");
    let examination_ids: Vec<Uuid> = created
        .iter()
        .map(|item| {
            Uuid::parse_str(item.get("id").and_then(|v| v.as_str()).expect("missing exam id"))
                .expect("invalid exam id")
        })
        .take(2)
        .collect();

    let update_payload = serde_json::json!({
        "school_id": school_id,
        "examination_ids": examination_ids,
        "duration_minutes": 75,
        "end_time": null,
        "exam_type": "THEORY",
        "is_published": true,
        "start_time": null,
        "title": "Updated Exam",
        "total_marks": 100,
        "is_online": false,
        "session_id": session_id,
        "term_id": term_id
    });

    let update_resp = client
        .put(&format!("{}/api/auth/examinations", api_url))
        .json(&update_payload)
        .send()
        .await
        .expect("failed to update examinations");

    assert_eq!(update_resp.status(), 200, "update should return 200");
    let updated: Vec<serde_json::Value> = update_resp.json().await.expect("failed parsing update response");
    assert_eq!(updated.len(), 2, "two exams should have been updated");

    for item in updated {
        assert_eq!(item.get("title").and_then(|v| v.as_str()), Some("Updated Exam"));
        assert_eq!(item.get("exam_type").and_then(|v| v.as_str()), Some("THEORY"));
        assert_eq!(item.get("is_online").and_then(|v| v.as_bool()), Some(false));
    }
}

#[path = "../common/mod.rs"]
mod common;

use actix_web::{web, App, HttpServer};
use common::*;
use school_backend::{db::Database, handlers, middleware::AuthMiddleware};
use std::collections::HashMap;
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

    assert!(
        response.status().is_success(),
        "expected signup to succeed, got {}",
        response.status()
    );

    response
        .json::<common::SignUpResponse>()
        .await
        .expect("failed to parse signup response")
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
                        web::scope("/api").service(
                            web::scope("/auth")
                                .route("/sign-up", web::post().to(handlers::auth::sign_up))
                                .route("/sign-in", web::post().to(handlers::auth::sign_in))
                                .configure(handlers::school_package::configure),
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

    ready_rx.recv().expect("failed waiting for server startup");
    format!("http://{}", addr)
}

async fn create_school_admin_and_token(
    client: &reqwest::Client,
    pool: &sqlx::PgPool,
    api_url: &str,
) -> (String, Uuid) {
    let email = generate_test_email("school-package-admin");
    let phone = generate_test_phone();
    let password = "SecurePassword123!";

    let signup_request = build_signup_request(
        &email,
        password,
        "School",
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
        .expect("failed to activate school admin user");

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

async fn ensure_subject(pool: &sqlx::PgPool, school_id: Uuid, suffix_seed: &str) -> Uuid {
    let subject_id = Uuid::new_v4();
    let unique = &subject_id.to_string()[..8];
    let subject_name = format!("School Package Subject {}-{}", suffix_seed, unique);
    let subject_code = format!("SP-{}", &subject_id.to_string()[..8]);
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
        .bind(school_id)
        .bind(subject_name.clone())
        .bind(subject_code.clone())
        .bind(Some("School package test subject".to_string()))
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
        .bind(subject_name.clone())
        .bind(subject_code.clone())
        .bind(Some("School package test subject".to_string()))
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
        .bind(subject_name)
        .bind(subject_code)
        .bind(Some("School package test subject".to_string()))
        .bind(3_i32)
        .execute(pool)
        .await
        .expect("failed to insert subject");
    } else {
        panic!("unsupported subjects table schema: {:?}", columns);
    }

    subject_id
}

async fn ensure_subject_with_id(
    pool: &sqlx::PgPool,
    school_id: Uuid,
    subject_id: Uuid,
    subject_name: &str,
    subject_code: &str,
) {
    let exists = sqlx::query_scalar::<_, bool>(
        "SELECT EXISTS(SELECT 1 FROM subjects WHERE id = $1 AND is_active = true)",
    )
    .bind(subject_id)
    .fetch_one(pool)
    .await
    .expect("failed checking subject existence");

    if exists {
        return;
    }

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
        .bind(school_id)
        .bind(subject_name)
        .bind(subject_code)
        .bind(Some("School package fixed subject".to_string()))
        .bind(3_i32)
        .execute(pool)
        .await
        .expect("failed to insert fixed-id subject");
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
        .bind(subject_name)
        .bind(subject_code)
        .bind(Some("School package fixed subject".to_string()))
        .bind(3_i32)
        .execute(pool)
        .await
        .expect("failed to insert fixed-id subject");
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
        .bind(subject_name)
        .bind(subject_code)
        .bind(Some("School package fixed subject".to_string()))
        .bind(3_i32)
        .execute(pool)
        .await
        .expect("failed to insert fixed-id subject");
    } else {
        panic!("unsupported subjects table schema: {:?}", columns);
    }
}

async fn ensure_school_subject(pool: &sqlx::PgPool, school_id: Uuid, subject_id: Uuid) -> Uuid {
    let school_subject_id = Uuid::new_v4();
    sqlx::query(
        r#"
        INSERT INTO school_subjects (id, created_at, is_active, updated_at, school_id, subject_id)
        VALUES ($1, NOW(), true, NOW(), $2, $3)
        ON CONFLICT (school_id, subject_id)
        DO UPDATE SET is_active = true, updated_at = NOW()
        "#,
    )
    .bind(school_subject_id)
    .bind(school_id)
    .bind(subject_id)
    .execute(pool)
    .await
    .expect("failed to upsert school_subject");

    sqlx::query_scalar::<_, Uuid>(
        "SELECT id FROM school_subjects WHERE school_id = $1 AND subject_id = $2 AND is_active = true LIMIT 1",
    )
    .bind(school_id)
    .bind(subject_id)
    .fetch_one(pool)
    .await
    .expect("failed to fetch school_subject id")
}

async fn create_track_department_class(
    client: &reqwest::Client,
    api_url: &str,
    token: &str,
    school_id: Uuid,
    suffix_seed: &str,
) -> (Uuid, Uuid, Uuid) {
    let unique = &Uuid::new_v4().to_string()[..8];
    let track_name = format!("Science Track {}-{}", suffix_seed, unique);
    let department_name = format!("Science Department {}-{}", suffix_seed, unique);
    let class_name = format!("JSS 1A {}-{}", suffix_seed, unique);
    let class_code = format!("JSS1A-{}", unique);

    let track_payload = serde_json::json!({
        "school_id": school_id,
        "name": track_name,
        "description": "Track created in integration test"
    });

    let track_resp = client
        .post(&format!("{}/api/auth/school/tracks", api_url))
        .bearer_auth(token)
        .json(&track_payload)
        .send()
        .await
        .expect("failed to create track");

    let track_status = track_resp.status();
    let track_body = track_resp.text().await.expect("failed to read track body");
    assert_eq!(
        track_status,
        201,
        "track creation should succeed, body: {}",
        track_body
    );
    let track_json: serde_json::Value =
        serde_json::from_str(&track_body).expect("invalid track response");
    let track_id = Uuid::parse_str(
        track_json
            .get("id")
            .and_then(|v| v.as_str())
            .expect("missing track id"),
    )
    .expect("invalid track id");

    let department_payload = serde_json::json!({
        "school_id": school_id,
        "track_id": track_id,
        "name": department_name,
        "description": "Department created in integration test"
    });

    let department_resp = client
        .post(&format!("{}/api/auth/school/departments", api_url))
        .bearer_auth(token)
        .json(&department_payload)
        .send()
        .await
        .expect("failed to create department");

    let department_status = department_resp.status();
    let department_body = department_resp
        .text()
        .await
        .expect("failed to read department body");
    assert_eq!(
        department_status,
        201,
        "department creation should succeed, body: {}",
        department_body
    );
    let department_json: serde_json::Value =
        serde_json::from_str(&department_body).expect("invalid department response");
    let department_id = Uuid::parse_str(
        department_json
            .get("id")
            .and_then(|v| v.as_str())
            .expect("missing department id"),
    )
    .expect("invalid department id");

    let class_payload = serde_json::json!({
        "school_id": school_id,
        "department_id": department_id,
        "class_name": class_name,
        "class_code": class_code,
        "classroom_location": "Block A",
        "current_enrollment": 0,
        "grade_level": 1,
        "max_capacity": 40,
        "scoring_scheme": "PERCENTAGE",
        "class_staff_id": null,
        "term": null
    });

    let class_resp = client
        .post(&format!("{}/api/auth/school/classes", api_url))
        .bearer_auth(token)
        .json(&class_payload)
        .send()
        .await
        .expect("failed to create class");

    let class_status = class_resp.status();
    let class_body = class_resp.text().await.expect("failed to read class body");
    assert_eq!(
        class_status,
        201,
        "class creation should succeed, body: {}",
        class_body
    );
    let class_json: serde_json::Value =
        serde_json::from_str(&class_body).expect("invalid class response");
    let class_id = Uuid::parse_str(
        class_json
            .get("id")
            .and_then(|v| v.as_str())
            .expect("missing class id"),
    )
    .expect("invalid class id");

    (track_id, department_id, class_id)
}

#[tokio::test]
async fn update_school_data_endpoint_updates_school_profile() {
    let client = reqwest::Client::new();
    let pool = get_db_pool().await;
    let api_url = spawn_test_api().await;

    let (token, school_id) = create_school_admin_and_token(&client, &pool, &api_url).await;

    let payload = serde_json::json!({
        "name": "Updated School Name",
        "city": "Lagos",
        "state": "Lagos State",
        "admin_name": "Updated Admin",
        "admin_phone": "+2348012345678",
        "website": "https://example.com"
    });

    let response = client
        .put(&format!("{}/api/auth/school/schools/{}", api_url, school_id))
        .bearer_auth(&token)
        .json(&payload)
        .send()
        .await
        .expect("failed to call update school endpoint");

    assert_eq!(response.status(), 200, "expected school update to succeed");
    let body: serde_json::Value = response.json().await.expect("invalid update response");

    assert_eq!(body.get("name").and_then(|v| v.as_str()), Some("Updated School Name"));
    assert_eq!(body.get("city").and_then(|v| v.as_str()), Some("Lagos"));
    assert_eq!(body.get("state").and_then(|v| v.as_str()), Some("Lagos State"));
    assert_eq!(body.get("admin_name").and_then(|v| v.as_str()), Some("Updated Admin"));
    assert_eq!(body.get("website").and_then(|v| v.as_str()), Some("https://example.com"));

    let db_school = sqlx::query_scalar::<_, String>("SELECT name FROM schools WHERE id = $1")
        .bind(school_id)
        .fetch_one(&pool)
        .await
        .expect("failed to read updated school from database");

    assert_eq!(db_school, "Updated School Name");
}

#[tokio::test]
async fn create_track_department_class_endpoint_creates_hierarchy() {
    let client = reqwest::Client::new();
    let pool = get_db_pool().await;
    let api_url = spawn_test_api().await;

    let (token, school_id) = create_school_admin_and_token(&client, &pool, &api_url).await;
    let (track_id, department_id, class_id) =
        create_track_department_class(&client, &api_url, &token, school_id, "hier").await;

    let class_track_id = sqlx::query_scalar::<_, Option<Uuid>>("SELECT track_id FROM classes WHERE id = $1")
        .bind(class_id)
        .fetch_one(&pool)
        .await
        .expect("failed to load class track_id");

    let class_department_id = sqlx::query_scalar::<_, Option<Uuid>>("SELECT department_id FROM classes WHERE id = $1")
        .bind(class_id)
        .fetch_one(&pool)
        .await
        .expect("failed to load class department_id");

    assert_eq!(class_track_id, Some(track_id));
    assert_eq!(class_department_id, Some(department_id));
}

#[tokio::test]
async fn link_classes_to_subject_endpoint_links_multiple_classes() {
    let client = reqwest::Client::new();
    let pool = get_db_pool().await;
    let api_url = spawn_test_api().await;

    let (token, school_id) = create_school_admin_and_token(&client, &pool, &api_url).await;
    let (_, _, class_a) =
        create_track_department_class(&client, &api_url, &token, school_id, "a").await;
    let (_, _, class_b) =
        create_track_department_class(&client, &api_url, &token, school_id, "b").await;
    let subject_id = ensure_subject(&pool, school_id, "link").await;
    let school_subject_id = ensure_school_subject(&pool, school_id, subject_id).await;

    let payload = serde_json::json!({
        "school_id": school_id,
        "class_ids": [class_a, class_b],
        "staff_id": null,
        "assigned_by": null
    });

    let response = client
        .post(&format!(
            "{}/api/auth/school/school-subjects/{}/classes/link",
            api_url, school_subject_id
        ))
        .bearer_auth(&token)
        .json(&payload)
        .send()
        .await
        .expect("failed to call class-subject link endpoint");

    let status = response.status();
    let raw_body = response.text().await.expect("failed reading link response body");
    assert_eq!(
        status,
        201,
        "expected class-subject link to succeed, body: {}",
        raw_body
    );
    let body: serde_json::Value = serde_json::from_str(&raw_body).expect("invalid link response");
    let links = body.as_array().expect("expected array response");
    assert_eq!(links.len(), 2, "expected one link per class");

    let count = sqlx::query_scalar::<_, i64>(
        "SELECT COUNT(*) FROM class_subjects WHERE school_id = $1 AND school_subject_id = $2 AND class_id IN ($3, $4) AND is_active = true",
    )
    .bind(school_id)
    .bind(school_subject_id)
    .bind(class_a)
    .bind(class_b)
    .fetch_one(&pool)
    .await
    .expect("failed to count class_subjects rows");

    assert_eq!(count, 2, "both classes should be linked to the subject");
}

#[tokio::test]
async fn save_school_subjects_endpoint_creates_school_subject_links() {
    let client = reqwest::Client::new();
    let pool = get_db_pool().await;
    let api_url = spawn_test_api().await;

    let (token, school_id) = create_school_admin_and_token(&client, &pool, &api_url).await;
    let subject_id = ensure_subject(&pool, school_id, "save-school-subject").await;

    let payload = serde_json::json!({
        "school_id": school_id,
        "subject_ids": [subject_id]
    });

    let response = client
        .post(&format!("{}/api/auth/school/school-subjects", api_url))
        .bearer_auth(&token)
        .json(&payload)
        .send()
        .await
        .expect("failed to call school-subjects endpoint");

    let status = response.status();
    let body_text = response
        .text()
        .await
        .expect("failed to read school-subjects response");

    assert_eq!(
        status,
        201,
        "expected school-subject save to succeed, body: {}",
        body_text
    );

    let count = sqlx::query_scalar::<_, i64>(
        "SELECT COUNT(*) FROM school_subjects WHERE school_id = $1 AND subject_id = $2 AND is_active = true",
    )
    .bind(school_id)
    .bind(subject_id)
    .fetch_one(&pool)
    .await
    .expect("failed to count school_subject rows");

    assert_eq!(count, 1, "school-subject link should be saved");
}

#[tokio::test]
async fn initialize_default_structure_links_only_selected_school_subject_mappings() {
    let client = reqwest::Client::new();
    let pool = get_db_pool().await;
    let api_url = spawn_test_api().await;

    let (token, school_id) = create_school_admin_and_token(&client, &pool, &api_url).await;

    let agriculture_subject_id =
        Uuid::parse_str("11f28946-9e04-4060-933f-0c09ff9c7345").expect("invalid uuid");
    let business_subject_id =
        Uuid::parse_str("939fbfe7-46d7-4502-9f03-b6bdc283176e").expect("invalid uuid");

    ensure_subject_with_id(
        &pool,
        school_id,
        agriculture_subject_id,
        "ZZ_TEST_FIXED_Agriculture_11f28946",
        "AGR-FIXED",
    )
    .await;
    ensure_subject_with_id(
        &pool,
        school_id,
        business_subject_id,
        "ZZ_TEST_FIXED_Business_939fbfe7",
        "BUS-FIXED",
    )
    .await;

    let save_school_subjects_payload = serde_json::json!({
        "school_id": school_id,
        "subject_ids": [agriculture_subject_id, business_subject_id]
    });

    let save_response = client
        .post(&format!("{}/api/auth/school/school-subjects", api_url))
        .bearer_auth(&token)
        .json(&save_school_subjects_payload)
        .send()
        .await
        .expect("failed to save school_subjects");
    assert_eq!(save_response.status(), 201, "expected school-subject save to succeed");

    let initialize_payload = serde_json::json!({ "school_id": school_id });
    let initialize_response = client
        .post(&format!("{}/api/auth/school/default-structure/initialize", api_url))
        .bearer_auth(&token)
        .json(&initialize_payload)
        .send()
        .await
        .expect("failed to initialize default structure");
    assert_eq!(initialize_response.status(), 201, "expected default initialization to succeed");

    let linked_rows: Vec<(Uuid, String)> = sqlx::query_as(
        r#"
        SELECT ss.subject_id, c.class_name
        FROM class_subjects cs
        JOIN school_subjects ss ON ss.id = cs.school_subject_id
        JOIN classes c ON c.id = cs.class_id
        WHERE cs.school_id = $1
          AND cs.is_active = true
          AND ss.is_active = true
          AND c.is_active = true
          AND ss.subject_id IN ($2, $3)
        ORDER BY ss.subject_id, c.class_name
        "#,
    )
    .bind(school_id)
    .bind(agriculture_subject_id)
    .bind(business_subject_id)
    .fetch_all(&pool)
    .await
    .expect("failed to fetch linked class-subject rows");

    let mut by_subject: HashMap<Uuid, Vec<String>> = HashMap::new();
    for (subject_id, class_name) in linked_rows {
        by_subject.entry(subject_id).or_default().push(class_name);
    }

    let mut agriculture_classes = by_subject
        .remove(&agriculture_subject_id)
        .expect("expected agriculture classes");
    agriculture_classes.sort();
    assert_eq!(agriculture_classes, vec!["SSS 1", "SSS 2", "SSS 3"]);

    let mut business_classes = by_subject
        .remove(&business_subject_id)
        .expect("expected business studies classes");
    business_classes.sort();
    assert_eq!(business_classes, vec!["JSS 1", "JSS 2", "JSS 3"]);

    assert!(
        by_subject.is_empty(),
        "expected links only for explicitly selected school_subjects"
    );
}

#[tokio::test]
async fn initialize_default_structure_creates_structure_for_empty_school() {
    let client = reqwest::Client::new();
    let pool = get_db_pool().await;
    let api_url = spawn_test_api().await;

    let (token, school_id) = create_school_admin_and_token(&client, &pool, &api_url).await;

    let payload = serde_json::json!({
        "school_id": school_id
    });

    let response = client
        .post(&format!("{}/api/auth/school/default-structure/initialize", api_url))
        .bearer_auth(&token)
        .json(&payload)
        .send()
        .await
        .expect("failed to call initialize default structure endpoint");

    let status = response.status();
    let body_text = response.text().await.expect("failed to read initialize response body");
    assert_eq!(status, 201, "expected structure creation, body: {}", body_text);

    let body: serde_json::Value =
        serde_json::from_str(&body_text).expect("invalid initialize response");
    assert_eq!(body.get("created").and_then(|v| v.as_bool()), Some(true));

    let reported_links = body
        .get("class_subjects_created")
        .and_then(|v| v.as_i64())
        .expect("missing class_subjects_created");

    let track_count = sqlx::query_scalar::<_, i64>(
        "SELECT COUNT(*) FROM education_tracks WHERE school_id = $1 AND is_active = true",
    )
    .bind(school_id)
    .fetch_one(&pool)
    .await
    .expect("failed to count tracks");

    let department_count = sqlx::query_scalar::<_, i64>(
        "SELECT COUNT(*) FROM departments WHERE school_id = $1 AND is_active = true",
    )
    .bind(school_id)
    .fetch_one(&pool)
    .await
    .expect("failed to count departments");

    let class_count = sqlx::query_scalar::<_, i64>(
        "SELECT COUNT(*) FROM classes WHERE school_id = $1 AND is_active = true",
    )
    .bind(school_id)
    .fetch_one(&pool)
    .await
    .expect("failed to count classes");

    let class_subject_count = sqlx::query_scalar::<_, i64>(
        "SELECT COUNT(*) FROM class_subjects WHERE school_id = $1 AND is_active = true",
    )
    .bind(school_id)
    .fetch_one(&pool)
    .await
    .expect("failed to count class_subjects");

    let class_grade_rows: Vec<(String, Option<i32>)> = sqlx::query_as(
        "SELECT class_name, grade_level FROM classes WHERE school_id = $1 AND is_active = true",
    )
    .bind(school_id)
    .fetch_all(&pool)
    .await
    .expect("failed to load class grade levels");

    let class_grade_map: HashMap<String, Option<i32>> = class_grade_rows.into_iter().collect();

    assert_eq!(track_count, 1, "expected exactly one default track");
    assert_eq!(department_count, 4, "expected four default departments");
    assert_eq!(class_count, 16, "expected sixteen default classes");
    assert_eq!(class_subject_count, 0, "expected no default class_subject links when no school_subjects are preselected");
    assert_eq!(
        class_subject_count,
        reported_links,
        "database class_subject count should match initializer response"
    );

    let expected_grade_levels = [
        ("Kindergarten", -3_i32),
        ("Pre-Nursery", -2_i32),
        ("Nursery 1", -1_i32),
        ("Nursery 2", 0_i32),
        ("Primary 1", 1_i32),
        ("Primary 2", 2_i32),
        ("Primary 3", 3_i32),
        ("Primary 4", 4_i32),
        ("Primary 5", 5_i32),
        ("Primary 6", 6_i32),
        ("JSS 1", 7_i32),
        ("JSS 2", 8_i32),
        ("JSS 3", 9_i32),
        ("SSS 1", 10_i32),
        ("SSS 2", 11_i32),
        ("SSS 3", 12_i32),
    ];

    for (class_name, grade_level) in expected_grade_levels {
        assert_eq!(
            class_grade_map.get(class_name),
            Some(&Some(grade_level)),
            "expected grade_level {} for class {}",
            grade_level,
            class_name
        );
    }
}

#[tokio::test]
async fn initialize_default_structure_is_idempotent_when_structure_exists() {
    let client = reqwest::Client::new();
    let pool = get_db_pool().await;
    let api_url = spawn_test_api().await;

    let (token, school_id) = create_school_admin_and_token(&client, &pool, &api_url).await;

    let payload = serde_json::json!({
        "school_id": school_id
    });

    let first_response = client
        .post(&format!("{}/api/auth/school/default-structure/initialize", api_url))
        .bearer_auth(&token)
        .json(&payload)
        .send()
        .await
        .expect("failed first initialize call");
    assert_eq!(first_response.status(), 201, "first initialize should create structure");

    let second_response = client
        .post(&format!("{}/api/auth/school/default-structure/initialize", api_url))
        .bearer_auth(&token)
        .json(&payload)
        .send()
        .await
        .expect("failed second initialize call");

    let second_status = second_response.status();
    let second_body_text = second_response
        .text()
        .await
        .expect("failed to read second initialize response body");
    assert_eq!(
        second_status,
        200,
        "second initialize should be idempotent, body: {}",
        second_body_text
    );

    let second_body: serde_json::Value =
        serde_json::from_str(&second_body_text).expect("invalid second initialize response");
    assert_eq!(second_body.get("created").and_then(|v| v.as_bool()), Some(false));

    let track_count = sqlx::query_scalar::<_, i64>(
        "SELECT COUNT(*) FROM education_tracks WHERE school_id = $1 AND is_active = true",
    )
    .bind(school_id)
    .fetch_one(&pool)
    .await
    .expect("failed to count tracks");

    let department_count = sqlx::query_scalar::<_, i64>(
        "SELECT COUNT(*) FROM departments WHERE school_id = $1 AND is_active = true",
    )
    .bind(school_id)
    .fetch_one(&pool)
    .await
    .expect("failed to count departments");

    let class_count = sqlx::query_scalar::<_, i64>(
        "SELECT COUNT(*) FROM classes WHERE school_id = $1 AND is_active = true",
    )
    .bind(school_id)
    .fetch_one(&pool)
    .await
    .expect("failed to count classes");

    let class_subject_count = sqlx::query_scalar::<_, i64>(
        "SELECT COUNT(*) FROM class_subjects WHERE school_id = $1 AND is_active = true",
    )
    .bind(school_id)
    .fetch_one(&pool)
    .await
    .expect("failed to count class_subjects");

    assert_eq!(track_count, 1, "expected one track after repeated initialize");
    assert_eq!(department_count, 4, "expected four departments after repeated initialize");
    assert_eq!(class_count, 16, "expected sixteen classes after repeated initialize");
    assert_eq!(
        class_subject_count,
        0,
        "expected no class_subject links after repeated initialize when none were created initially"
    );
}

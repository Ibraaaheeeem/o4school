mod common;

use common::*;
use school_backend::db::Database;
use school_backend::models::auth::{CreateParentInfo, CreateParentWithUserRequest};
use school_backend::services::ParentService;
use uuid::Uuid;

#[tokio::test]
async fn school_admin_creates_parent_with_existing_user_email_reuses_user_and_skips_duplicate_role() {
    let database_url = std::env::var("DATABASE_URL")
        .unwrap_or_else(|_| "postgres://postgres:password@localhost:5432/myschool".to_string());
    let db = Database::new(&database_url).await.expect("Failed to create Database");

    let school_id = Uuid::parse_str("02ba1e88-cb88-4e10-9d3f-81eb62912e1d").expect("Invalid school_id");

    let test_email = generate_test_email("parent-create-with-user");

    let req = CreateParentWithUserRequest {
        school_id,
        email: test_email.clone(),
        phone_number: Some(generate_test_phone()),
        first_name: "Parent".to_string(),
        middle_name: None,
        last_name: "Creator".to_string(),
        gender: Some("F".to_string()),
        date_of_birth: None,
        parent: CreateParentInfo {
            occupation: Some("Trader".to_string()),
            employer_name: Some("Biz Ltd".to_string()),
            business_address: Some("Main Street".to_string()),
        },
        parent_student_relationships: None,
    };

    let created = ParentService::create_parent_with_user(&db, req.clone())
        .await
        .expect("Failed to create parent with user");

    assert_eq!(created.school_id, school_id, "parent school should match request");

    let created_again = ParentService::create_parent_with_user(&db, req)
        .await
        .expect("Second create should return existing parent role record");

    assert_eq!(created_again.id, created.id, "duplicate role should not create a new parent row");

    let role_count: i64 = sqlx::query_scalar(
        "SELECT COUNT(*) FROM parents WHERE user_id = $1 AND school_id = $2 AND is_active = true"
    )
    .bind(created.user_id)
    .bind(school_id)
    .fetch_one(db.pool())
    .await
    .expect("Failed to query parent role count");

    assert_eq!(role_count, 1, "only one active parent role row should exist for user+school");

    let _ = sqlx::query("DELETE FROM parents WHERE user_id = $1 AND school_id = $2")
        .bind(created.user_id)
        .bind(school_id)
        .execute(db.pool())
        .await;

    let _ = db::delete_test_user(&db.pool(), &test_email).await;
}

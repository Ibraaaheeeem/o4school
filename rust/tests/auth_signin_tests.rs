/// Integration tests for authentication sign-in endpoints
mod common;

use common::*;
use sqlx::PgPool;
use uuid::Uuid;

#[cfg(test)]
mod auth_signin_integration_tests {
    use super::common::*;
    use super::*;
    use std::sync::Once;

    static INIT: Once = Once::new();

    fn init() {
        INIT.call_once(|| {
            let _ = env_logger::builder()
                .is_test(true)
                .try_init();
        });
    }

    async fn activate_user(pool: &PgPool, user_id: Uuid) {
        sqlx::query("UPDATE users SET is_active = true, is_approved = true, status = 'ACTIVE' WHERE id = $1")
            .bind(user_id)
            .execute(pool)
            .await
            .expect("Failed to activate user");
    }

    // Helper to post sign-in and return serde_json::Value
    async fn signin_request(client: &reqwest::Client, email: &str, password: &str) -> reqwest::Response {
        let payload = serde_json::json!({"email": email, "password": password});
        client
            .post(&format!("{}{}", constants::API_URL, "/api/auth/sign-in"))
            .json(&payload)
            .send()
            .await
            .expect("Failed to call sign-in")
    }

    #[tokio::test]
    async fn test_signin_success_with_active_user() {
        init();

        let pool = get_db_pool().await;
        let client = get_http_client();
        let test_email = generate_test_email("signin-success");
        let password = "SecurePassword123!";

        // Ensure clean state
        let _ = db::delete_test_user(&pool, &test_email).await;

        // Create user via signup and then activate
        let signup_request = build_signup_request(
            &test_email,
            password,
            "Test",
            "User",
            &generate_test_phone(),
            "STAFF",
            Some(constants::TEST_SCHOOL_CODE_1),
        );
        let signup_response = http::signup_expect_success(&client, signup_request).await;
        let user_id = Uuid::parse_str(&signup_response.user_id).unwrap();
        activate_user(&pool, user_id).await;

        // Debug: fetch user from DB and print verified_at
        let db_user = db::get_user_by_email(&pool, &test_email).await;
        println!("DEBUG db_user after activate: {:?}", db_user);

        // Sign in
        let response = signin_request(&client, &test_email, password).await;
        let status = response.status();
        if !status.is_success() {
            let text = response.text().await.unwrap_or_else(|_| "<no-body>".to_string());
            panic!("Sign-in failed: status={} body={} ", status, text);
        }
        let body = response.json::<serde_json::Value>().await.expect("parse json");

        assert!(body.get("access_token").and_then(|v| v.as_str()).map(|s| !s.is_empty()).unwrap_or(false), "access_token present");
        assert!(body.get("user_id").is_some(), "user_id present");
        assert!(body.get("expires_in").and_then(|v| v.as_i64()).map(|v| v > 0).unwrap_or(false), "expires_in positive");
        assert!(body.get("schools").and_then(|v| v.as_array()).is_some(), "schools array present");

        println!("✓ Test: sign-in success for active user");
        let _ = db::delete_test_user(&pool, &test_email).await;
    }

    #[tokio::test]
    async fn test_signin_fails_with_wrong_password() {
        init();

        let pool = get_db_pool().await;
        let client = get_http_client();
        let test_email = generate_test_email("signin-wrong-pw");
        let password = "SecurePassword123!";

        let _ = db::delete_test_user(&pool, &test_email).await;

        let signup_request = build_signup_request(
            &test_email,
            password,
            "Test",
            "User",
            &generate_test_phone(),
            "PARENT",
            Some(constants::TEST_SCHOOL_CODE_1),
        );
        let signup_response = http::signup_expect_success(&client, signup_request).await;
        let user_id = Uuid::parse_str(&signup_response.user_id).unwrap();
        activate_user(&pool, user_id).await;

        // Attempt sign-in with wrong password
        let response = signin_request(&client, &test_email, "WrongPassword!").await;
        assert!(response.status().is_client_error(), "Wrong password should return 4xx");

        println!("✓ Test: sign-in fails with wrong password");
        let _ = db::delete_test_user(&pool, &test_email).await;
    }

    #[tokio::test]
    async fn test_signin_fails_for_nonexistent_email() {
        init();

        let client = get_http_client();
        let email = generate_test_email("signin-nonexistent");

        // No prior signup
        let response = signin_request(&client, &email, "Whatever123!").await;
        assert!(response.status().is_client_error() || response.status().is_server_error(), "Non-existent email should error");

        println!("✓ Test: sign-in fails for non-existent email");
    }

    #[tokio::test]
    async fn test_signin_fails_when_user_not_active() {
        init();

        let pool = get_db_pool().await;
        let client = get_http_client();
        let test_email = generate_test_email("signin-not-active");
        let password = "SecurePassword123!";

        let _ = db::delete_test_user(&pool, &test_email).await;

        // Signup but do NOT activate
        let signup_request = build_signup_request(
            &test_email,
            password,
            "Test",
            "User",
            &generate_test_phone(),
            "STAFF",
            Some(constants::TEST_SCHOOL_CODE_1),
        );
        let _ = http::signup_expect_success(&client, signup_request).await;

        // Sign-in attempt should fail until activation
        let response = signin_request(&client, &test_email, password).await;
        assert!(response.status().is_client_error() || response.status().is_server_error(), "Inactive user should not sign in");

        println!("✓ Test: sign-in fails for inactive user");
        let _ = db::delete_test_user(&pool, &test_email).await;
    }

    #[tokio::test]
    async fn test_signin_returns_all_user_schools_and_roles() {
        init();

        let pool = get_db_pool().await;
        let client = get_http_client();
        let test_email = generate_test_email("signin-multi-role");
        let password = "SecurePassword123!";

        let _ = db::delete_test_user(&pool, &test_email).await;

        // First signup as STAFF at school 1
        let signup_request = build_signup_request(
            &test_email,
            password,
            "Test",
            "User",
            &generate_test_phone(),
            "STAFF",
            Some(constants::TEST_SCHOOL_CODE_1),
        );
        let resp1 = http::signup_expect_success(&client, signup_request).await;

        // Second signup for same email as PARENT at same school (adds role)
        let signup_request = build_signup_request(
            &test_email,
            password,
            "Test",
            "User",
            &generate_test_phone(),
            "PARENT",
            Some(constants::TEST_SCHOOL_CODE_1),
        );
        let _resp2 = http::signup_expect_success(&client, signup_request).await;

        let user_id = Uuid::parse_str(&resp1.user_id).unwrap();
        activate_user(&pool, user_id).await;

        // Sign in and inspect schools/roles
        let response = signin_request(&client, &test_email, password).await;
        let status = response.status();
        if !status.is_success() {
            let text = response.text().await.unwrap_or_else(|_| "<no-body>".to_string());
            panic!("Sign-in failed: status={} body={} ", status, text);
        }
        let body = response.json::<serde_json::Value>().await.expect("parse json");

        let schools = body.get("schools").and_then(|v| v.as_array()).expect("schools array");
        assert!(!schools.is_empty(), "At least one school present");
        // Each school should have roles array with at least one role
        for school in schools.iter() {
            let roles = school.get("roles").and_then(|r| r.as_array()).expect("roles array");
            assert!(!roles.is_empty(), "roles should be present for school");
        }

        println!("✓ Test: sign-in returns all user schools and roles");
        let _ = db::delete_test_user(&pool, &test_email).await;
    }
}

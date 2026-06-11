/// Integration tests for forgot-password and reset-password flows
mod common;

use common::*;
use sqlx::PgPool;
use uuid::Uuid;

#[cfg(test)]
mod auth_password_flow_tests {
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

    async fn fetch_otp(pool: &PgPool, email: &str) -> Option<String> {
        sqlx::query_scalar::<_, Option<String>>("SELECT otp_code FROM users WHERE email = $1 ORDER BY created_at DESC LIMIT 1")
            .bind(email)
            .fetch_one(pool)
            .await
            .ok()
            .flatten()
    }

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
    async fn test_forgot_password_then_reset() {
        init();

        let pool = get_db_pool().await;
        let client = get_http_client();
        let test_email = generate_test_email("forgot-reset");
        let old_password = "SecurePassword123!";
        let new_password = "NewSecurePass123!";

        let _ = db::delete_test_user(&pool, &test_email).await;

        // Signup user
        let signup_request = build_signup_request(
            &test_email,
            old_password,
            "Test",
            "User",
            &generate_test_phone(),
            "STAFF",
            Some(constants::TEST_SCHOOL_CODE_1),
        );
        let _ = http::signup_expect_success(&client, signup_request).await;

        // Request forgot password
        let resp = client
            .post(&format!("{}{}", constants::API_URL, "/api/auth/forgot-password"))
            .json(&serde_json::json!({"email": &test_email}))
            .send()
            .await
            .expect("forgot-password request failed");
        assert!(resp.status().is_success(), "forgot-password should succeed");

        // Read reset token from DB
        let otp = fetch_otp(&pool, &test_email).await.expect("No otp found");

        // Call reset-password
        let reset_resp = client
            .post(&format!("{}{}", constants::API_URL, "/api/auth/reset-password"))
            .json(&serde_json::json!({
                "email": &test_email,
                "otp": otp,
                "new_password": new_password,
                "confirm_password": new_password
            }))
            .send()
            .await
            .expect("reset-password request failed");
        assert!(reset_resp.status().is_success(), "reset-password should succeed");

        // Sign in with new password should not succeed because account is not active
        let signin = signin_request(&client, &test_email, new_password).await;
        assert!(!signin.status().is_success(), "Sign-in for non-active account should not succeed");

        println!("✓ Test: forgot-password → reset-password flow");
        let _ = db::delete_test_user(&pool, &test_email).await;
    }

    #[tokio::test]
    async fn test_activate_then_forgot_and_reset() {
        init();

        let pool = get_db_pool().await;
        let client = get_http_client();
        let test_email = generate_test_email("activate-forgot-reset");
        let password = "SecurePassword123!";
        let new_password = "BrandNewPass123!";

        let _ = db::delete_test_user(&pool, &test_email).await;

        // Signup user
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

        // Activate flow: request activation OTP
        let act_resp = client
            .post(&format!("{}{}", constants::API_URL, "/api/auth/activate"))
            .json(&serde_json::json!({"email": &test_email}))
            .send()
            .await
            .expect("activate request failed");
        assert!(act_resp.status().is_success(), "activate should succeed");

        // Get activation OTP and verify via verify-otp
        let otp = fetch_otp(&pool, &test_email).await.expect("No otp for activate");
        let verify_resp = client
            .post(&format!("{}{}", constants::API_URL, "/api/auth/verify-otp"))
            .json(&serde_json::json!({
                "email": &test_email,
                "otp_code": otp,
                "next_route": "SIGN_IN"
            }))
            .send()
            .await
            .expect("verify-otp request failed");
        assert!(verify_resp.status().is_success(), "verify-otp should succeed");

        // Now request forgot password
        let resp = client
            .post(&format!("{}{}", constants::API_URL, "/api/auth/forgot-password"))
            .json(&serde_json::json!({"email": &test_email}))
            .send()
            .await
            .expect("forgot-password request failed");
        assert!(resp.status().is_success(), "forgot-password should succeed");

        // Read reset token
        let reset_otp = fetch_otp(&pool, &test_email).await.expect("No reset otp");

        // Reset password
        let reset_resp = client
            .post(&format!("{}{}", constants::API_URL, "/api/auth/reset-password"))
            .json(&serde_json::json!({
                "email": &test_email,
                "otp": reset_otp,
                "new_password": new_password,
                "confirm_password": new_password
            }))
            .send()
            .await
            .expect("reset-password request failed");
        assert!(reset_resp.status().is_success(), "reset-password should succeed");

        // Sign in with new password
        let signin = signin_request(&client, &test_email, new_password).await;
        assert!(signin.status().is_success(), "Sign-in with reset password should succeed");

        println!("✓ Test: activate → forgot-password → reset-password flow");
        let _ = db::delete_test_user(&pool, &test_email).await;
    }
}

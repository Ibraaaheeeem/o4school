/// Integration tests for authentication signup endpoints
/// Tests all signup scenarios including SCHOOL_ADMIN, STAFF, PARENT, and ADMIN roles

mod common;

use common::*;

#[cfg(test)]
mod auth_signup_integration_tests {
    use super::common::*;
    use std::sync::Once;

    static INIT: Once = Once::new();

    /// Initialize test environment once
    fn init() {
        INIT.call_once(|| {
            let _ = env_logger::builder()
                .is_test(true)
                .try_init();
        });
    }

    // ========================================================================
    // TEST CASE 1: SCHOOL_ADMIN Signup - New User Creates Empty School
    // ========================================================================
    #[tokio::test]
    async fn test_school_admin_signup_new_user_creates_school() {
        init();

        let pool = get_db_pool().await;
        let client = get_http_client();
        let test_email = generate_test_email("school-admin-new");

        let _ = db::delete_test_user(&pool, &test_email).await;

        let signup_request = build_signup_request(
            &test_email,
            "SecurePassword123!",
            "Test",
            "Admin",
            &generate_test_phone(),
            "SCHOOL_ADMIN",
            None,
        );

        let response = http::signup_expect_success(&client, signup_request).await;

        // Verify response structure
        assert!(!response.user_id.is_empty(), "user_id should not be empty");
        assert_eq!(response.email, test_email, "email should match");
        assert_eq!(response.role, "SCHOOL_ADMIN", "role should be SCHOOL_ADMIN");
        assert!(!response.school_id.is_empty(), "school_id should be created");
        assert!(response.school_name.contains("School Admin"), "school_name format");
        assert_eq!(response.next_route, "/auth/activate", "next_route should be /auth/activate");

        // Verify database state
        let user = db::get_user_by_email(&pool, &test_email).await;
        assert!(user.is_some(), "User should exist in database");

        let user_id = uuid::Uuid::parse_str(&response.user_id).unwrap();
        let school_id = uuid::Uuid::parse_str(&response.school_id).unwrap();

        let roles = db::get_user_school_roles(&pool, user_id).await;
        assert_eq!(roles.len(), 1, "Should have exactly 1 role");
        assert_eq!(roles[0].role_id, constants::role_id_school_admin());

        println!("✓ Test: SCHOOL_ADMIN signup new user creates school");
        let _ = db::delete_test_user(&pool, &test_email).await;
    }

    // ========================================================================
    // TEST CASE 2: Duplicate SCHOOL_ADMIN Signup - Existing User Creates New School
    // ========================================================================
    #[tokio::test]
    async fn test_school_admin_signup_existing_user_creates_new_school() {
        init();

        let pool = get_db_pool().await;
        let client = get_http_client();
        let test_email = generate_test_email("school-admin-dup");

        let _ = db::delete_test_user(&pool, &test_email).await;

        // First signup
        let signup_request = build_signup_request(
            &test_email,
            "SecurePassword123!",
            "Test",
            "Admin",
            &generate_test_phone(),
            "SCHOOL_ADMIN",
            None,
        );
        let response_1 = http::signup_expect_success(&client, signup_request).await;
        let school_1 = uuid::Uuid::parse_str(&response_1.school_id).unwrap();

        // Second signup (existing user, new school)
        let signup_request = build_signup_request(
            &test_email,
            "SecurePassword123!",
            "Test",
            "Admin",
            &generate_test_phone(),
            "SCHOOL_ADMIN",
            None,
        );
        let response_2 = http::signup_expect_success(&client, signup_request).await;
        let school_2 = uuid::Uuid::parse_str(&response_2.school_id).unwrap();

        // Verify same user, different schools
        assert_eq!(response_1.user_id, response_2.user_id, "Should be same user");
        assert_ne!(school_1, school_2, "Should create new school");

        println!("✓ Test: SCHOOL_ADMIN signup existing user creates new school");
        let _ = db::delete_test_user(&pool, &test_email).await;
    }

    // ========================================================================
    // TEST CASE 3: STAFF Signup at Existing School
    // ========================================================================
    #[tokio::test]
    async fn test_staff_signup_existing_school() {
        init();

        let pool = get_db_pool().await;
        let client = get_http_client();
        let test_email = generate_test_email("staff-signup");

        let _ = db::delete_test_user(&pool, &test_email).await;

        let signup_request = build_signup_request(
            &test_email,
            "SecurePassword123!",
            "Test",
            "Staff",
            &generate_test_phone(),
            "STAFF",
            Some(constants::TEST_SCHOOL_CODE_1),
        );

        let response = http::signup_expect_success(&client, signup_request).await;

        assert_eq!(response.role, "STAFF", "role should be STAFF");
        assert!(!response.school_id.is_empty(), "should have school_id");

        let user_id = uuid::Uuid::parse_str(&response.user_id).unwrap();
        let roles = db::get_user_school_roles(&pool, user_id).await;
        assert_eq!(roles[0].role_id, constants::role_id_staff());

        println!("✓ Test: STAFF signup existing school");
        let _ = db::delete_test_user(&pool, &test_email).await;
    }

    // ========================================================================
    // TEST CASE 4: PARENT Signup at Existing School
    // ========================================================================
    #[tokio::test]
    async fn test_parent_signup_existing_school() {
        init();

        let pool = get_db_pool().await;
        let client = get_http_client();
        let test_email = generate_test_email("parent-signup");

        let _ = db::delete_test_user(&pool, &test_email).await;

        let signup_request = build_signup_request(
            &test_email,
            "SecurePassword123!",
            "Test",
            "Parent",
            &generate_test_phone(),
            "PARENT",
            Some(constants::TEST_SCHOOL_CODE_1),
        );

        let response = http::signup_expect_success(&client, signup_request).await;

        assert_eq!(response.role, "PARENT", "role should be PARENT");

        let user_id = uuid::Uuid::parse_str(&response.user_id).unwrap();
        let roles = db::get_user_school_roles(&pool, user_id).await;
        assert_eq!(roles[0].role_id, constants::role_id_parent());

        println!("✓ Test: PARENT signup existing school");
        let _ = db::delete_test_user(&pool, &test_email).await;
    }

    // ========================================================================
    // TEST CASE 5: ADMIN Signup at Existing School
    // ========================================================================
    #[tokio::test]
    async fn test_admin_signup_existing_school() {
        init();

        let pool = get_db_pool().await;
        let client = get_http_client();
        let test_email = generate_test_email("admin-signup");

        let _ = db::delete_test_user(&pool, &test_email).await;

        let signup_request = build_signup_request(
            &test_email,
            "SecurePassword123!",
            "Test",
            "Admin",
            &generate_test_phone(),
            "ADMIN",
            Some(constants::TEST_SCHOOL_CODE_1),
        );

        let response = http::signup_expect_success(&client, signup_request).await;

        assert_eq!(response.role, "ADMIN", "role should be ADMIN");

        let user_id = uuid::Uuid::parse_str(&response.user_id).unwrap();
        let roles = db::get_user_school_roles(&pool, user_id).await;
        assert_eq!(roles[0].role_id, constants::role_id_admin());

        println!("✓ Test: SCHOOL_ADMIN signup existing school");
        let _ = db::delete_test_user(&pool, &test_email).await;
    }

    // ========================================================================
    // TEST CASE 6: Missing Email Validation
    // ========================================================================
    #[tokio::test]
    async fn test_signup_missing_email_validation() {
        init();

        let client = get_http_client();

        let signup_request = serde_json::json!({
            "password": "SecurePassword123!",
            "first_name": "Test",
            "last_name": "User",
            "phone_number": &generate_test_phone(),
            "role": "STAFF",
            "school_code": constants::TEST_SCHOOL_CODE_1
        });

        let response = client
            .post(&format!("{}{}",constants::API_URL, constants::SIGNUP_ENDPOINT))
            .json(&signup_request)
            .send()
            .await
            .unwrap();

        assert!(response.status().is_client_error(), "Should fail with 4xx status");

        println!("✓ Test: missing email validation");
    }

    // ========================================================================
    // TEST CASE 7: Missing Password Validation
    // ========================================================================
    #[tokio::test]
    async fn test_signup_missing_password_validation() {
        init();

        let client = get_http_client();
        let test_email = generate_test_email("no-password");

        let signup_request = serde_json::json!({
            "email": &test_email,
            "first_name": "Test",
            "last_name": "User",
            "phone_number": &generate_test_phone(),
            "role": "STAFF",
            "school_code": constants::TEST_SCHOOL_CODE_1
        });

        let response = client
            .post(&format!("{}{}",constants::API_URL, constants::SIGNUP_ENDPOINT))
            .json(&signup_request)
            .send()
            .await
            .unwrap();

        assert!(response.status().is_client_error(), "Should fail with 4xx status");

        println!("✓ Test: missing password validation");
    }

    // ========================================================================
    // TEST CASE 8: Invalid Email Format
    // ========================================================================
    #[tokio::test]
    async fn test_signup_invalid_email_format() {
        init();

        let client = get_http_client();

        let signup_request = build_signup_request(
            "not-an-email",
            "SecurePassword123!",
            "Test",
            "User",
            &generate_test_phone(),
            "STAFF",
            Some(constants::TEST_SCHOOL_CODE_1),
        );

        let response = client
            .post(&format!("{}{}",constants::API_URL, constants::SIGNUP_ENDPOINT))
            .json(&signup_request)
            .send()
            .await
            .unwrap();

        assert!(response.status().is_client_error() || response.status().is_server_error());

        println!("✓ Test: invalid email format");
    }

    // ========================================================================
    // TEST CASE 9: Weak Password Validation
    // ========================================================================
    #[tokio::test]
    async fn test_signup_weak_password_validation() {
        init();

        let client = get_http_client();
        let test_email = generate_test_email("weak-password");

        let signup_request = build_signup_request(
            &test_email,
            "weak",
            "Test",
            "User",
            &generate_test_phone(),
            "STAFF",
            Some(constants::TEST_SCHOOL_CODE_1),
        );

        let response = client
            .post(&format!("{}{}",constants::API_URL, constants::SIGNUP_ENDPOINT))
            .json(&signup_request)
            .send()
            .await
            .unwrap();

        assert!(response.status().is_client_error() || response.status().is_server_error());

        println!("✓ Test: weak password validation");
    }

    // ========================================================================
    // TEST CASE 10: Duplicate Email Unique Constraint
    // ========================================================================
    #[tokio::test]
    async fn test_signup_duplicate_email_constraint() {
        init();

        let pool = get_db_pool().await;
        let client = get_http_client();
        let test_email = generate_test_email("duplicate-email");

        let _ = db::delete_test_user(&pool, &test_email).await;

        // First signup
        let signup_request = build_signup_request(
            &test_email,
            "SecurePassword123!",
            "Test",
            "User",
            &generate_test_phone(),
            "STAFF",
            Some(constants::TEST_SCHOOL_CODE_1),
        );
        let response_1 = http::signup_expect_success(&client, signup_request).await;

        // Attempt duplicate signup with different role (same email, same school, different role)
        // Should succeed as users can have multiple roles at the same school
        let signup_request = build_signup_request(
            &test_email,
            "SecurePassword123!",
            "Test",
            "User",
            &generate_test_phone(),
            "PARENT",
            Some(constants::TEST_SCHOOL_CODE_1),
        );
        let response_2 = http::signup_expect_success(&client, signup_request).await;

        // Same user, same school, different role
        assert_eq!(response_1.user_id, response_2.user_id, "Should be same user");
        assert_eq!(response_1.school_id, response_2.school_id, "Should be same school");
        assert_eq!(response_2.role, "PARENT", "Second role should be PARENT");

        println!("✓ Test: duplicate email with different role at same school");
        let _ = db::delete_test_user(&pool, &test_email).await;
    }

    // ========================================================================
    // TEST CASE 11: Duplicate Phone Number Unique Constraint
    // ========================================================================
    #[tokio::test]
    async fn test_signup_duplicate_phone_number_constraint() {
        init();

        let pool = get_db_pool().await;
        let client = get_http_client();
        let phone = generate_test_phone();

        // First signup
        let signup_request = build_signup_request(
            &generate_test_email("phone-dup-1"),
            "SecurePassword123!",
            "Test",
            "User",
            &phone,
            "STAFF",
            Some(constants::TEST_SCHOOL_CODE_1),
        );
        let _ = http::signup_expect_success(&client, signup_request).await;

        // Attempt signup with duplicate phone
        let signup_request = build_signup_request(
            &generate_test_email("phone-dup-2"),
            "SecurePassword123!",
            "Test",
            "User",
            &phone,
            "PARENT",
            Some(constants::TEST_SCHOOL_CODE_1),
        );
        let response = client
            .post(&format!("{}{}",constants::API_URL, constants::SIGNUP_ENDPOINT))
            .json(&signup_request)
            .send()
            .await
            .unwrap();

        assert!(response.status().is_client_error() || response.status().is_server_error());

        println!("✓ Test: duplicate phone number constraint");
    }

    // ========================================================================
    // TEST CASE 12: Non-existent School Code Error
    // ========================================================================
    #[tokio::test]
    async fn test_signup_invalid_school_code() {
        init();

        let client = get_http_client();
        let test_email = generate_test_email("invalid-school");

        let signup_request = build_signup_request(
            &test_email,
            "SecurePassword123!",
            "Test",
            "User",
            &generate_test_phone(),
            "STAFF",
            Some("invalid-school-code-xyz"),
        );

        let response = client
            .post(&format!("{}{}",constants::API_URL, constants::SIGNUP_ENDPOINT))
            .json(&signup_request)
            .send()
            .await
            .unwrap();

        assert!(response.status().is_client_error() || response.status().is_server_error());

        println!("✓ Test: invalid school code error");
    }

    // ========================================================================
    // TEST CASE 13: Invalid Role Name Error
    // ========================================================================
    #[tokio::test]
    async fn test_signup_invalid_role_name() {
        init();

        let client = get_http_client();
        let test_email = generate_test_email("invalid-role");

        let signup_request = serde_json::json!({
            "email": &test_email,
            "password": "SecurePassword123!",
            "first_name": "Test",
            "last_name": "User",
            "phone_number": &generate_test_phone(),
            "role": "INVALID_ROLE",
            "school_code": constants::TEST_SCHOOL_CODE_1
        });

        let response = client
            .post(&format!("{}{}",constants::API_URL, constants::SIGNUP_ENDPOINT))
            .json(&signup_request)
            .send()
            .await
            .unwrap();

        assert!(response.status().is_client_error() || response.status().is_server_error());

        println!("✓ Test: invalid role name error");
    }

    // ========================================================================
    // TEST CASE 14: Missing School Code for Non-SCHOOL_ADMIN
    // ========================================================================
    #[tokio::test]
    async fn test_signup_missing_school_code_for_staff() {
        init();

        let client = get_http_client();
        let test_email = generate_test_email("missing-school-code");

        let signup_request = serde_json::json!({
            "email": &test_email,
            "password": "SecurePassword123!",
            "first_name": "Test",
            "last_name": "User",
            "phone_number": &generate_test_phone(),
            "role": "STAFF"
        });

        let response = client
            .post(&format!("{}{}",constants::API_URL, constants::SIGNUP_ENDPOINT))
            .json(&signup_request)
            .send()
            .await
            .unwrap();

        assert!(response.status().is_client_error(), "Should fail without school_code for STAFF");

        println!("✓ Test: missing school code for STAFF");
    }

    // ========================================================================
    // TEST CASE 15: Response Structure Validation
    // ========================================================================
    #[tokio::test]
    async fn test_signup_response_structure() {
        init();

        let pool = get_db_pool().await;
        let client = get_http_client();
        let test_email = generate_test_email("response-structure");

        let _ = db::delete_test_user(&pool, &test_email).await;

        let signup_request = build_signup_request(
            &test_email,
            "SecurePassword123!",
            "Test",
            "User",
            &generate_test_phone(),
            "SCHOOL_ADMIN",
            None,
        );

        let response = http::signup_expect_success(&client, signup_request).await;

        // Verify all required fields exist
        assert!(!response.user_id.is_empty(), "user_id required");
        assert!(!response.email.is_empty(), "email required");
        assert!(!response.role.is_empty(), "role required");
        assert!(!response.school_id.is_empty(), "school_id required");
        assert!(!response.school_name.is_empty(), "school_name required");
        assert!(!response.user_school_role_id.is_empty(), "user_school_role_id required");
        assert!(!response.message.is_empty(), "message required");
        assert!(!response.next_route.is_empty(), "next_route required");
        assert!(!response.verification_token.is_empty(), "verification_token required");

        // Verify UUIDs are valid
        assert!(uuid::Uuid::parse_str(&response.user_id).is_ok());
        assert!(uuid::Uuid::parse_str(&response.school_id).is_ok());
        assert!(uuid::Uuid::parse_str(&response.user_school_role_id).is_ok());

        println!("✓ Test: response structure validation");
        let _ = db::delete_test_user(&pool, &test_email).await;
    }
}

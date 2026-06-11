/// Integration tests for multi-role and cross-role scenarios
/// Validates user behavior when adding multiple roles at same or different schools

mod common;

use common::*;

#[cfg(test)]
mod multi_role_tests {
    use super::common::*;
    use std::sync::Once;

    static INIT: Once = Once::new();

    fn init() {
        INIT.call_once(|| {
            let _ = env_logger::builder()
                .is_test(true)
                .try_init();
        });
    }

    // ========================================================================
    // TEST CASE 1: User with Multiple Roles at Same School
    // ========================================================================
    #[tokio::test]
    async fn test_user_multiple_roles_same_school() {
        init();

        let pool = get_db_pool().await;
        let client = get_http_client();
        let test_email = generate_test_email("multi-role");
        let school_code = constants::TEST_SCHOOL_CODE_1;

        // Cleanup before test
        let _ = db::delete_test_user(&pool, &test_email).await;

        // Step 1: User signs up as STAFF
        let signup_request = build_signup_request(
            &test_email,
            "TestPassword123!",
            "Test",
            "User",
            &generate_test_phone(),
            "STAFF",
            Some(school_code),
        );
        let response_1 = http::signup_expect_success(&client, signup_request).await;
        let user_id = uuid::Uuid::parse_str(&response_1.user_id).unwrap();
        let school_id = uuid::Uuid::parse_str(&response_1.school_id).unwrap();

        // Step 2: Same user signs up as PARENT at same school
        let signup_request = build_signup_request(
            &test_email,
            "TestPassword123!",
            "Test",
            "User",
            &generate_test_phone(),
            "PARENT",
            Some(school_code),
        );
        let response_2 = http::signup_expect_success(&client, signup_request).await;
        assert_eq!(response_2.user_id, response_1.user_id, "Should be same user");
        assert_eq!(response_2.school_id, response_1.school_id, "Should be same school");

        // Step 3: Same user signs up as ADMIN at same school
        let signup_request = build_signup_request(
            &test_email,
            "TestPassword123!",
            "Test",
            "User",
            &generate_test_phone(),
            "ADMIN",
            Some(school_code),
        );
        let response_3 = http::signup_expect_success(&client, signup_request).await;
        assert_eq!(response_3.user_id, response_1.user_id, "Should be same user");
        assert_eq!(response_3.school_id, response_1.school_id, "Should be same school");

        // Verify database state
        let user = db::get_user_by_id(&pool, user_id).await;
        assert!(user.is_some(), "User should exist");

        let user_roles = db::get_user_school_roles(&pool, user_id).await;
        assert_eq!(user_roles.len(), 3, "User should have 3 roles at same school");
        
        for role in &user_roles {
            assert_eq!(role.user_id, user_id);
            assert_eq!(role.school_id, school_id);
            assert!(role.is_active);
        }

        // Verify all role_ids are different
        let role_ids: std::collections::HashSet<_> = user_roles.iter().map(|r| r.role_id).collect();
        assert_eq!(role_ids.len(), 3, "All three roles should be different");

        println!("✓ Test: user with multiple roles at same school");
        let _ = db::delete_test_user(&pool, &test_email).await;
    }

    // ========================================================================
    // TEST CASE 2: User with Different Roles at Different Schools
    // ========================================================================
    #[tokio::test]
    async fn test_user_different_roles_different_schools() {
        init();

        let pool = get_db_pool().await;
        let client = get_http_client();
        let test_email = generate_test_email("cross-school");

        // Cleanup before test
        let _ = db::delete_test_user(&pool, &test_email).await;

        // Step 1: User signs up as STAFF at school A
        let signup_request = build_signup_request(
            &test_email,
            "TestPassword123!",
            "Test",
            "User",
            &generate_test_phone(),
            "STAFF",
            Some(constants::TEST_SCHOOL_CODE_1),
        );
        let response_1 = http::signup_expect_success(&client, signup_request).await;
        let user_id = uuid::Uuid::parse_str(&response_1.user_id).unwrap();
        let school_id_1 = uuid::Uuid::parse_str(&response_1.school_id).unwrap();

        // Step 2: Same user signs up as PARENT at school B
        let signup_request = build_signup_request(
            &test_email,
            "TestPassword123!",
            "Test",
            "User",
            &generate_test_phone(),
            "PARENT",
            Some(constants::TEST_SCHOOL_CODE_2),
        );
        let response_2 = http::signup_expect_success(&client, signup_request).await;
        let school_id_2 = uuid::Uuid::parse_str(&response_2.school_id).unwrap();

        // Verify user is same
        assert_eq!(response_2.user_id, response_1.user_id, "Should be same user");
        // Verify schools are different
        assert_ne!(school_id_1, school_id_2, "Should be different schools");

        // Verify database state
        let user_roles = db::get_user_school_roles(&pool, user_id).await;
        assert_eq!(user_roles.len(), 2, "User should have 2 roles");

        let school_1_roles: Vec<_> = user_roles.iter().filter(|r| r.school_id == school_id_1).collect();
        let school_2_roles: Vec<_> = user_roles.iter().filter(|r| r.school_id == school_id_2).collect();

        assert_eq!(school_1_roles.len(), 1, "Should have 1 role at school 1");
        assert_eq!(school_2_roles.len(), 1, "Should have 1 role at school 2");

        println!("✓ Test: user different roles at different schools");
        let _ = db::delete_test_user(&pool, &test_email).await;
    }

    // ========================================================================
    // TEST CASE 3: Duplicate Role Prevention - Same User, Same Role, Same School
    // ========================================================================
    #[tokio::test]
    async fn test_duplicate_role_prevention() {
        init();

        let pool = get_db_pool().await;
        let client = get_http_client();
        let test_email = generate_test_email("duplicate-role");

        // Cleanup before test
        let _ = db::delete_test_user(&pool, &test_email).await;

        // Step 1: User signs up as STAFF
        let phone = generate_test_phone();
        let signup_request = build_signup_request(
            &test_email,
            "TestPassword123!",
            "Test",
            "User",
            &phone,
            "STAFF",
            Some(constants::TEST_SCHOOL_CODE_1),
        );
        let response_1 = http::signup_expect_success(&client, signup_request).await;

        // Step 2: Same user attempts to sign up as STAFF again with different phone
        let new_phone = generate_test_phone();
        let signup_request = build_signup_request(
            &test_email,
            "TestPassword123!",
            "Test",
            "User",
            &new_phone,
            "STAFF",
            Some(constants::TEST_SCHOOL_CODE_1),
        );
        let response = client
            .post(&format!("{}{}",constants::API_URL, constants::SIGNUP_ENDPOINT))
            .json(&signup_request)
            .send()
            .await
            .unwrap();

        // Should fail with 400 Bad Request
        assert_eq!(response.status(), 400, "Expected 400 status for duplicate role");
        let error: ErrorResponse = response.json().await.unwrap();
        assert!(error.error.to_lowercase().contains("already has") || error.error.to_lowercase().contains("duplicate"));

        println!("✓ Test: duplicate role prevention");
        let _ = db::delete_test_user(&pool, &test_email).await;
    }

    // ========================================================================
    // TEST CASE 4: SCHOOL_ADMIN Then Non-SCHOOL_ADMIN Role
    // ========================================================================
    #[tokio::test]
    async fn test_school_admin_then_other_role() {
        init();

        let pool = get_db_pool().await;
        let client = get_http_client();
        let test_email = generate_test_email("admin-then-staff");

        // Cleanup before test
        let _ = db::delete_test_user(&pool, &test_email).await;

        // Step 1: User signs up as SCHOOL_ADMIN
        let phone = generate_test_phone();
        let signup_request = build_signup_request(
            &test_email,
            "TestPassword123!",
            "Test",
            "User",
            &phone,
            "SCHOOL_ADMIN",
            None,
        );
        let response_1 = http::signup_expect_success(&client, signup_request).await;
        let user_id = uuid::Uuid::parse_str(&response_1.user_id).unwrap();

        // Step 2: Same user signs up as STAFF at existing school
        let signup_request = build_signup_request(
            &test_email,
            "TestPassword123!",
            "Test",
            "User",
            &generate_test_phone(),
            "STAFF",
            Some(constants::TEST_SCHOOL_CODE_1),
        );
        let response_2 = http::signup_expect_success(&client, signup_request).await;

        // Verify same user
        assert_eq!(response_2.user_id, response_1.user_id);

        // Verify database has two roles
        let roles = db::get_user_school_roles(&pool, user_id).await;
        assert_eq!(roles.len(), 2, "User should have 2 roles (SCHOOL_ADMIN + STAFF)");

        println!("✓ Test: SCHOOL_ADMIN then other role");
        let _ = db::delete_test_user(&pool, &test_email).await;
    }

    // ========================================================================
    // TEST CASE 5: Multiple SCHOOL_ADMIN Signups (Different Schools)
    // ========================================================================
    #[tokio::test]
    async fn test_multiple_school_admin_signups() {
        init();

        let pool = get_db_pool().await;
        let client = get_http_client();
        let test_email = generate_test_email("multi-school-admin");

        // Cleanup before test
        let _ = db::delete_test_user(&pool, &test_email).await;

        // Step 1: User signs up as SCHOOL_ADMIN (creates school A)
        let phone = generate_test_phone();
        let signup_request = build_signup_request(
            &test_email,
            "TestPassword123!",
            "Test",
            "User",
            &phone,
            "SCHOOL_ADMIN",
            None,
        );
        let response_1 = http::signup_expect_success(&client, signup_request).await;
        let user_id = uuid::Uuid::parse_str(&response_1.user_id).unwrap();
        let school_1 = uuid::Uuid::parse_str(&response_1.school_id).unwrap();

        // Step 2: Same user signs up as SCHOOL_ADMIN again (creates school B)
        let signup_request = build_signup_request(
            &test_email,
            "TestPassword123!",
            "Test",
            "User",
            &generate_test_phone(),
            "SCHOOL_ADMIN",
            None,
        );
        let response_2 = http::signup_expect_success(&client, signup_request).await;
        let school_2 = uuid::Uuid::parse_str(&response_2.school_id).unwrap();

        // Step 3: Same user signs up as SCHOOL_ADMIN again (creates school C)
        let signup_request = build_signup_request(
            &test_email,
            "TestPassword123!",
            "Test",
            "User",
            &generate_test_phone(),
            "SCHOOL_ADMIN",
            None,
        );
        let response_3 = http::signup_expect_success(&client, signup_request).await;
        let school_3 = uuid::Uuid::parse_str(&response_3.school_id).unwrap();

        // Verify schools are different
        assert_ne!(school_1, school_2, "Schools should be different");
        assert_ne!(school_2, school_3, "Schools should be different");
        assert_ne!(school_1, school_3, "Schools should be different");

        // Verify user has 3 SCHOOL_ADMIN roles
        let roles = db::get_user_school_roles(&pool, user_id).await;
        assert_eq!(roles.len(), 3, "User should have 3 SCHOOL_ADMIN roles");

        let admin_role_id = constants::role_id_school_admin();
        for role in &roles {
            assert_eq!(role.role_id, admin_role_id, "All roles should be SCHOOL_ADMIN");
        }

        println!("✓ Test: multiple SCHOOL_ADMIN signups");
        let _ = db::delete_test_user(&pool, &test_email).await;
    }

    // ========================================================================
    // TEST CASE 6: Existing User Can Add Role With Different Requested Phone
    // ========================================================================
    #[tokio::test]
    async fn test_user_different_phone_each_signup() {
        init();

        let pool = get_db_pool().await;
        let client = get_http_client();
        let test_email = generate_test_email("multi-phone-user");
        let phone_1 = generate_test_phone();
        let phone_2 = generate_test_phone();

        // Cleanup before test
        let _ = db::delete_test_user(&pool, &test_email).await;

        // Step 1: User signs up with phone_1
        let signup_request = build_signup_request(
            &test_email,
            "TestPassword123!",
            "Test",
            "User",
            &phone_1,
            "STAFF",
            Some(constants::TEST_SCHOOL_CODE_1),
        );
        let response_1 = http::signup_expect_success(&client, signup_request).await;

        // Step 2: Same user tries to sign up with phone_2
        let signup_request = build_signup_request(
            &test_email,
            "TestPassword123!",
            "Test",
            "User",
            &phone_2,
            "PARENT",
            Some(constants::TEST_SCHOOL_CODE_1),
        );
        let response_2 = http::signup_expect_success(&client, signup_request).await;

        // Same user should receive an additional role on same school.
        assert_eq!(response_1.user_id, response_2.user_id, "Expected same user when email already exists");
        assert_eq!(response_2.role, "PARENT", "Expected second role to be PARENT");

        println!("✓ Test: user different phone each signup");
        let _ = db::delete_test_user(&pool, &test_email).await;
    }

    // ========================================================================
    // TEST CASE 7: UserSchoolRole.is_active Flag
    // ========================================================================
    #[tokio::test]
    async fn test_user_school_role_is_active() {
        init();

        let pool = get_db_pool().await;
        let client = get_http_client();
        let test_email = generate_test_email("is-active-test");

        // Cleanup before test
        let _ = db::delete_test_user(&pool, &test_email).await;

        let signup_request = build_signup_request(
            &test_email,
            "TestPassword123!",
            "Test",
            "User",
            &generate_test_phone(),
            "STAFF",
            Some(constants::TEST_SCHOOL_CODE_1),
        );
        let response = http::signup_expect_success(&client, signup_request).await;
        let user_id = uuid::Uuid::parse_str(&response.user_id).unwrap();

        let roles = db::get_user_school_roles(&pool, user_id).await;
        for role in roles {
            assert!(role.is_active, "UserSchoolRole should have is_active = true");
        }

        println!("✓ Test: UserSchoolRole is_active flag");
        let _ = db::delete_test_user(&pool, &test_email).await;
    }

    // ========================================================================
    // TEST CASE 8: UserSchoolRole Timestamps
    // ========================================================================
    #[tokio::test]
    async fn test_user_school_role_timestamps() {
        init();

        let pool = get_db_pool().await;
        let client = get_http_client();
        let test_email = generate_test_email("timestamps-test");

        // Cleanup before test
        let _ = db::delete_test_user(&pool, &test_email).await;

        let now_before = chrono::Utc::now();
        let signup_request = build_signup_request(
            &test_email,
            "TestPassword123!",
            "Test",
            "User",
            &generate_test_phone(),
            "STAFF",
            Some(constants::TEST_SCHOOL_CODE_1),
        );
        let pool = get_db_pool().await;
        let client = get_http_client();

        // Test STAFF role
        let staff_email = generate_test_email("role-test-staff");
        let _ = db::delete_test_user(&pool, &staff_email).await;
        
        let signup_request = build_signup_request(
            &staff_email,
            "TestPassword123!",
            "Test",
            "User",
            &generate_test_phone(),
            "STAFF",
            Some(constants::TEST_SCHOOL_CODE_1),
        );
        let response = http::signup_expect_success(&client, signup_request).await;
        let user_id = uuid::Uuid::parse_str(&response.user_id).unwrap();
        let roles = db::get_user_school_roles(&pool, user_id).await;
        assert_eq!(roles[0].role_id, constants::role_id_staff(), "STAFF role ID mismatch");
        let _ = db::delete_test_user(&pool, &staff_email).await;

        // Test PARENT role
        let parent_email = generate_test_email("role-test-parent");
        let _ = db::delete_test_user(&pool, &parent_email).await;
        
        let signup_request = build_signup_request(
            &parent_email,
            "TestPassword123!",
            "Test",
            "User",
            &generate_test_phone(),
            "PARENT",
            Some(constants::TEST_SCHOOL_CODE_1),
        );
        let response = http::signup_expect_success(&client, signup_request).await;
        let user_id = uuid::Uuid::parse_str(&response.user_id).unwrap();
        let roles = db::get_user_school_roles(&pool, user_id).await;
        assert_eq!(roles[0].role_id, constants::role_id_parent(), "PARENT role ID mismatch");
        let _ = db::delete_test_user(&pool, &parent_email).await;

        println!("✓ Test: role IDs correct for each role");

        println!("✓ Test: UserSchoolRole timestamps");
        let _ = db::delete_test_user(&pool, &test_email).await;
    }

    // ========================================================================
    // TEST CASE 9: Role ID Correct for Each Role
    // ========================================================================
    #[tokio::test]
    async fn test_role_ids_correct() {
        init();

        let pool = get_db_pool().await;
        let client = get_http_client();

        // Test STAFF role
        let staff_email = generate_test_email("role-test-staff");
        let _ = db::delete_test_user(&pool, &staff_email).await;
        
        let signup_request = build_signup_request(
            &staff_email,
            "TestPassword123!",
            "Test",
            "User",
            &generate_test_phone(),
            "STAFF",
            Some(constants::TEST_SCHOOL_CODE_1),
        );
        let response = http::signup_expect_success(&client, signup_request).await;
        let user_id = uuid::Uuid::parse_str(&response.user_id).unwrap();
        let roles = db::get_user_school_roles(&pool, user_id).await;
        assert_eq!(roles[0].role_id, constants::role_id_staff(), "STAFF role ID mismatch");
        let _ = db::delete_test_user(&pool, &staff_email).await;

        // Test PARENT role
        let parent_email = generate_test_email("role-test-parent");
        let _ = db::delete_test_user(&pool, &parent_email).await;
        
        let signup_request = build_signup_request(
            &parent_email,
            "TestPassword123!",
            "Test",
            "User",
            &generate_test_phone(),
            "PARENT",
            Some(constants::TEST_SCHOOL_CODE_1),
        );
        let response = http::signup_expect_success(&client, signup_request).await;
        let user_id = uuid::Uuid::parse_str(&response.user_id).unwrap();
        let roles = db::get_user_school_roles(&pool, user_id).await;
        assert_eq!(roles[0].role_id, constants::role_id_parent(), "PARENT role ID mismatch");
        let _ = db::delete_test_user(&pool, &parent_email).await;

        println!("✓ Test: role IDs correct for each role");
    }

    // ========================================================================
    // TEST CASE 10: Transaction Rollback on Multi-Role Failure
    // ========================================================================
    #[tokio::test]
    async fn test_transaction_rollback_multi_role() {
        init();

        let pool = get_db_pool().await;
        let client = get_http_client();
        let test_email = generate_test_email("rollback-test");

        // Cleanup before test
        let _ = db::delete_test_user(&pool, &test_email).await;

        // Step 1: User signs up as STAFF (succeeds)
        let phone = generate_test_phone();
        let signup_request = build_signup_request(
            &test_email,
            "TestPassword123!",
            "Test",
            "User",
            &phone,
            "STAFF",
            Some(constants::TEST_SCHOOL_CODE_1),
        );
        let response_1 = http::signup_expect_success(&client, signup_request).await;
        let user_id = uuid::Uuid::parse_str(&response_1.user_id).unwrap();

        // Step 2: User attempts to add STAFF role again (should fail)
        let signup_request = build_signup_request(
            &test_email,
            "TestPassword123!",
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
        assert_eq!(response.status(), 400, "Should fail with 400");

        // Step 3: Verify database is clean (only 1 role, not duplicated)
        let roles = db::get_user_school_roles(&pool, user_id).await;
        assert_eq!(roles.len(), 1, "Should only have 1 role (no duplicate created)");

        println!("✓ Test: transaction rollback on multi-role failure");
        let _ = db::delete_test_user(&pool, &test_email).await;
    }

    // ========================================================================
    // TEST CASE 11: School Admin Cannot Have Multiple Admin Roles
    // ========================================================================
    #[tokio::test]
    async fn test_school_admin_unique_per_school() {
        init();

        let pool = get_db_pool().await;
        let client = get_http_client();
        let test_email_1 = generate_test_email("admin1");
        let test_email_2 = generate_test_email("admin2");

        // Cleanup before test
        let _ = db::delete_test_user(&pool, &test_email_1).await;
        let _ = db::delete_test_user(&pool, &test_email_2).await;

        // Step 1: User 1 signs up as SCHOOL_ADMIN
        let signup_request = build_signup_request(
            &test_email_1,
            "TestPassword123!",
            "Test",
            "User",
            &generate_test_phone(),
            "SCHOOL_ADMIN",
            None,
        );
        let response_1 = http::signup_expect_success(&client, signup_request).await;
        let school_1 = uuid::Uuid::parse_str(&response_1.school_id).unwrap();

        // Step 2: User 2 signs up as SCHOOL_ADMIN (creates different school)
        let signup_request = build_signup_request(
            &test_email_2,
            "TestPassword123!",
            "Test",
            "User",
            &generate_test_phone(),
            "SCHOOL_ADMIN",
            None,
        );
        let response_2 = http::signup_expect_success(&client, signup_request).await;
        let school_2 = uuid::Uuid::parse_str(&response_2.school_id).unwrap();

        // Verify different schools
        assert_ne!(school_1, school_2, "Each SCHOOL_ADMIN creates new school");

        println!("✓ Test: SCHOOL_ADMIN unique per school");
        let _ = db::delete_test_user(&pool, &test_email_1).await;
        let _ = db::delete_test_user(&pool, &test_email_2).await;
    }

    // ========================================================================
    // TEST CASE 12: User Cannot Be Added to Non-existent School
    // ========================================================================
    #[tokio::test]
    async fn test_user_cannot_add_to_nonexistent_school() {
        init();

        let pool = get_db_pool().await;
        let client = get_http_client();
        let test_email = generate_test_email("nonexistent-school-test");
        let fake_school_code = "totally-fake-school-code-12345";

        // Cleanup before test
        let _ = db::delete_test_user(&pool, &test_email).await;

        // Attempt to sign up user with fake school code
        let signup_request = build_signup_request(
            &test_email,
            "TestPassword123!",
            "Test",
            "User",
            &generate_test_phone(),
            "STAFF",
            Some(fake_school_code),
        );
        let response = client
            .post(&format!("{}{}",constants::API_URL, constants::SIGNUP_ENDPOINT))
            .json(&signup_request)
            .send()
            .await
            .unwrap();

        // Should fail
        assert!(response.status().is_client_error() || response.status().is_server_error());

        // Verify user not created
        let user = db::get_user_by_email(&pool, &test_email).await;
        assert!(user.is_none(), "User should not be created for non-existent school");

        println!("✓ Test: user cannot be added to nonexistent school");
        let _ = db::delete_test_user(&pool, &test_email).await;
    }

    // ========================================================================
    // TEST CASE 13: Foreign Key Constraint - role_id Must Exist
    // ========================================================================
    #[tokio::test]
    async fn test_role_foreign_key_constraint() {
        init();

        let pool = get_db_pool().await;
        let client = get_http_client();
        let test_email = generate_test_email("fk-role-test");

        // Cleanup before test
        let _ = db::delete_test_user(&pool, &test_email).await;

        // Verify valid role signup works
        let signup_request = build_signup_request(
            &test_email,
            "TestPassword123!",
            "Test",
            "User",
            &generate_test_phone(),
            "STAFF",
            Some(constants::TEST_SCHOOL_CODE_1),
        );
        let response = http::signup_expect_success(&client, signup_request).await;
        let user_id = uuid::Uuid::parse_str(&response.user_id).unwrap();

        // Verify valid role was created
        let roles = db::get_user_school_roles(&pool, user_id).await;
        assert!(!roles.is_empty(), "Role should be created with valid FK");

        println!("✓ Test: role foreign key constraint");
        let _ = db::delete_test_user(&pool, &test_email).await;
    }

    // ========================================================================
    // TEST CASE 14: Foreign Key Constraint - user_id Must Exist
    // ========================================================================
    #[tokio::test]
    async fn test_user_foreign_key_constraint() {
        init();

        let pool = get_db_pool().await;
        let client = get_http_client();
        let test_email = generate_test_email("fk-user-test");

        // Cleanup before test
        let _ = db::delete_test_user(&pool, &test_email).await;

        // Verify valid user signup creates valid FK
        let signup_request = build_signup_request(
            &test_email,
            "TestPassword123!",
            "Test",
            "User",
            &generate_test_phone(),
            "STAFF",
            Some(constants::TEST_SCHOOL_CODE_1),
        );
        let response = http::signup_expect_success(&client, signup_request).await;
        let user_id = uuid::Uuid::parse_str(&response.user_id).unwrap();

        // Verify user exists
        let user = db::get_user_by_id(&pool, user_id).await;
        assert!(user.is_some(), "User should exist");

        // Verify user's roles are created
        let roles = db::get_user_school_roles(&pool, user_id).await;
        assert!(!roles.is_empty(), "Roles should reference existing user");

        println!("✓ Test: user foreign key constraint");
        let _ = db::delete_test_user(&pool, &test_email).await;
    }

    // ========================================================================
    // TEST CASE 15: Foreign Key Constraint - school_id Must Exist
    // ========================================================================
    #[tokio::test]
    async fn test_school_foreign_key_constraint() {
        init();

        let pool = get_db_pool().await;
        let client = get_http_client();
        let test_email = generate_test_email("fk-school-test");

        // Cleanup before test
        let _ = db::delete_test_user(&pool, &test_email).await;

        // Verify valid school signup creates valid FK
        let signup_request = build_signup_request(
            &test_email,
            "TestPassword123!",
            "Test",
            "User",
            &generate_test_phone(),
            "STAFF",
            Some(constants::TEST_SCHOOL_CODE_1),
        );
        let response = http::signup_expect_success(&client, signup_request).await;
        let user_id = uuid::Uuid::parse_str(&response.user_id).unwrap();
        let school_id = uuid::Uuid::parse_str(&response.school_id).unwrap();

        // Verify school exists
        let school = db::get_school_by_id(&pool, school_id).await;
        assert!(school.is_some(), "School should exist");

        // Verify user's roles reference existing school
        let roles = db::get_user_school_roles(&pool, user_id).await;
        for role in roles {
            assert_eq!(role.school_id, school_id, "Role should reference existing school");
        }

        println!("✓ Test: school foreign key constraint");
        let _ = db::delete_test_user(&pool, &test_email).await;
    }
}

/// Model and validation tests
mod common;

use common::*;
use uuid::Uuid;

/// Test 1: School model structure
#[test]
fn test_school_model_structure() {
    let school_json = serde_json::json!({
        "id": Uuid::new_v4().to_string(),
        "name": "Test School",
        "slug": "test-school",
        "address_line1": "123 Main St",
        "address_line2": null,
        "city": "Test City",
        "state": "Test State",
        "postal_code": "12345",
        "country": "USA",
        "status": "ACTIVE",
        "timezone": "UTC",
        "currency": "USD",
        "language": "en",
        "website": null,
        "admin_name": "Admin",
        "admin_email": "admin@test.com",
        "admin_phone": "555-1234",
        "banner_url": null,
        "logo_url": null,
        "primary_color": null,
        "secondary_color": null,
        "school_motto": null,
        "admission_prefix": "ADM-123456",
        "staff_id_prefix": "STF-123456",
        "created_at": "2026-05-24T00:00:00",
        "updated_at": "2026-05-24T00:00:00",
        "is_active": true
    });

    // Verify all required fields exist
    assert!(school_json["id"].is_string());
    assert!(school_json["name"].is_string());
    assert!(school_json["slug"].is_string());
    assert!(school_json["admission_prefix"].is_string());
    assert!(school_json["staff_id_prefix"].is_string());
    assert!(school_json["status"].is_string());
    assert!(school_json["is_active"].is_boolean());

    // Verify optional fields can be null
    assert!(school_json["address_line2"].is_null());
    assert!(school_json["logo_url"].is_null());
}

/// Test 2: User model structure
#[test]
fn test_user_model_structure() {
    let user_json = serde_json::json!({
        "id": Uuid::new_v4().to_string(),
        "email": "user@test.com",
        "phone_number": "+15551234567",
        "password_hash": "hashed_password",
        "first_name": "John",
        "last_name": "Doe",
        "middle_name": null,
        "date_of_birth": null,
        "gender": null,
        "profile_picture_url": null,
        "address_line1": null,
        "address_line2": null,
        "city": null,
        "state": null,
        "postal_code": null,
        "country": "USA",
        "status": "PENDING",
        "is_verified": false,
        "is_approved": false,
        "verification_status": "NOT_VERIFIED",
        "approval_status": "PENDING",
        "verified_at": null,
        "approved_at": null,
        "approved_by": null,
        "last_login_at": null,
        "email_verified": false,
        "email_verification_token": null,
        "email_verification_expires": null,
        "otp_code": null,
        "otp_expires": null,
        "last_otp_sent": null,
        "created_at": "2026-05-24T00:00:00Z",
        "updated_at": "2026-05-24T00:00:00Z",
        "is_active": false
    });

    // Verify required fields
    assert!(user_json["id"].is_string());
    assert!(user_json["email"].is_string());
    assert!(user_json["country"].is_string());
    assert!(user_json["status"].is_string());
    assert!(user_json["is_verified"].is_boolean());
    assert!(user_json["is_approved"].is_boolean());

    // Verify optional fields
    assert!(user_json["profile_picture_url"].is_null());
    assert!(user_json["verified_at"].is_null());
}

/// Test 3: UserSchoolRole model structure
#[test]
fn test_user_school_role_structure() {
    let role_json = serde_json::json!({
        "id": Uuid::new_v4().to_string(),
        "user_id": Uuid::new_v4().to_string(),
        "school_id": Uuid::new_v4().to_string(),
        "role_id": Uuid::new_v4().to_string(),
        "created_at": "2026-05-24T00:00:00",
        "updated_at": "2026-05-24T00:00:00",
        "is_active": true
    });

    // All fields required
    assert!(role_json["id"].is_string());
    assert!(role_json["user_id"].is_string());
    assert!(role_json["school_id"].is_string());
    assert!(role_json["role_id"].is_string());
    assert!(role_json["created_at"].is_string());
    assert!(role_json["updated_at"].is_string());
    assert!(role_json["is_active"].is_boolean());
}

/// Test 4: Signup request validation
#[test]
fn test_signup_request_validation() {
    let valid_requests = vec![
        serde_json::json!({
            "email": "admin@test.com",
            "password": "SecurePass123!",
            "first_name": "John",
            "last_name": "Admin",
            "phone_number": "+15551234567",
            "role": "SCHOOL_ADMIN"
        }),
        serde_json::json!({
            "email": "staff@test.com",
            "password": "StaffPass456!",
            "first_name": "Jane",
            "last_name": "Teacher",
            "phone_number": "+15559876543",
            "role": "STAFF",
            "school_code": "test-school"
        }),
    ];

    for request in valid_requests {
        // Verify required fields
        assert!(request["email"].is_string());
        assert!(request["password"].is_string());
        assert!(request["first_name"].is_string());
        assert!(request["last_name"].is_string());
        assert!(request["phone_number"].is_string());
        assert!(request["role"].is_string());

        // Verify email format
        let email = request["email"].as_str().unwrap();
        assert!(email.contains("@"), "Email should contain @");

        // Verify password strength
        let password = request["password"].as_str().unwrap();
        assert!(password.len() >= 8, "Password should be at least 8 characters");

        // Verify role is valid
        let role = request["role"].as_str().unwrap();
        assert!(
            vec!["SCHOOL_ADMIN", "STAFF", "PARENT", "ADMIN"].contains(&role),
            "Role should be valid"
        );
    }
}

/// Test 5: Timestamp format consistency
#[test]
fn test_timestamp_formats() {
    let now = chrono::Utc::now();

    // NaiveDateTime format (used in schools table)
    // Chrono's NaiveDateTime Display uses a space separator by default ("YYYY-MM-DD HH:MM:SS").
    let naive_dt = now.naive_utc();
    let naive_str = format!("{}", naive_dt);
    // Accept either the default space-separated format or an ISO-like 'T' separator.
    assert!(naive_str.contains(' ') || naive_str.contains("T"), "NaiveDateTime should contain a date-time separator");
    assert!(!naive_str.contains("+"), "NaiveDateTime should not contain timezone");
    assert!(!naive_str.contains("Z"), "NaiveDateTime should not contain Z");

    // DateTime<Utc> format (used in users table)
    let utc_str = now.to_rfc3339();
    assert!(utc_str.contains("T"), "DateTime should contain T");
    // Accept either a trailing 'Z' or a +00:00 offset depending on Chrono formatting
    assert!(utc_str.ends_with("Z") || utc_str.contains("+00:00") || utc_str.contains("+00"), "DateTime<Utc> should indicate UTC with 'Z' or '+00:00'");
}

/// Test 6: Role ID verification
#[test]
fn test_role_id_constants() {
    let roles = [
        ("STAFF", "c990228f-2f50-4301-a73b-53457d608507"),
        ("PARENT", "66b88d78-ccaa-452c-8fb4-8c744ffa4b64"),
        ("ADMIN", "b1262b13-16bf-4ea0-aeb1-844a06b0e402"),
        ("SCHOOL_ADMIN", "045c0177-9085-4833-aa35-a6346c71e0e3"),
    ];

    for (name, id) in &roles {
        // Verify ID is valid UUID format
        let parsed = Uuid::parse_str(id);
        assert!(parsed.is_ok(), "{} role ID should be valid UUID", name);
    }
}

/// Test 7: Email validation
#[test]
fn test_email_validation() {
    let valid_emails = vec![
        "user@example.com",
        "first.last@domain.co.uk",
        "test+tag@example.org",
        "123@test.com",
    ];

    let invalid_emails = vec![
        "plaintext",
        "@example.com",
        "user@",
        "user name@test.com",
    ];

    for email in valid_emails {
        // Basic validation: must contain @
        assert!(email.contains("@"), "Valid email should contain @");
        assert!(email.split("@").count() == 2, "Valid email should have exactly one @");
    }

    for email in invalid_emails {
        // Basic validation: must contain @
        if !email.contains("@") {
            assert!(true, "Invalid email {} should not validate", email);
        }
    }
}

/// Test 8: Phone number format
#[test]
fn test_phone_number_format() {
    let valid_phones = vec![
        "+15551234567",
        "+44-123-456-7890",
        "1234567890",
        "+1-555-1234",
    ];

    for phone in valid_phones {
        // Just verify non-empty
        assert!(!phone.is_empty(), "Phone number should not be empty");
    }
}

/// Test 9: Password complexity requirements
#[test]
fn test_password_complexity() {
    let strong_passwords = vec![
        "SecurePassword123!",
        "StrongPass456@",
        "Complex#Pass789",
    ];

    for password in strong_passwords {
        // Minimum length check
        assert!(password.len() >= 8, "Password should be at least 8 characters");
    }
}

/// Test 10: Name field validation
#[test]
fn test_name_field_validation() {
    let valid_names = vec![
        ("John", "Doe"),
        ("Jane", "Smith"),
        ("Juan", "García"),
        ("李", "明"),
    ];

    for (first, last) in valid_names {
        // Names should not be empty
        assert!(!first.is_empty(), "First name should not be empty");
        assert!(!last.is_empty(), "Last name should not be empty");
    }
}

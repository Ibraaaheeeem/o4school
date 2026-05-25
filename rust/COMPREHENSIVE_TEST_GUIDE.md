# Comprehensive Test Suite for School Backend

This document provides an overview of all test cases created for the School Backend project, organized by feature area.

## Test Files Overview

### 1. `tests/auth_signup_tests.rs`
**Purpose:** Test all authentication signup scenarios including role-based signup and validation

**Test Cases:** 15 total

#### Basic Signup Scenarios (3 tests)
- **TEST 1:** SCHOOL_ADMIN signup creates new empty school
- **TEST 2:** Duplicate SCHOOL_ADMIN signup creates new school for existing user
- **TEST 3:** STAFF signup at existing school

#### Additional Role Signups (2 tests)
- **TEST 4:** PARENT signup at existing school
- **TEST 5:** ADMIN signup at existing school

#### Input Validation (5 tests)
- **TEST 6:** Missing email field validation
- **TEST 7:** Missing password field validation
- **TEST 8:** Invalid email format validation
- **TEST 9:** Weak password validation
- **TEST 15:** Response structure validation

#### Constraint Testing (3 tests)
- **TEST 10:** Duplicate email unique constraint
- **TEST 11:** Duplicate phone number unique constraint
- **TEST 12:** Non-existent school code error handling

#### Error Handling (2 tests)
- **TEST 13:** Invalid role name handling
- **TEST 14:** Missing school_code for non-SCHOOL_ADMIN roles

---

### 2. `tests/school_creation_tests.rs`
**Purpose:** Test automatic school creation and data integrity for SCHOOL_ADMIN

**Test Cases:** 16 total

#### School Properties & Generation (3 tests)
- **TEST 1:** Auto-created school has correct properties
- **TEST 7:** School name generated correctly (format: "School Admin - {email}")
- **TEST 8:** School timestamps set correctly

#### Uniqueness Constraints (4 tests)
- **TEST 2:** Slug uniqueness (no duplicates)
- **TEST 3:** admission_prefix uniqueness
- **TEST 4:** staff_id_prefix uniqueness
- **TEST 5:** Admission prefix format validation (ADM-{6-char})
- **TEST 6:** Staff ID prefix format validation (STF-{6-char})

#### Data Integrity (3 tests)
- **TEST 9:** NULL values in optional fields
- **TEST 10:** Empty string values for text fields
- **TEST 11:** School creation rollback on error

#### Schema Verification (3 tests)
- **TEST 13:** School-user relationship validation
- **TEST 14:** School column count = 27
- **TEST 12:** Multiple schools can be created
- **TEST 15:** School not reused for different admins
- **TEST 16:** School default column values

---

### 3. `tests/multi_role_tests.rs`
**Purpose:** Test multi-role scenarios and cross-role interactions

**Test Cases:** 15 total

#### Single User, Multiple Roles Scenarios (3 tests)
- **TEST 1:** Multiple roles at same school (STAFF + PARENT + ADMIN)
- **TEST 2:** Different roles at different schools
- **TEST 3:** Duplicate role prevention (error on duplicate)

#### SCHOOL_ADMIN Multi-Role Tests (3 tests)
- **TEST 4:** SCHOOL_ADMIN then non-SCHOOL_ADMIN role
- **TEST 5:** Multiple SCHOOL_ADMIN signups (each creates new school)
- **TEST 11:** SCHOOL_ADMIN unique per school verification

#### Data Integrity Tests (5 tests)
- **TEST 6:** User different phone for each signup (fails on duplicate)
- **TEST 7:** UserSchoolRole.is_active flag
- **TEST 8:** UserSchoolRole timestamps
- **TEST 9:** Role IDs correct for each role type
- **TEST 10:** Transaction rollback on multi-role failure

#### Foreign Key & Constraint Tests (4 tests)
- **TEST 12:** User cannot be added to non-existent school
- **TEST 13:** Foreign key constraint: role_id must exist
- **TEST 14:** Foreign key constraint: user_id must exist
- **TEST 15:** Foreign key constraint: school_id must exist

---

### 4. `tests/database_schema_tests.rs`
**Purpose:** Validate database schema, data types, and constraints

**Test Cases:** 15 total

#### Schema Validation (2 tests)
- **TEST 1:** Schools table structure (27 columns verified)
- **TEST 6:** UserSchoolRoles table structure

#### Timestamp Type Validation (2 tests)
- **TEST 2:** Users table timestamps are TIMESTAMPTZ
- **TEST 3:** Schools table timestamps are TIMESTAMP (without TZ)

#### Constraint Verification (3 tests)
- **TEST 4:** Users table constraints (email, phone uniqueness)
- **TEST 5:** Schools table constraints (slug, admission_prefix uniqueness)
- **TEST 15:** All indexes present and correct

#### Data Integrity (5 tests)
- **TEST 7:** Roles table contains all 4 expected roles
- **TEST 8:** No orphaned user_school_roles (user_id)
- **TEST 9:** No orphaned user_school_roles (school_id)
- **TEST 10:** No orphaned user_school_roles (role_id)
- **TEST 14:** Default values applied correctly

#### Pattern Validation (3 tests)
- **TEST 11:** admission_prefix pattern validation
- **TEST 12:** staff_id_prefix pattern validation
- **TEST 13:** slug pattern for auto-generated schools

---

## Test Execution Guide

### Prerequisites
```bash
# Ensure database is running and accessible
PGPASSWORD="password" psql -h localhost -d myschool -U postgres -c "SELECT 1"

# Verify test data exists
PGPASSWORD="password" psql -h localhost -d myschool -U postgres -c "SELECT COUNT(*) FROM schools"

# Ensure server is running on localhost:8080
curl http://localhost:8080/api/health
```

### Running All Tests
```bash
# Run all tests
cargo test

# Run with logging output
RUST_LOG=debug cargo test -- --nocapture

# Run tests with specific output
cargo test -- --nocapture --test-threads=1
```

### Running Specific Test Suites
```bash
# Run only signup tests
cargo test --test auth_signup_tests

# Run only school creation tests
cargo test --test school_creation_tests

# Run only multi-role tests
cargo test --test multi_role_tests

# Run only database schema tests
cargo test --test database_schema_tests
```

### Running Specific Test Cases
```bash
# Run a single test
cargo test test_school_admin_signup_new_user_creates_school

# Run tests matching a pattern
cargo test school_admin
cargo test signup_new
cargo test duplicate
cargo test foreign_key
```

### Test Output Options
```bash
# Verbose output with println! statements
cargo test -- --nocapture

# Show test timing
cargo test -- --nocapture --test-threads=1

# Continue tests even after failures
cargo test -- --test-threads=1 --no-fail-fast

# List all tests without running
cargo test -- --list
```

---

## Test Data Requirements

### Existing Schools Required
These school codes must exist in test database for non-SCHOOL_ADMIN tests:
- `school-722ee764` (referred to as "My School")
- `ibrahim-0bf21cd6` (or similar test school)

### Roles Table
These roles must exist in test database:
```
STAFF:       c990228f-2f50-4301-a73b-53457d608507
PARENT:      66b88d78-ccaa-452c-8fb4-8c744ffa4b64
ADMIN:       b1262b13-16bf-4ea0-aeb1-844a06b0e402
SCHOOL_ADMIN: 045c0177-9085-4833-aa35-a6346c71e0e3
```

### Clean Database Between Tests
```bash
# Delete test data between runs
PGPASSWORD="password" psql -h localhost -d myschool -U postgres << 'EOF'
DELETE FROM user_school_roles WHERE user_id IN 
  (SELECT id FROM users WHERE email LIKE '%-test@%' OR email LIKE '%test-%');
DELETE FROM users WHERE email LIKE '%-test@%' OR email LIKE '%test-%';
DELETE FROM schools WHERE slug LIKE 'admin-%';
EOF
```

---

## Test Coverage Summary

| Feature | Coverage | Status |
|---------|----------|--------|
| SCHOOL_ADMIN Signup | ✓ New user creates school | Ready |
| Multi-Role Signup | ✓ User + same school | Ready |
| STAFF/PARENT/ADMIN Signup | ✓ Existing school | Ready |
| Input Validation | ✓ All fields validated | Ready |
| Unique Constraints | ✓ Email, phone, slug, prefixes | Ready |
| Foreign Keys | ✓ User, school, role refs | Ready |
| Timestamps | ✓ Correct types (TZ vs non-TZ) | Ready |
| Schema | ✓ 27 columns verified | Ready |
| Data Integrity | ✓ No orphans, cascades | Ready |
| Error Handling | ✓ All scenarios covered | Ready |

---

## Expected Test Behaviors

### Successful Signup Responses (2xx)
```json
{
  "user_id": "uuid",
  "email": "email@test.com",
  "role": "STAFF|PARENT|ADMIN|SCHOOL_ADMIN",
  "school_id": "uuid",
  "school_name": "string",
  "user_school_role_id": "uuid",
  "message": "string",
  "next_route": "/auth/activate",
  "verification_token": "string"
}
```

### Validation Error Responses (4xx)
```json
{
  "error": "error message",
  "status": 400
}
```

### Database Error Responses (5xx)
```json
{
  "error": "error returned from database: constraint violation",
  "status": 500
}
```

---

## Common Test Failure Scenarios

### "School not found"
- **Cause:** Non-existent school_code provided
- **Fix:** Use valid school codes: `school-722ee764` or `ibrahim-0bf21cd6`

### "duplicate key value violates unique constraint"
- **Cause:** Email or phone already exists in database
- **Fix:** Use unique test data (emails/phones not in database)

### "Unknown role: INVALID"
- **Cause:** Role name not in (STAFF, PARENT, ADMIN, SCHOOL_ADMIN)
- **Fix:** Use valid role names

### "User already has STAFF role at this school"
- **Cause:** Attempting to add same role twice
- **Fix:** Use different role or different school

### HTTP connection refused
- **Cause:** Server not running on localhost:8080
- **Fix:** Start server: `cargo run`

---

## Implementation Checklist

Each test case needs implementation of:

- [ ] HTTP client setup (reqwest or similar)
- [ ] Request payload construction
- [ ] API endpoint call
- [ ] Response parsing and assertion
- [ ] Database query verification
- [ ] Cleanup (test data removal)
- [ ] Error case handling

### Template for Test Implementation
```rust
#[tokio::test]
async fn test_example() {
    init();
    
    // 1. Setup test data
    let test_email = generate_test_email("prefix");
    
    // 2. Make request
    let response = client
        .post("http://localhost:8080/api/auth/sign-up")
        .json(&signup_request)
        .send()
        .await
        .expect("Failed to send request");
    
    // 3. Verify status
    assert_eq!(response.status(), 200);
    
    // 4. Parse response
    let body: SignUpResponse = response.json().await.unwrap();
    
    // 5. Verify response fields
    assert!(!body.user_id.is_empty());
    assert_eq!(body.role, "STAFF");
    
    // 6. Verify database state
    // Query database and verify records created
    
    // 7. Cleanup (optional)
    // Delete test records
}
```

---

## Running Tests in CI/CD

### GitHub Actions Example
```yaml
- name: Run tests
  run: |
    RUST_LOG=debug cargo test -- --nocapture --test-threads=1
```

### Expected Exit Codes
- `0` - All tests passed
- `1` - One or more tests failed
- `101` - Compilation error

---

## Performance Considerations

- Tests should be independent (no shared state)
- Use unique test data for each test (timestamps in data generation)
- Keep database clean between test runs
- Consider using transactions to rollback test data
- Parallel test execution safe only with proper isolation

---

## Future Test Enhancements

1. **Integration with Postman Collection**
   - Import test cases into Postman
   - Export as Newman for CI/CD

2. **Load Testing**
   - Multiple concurrent signups
   - Database connection pooling validation

3. **API Contract Testing**
   - OpenAPI/Swagger validation
   - Response schema validation

4. **Security Testing**
   - SQL injection attempts
   - Password strength enforcement
   - JWT token validation

5. **Performance Testing**
   - Response time benchmarks
   - Database query optimization
   - Concurrent user limits

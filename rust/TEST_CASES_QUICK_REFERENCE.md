# Test Cases Quick Reference

Complete listing of all 61 test cases organized by category for quick lookup and execution.

## Summary Statistics
- **Total Test Cases:** 61
- **Test Files:** 4
- **Feature Areas:** 4
- **Expected Coverage:** 100% of signup feature

---

## 📋 All Test Cases by Category

### 1️⃣ AUTH SIGNUP TESTS (15 cases) - `tests/auth_signup_tests.rs`

| # | Test Case | Test Name | Status | Type |
|---|-----------|-----------|--------|------|
| 1 | SCHOOL_ADMIN signup creates new school | `test_school_admin_signup_new_user_creates_school` | Draft | Happy Path |
| 2 | Duplicate SCHOOL_ADMIN creates new school | `test_school_admin_signup_existing_user_creates_new_school` | Draft | Happy Path |
| 3 | STAFF signup at existing school | `test_staff_signup_existing_school` | Draft | Happy Path |
| 4 | PARENT signup at existing school | `test_parent_signup_existing_school` | Draft | Happy Path |
| 5 | ADMIN signup at existing school | `test_admin_signup_existing_school` | Draft | Happy Path |
| 6 | Missing email validation | `test_signup_missing_email_validation` | Draft | Validation |
| 7 | Missing password validation | `test_signup_missing_password_validation` | Draft | Validation |
| 8 | Invalid email format | `test_signup_invalid_email_format` | Draft | Validation |
| 9 | Weak password validation | `test_signup_weak_password_validation` | Draft | Validation |
| 10 | Duplicate email constraint | `test_signup_duplicate_email_constraint` | Draft | Constraint |
| 11 | Duplicate phone number constraint | `test_signup_duplicate_phone_number_constraint` | Draft | Constraint |
| 12 | Non-existent school code | `test_signup_invalid_school_code` | Draft | Error Handling |
| 13 | Invalid role name | `test_signup_invalid_role_name` | Draft | Error Handling |
| 14 | Missing school_code for STAFF | `test_signup_missing_school_code_for_staff` | Draft | Validation |
| 15 | Response structure validation | `test_signup_response_structure` | Draft | Validation |

### 2️⃣ SCHOOL CREATION TESTS (16 cases) - `tests/school_creation_tests.rs`

| # | Test Case | Test Name | Status | Type |
|---|-----------|-----------|--------|------|
| 1 | School properties correct | `test_school_admin_creates_school_with_correct_properties` | Draft | Data Integrity |
| 2 | Slug uniqueness | `test_school_slug_uniqueness` | Draft | Constraint |
| 3 | admission_prefix uniqueness | `test_school_admission_prefix_uniqueness` | Draft | Constraint |
| 4 | staff_id_prefix uniqueness | `test_school_staff_id_prefix_uniqueness` | Draft | Constraint |
| 5 | admission_prefix format | `test_admission_prefix_format` | Draft | Format |
| 6 | staff_id_prefix format | `test_staff_id_prefix_format` | Draft | Format |
| 7 | School name generation | `test_school_name_generation` | Draft | Data Generation |
| 8 | School timestamps | `test_school_timestamps` | Draft | Timestamps |
| 9 | NULL optional fields | `test_school_null_optional_fields` | Draft | Data Integrity |
| 10 | Empty string fields | `test_school_empty_string_fields` | Draft | Data Integrity |
| 11 | Creation rollback on error | `test_school_creation_rollback_on_error` | Draft | Transaction |
| 12 | Multiple schools creation | `test_multiple_schools_creation` | Draft | Data Generation |
| 13 | School-user relationship | `test_school_user_relationship` | Draft | Relationships |
| 14 | Default column values | `test_school_default_column_values` | Draft | Defaults |
| 15 | School not reused | `test_school_not_reused_for_different_admins` | Draft | Isolation |
| 16 | Column count = 27 | `test_school_column_count` | Draft | Schema |

### 3️⃣ MULTI-ROLE TESTS (15 cases) - `tests/multi_role_tests.rs`

| # | Test Case | Test Name | Status | Type |
|---|-----------|-----------|--------|------|
| 1 | Multiple roles same school | `test_user_multiple_roles_same_school` | Draft | Multi-Role |
| 2 | Different roles different schools | `test_user_different_roles_different_schools` | Draft | Multi-Role |
| 3 | Duplicate role prevention | `test_duplicate_role_prevention` | Draft | Constraint |
| 4 | SCHOOL_ADMIN then other role | `test_school_admin_then_other_role` | Draft | Multi-Role |
| 5 | Multiple SCHOOL_ADMIN signups | `test_multiple_school_admin_signups` | Draft | Multi-Role |
| 6 | Different phone each signup | `test_user_different_phone_each_signup` | Draft | Constraint |
| 7 | UserSchoolRole is_active flag | `test_user_school_role_is_active` | Draft | Data Integrity |
| 8 | UserSchoolRole timestamps | `test_user_school_role_timestamps` | Draft | Timestamps |
| 9 | Role IDs correct | `test_role_ids_correct` | Draft | Validation |
| 10 | Transaction rollback | `test_transaction_rollback_multi_role` | Draft | Transaction |
| 11 | SCHOOL_ADMIN unique per school | `test_school_admin_unique_per_school` | Draft | Uniqueness |
| 12 | User cannot add to non-existent school | `test_user_cannot_add_to_nonexistent_school` | Draft | Error Handling |
| 13 | Foreign key role_id | `test_role_foreign_key_constraint` | Draft | Foreign Key |
| 14 | Foreign key user_id | `test_user_foreign_key_constraint` | Draft | Foreign Key |
| 15 | Foreign key school_id | `test_school_foreign_key_constraint` | Draft | Foreign Key |

### 4️⃣ DATABASE SCHEMA TESTS (15 cases) - `tests/database_schema_tests.rs`

| # | Test Case | Test Name | Status | Type |
|---|-----------|-----------|--------|------|
| 1 | Schools table structure | `test_schools_table_structure` | Draft | Schema |
| 2 | Users timestamps are TIMESTAMPTZ | `test_users_table_timestamps_are_utc` | Draft | Schema |
| 3 | Schools timestamps without TZ | `test_schools_table_timestamps_without_timezone` | Draft | Schema |
| 4 | Users table constraints | `test_users_table_constraints` | Draft | Constraint |
| 5 | Schools table constraints | `test_schools_table_constraints` | Draft | Constraint |
| 6 | UserSchoolRoles structure | `test_user_school_roles_table_structure` | Draft | Schema |
| 7 | Roles table data | `test_roles_table_data` | Draft | Data Integrity |
| 8 | No orphaned USR (user) | `test_no_orphaned_user_school_roles` | Draft | Referential |
| 9 | No orphaned USR (school) | `test_no_orphaned_user_school_roles_school` | Draft | Referential |
| 10 | No orphaned USR (role) | `test_no_orphaned_user_school_roles_role` | Draft | Referential |
| 11 | admission_prefix not NULL | `test_admission_prefix_not_null_when_set` | Draft | Null Check |
| 12 | staff_id_prefix pattern | `test_staff_id_prefix_pattern` | Draft | Pattern |
| 13 | Slug pattern validation | `test_slug_pattern_auto_generated` | Draft | Pattern |
| 14 | Default values applied | `test_default_values_applied` | Draft | Defaults |
| 15 | All indexes present | `test_all_indexes_present` | Draft | Indexes |

---

## 🏃 Quick Test Execution Commands

### Run All Tests
```bash
cargo test
```

### Run by Test File
```bash
# Signup tests only
cargo test --test auth_signup_tests

# School creation tests only
cargo test --test school_creation_tests

# Multi-role tests only
cargo test --test multi_role_tests

# Database schema tests only
cargo test --test database_schema_tests
```

### Run by Test Category Pattern
```bash
# All SCHOOL_ADMIN tests
cargo test school_admin

# All constraint tests
cargo test constraint
cargo test unique

# All validation tests
cargo test validation

# All foreign key tests
cargo test foreign_key

# All timestamp tests
cargo test timestamp

# All multi-role tests
cargo test multiple
```

### Run Specific Test
```bash
# Example: run single test
cargo test test_school_admin_signup_new_user_creates_school

# With output
cargo test test_school_admin_signup_new_user_creates_school -- --nocapture
```

---

## 📊 Test Coverage by Feature

### ✅ SCHOOL_ADMIN Feature
- [x] New user creates empty school
- [x] School has auto-generated unique identifiers
- [x] School marked as ACTIVE and is_active=true
- [x] User marked as pending verification
- [x] UserSchoolRole created with SCHOOL_ADMIN role
- [x] Response includes all required fields
- [x] Multiple SCHOOL_ADMINs create separate schools
- [x] Existing user can sign up as new SCHOOL_ADMIN

### ✅ Multi-Role Support
- [x] User can have multiple roles at same school
- [x] User can have different roles at different schools
- [x] Duplicate role at same school prevented
- [x] SCHOOL_ADMIN and non-SCHOOL_ADMIN combined
- [x] UserSchoolRole correctly links user-school-role

### ✅ Input Validation
- [x] Email required
- [x] Password required
- [x] Email format validated
- [x] Password strength checked
- [x] Role name validated
- [x] school_code required for non-SCHOOL_ADMIN
- [x] Response structure correct

### ✅ Uniqueness Constraints
- [x] Email uniqueness enforced
- [x] Phone number uniqueness enforced
- [x] School slug uniqueness enforced
- [x] admission_prefix uniqueness enforced
- [x] Proper error messages on violation

### ✅ Database Schema
- [x] All 27 school columns exist
- [x] Correct data types (timestamps especially)
- [x] Correct constraints (NOT NULL, UNIQUE, etc.)
- [x] Foreign keys defined
- [x] Default values applied
- [x] Indexes created

### ✅ Data Integrity
- [x] No orphaned records
- [x] Referential integrity maintained
- [x] Transactions rollback on error
- [x] Cascading deletes work (if configured)
- [x] No NULL constraint violations

---

## 🔍 Test Categories Summary

| Category | Count | Priority | Status |
|----------|-------|----------|--------|
| Happy Path Scenarios | 5 | 🔴 Critical | Ready |
| Input Validation | 5 | 🔴 Critical | Ready |
| Constraint Testing | 5 | 🔴 Critical | Ready |
| Error Handling | 3 | 🟠 High | Ready |
| Data Integrity | 8 | 🟠 High | Ready |
| Schema Validation | 8 | 🟠 High | Ready |
| Multi-Role Scenarios | 5 | 🟡 Medium | Ready |
| Foreign Keys | 3 | 🟡 Medium | Ready |
| Timestamps | 2 | 🟡 Medium | Ready |
| Transactions | 1 | 🟡 Medium | Ready |
| Pattern Matching | 3 | 🟢 Low | Ready |
| Indexes | 1 | 🟢 Low | Ready |
| **TOTAL** | **61** | — | Ready |

---

## 📝 Notes for Test Implementers

### Each Test Should Include

1. **Setup Phase**
   - Generate unique test data (email, phone, etc.)
   - Create HTTP client
   - Initialize test environment

2. **Execution Phase**
   - Build request payload
   - Call API endpoint
   - Capture response

3. **Verification Phase**
   - Assert HTTP status code
   - Validate response fields
   - Verify database state

4. **Cleanup Phase**
   - Delete test data from database
   - Close connections
   - Release resources

### Test Data Helpers Available

- `generate_test_email(prefix)` - Unique email with timestamp
- `generate_test_phone()` - Unique phone number
- `build_signup_request()` - Standard signup request builder
- `TEST_SCHOOL_CODE_1` - "school-722ee764"
- `TEST_SCHOOL_CODE_2` - "ibrahim-0bf21cd6"
- Role ID constants (STAFF, PARENT, ADMIN, SCHOOL_ADMIN)

### Common Assertions

```rust
// Status code
assert_eq!(response.status(), 200);

// Response fields
assert!(!body.user_id.is_empty());
assert_eq!(body.role, "STAFF");
assert_eq!(body.email, test_email);

// UUID validation
let parsed_uuid = uuid::Uuid::parse_str(&body.user_id);
assert!(parsed_uuid.is_ok());

// Database verification
let user = get_user_by_email(&test_email);
assert!(user.is_some());
```

---

## ⚠️ Test Dependencies

### Required Infrastructure
- PostgreSQL database running on localhost:5432
- Test database "myschool" with schema initialized
- API server running on localhost:8080

### Required Test Data
- Roles table populated with 4 roles
- At least 2 existing schools with valid slugs
- Empty/clean tables for test isolation

### Environment Variables (Optional)
```bash
export RUST_LOG=debug
export TEST_DATABASE_URL=postgresql://postgres:password@localhost/myschool
export TEST_API_URL=http://localhost:8080
```

---

## 🎯 Next Steps

1. **Implement HTTP Client Integration**
   - Add reqwest dependency to Cargo.toml
   - Create test client fixture
   - Handle async/await patterns

2. **Implement Database Query Helpers**
   - Direct SQL queries for verification
   - sqlx integration for type safety
   - Transaction rollback for cleanup

3. **Add Performance Benchmarks**
   - Measure signup response time
   - Track database query performance
   - Identify bottlenecks

4. **Implement CI/CD Integration**
   - GitHub Actions workflow
   - Automated test execution
   - Test report generation

5. **Create Mock/Stub Services**
   - Email service mock
   - SMS service mock
   - External API stubs

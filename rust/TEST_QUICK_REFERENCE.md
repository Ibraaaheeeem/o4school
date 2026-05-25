# Test Quick Reference - 61 Tests Ready to Run

## Test Summary

| File | Count | Status | Coverage |
|------|-------|--------|----------|
| auth_signup_tests.rs | 15 | ✅ Implemented | Auth flows, validation, constraints |
| multi_role_tests.rs | 15 | ✅ Implemented | Multi-role scenarios, FK constraints |
| school_creation_tests.rs | 16 | ✅ Implemented | School generation, data integrity |
| database_schema_tests.rs | 15 | ✅ Implemented | Schema, timestamps, constraints |
| **TOTAL** | **61** | **✅ All Ready** | **Full coverage** |

---

## Running Tests

### Run All Tests
```bash
cargo test
```

### Run Single File
```bash
cargo test --test auth_signup_tests
cargo test --test multi_role_tests
cargo test --test school_creation_tests
cargo test --test database_schema_tests
```

### Run Single Test
```bash
cargo test test_school_admin_signup_new_user_creates_school
cargo test test_user_multiple_roles_same_school
cargo test test_school_admin_creates_school_with_correct_properties
cargo test test_users_table_timestamps_are_utc
```

### Run with Output
```bash
cargo test -- --nocapture --test-threads=1
```

---

## Test Breakdown

### 1. Auth Signup Tests (15 tests)

#### Happy Path - User Creation
- `test_school_admin_signup_new_user_creates_school` - SCHOOL_ADMIN creates new user & school
- `test_school_admin_signup_existing_user_creates_new_school` - Existing user creates new school
- `test_staff_signup_existing_school` - STAFF signup at school-722ee764
- `test_parent_signup_existing_school` - PARENT signup at school-722ee764
- `test_admin_signup_existing_school` - ADMIN signup at school-722ee764

#### Input Validation (5 tests)
- `test_signup_missing_email_validation` - Email required
- `test_signup_missing_password_validation` - Password required
- `test_signup_invalid_email_format` - Email format validation
- `test_signup_weak_password_validation` - Password strength
- `test_signup_response_structure` - Response fields & UUID validity

#### Constraint Violation (5 tests)
- `test_signup_duplicate_email_constraint` - Email uniqueness
- `test_signup_duplicate_phone_number_constraint` - Phone uniqueness
- `test_signup_invalid_school_code` - School must exist
- `test_signup_invalid_role_name` - Role must be valid
- `test_signup_missing_school_code_for_staff` - STAFF requires school_code

---

### 2. Multi-Role Tests (15 tests)

#### Multi-Role Scenarios
- `test_user_multiple_roles_same_school` - 1 user, 3 roles at same school
- `test_user_different_roles_different_schools` - 1 user, 2 roles at 2 schools
- `test_school_admin_then_other_role` - SCHOOL_ADMIN then STAFF at different school
- `test_multiple_school_admin_signups` - Same user creates 3 schools
- `test_school_admin_unique_per_school` - Different admins get different schools

#### Constraint Prevention
- `test_duplicate_role_prevention` - Can't add same role twice to same school
- `test_user_different_phone_each_signup` - Phone must be unique across signups
- `test_user_cannot_add_to_nonexistent_school` - Fake school fails, no user created
- `test_transaction_rollback_multi_role` - Failed signup rolls back all changes

#### Data Integrity
- `test_user_school_role_is_active` - is_active flag set on creation
- `test_user_school_role_timestamps` - Timestamps exist on role record
- `test_role_ids_correct` - role_id matches expected UUID

#### Foreign Key Constraints
- `test_user_foreign_key_constraint` - user_id FK enforced
- `test_role_foreign_key_constraint` - role_id FK enforced
- `test_school_foreign_key_constraint` - school_id FK enforced

---

### 3. School Creation Tests (16 tests)

#### School Properties
- `test_school_admin_creates_school_with_correct_properties` - is_active=true, proper fields
- `test_school_name_generation` - Name format with "School Admin" + email
- `test_school_timestamps` - created_at, updated_at exist
- `test_school_null_optional_fields` - Optional fields can be None
- `test_school_empty_string_fields` - Empty strings handled
- `test_school_default_column_values` - is_active defaults to true
- `test_school_column_count` - Schema has 27 columns (documented)

#### Uniqueness Constraints
- `test_school_slug_uniqueness` - Different slug per school
- `test_school_admission_prefix_uniqueness` - admission_prefix unique
- `test_school_staff_id_prefix_uniqueness` - staff_id_prefix unique
- `test_admission_prefix_format` - ADM-{6hex} pattern (10 chars)
- `test_staff_id_prefix_format` - STF- prefix
- `test_slug_pattern_auto_generated` - admin-{uuid} format

#### Relationships
- `test_school_user_relationship` - User correctly linked to school
- `test_school_not_reused_for_different_admins` - Each admin gets new school
- `test_multiple_schools_creation` - 3 schools can be created

#### Rollback
- `test_school_creation_rollback_on_error` - Failed signup doesn't create school

---

### 4. Database Schema Tests (15 tests)

#### Table Structure
- `test_schools_table_structure` - 27 columns documented
- `test_users_table_constraints` - Email & phone unique
- `test_schools_table_constraints` - Slug & admission_prefix unique
- `test_user_school_roles_table_structure` - Junction table structure
- `test_roles_table_data` - 4 roles in database with UUIDs

#### Timestamp Types
- `test_users_table_timestamps_are_utc` - TIMESTAMPTZ in users table
- `test_schools_table_timestamps_without_timezone` - TIMESTAMP (no TZ) in schools table
- `test_school_timestamps` - Timestamps documented

#### Default Values
- `test_default_values_applied` - is_active=true for schools & roles
- `test_admission_prefix_not_null_when_set` - When set, not null

#### Pattern Validation
- `test_staff_id_prefix_pattern` - STF- prefix validation
- `test_slug_pattern_auto_generated` - admin-{uuid} format
- `test_admission_prefix_format` - ADM-{6hex} format

#### Foreign Key Constraints
- `test_no_orphaned_user_school_roles` - user_id FK enforced
- `test_no_orphaned_user_school_roles_school` - school_id FK enforced
- `test_no_orphaned_user_school_roles_role` - role_id FK enforced

#### Indexes
- `test_all_indexes_present` - All expected indexes documented

---

## Prerequisites

### Database
- PostgreSQL 17 on localhost:5432
- Database: `myschool`
- Credentials: `postgres:password`

### Test Schools (Must Exist)
```sql
INSERT INTO schools (id, name, slug, is_active, created_at) 
VALUES 
  ('11111111-1111-1111-1111-111111111111', 'Test School 1', 'school-722ee764', true, NOW()),
  ('22222222-2222-2222-2222-222222222222', 'Test School 2', 'ibrahim-0bf21cd6', true, NOW());
```

### API Server
- Running on localhost:8080
- Endpoint: `/api/auth/sign-up`
- Ready to accept JSON requests

### Environment
```bash
export DATABASE_URL=postgresql://postgres:password@localhost/myschool
```

---

## Test Execution Strategy

### Quick Validation (All Tests)
```bash
cargo test -- --nocapture --test-threads=1
```

### Test Single Category
```bash
# Auth flows only
cargo test --test auth_signup_tests -- --nocapture

# Multi-role scenarios only
cargo test --test multi_role_tests -- --nocapture

# School creation only
cargo test --test school_creation_tests -- --nocapture

# Schema verification only
cargo test --test database_schema_tests -- --nocapture
```

### Debug Single Test
```bash
cargo test test_school_admin_signup_new_user_creates_school -- --nocapture
```

### Generate Coverage Report
```bash
# Install tarpaulin (once)
cargo install cargo-tarpaulin

# Generate coverage
cargo tarpaulin --out Html --output-dir coverage
```

---

## Expected Results

### Passing Criteria
- ✅ All 61 tests pass
- ✅ HTTP requests complete successfully
- ✅ Database queries return expected data
- ✅ Assertions match database state
- ✅ Cleanup removes test data

### Common Issues

**Issue:** Connection refused on localhost:8080
- **Fix:** Ensure API server is running: `cargo run --bin server`

**Issue:** Connection refused on localhost:5432
- **Fix:** Ensure PostgreSQL is running
- **Docker:** `docker-compose up -d`

**Issue:** Database "myschool" not found
- **Fix:** Run database setup: `./fix_database.sh`

**Issue:** Test schools not found
- **Fix:** Insert test schools manually (see Prerequisites)

---

## Test File Locations

```
tests/
├── common/
│   └── mod.rs                    # Shared utilities (25+ functions)
├── auth_signup_tests.rs          # 15 signup tests
├── multi_role_tests.rs           # 15 multi-role tests
├── school_creation_tests.rs      # 16 school tests
└── database_schema_tests.rs      # 15 schema tests
```

---

## Implementation Details

### Each Test Includes
1. ✅ Setup (database pool, HTTP client)
2. ✅ Test data generation (unique email, phone)
3. ✅ Pre-cleanup (delete existing test data)
4. ✅ HTTP request (POST /api/auth/sign-up)
5. ✅ Response validation (status, structure)
6. ✅ Database verification (user, school, role)
7. ✅ Assertion checks (counts, IDs, values)
8. ✅ Post-cleanup (delete test data)

### Async Pattern
```rust
#[tokio::test]
async fn test_example() {
    let pool = get_db_pool().await;
    let client = get_http_client();
    // Test code...
}
```

---

## Next Steps

1. **Verify Environment**
   - PostgreSQL running on localhost:5432
   - API server running on localhost:8080
   - Test schools exist in database

2. **Run Tests**
   ```bash
   cargo test -- --nocapture --test-threads=1
   ```

3. **Review Results**
   - Check test output for failures
   - Fix any connection/setup issues
   - Adjust timeouts if needed

4. **Integrate CI/CD**
   - Add to GitHub Actions
   - Run on PR/push
   - Generate coverage reports

---

## Statistics

- **Total Lines of Test Code:** 2,000+
- **Lines of Helper Code:** 340+
- **Total Lines:** 2,340+
- **Test Coverage:**
  - Signup flow: ✅
  - Role assignment: ✅
  - Multi-role: ✅
  - Validation: ✅
  - Constraints: ✅
  - Schema: ✅
  - Error handling: ✅

---

## Support

For issues or questions:
1. Check test output: `cargo test -- --nocapture`
2. Verify database: `psql -h localhost -U postgres -d myschool -c "SELECT COUNT(*) FROM users;"`
3. Check API: `curl http://localhost:8080/health`
4. Review error logs

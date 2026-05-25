# Test Setup and Execution Guide

## Quick Start

The easiest way to run tests is using the provided script:

```bash
bash run_tests.sh
```

This script will:
1. ✅ Verify PostgreSQL is running
2. ✅ Start the API server if needed  
3. ✅ Run all tests with proper configuration

## Manual Test Execution

If you prefer to run tests manually, follow these steps:

### Step 1: Start PostgreSQL
```bash
# Ubuntu/Debian
sudo systemctl start postgresql

# Or check if it's running
sudo systemctl status postgresql
```

### Step 2: Start the API Server
Open a terminal and run:
```bash
cargo run
```

Wait for output like:
```
[INFO] Starting School Backend Server on 127.0.0.1:8080
[INFO] Database connection successful
[INFO] Database health check passed
```

### Step 3: Run Tests
Open another terminal and run:
```bash
cargo test -- --test-threads=1 --nocapture
```

### Optional: Run Specific Test
```bash
cargo test --test auth_signup_tests test_school_admin_signup_new_user_creates_school -- --nocapture
```

## Important Notes

### `--test-threads=1` is Required
Tests must run serially because:
- Each test creates its own database pool
- Pool has limited connections (set to 5 for tests)
- Parallel execution can cause EOF errors
- Serial execution ensures clean state between tests

### Database Requirements
- PostgreSQL must be running and accessible
- Database must exist: `myschool`
- User credentials from `.env`: `postgres:password`
- Connection string: `postgres://postgres:password@localhost:5432/myschool`

### Server Must Be Running
The integration tests make HTTP requests to:
- Base URL: `http://127.0.0.1:8080`
- Endpoints tested: `/api/auth/sign-up` and related auth endpoints

## Troubleshooting

### "Failed to connect to test database"
**Cause**: PostgreSQL not running or not accessible

**Fix**:
```bash
# Start PostgreSQL
sudo systemctl start postgresql

# Verify connection
PGPASSWORD=password psql -h localhost -U postgres -d myschool -c "SELECT 1;"
```

### "Expected to read 5 bytes, got 0 bytes at EOF"
**Cause**: Running tests in parallel without server running

**Fix**:
```bash
# Option 1: Use the script
bash run_tests.sh

# Option 2: Manual
cargo run &  # Start server in background
sleep 3
cargo test -- --test-threads=1 --nocapture
```

### "Connection refused" on HTTP requests
**Cause**: API server not running

**Fix**:
```bash
# Terminal 1
cargo run

# Terminal 2
cargo test -- --test-threads=1 --nocapture
```

### Tests timeout or hang
**Cause**: Multiple tests trying to acquire the single database connection

**Fix**: 
- Ensure using `--test-threads=1`
- Kill any other `cargo test` processes: `pkill -f "cargo test"`
- Check if server is running properly

## Common Test Commands

```bash
# Run all tests (recommended way)
bash run_tests.sh

# Run all tests manually (serially)
cargo test -- --test-threads=1 --nocapture

# Run specific test file
cargo test --test auth_signup_tests -- --test-threads=1 --nocapture

# Run specific test
cargo test test_school_admin_signup_new_user_creates_school -- --nocapture

# Run tests with backtrace for debugging
RUST_BACKTRACE=1 cargo test -- --test-threads=1 --nocapture

# Run with full backtrace
RUST_BACKTRACE=full cargo test -- --test-threads=1 --nocapture
```

## Test Structure

Tests are organized by functionality:
- `tests/auth_signup_tests.rs` - Authentication signup tests
- `tests/multi_role_tests.rs` - Multi-role signup scenarios
- `tests/school_creation_tests.rs` - School creation logic
- `tests/database_schema_tests.rs` - Database schema validation
- `tests/common/mod.rs` - Shared test utilities and helpers

## CI/CD Integration

For CI/CD pipelines, ensure:
1. PostgreSQL service is started before tests
2. Database is initialized and seeded
3. API server is started in background
4. Tests run with `--test-threads=1`

Example GitHub Actions workflow:
```yaml
- name: Start PostgreSQL
  run: sudo systemctl start postgresql

- name: Start API Server
  run: cargo run > /tmp/server.log 2>&1 &

- name: Wait for server
  run: sleep 5

- name: Run tests
  run: cargo test -- --test-threads=1 --nocapture
```

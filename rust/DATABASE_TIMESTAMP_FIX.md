# Fix: Database Schema Timestamp Column Mismatch

## Problem
The API tests are failing with the following error:
```
error occurred while decoding column "email_verification_expires": mismatched types; 
Rust type `core::option::Option<chrono::datetime::DateTime<chrono::offset::utc::Utc>>` 
(as SQL type `TIMESTAMPTZ`) is not compatible with SQL type `TIMESTAMP`
```

## Root Cause
The database schema has timestamp columns defined as `TIMESTAMP` (without timezone), but the Rust code uses `DateTime<Utc>` which expects `TIMESTAMPTZ` (timestamp with timezone).

### Affected Columns
The following columns in the `users` table need to be updated:
- `email_verification_expires`
- `otp_expires`
- `last_otp_sent`
- `verified_at`
- `approved_at`
- `last_login_at`
- `created_at`
- `updated_at`

## Solution
Run the following SQL commands to convert the TIMESTAMP columns to TIMESTAMPTZ:

```sql
-- Connect to your database first
-- psql -U postgres -d myschool

-- Alter timestamp columns to use timezone
ALTER TABLE users 
  ALTER COLUMN email_verification_expires TYPE TIMESTAMPTZ USING email_verification_expires AT TIME ZONE 'UTC',
  ALTER COLUMN otp_expires TYPE TIMESTAMPTZ USING otp_expires AT TIME ZONE 'UTC',
  ALTER COLUMN last_otp_sent TYPE TIMESTAMPTZ USING last_otp_sent AT TIME ZONE 'UTC',
  ALTER COLUMN verified_at TYPE TIMESTAMPTZ USING verified_at AT TIME ZONE 'UTC',
  ALTER COLUMN approved_at TYPE TIMESTAMPTZ USING approved_at AT TIME ZONE 'UTC',
  ALTER COLUMN last_login_at TYPE TIMESTAMPTZ USING last_login_at AT TIME ZONE 'UTC',
  ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
  ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC';
```

## How to Apply the Fix

### Option 1: Using psql CLI
```bash
# Connect to PostgreSQL
psql -U postgres -d myschool

# Run the SQL commands above
```

### Option 2: Using Docker (if using docker-compose)
```bash
# Access the database container
docker exec -it <postgres_container_id> psql -U postgres -d myschool

# Run the SQL commands above
```

### Option 3: Verify from logs
If you're using the same docker-compose setup:
```bash
# Check container logs for the database service
docker-compose logs postgres

# Or connect directly
docker-compose exec postgres psql -U postgres -d myschool
```

## Verification
After running the SQL commands, test the endpoints again:

```bash
# Sign up endpoint should now work
curl -X POST http://localhost:8080/api/auth/sign-up \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "TestPass123",
    "first_name": "John",
    "last_name": "Doe",
    "phone_number": "+1234567890"
  }'
```

Expected response (201 Created):
```json
{
  "user_id": "550e8400-e29b-41d4-a716-446655440000",
  "email": "test@example.com",
  "message": "Sign up successful. Please verify your email to continue.",
  "next_route": "/auth/verify-email",
  "verification_token": "abcdef123456789abcdef123456789"
}
```

## Prevention for Future
When creating database schemas, always use `TIMESTAMPTZ` for timestamp columns that will be handled by Rust code using `chrono::DateTime<Utc>`.

### Correct Table Definition
```sql
CREATE TABLE users (
  id UUID PRIMARY KEY,
  email VARCHAR(255) NOT NULL UNIQUE,
  email_verification_expires TIMESTAMPTZ,
  otp_expires TIMESTAMPTZ,
  last_otp_sent TIMESTAMPTZ,
  verified_at TIMESTAMPTZ,
  approved_at TIMESTAMPTZ,
  last_login_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  -- ... other columns
);
```

## Summary of Changes Made

✅ Added `send_otp()` handler in [src/handlers/auth.rs](src/handlers/auth.rs)  
✅ Added `verify_otp()` handler in [src/handlers/auth.rs](src/handlers/auth.rs)  
✅ Added `/send-otp` route in [src/main.rs](src/main.rs)  
✅ Added `/verify-otp` route in [src/main.rs](src/main.rs)  
✅ Postman collection updated with both endpoints  

**Remaining Action**: Apply the SQL commands above to fix the database schema.

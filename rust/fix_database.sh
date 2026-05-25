#!/bin/bash

# Database Timestamp Fix Script
# This script fixes the timestamp columns in the users table

set -e

DB_HOST="localhost"
DB_PORT="5432"
DB_NAME="myschool"
DB_USER="postgres"
DB_PASSWORD="password"

echo "Connecting to database: $DB_NAME"

# Run the SQL fix
PGPASSWORD="$DB_PASSWORD" psql -h "$DB_HOST" -p "$DB_PORT" -d "$DB_NAME" -U "$DB_USER" << EOF

-- Fix timestamp columns to use timezone
ALTER TABLE users 
  ALTER COLUMN email_verification_expires TYPE TIMESTAMPTZ USING email_verification_expires AT TIME ZONE 'UTC',
  ALTER COLUMN otp_expires TYPE TIMESTAMPTZ USING otp_expires AT TIME ZONE 'UTC',
  ALTER COLUMN last_otp_sent TYPE TIMESTAMPTZ USING last_otp_sent AT TIME ZONE 'UTC',
  ALTER COLUMN verified_at TYPE TIMESTAMPTZ USING verified_at AT TIME ZONE 'UTC',
  ALTER COLUMN approved_at TYPE TIMESTAMPTZ USING approved_at AT TIME ZONE 'UTC',
  ALTER COLUMN last_login_at TYPE TIMESTAMPTZ USING last_login_at AT TIME ZONE 'UTC',
  ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
  ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC';

-- Verify the columns are fixed
\d+ users

EOF

echo ""
echo "✅ Database schema fix completed successfully!"
echo ""
echo "Next steps:"
echo "1. Rebuild the project: cargo build"
echo "2. Restart the server: cargo run"
echo "3. Test the endpoints with Postman using the collection"

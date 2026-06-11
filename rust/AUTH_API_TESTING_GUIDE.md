# 4School Auth API - Complete Testing Guide

## Prerequisites
- ✅ Rust backend running on `http://localhost:8080`
- ✅ PostgreSQL database running with fixed TIMESTAMPTZ columns
- ✅ Updated Postman collection imported
- ✅ Old test user deleted from database

## Step-by-Step Testing Workflow

### Step 1: Verify Backend is Running
```bash
curl -s http://127.0.0.1:8080/api/health | jq .
```

Expected response:
```json
{
  "status": "ok",
  "message": "Server is running"
}
```

---

### Step 2: Import Postman Collection
1. Open Postman
2. Click **File → Import**
3. Select `postman_collection.json` from `/home/abuhaneefayn/Desktop/4school/rust/`
4. Click **Import**

---

### Step 3: Configure Environment
1. In Postman, look for the **Variables** section at the bottom of the collection
2. Confirm these variables are set:
   - `base_url`: `http://localhost:8080`
   - `email`: (will be auto-set by Sign Up)
   - `user_id`: (will be auto-set by Sign Up)
   - `verification_token`: (will be auto-set by Sign Up)
   - `access_token`: (will be auto-set by Sign In)

---

### Step 4: Run Tests in Order

#### Test 4.1: Sign Up ✅
**Endpoint**: `POST /api/auth/sign-up`

**What happens**:
- Creates a new user with dynamic email (includes timestamp to prevent duplicates)
- User status set to PENDING
- Generates random phone number
- Auto-captures: `user_id`, `email`, `verification_token`

**Expected Response** (201 Created):
```json
{
  "user_id": "550e8400-e29b-41d4-a716-446655440001",
  "email": "testuser1716193589123@example.com",
  "message": "Sign up successful. Please verify your email to continue.",
  "next_route": "/auth/verify-email",
  "verification_token": "abcdef123456789abcdef1234567890"
}
```

**Actions**:
- ✅ Click **Send**
- ✅ Check console for captured values
- ✅ Note the `verification_token` value (you'll lysee it in the test output)

---

#### Test 4.2: Verify Email ✅
**Endpoint**: `POST /api/auth/verify-email`

**What happens**:
- Uses captured `user_id` and `verification_token`
- Marks email as verified
- Sets verification status to VERIFIED

**Expected Response** (200 OK):
```json
{
  "user_id": "550e8400-e29b-41d4-a716-446655440001",
  "email": "testuser1716193589123@example.com",
  "message": "Email verified successfully",
  "next_route": "/auth/activate",
  "verified_at": "2026-05-19T20:33:32.123Z"
}
```

**Actions**:
- ✅ Click **Send**
- ✅ Verify response is 200 OK

---

#### Test 4.3: Activate Account ✅
**Endpoint**: `POST /api/auth/activate`

**What happens**:
- Uses captured `user_id`
- Changes status from PENDING to ACTIVE
- Sets approval status to APPROVED

**Expected Response** (200 OK):
```json
{
  "user_id": "550e8400-e29b-41d4-a716-446655440001",
  "email": "testuser1716193589123@example.com",
  "message": "Account activated successfully",
  "next_route": "/profile/complete",
  "activated_at": "2026-05-19T20:33:32.456Z"
}
```

**Actions**:
- ✅ Click **Send**
- ✅ Verify response is 200 OK

---

#### Test 4.4: Sign In ✅
**Endpoint**: `POST /api/auth/sign-in`

**What happens**:
- Uses captured `email`
- Verifies password (must match sign-up password: `SecurePassword123`)
- Generates JWT token
- Auto-captures: `access_token`, `user_id`

**Expected Response** (200 OK):
```json
{
  "user_id": "550e8400-e29b-41d4-a716-446655440001",
  "email": "testuser1716193589123@example.com",
  "first_name": "John",
  "last_name": "Doe",
  "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refresh_token": null,
  "token_type": "Bearer",
  "expires_in": 3600,
  "message": "Sign in successful",
  "next_route": "/dashboard",
  "status": "ACTIVE"
}
```

**Actions**:
- ✅ Click **Send**
- ✅ Verify response is 200 OK
- ✅ Check console for captured `access_token`

---

#### Test 4.5: Send OTP ✅
**Endpoint**: `POST /api/auth/send-otp`

**What happens**:
- Uses captured `email`
- Generates random 6-digit OTP code
- Stores OTP with 15-minute expiration
- Logs OTP to console (for testing purposes)

**Expected Response** (200 OK):
```json
{
  "email": "testuser1716193589123@example.com",
  "message": "OTP sent successfully to your email",
  "otp_sent": true,
  "expires_in_seconds": 900,
  "next_route": "/auth/verify-otp"
}
```

**Actions**:
- ✅ Click **Send**
- ✅ **CHECK SERVER LOGS** for the 6-digit OTP code (look for "OTP sent to email" message)
- ✅ Copy the OTP code from logs

**Getting the OTP from Logs**:
If running server with: `cargo run`
- Look for log line: `[TIMESTAMP] INFO school_backend::services::auth_service] OTP sent to email: ...`
- The actual OTP should be visible in the database update or use a debugger
- **For testing**: You need to query the database or check backend logs

**Alternative**: Modify auth_service.rs to log the OTP (currently it's stored but not logged for security)

---

#### Test 4.6: Verify OTP ⚠️
**Endpoint**: `POST /api/auth/verify-otp`

**What happens**:
- Uses captured `email`
- Verifies the 6-digit OTP code
- Clears OTP from database

**Expected Response** (200 OK):
```json
{
  "user_id": "550e8400-e29b-41d4-a716-446655440001",
  "email": "testuser1716193589123@example.com",
  "message": "OTP verified successfully",
  "otp_verified": true,
  "next_route": "/auth/sign-in",
  "verified_at": "2026-05-19T20:33:32.789Z"
}
```

**Actions**:
1. ✅ Go to the request body
2. ✅ Replace `"123456"` with the actual OTP code from server logs
3. ✅ Click **Send**

---

#### Test 4.7: Forgot Password ✅
**Endpoint**: `POST /api/auth/forgot-password`

**What happens**:
- Uses captured `email`
- Generates reset token
- Stores with 1-hour expiration
- Logs reset token to console (for testing)

**Expected Response** (200 OK):
```json
{
  "email": "testuser1716193589123@example.com",
  "message": "Password reset link sent to your email",
  "next_route": "/auth/reset-password",
  "reset_token_sent": true
}
```

**Actions**:
- ✅ Click **Send**
- ✅ **CHECK SERVER LOGS** for the reset token
- ✅ Copy the reset token from logs

---

#### Test 4.8: Reset Password ⚠️
**Endpoint**: `POST /api/auth/reset-password`

**What happens**:
- Uses captured `email`
- Validates reset token matches
- Verifies token hasn't expired
- Hashes new password and updates database

**Expected Response** (200 OK):
```json
{
  "user_id": "550e8400-e29b-41d4-a716-446655440001",
  "email": "testuser1716193589123@example.com",
  "message": "Password reset successfully",
  "next_route": "/auth/sign-in",
  "reset_at": "2026-05-19T20:33:32.999Z"
}
```

**Actions**:
1. ✅ Go to the request body
2. ✅ Replace `"PASTE_RESET_TOKEN_FROM_LOGS_HERE"` with actual token from logs
3. ✅ Click **Send**

---

#### Test 4.9: Logout ✅
**Endpoint**: `POST /api/auth/logout`

**What happens**:
- Uses captured `access_token` and `user_id`
- Invalidates session
- Returns success message

**Expected Response** (200 OK):
```json
{
  "message": "Logged out successfully",
  "next_route": "/auth/sign-in"
}
```

**Actions**:
- ✅ Click **Send**
- ✅ Verify response is 200 OK

---

## Summary of Test Results

| Endpoint | Status | Notes |
|----------|--------|-------|
| Sign Up | ✅ | Creates user with dynamic email |
| Verify Email | ✅ | Uses captured verification token |
| Activate Account | ✅ | Uses captured user_id |
| Sign In | ✅ | Uses captured email, returns JWT |
| Send OTP | ✅ | Check logs for 6-digit OTP |
| Verify OTP | ⚠️ | Requires manual OTP from logs |
| Forgot Password | ✅ | Check logs for reset token |
| Reset Password | ⚠️ | Requires manual token from logs |
| Logout | ✅ | Uses captured access_token |

---

## Viewing Server Logs

To see the OTP and Reset tokens, check the server output:

```bash
# If running with cargo run
# Look for lines like:
# [2026-05-19T20:33:31Z INFO school_backend::services::auth_service] OTP sent to email: ...
# [2026-05-19T20:33:32Z INFO school_backend::services::auth_service] Password reset requested for user: ...
```

---

## Troubleshooting

### Sign Up Fails with "User already exists"
- Run: `PGPASSWORD="password" psql -h localhost -d myschool -U postgres -c "DELETE FROM users;"`
- Make sure to use a fresh Postman run

### Verify Email Fails with "User not found"
- Make sure you ran Sign Up first
- Check that `user_id` variable is captured (should show in Postman console)

### Sign In Fails with "User account is PENDING"
- Make sure you ran Verify Email and Activate Account first
- User status must be ACTIVE

### Verify OTP Fails with "Invalid OTP code"
- Make sure you copy the exact OTP from server logs
- OTP must be 6 digits
- OTP must not be expired (15 minutes)

### Reset Password Fails with "Invalid reset token"
- Make sure you copy the exact reset token from server logs
- Token must not be expired (1 hour)
- Token must match exactly (case-sensitive)

---

## Security Notes for Production

⚠️ **DO NOT** log OTP codes or reset tokens in production logs
- Currently, tokens are NOT logged to console (secure)
- For testing, you can query the database directly to get values

**For Production Testing**:
```bash
# Get OTP from database
PGPASSWORD="password" psql -h localhost -d myschool -U postgres -c \
  "SELECT email, otp_code FROM users WHERE email = 'test@example.com' LIMIT 1;"

# Get reset token from database
PGPASSWORD="password" psql -h localhost -d myschool -U postgres -c \
  "SELECT email, otp_code FROM users WHERE email = 'test@example.com' LIMIT 1;"
```

---

## Next Steps

1. ✅ Run all 9 tests in order
2. ✅ Verify each response matches expected output
3. ✅ Test integration with Android app
4. ✅ Add email sending service (currently marked with TODO)
5. ✅ Add more comprehensive error handling

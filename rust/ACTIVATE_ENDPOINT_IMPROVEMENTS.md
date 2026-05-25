# Activate Account Endpoint Refactoring

## Overview
The `/api/auth/activate` endpoint has been completely refactored to intelligently handle three distinct user account scenarios by checking email address existence and activation status in the database.

## Previous Behavior
- Required `user_id` and `activation_token` in request body
- Simple account activation without email validation
- Didn't handle missing or already-active accounts gracefully

## New Behavior

### Three Scenarios Handled

#### 1. **Email Not Found** (New Account Flow)
- **Request**: `{"email": "nonexistent@example.com"}`
- **Response Status**: HTTP 200 OK
- **Response Body**:
```json
{
  "email": "nonexistent@example.com",
  "user_id": null,
  "status": "email_not_found",
  "message": "Email address not found in our records",
  "next_route": "/auth/sign-up",
  "otp_sent": false
}
```
- **UX**: Direct user to sign-up page

#### 2. **Email Inactive - OTP Sent** (Activation Flow)
- **Request**: `{"email": "user@example.com"}`
- **Conditions**: 
  - Email exists in database
  - `is_active = false`
  - `status != "ACTIVE"`
- **Response Status**: HTTP 200 OK
- **Response Body**:
```json
{
  "email": "user@example.com",
  "user_id": "d5cd9e29-5e33-4b2d-be7c-502605e698a9",
  "status": "otp_sent",
  "message": "OTP sent to user@example.com. Please enter the OTP to verify your account.",
  "next_route": "/auth/verify-otp",
  "otp_sent": true
}
```
- **Actions Taken**:
  - Generate 6-digit OTP
  - Store OTP in database with 15-minute expiration
  - Set `last_otp_sent` timestamp
  - Log OTP to server logs (TODO: Send via email)
- **UX**: Direct user to OTP verification page

#### 3. **Email Already Active** (Already Registered Flow)
- **Request**: `{"email": "active@example.com"}`
- **Conditions**:
  - Email exists in database
  - `is_active = true`
  - `status = "ACTIVE"`
- **Response Status**: HTTP 200 OK
- **Response Body**:
```json
{
  "email": "active@example.com",
  "user_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "status": "email_already_active",
  "message": "Email address is already active. Please sign in to continue.",
  "next_route": "/auth/sign-in",
  "otp_sent": false
}
```
- **UX**: Direct user to sign-in page

## Code Changes

### 1. Request/Response Models (`src/models/auth.rs`)
**Changed ActivationRequest**:
```rust
// Before
pub struct ActivationRequest {
    pub user_id: Uuid,
    pub activation_token: String,
}

// After
pub struct ActivationRequest {
    pub email: String,
}
```

**Changed ActivationResponse**:
```rust
// Before
pub struct ActivationResponse {
    pub user_id: Uuid,
    pub email: String,
    pub message: String,
    pub next_route: String,
    pub activated_at: String,
}

// After
pub struct ActivationResponse {
    pub email: String,
    pub user_id: Option<Uuid>,  // Optional because email might not be found
    pub status: String,          // "email_not_found" | "otp_sent" | "email_already_active"
    pub message: String,
    pub next_route: String,
    pub otp_sent: bool,
}
```

### 2. Service Logic (`src/services/auth_service.rs`)

**Updated activate_account function**:
- Now accepts email instead of user_id
- Implements three-scenario logic
- Generates and stores OTP on demand
- Returns appropriate status for client routing

**Updated verify_otp function**:
- Now activates the account on successful OTP verification
- Sets `is_active = true`, `status = "ACTIVE"`, `approval_status = "APPROVED"`
- Clears OTP after verification

**Updated sign_up function**:
- Changed `is_active: true` → `is_active: false`
- Users now start as inactive and must activate via OTP

### 3. Database Access (`src/db/repositories/user_repository.rs`)

**Enhanced update method**:
- Previous: Updated only 8 fields
- Now: Updates all 33 user fields including OTP-related fields
- This ensures OTP code and expiration are properly persisted

```rust
// All fields now updated:
- otp_code
- otp_expires  
- last_otp_sent
- email_verified
- email_verification_token
- approval_status
- verified_at, approved_at, approved_by
- And all other user fields
```

### 4. Handler Updates (`src/handlers/auth.rs`)

**Updated logging** to handle optional user_id:
```rust
// Before (failed on None)
log::info!("Activate handler: account activated for {}", response.user_id);

// After (handles all scenarios)
log::info!(
    "Activate handler: status={}, email={}, next_route={}",
    response.status,
    response.email,
    response.next_route
);
```

## Complete User Journey

### New User (Sign Up → Activate → Verify OTP → Sign In)
```
1. User Signs Up
   → is_active: false, status: PENDING
   → receives verification_token

2. User Calls Activate with Email
   → status: "otp_sent"
   → OTP generated and stored (15 min expiration)
   → User directed to OTP verification

3. User Verifies OTP
   → OTP validated against database
   → Account activated: is_active: true, status: ACTIVE
   → User directed to sign in

4. User Signs In
   → Uses email and password
   → Receives JWT access token
   → Full access to application
```

### Returning User (Activate Endpoint)
```
1. User Calls Activate with Email
   → status: "email_already_active"
   → User directed to sign in
```

### Non-Registered User (Activate Endpoint)
```
1. User Calls Activate with Email
   → status: "email_not_found"
   → User directed to sign up
```

## Database State Transitions

| Event | is_active | status | email_verified | otp_code | Notes |
|-------|-----------|--------|---|---|---------|
| Sign Up | false | PENDING | false | NULL | New user created |
| Activate Called | false | PENDING | false | XXXXXX | OTP generated (15 min) |
| OTP Verified | true | ACTIVE | true | NULL | Account activated |
| Already Active | true | ACTIVE | true | NULL | No changes |

## Testing Flow in Postman

### Test Scenario 1: New User Email
```bash
POST /api/auth/activate
Body: {"email": "does_not_exist@example.com"}
Expected: "email_not_found" → Suggest sign up
```

### Test Scenario 2: Inactive User Email
```bash
# First create user (automatically inactive)
POST /api/auth/sign-up
Body: {"email": "test@example.com", "password": "SecurePass123", ...}

# Then activate (sends OTP)
POST /api/auth/activate
Body: {"email": "test@example.com"}
Expected: "otp_sent" → OTP stored in database

# Then verify OTP
POST /api/auth/verify-otp
Body: {"email": "test@example.com", "otp_code": "XXXXXX"}
Expected: Account activated
```

### Test Scenario 3: Already Active User
```bash
# From scenario 2, user is now active
POST /api/auth/activate
Body: {"email": "test@example.com"}
Expected: "email_already_active" → Suggest sign in
```

## API Response Schema

### Success Response (All Scenarios)
```json
{
  "email": "string",
  "user_id": "uuid | null",
  "status": "email_not_found | otp_sent | email_already_active",
  "message": "string",
  "next_route": "/auth/sign-up | /auth/verify-otp | /auth/sign-in",
  "otp_sent": "boolean"
}
```

## Error Handling

The endpoint now returns HTTP 200 with informative status codes for all scenarios. Previous error-based approach changed to informative status-based approach for better UX.

## Deployment Notes

1. **Database Migration**: No schema changes required. Only uses existing fields.
2. **Breaking Changes**: Yes - API contract changed significantly
   - Request body changed from `{user_id, activation_token}` to `{email}`
   - Response structure completely redesigned
3. **Backward Compatibility**: None - clients must update
4. **Testing Required**:
   - Unit tests for all three scenarios
   - Integration tests with database
   - Postman collection provided

## Benefits

✅ Simpler UX - Single endpoint handles all account states  
✅ No token management - Just email required  
✅ Smart routing - System tells client where to go next  
✅ Better error handling - No 404s, clear guidance instead  
✅ Account security - OTP verification before activation  
✅ Flexible - Easy to add more scenarios in future  

## Future Enhancements

- [ ] Send OTP via email (currently logged only)
- [ ] Rate limiting on OTP requests
- [ ] Resend OTP functionality
- [ ] OTP attempt counter to prevent brute force
- [ ] SMS OTP option
- [ ] Audit logging for all state changes

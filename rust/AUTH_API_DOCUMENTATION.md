# Authentication API Endpoints Documentation

## Overview

Complete authentication flow with route guidance in responses. Each endpoint returns a `next_route` field indicating the next step in the user journey.

## Base URL
```
http://localhost:8080/api/auth
```

---

## 1. Sign Up
**Endpoint:** `POST /api/auth/sign-up`

**Description:** Register a new user account

**Request:**
```json
{
  "email": "user@example.com",
  "password": "SecurePassword123",
  "first_name": "John",
  "last_name": "Doe",
  "phone_number": "+1234567890"
}
```

**Success Response (201 Created):**
```json
{
  "user_id": "550e8400-e29b-41d4-a716-446655440000",
  "email": "user@example.com",
  "message": "Sign up successful. Please verify your email to continue.",
  "next_route": "/auth/verify-email",
  "verification_token": "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6"
}
```

**Error Responses:**
- `400 Bad Request` - Invalid email, weak password, or missing fields
- `400 Bad Request` - User already exists with that email

**Validation Rules:**
- Email must be valid format
- Password must be at least 8 characters
- First and last names are required

---

## 2. Verify Email
**Endpoint:** `POST /api/auth/verify-email`

**Description:** Verify user email with verification code received

**Request:**
```json
{
  "user_id": "550e8400-e29b-41d4-a716-446655440000",
  "verification_code": "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6"
}
```

**Success Response (200 OK):**
```json
{
  "user_id": "550e8400-e29b-41d4-a716-446655440000",
  "email": "user@example.com",
  "message": "Email verified successfully",
  "next_route": "/auth/activate",
  "verified_at": "2026-05-19T10:30:45.123456Z"
}
```

**Error Responses:**
- `400 Bad Request` - Invalid verification code
- `400 Bad Request` - Verification code expired (valid for 24 hours)
- `404 Not Found` - User not found

**Flow:**
1. User receives verification code via email
2. User submits code to this endpoint
3. After verification, proceed to account activation

---

## 3. Activate Account
**Endpoint:** `POST /api/auth/activate`

**Description:** Activate user account after email verification

**Request:**
```json
{
  "user_id": "550e8400-e29b-41d4-a716-446655440000",
  "activation_token": "optional-token-if-required"
}
```

**Success Response (200 OK):**
```json
{
  "user_id": "550e8400-e29b-41d4-a716-446655440000",
  "email": "user@example.com",
  "message": "Account activated successfully",
  "next_route": "/profile/complete",
  "activated_at": "2026-05-19T10:35:20.123456Z"
}
```

**Error Responses:**
- `400 Bad Request` - Account already activated
- `400 Bad Request` - Email not verified yet
- `404 Not Found` - User not found

**Flow:**
1. Email is verified
2. User activates account
3. User proceeds to complete profile

---

## 4. Sign In
**Endpoint:** `POST /api/auth/sign-in`

**Description:** Authenticate user and receive JWT access token

**Request:**
```json
{
  "email": "user@example.com",
  "password": "SecurePassword123"
}
```

**Success Response (200 OK):**
```json
{
  "user_id": "550e8400-e29b-41d4-a716-446655440000",
  "email": "user@example.com",
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

**Error Responses:**
- `401 Unauthorized` - Invalid email or password
- `401 Unauthorized` - Account not activated
- `401 Unauthorized` - Account suspended
- `404 Not Found` - User not found

**Token Usage:**
Include in subsequent requests:
```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Next Routes by Status:**
- `ACTIVE` with empty profile → `/profile/complete`
- `ACTIVE` with complete profile → `/dashboard`
- `PENDING` → `/auth/verify-email`
- `SUSPENDED` → `/support/contact`

---

## 5. Forgot Password
**Endpoint:** `POST /api/auth/forgot-password`

**Description:** Initiate password reset process

**Request:**
```json
{
  "email": "user@example.com"
}
```

**Success Response (200 OK):**
```json
{
  "email": "user@example.com",
  "message": "Password reset link sent to your email",
  "next_route": "/auth/reset-password",
  "reset_token_sent": true
}
```

**Error Responses:**
- `404 Not Found` - User with email not found

**Flow:**
1. User requests password reset
2. Email with reset token is sent
3. User receives reset token via email
4. User goes to reset-password endpoint

**Note:** 
- Reset token is valid for 1 hour
- Token is sent via email (TODO: implement email service)

---

## 6. Reset Password
**Endpoint:** `POST /api/auth/reset-password`

**Description:** Reset password using reset token

**Request:**
```json
{
  "email": "user@example.com",
  "reset_token": "reset-token-from-email",
  "new_password": "NewSecurePassword456",
  "confirm_password": "NewSecurePassword456"
}
```

**Success Response (200 OK):**
```json
{
  "user_id": "550e8400-e29b-41d4-a716-446655440000",
  "email": "user@example.com",
  "message": "Password reset successfully",
  "next_route": "/auth/sign-in",
  "reset_at": "2026-05-19T10:45:30.123456Z"
}
```

**Error Responses:**
- `400 Bad Request` - Passwords do not match
- `400 Bad Request` - Password too short (minimum 8 characters)
- `400 Bad Request` - Invalid reset token
- `400 Bad Request` - Reset token expired (valid for 1 hour)
- `404 Not Found` - User not found

**Validation Rules:**
- New password must be at least 8 characters
- Passwords must match
- Token must not be expired

---

## 7. Logout
**Endpoint:** `POST /api/auth/logout`

**Description:** Logout user and invalidate session

**Request:**
```json
{
  "user_id": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Success Response (200 OK):**
```json
{
  "message": "Logged out successfully",
  "next_route": "/auth/sign-in"
}
```

**Error Responses:**
- `404 Not Found` - User not found

**Flow:**
1. User sends logout request
2. Session is invalidated
3. User is redirected to sign-in

---

## Authentication User Journey

### First Time User
```
1. POST /auth/sign-up
   ↓ (returns next_route: /auth/verify-email)
   
2. POST /auth/verify-email (with code from email)
   ↓ (returns next_route: /auth/activate)
   
3. POST /auth/activate
   ↓ (returns next_route: /profile/complete)
   
4. Complete profile on frontend
   ↓
   
5. POST /auth/sign-in
   ↓ (returns next_route: /dashboard)
   
6. Access dashboard with access_token
```

### Existing User
```
1. POST /auth/sign-in
   ↓ (returns next_route: /dashboard and access_token)
   
2. Access dashboard with access_token
```

### Forgot Password Flow
```
1. POST /auth/forgot-password
   ↓ (returns next_route: /auth/reset-password)
   
2. User receives reset token via email
   
3. POST /auth/reset-password (with token and new password)
   ↓ (returns next_route: /auth/sign-in)
   
4. POST /auth/sign-in with new password
   ↓ (returns next_route: /dashboard)
```

---

## Common Response Structure

All responses include a `next_route` field that guides the client to the next step:

```json
{
  // ... specific response data ...
  "message": "Human-readable message",
  "next_route": "/next/endpoint/to/visit",
  // ... other fields ...
}
```

**Client Implementation:**
```javascript
// Example in React/JavaScript
const response = await fetch('/api/auth/sign-up', { /* ... */ });
const data = await response.json();

if (response.ok) {
  // Navigate user to next step
  navigate(data.next_route);
} else {
  // Show error and optionally navigate to suggested route
  showError(data.message);
  if (data.next_route) {
    suggestNext(data.next_route);
  }
}
```

---

## Security Considerations

1. **JWT Tokens**
   - Access token expires in 1 hour
   - Always use HTTPS in production
   - Store token securely on client (httpOnly cookie recommended)

2. **Password Security**
   - Minimum 8 characters required
   - Passwords are hashed with bcrypt
   - Never returned in responses

3. **Email Verification**
   - Tokens expire in 24 hours
   - Can only be verified once
   - Required before account activation

4. **Reset Tokens**
   - Expire in 1 hour
   - Can only be used once
   - Rate-limited to prevent abuse (TODO: implement)

5. **Session Management**
   - Implement token blacklist on logout (TODO: implement)
   - Validate tokens on protected routes
   - Refresh tokens for extended sessions (TODO: implement)

---

## Error Codes

| Code | Type | Reason |
|------|------|--------|
| 400 | BadRequest | Invalid input or validation failed |
| 401 | Unauthorized | Authentication failed or token invalid |
| 404 | NotFound | Resource not found |
| 409 | Conflict | Resource already exists |
| 500 | InternalServerError | Server error |

---

## Testing with cURL

### Sign Up
```bash
curl -X POST http://localhost:8080/api/auth/sign-up \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "TestPassword123",
    "first_name": "Test",
    "last_name": "User",
    "phone_number": "+1234567890"
  }'
```

### Sign In
```bash
curl -X POST http://localhost:8080/api/auth/sign-in \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "TestPassword123"
  }'
```

### Verify Email
```bash
curl -X POST http://localhost:8080/api/auth/verify-email \
  -H "Content-Type: application/json" \
  -d '{
    "user_id": "550e8400-e29b-41d4-a716-446655440000",
    "verification_code": "your-verification-code"
  }'
```

### Activate Account
```bash
curl -X POST http://localhost:8080/api/auth/activate \
  -H "Content-Type: application/json" \
  -d '{
    "user_id": "550e8400-e29b-41d4-a716-446655440000",
    "activation_token": ""
  }'
```

### Forgot Password
```bash
curl -X POST http://localhost:8080/api/auth/forgot-password \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com"
  }'
```

### Reset Password
```bash
curl -X POST http://localhost:8080/api/auth/reset-password \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "reset_token": "your-reset-token",
    "new_password": "NewPassword456",
    "confirm_password": "NewPassword456"
  }'
```

### Logout
```bash
curl -X POST http://localhost:8080/api/auth/logout \
  -H "Content-Type: application/json" \
  -d '{
    "user_id": "550e8400-e29b-41d4-a716-446655440000"
  }'
```

---

## Future Enhancements

- [ ] Email service integration for verification and reset emails
- [ ] Two-factor authentication (2FA)
- [ ] OAuth2 integration (Google, GitHub, etc.)
- [ ] Rate limiting on auth endpoints
- [ ] Token refresh mechanism
- [ ] Account lockout after failed attempts
- [ ] Social login integration
- [ ] Session management with redis

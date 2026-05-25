# Role-Based Sign-Up - Quick Reference

## Endpoint

```
POST /api/auth/sign-up
Content-Type: application/json
```

## Request Payload

```json
{
  "email": "user@example.com",
  "password": "SecurePass123",
  "first_name": "John",
  "last_name": "Doe",
  "phone_number": "+1234567890",
  "role": "STAFF",
  "school_code": "central-high"
}
```

## Response (201 Created)

```json
{
  "user_id": "550e8400-e29b-41d4-a716-446655440000",
  "email": "user@example.com",
  "role": "STAFF",
  "school_id": "660e8400-e29b-41d4-a716-446655440001",
  "school_name": "Central High School",
  "user_school_role_id": "770e8400-e29b-41d4-a716-446655440002",
  "message": "Sign up successful as STAFF for Central High School. Please verify your email to continue.",
  "next_route": "/auth/activate",
  "verification_token": "abc123def456..."
}
```

## Supported Roles

| Role | Requires school_code | Purpose |
|------|----------------------|---------|
| STAFF | ✅ Yes | School staff members |
| PARENT | ✅ Yes | Parents/guardians |
| ADMIN | ✅ Yes | School administrators |
| SCHOOL_ADMIN | ❌ No | System administrators (Phase 2) |

## cURL Examples

### STAFF Role
```bash
curl -X POST http://localhost:8080/api/auth/sign-up \
  -H "Content-Type: application/json" \
  -d '{
    "email": "staff@school.com",
    "password": "SecurePass123",
    "first_name": "John",
    "last_name": "Smith",
    "phone_number": "+1234567890",
    "role": "STAFF",
    "school_code": "central-high"
  }'
```

### PARENT Role
```bash
curl -X POST http://localhost:8080/api/auth/sign-up \
  -H "Content-Type: application/json" \
  -d '{
    "email": "parent@example.com",
    "password": "SecurePass123",
    "first_name": "Jane",
    "last_name": "Doe",
    "role": "PARENT",
    "school_code": "central-high"
  }'
```

### ADMIN Role  
```bash
curl -X POST http://localhost:8080/api/auth/sign-up \
  -H "Content-Type: application/json" \
  -d '{
    "email": "admin@school.com",
    "password": "SecurePass123",
    "first_name": "Bob",
    "last_name": "Johnson",
    "role": "ADMIN",
    "school_code": "central-high"
  }'
```

## Common Errors

### Invalid Role
**Status**: 400 Bad Request
```json
{
  "error": "Invalid role. Must be one of: STAFF, PARENT, ADMIN, SCHOOL_ADMIN"
}
```

### Missing school_code
**Status**: 400 Bad Request
```json
{
  "error": "school_code is required for STAFF role"
}
```

### School Not Found
**Status**: 404 Not Found
```json
{
  "error": "School with slug non-existent-school not found"
}
```

### Duplicate Role
**Status**: 400 Bad Request
```json
{
  "error": "User user@example.com already has STAFF role at this school"
}
```

### SCHOOL_ADMIN Not Supported (Phase 1)
**Status**: 400 Bad Request
```json
{
  "error": "SCHOOL_ADMIN registration requires manual setup. Please contact the system administrator."
}
```

## Database Schema Requirements

### schools table must have:
- id (UUID)
- name (VARCHAR)
- slug (VARCHAR, UNIQUE)
- email, phone, website, address fields (VARCHAR)
- status, timezone, currency, language (VARCHAR)
- academic_year_start, academic_year_end (VARCHAR)
- created_at, updated_at (TIMESTAMPTZ)
- is_active (BOOLEAN)

### Example school insert:
```sql
INSERT INTO schools (
  id, name, slug, email, phone, address_line1, city, state, postal_code, country,
  status, timezone, currency, language, academic_year_start, academic_year_end,
  admin_name, admin_email, admin_phone, created_at, updated_at, is_active
) VALUES (
  '11111111-1111-1111-1111-111111111111',
  'Central High School',
  'central-high',
  'admin@central.edu',
  '1234567890',
  '123 Main Street',
  'Springfield',
  'IL',
  '62701',
  'USA',
  'ACTIVE',
  'UTC',
  'USD',
  'en',
  '01-01',
  '12-31',
  '',
  'admin@central.edu',
  '',
  NOW(),
  NOW(),
  true
);
```

## Testing Steps

1. **Create test school** in database (see above)
2. **POST to /api/auth/sign-up** with STAFF role and school code
3. **Verify response** includes role, school_id, school_name, user_school_role_id
4. **Activate account** using /api/auth/activate with email
5. **Verify OTP** using /api/auth/verify-otp with OTP from database

## Files Reference

| File | Purpose |
|------|---------|
| src/models/auth.rs | SignUpRequest/Response with role fields |
| src/services/auth_service.rs | Enhanced sign_up logic with role handling |
| src/db/repositories/user_school_role_repository.rs | UserSchoolRole CRUD operations |
| src/db/repositories/school_repository.rs | School lookup by slug |
| ROLE_BASED_SIGNUP_GUIDE.md | Comprehensive documentation |

## Status

✅ **Ready for Testing** - All code complete and compiled successfully

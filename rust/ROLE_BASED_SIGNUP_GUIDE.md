# Role-Based Sign Up Implementation Guide

## Overview
The sign-up endpoint has been enhanced to support role-based account creation with school binding and multi-role management through the `UserSchoolRole` junction table.

## Key Features

### 1. Role-Based User Registration
Users can now sign up with one of four roles:
- **STAFF**: School staff members (requires school_code)
- **PARENT**: Parent/guardian (requires school_code)
- **ADMIN**: School administrator (requires school_code)
- **SCHOOL_ADMIN**: System-level school administrator (creates empty school)

### 2. Multi-Role Support
- A single email address can have **multiple roles** across different schools
- Roles are identified by the combination of `(user_id, school_id, role_id)`
- Duplicate UserSchoolRole combinations are prevented - returning error only if exact same role+school already exists for user

### 3. Request/Response Structure

#### SignUpRequest
```rust
{
  "email": "staff@school.com",
  "password": "SecurePass123",
  "first_name": "John",
  "last_name": "Doe",
  "phone_number": "+1234567890",  // Optional
  "role": "STAFF",                 // STAFF, PARENT, ADMIN, or SCHOOL_ADMIN
  "school_code": "central-high"    // Required for STAFF/PARENT/ADMIN; omitted for SCHOOL_ADMIN
}
```

#### SignUpResponse
```rust
{
  "user_id": "550e8400-e29b-41d4-a716-446655440000",
  "email": "staff@school.com",
  "role": "STAFF",
  "school_id": "660e8400-e29b-41d4-a716-446655440001",
  "school_name": "Central High School",
  "user_school_role_id": "770e8400-e29b-41d4-a716-446655440002",
  "message": "Sign up successful as STAFF for Central High School. Please verify your email to continue.",
  "next_route": "/auth/activate",
  "verification_token": "abcdef123456789abcdef123456789abcd"
}
```

## Implementation Details

### File Changes

#### 1. Models Updated
**File**: `src/models/auth.rs`
- Added `role: String` field to `SignUpRequest`
- Added `school_code: Option<String>` field to `SignUpRequest`
- Added `role`, `school_id`, `school_name`, `user_school_role_id` to `SignUpResponse`

#### 2. Repository Layer
**New File**: `src/db/repositories/user_school_role_repository.rs`
- `UserSchoolRoleRepository::exists()` - Check if role+school+user combination exists
- `UserSchoolRoleRepository::create()` - Create new UserSchoolRole record
- `UserSchoolRoleRepository::get_by_user_id()` - Get all roles for user
- `UserSchoolRoleRepository::get_by_school_id()` - Get all users for school

**Updated File**: `src/db/repositories/mod.rs`
- Added `pub mod user_school_role_repository`
- Added `pub use user_school_role_repository::UserSchoolRoleRepository`

#### 3. Service Logic
**File**: `src/services/auth_service.rs`
- Enhanced `sign_up()` with role-based flow:
  1. Validate role (must be one of: STAFF, PARENT, ADMIN, SCHOOL_ADMIN)
  2. Validate school_code requirement based on role
  3. Resolve school:
     - For STAFF/PARENT/ADMIN: lookup by slug
     - For SCHOOL_ADMIN: return error (manual setup required for production)
  4. Check if user exists
  5. If user exists, verify UserSchoolRole doesn't already exist
  6. Create new user if email is new
  7. Create UserSchoolRole linking user to school with role
  8. Return response with role and school information

- Added `get_role_id_for_name()` helper function:
  - Maps role names to hardcoded UUIDs (TODO: implement proper role lookup)
  - Role IDs:
    - STAFF: `00000000-0000-0000-0000-000000000001`
    - PARENT: `00000000-0000-0000-0000-000000000002`
    - ADMIN: `00000000-0000-0000-0000-000000000003`
    - SCHOOL_ADMIN: `00000000-0000-0000-0000-000000000004`

## API Workflow Examples

### Example 1: Staff Registration
```bash
curl -X POST http://localhost:8080/api/auth/sign-up \
  -H "Content-Type: application/json" \
  -d '{
    "email": "teacher@school.com",
    "password": "SecurePass123",
    "first_name": "Jane",
    "last_name": "Smith",
    "phone_number": "+1234567890",
    "role": "STAFF",
    "school_code": "central-high"
  }'
```

**Response (201 Created)**:
```json
{
  "user_id": "550e8400-e29b-41d4-a716-446655440000",
  "email": "teacher@school.com",
  "role": "STAFF",
  "school_id": "660e8400-e29b-41d4-a716-446655440001",
  "school_name": "Central High School",
  "user_school_role_id": "770e8400-e29b-41d4-a716-446655440002",
  "message": "Sign up successful as STAFF for Central High School. Please verify your email to continue.",
  "next_route": "/auth/activate",
  "verification_token": "abc123def456..."
}
```

**Next Steps**:
1. User receives verification email (to be implemented)
2. User calls `/api/auth/activate` with email
3. System sends OTP to email
4. User calls `/api/auth/verify-otp` with OTP
5. Account is activated and ready to use

### Example 2: Multiple Roles Same User
```bash
# First signup as STAFF at school1
curl -X POST http://localhost:8080/api/auth/sign-up \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "password": "SecurePass123",
    "first_name": "Alex",
    "last_name": "Johnson",
    "role": "STAFF",
    "school_code": "school-1"
  }'

# Later, same user can signup as ADMIN at school2
curl -X POST http://localhost:8080/api/auth/sign-up \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "password": "SecurePass123",
    "first_name": "Alex",
    "last_name": "Johnson",
    "role": "ADMIN",
    "school_code": "school-2"
  }'
```

Both registrations succeed, creating two `UserSchoolRole` records:
- `user_id + school-1 + STAFF` role
- `user_id + school-2 + ADMIN` role

### Example 3: Duplicate Role Prevention
```bash
# First signup - succeeds
curl -X POST http://localhost:8080/api/auth/sign-up -d '{
  "email": "user@example.com",
  "password": "SecurePass123",
  "first_name": "User",
  "last_name": "Test",
  "role": "STAFF",
  "school_code": "school-1"
}'

# Duplicate signup - fails with 400 Bad Request
curl -X POST http://localhost:8080/api/auth/sign-up -d '{
  "email": "user@example.com",
  "password": "SecurePass123",
  "first_name": "User",
  "last_name": "Test",
  "role": "STAFF",
  "school_code": "school-1"
}'

# Response:
# {
#   "error": "User user@example.com already has STAFF role at this school"
# }
```

## Database Requirements

### Schools Table
The `schools` table must exist with the following columns:
```sql
CREATE TABLE schools (
  id UUID PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  slug VARCHAR(100) UNIQUE NOT NULL,
  email VARCHAR(255),
  phone VARCHAR(20),
  website VARCHAR(255),
  address_line1 VARCHAR(255),
  address_line2 VARCHAR(255),
  city VARCHAR(100),
  state VARCHAR(100),
  postal_code VARCHAR(20),
  country VARCHAR(100),
  subdomain VARCHAR(100),
  custom_domain VARCHAR(255),
  ssl_enabled BOOLEAN DEFAULT false,
  status VARCHAR(50),
  timezone VARCHAR(50),
  currency VARCHAR(10),
  language VARCHAR(10),
  academic_year_start VARCHAR(10),
  academic_year_end VARCHAR(10),
  current_academic_year VARCHAR(10),
  admin_user_id UUID,
  admin_name VARCHAR(255),
  admin_email VARCHAR(255),
  admin_phone VARCHAR(20),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  is_active BOOLEAN DEFAULT true
);

CREATE INDEX idx_schools_slug ON schools(slug);
```

### Roles Table (TODO)
For production, implement a proper roles table:
```sql
CREATE TABLE roles (
  id UUID PRIMARY KEY,
  name VARCHAR(50) UNIQUE NOT NULL,
  description TEXT,
  role_type VARCHAR(50),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  is_active BOOLEAN DEFAULT true
);
```

## Error Scenarios

### 1. Invalid Role
```bash
curl -X POST http://localhost:8080/api/auth/sign-up -d '{
  "email": "user@example.com",
  "password": "SecurePass123",
  "first_name": "User",
  "last_name": "Test",
  "role": "INVALID_ROLE"
}'

# Response: 400 Bad Request
# { "error": "Invalid role. Must be one of: STAFF, PARENT, ADMIN, SCHOOL_ADMIN" }
```

### 2. Missing school_code for STAFF Role
```bash
curl -X POST http://localhost:8080/api/auth/sign-up -d '{
  "email": "user@example.com",
  "password": "SecurePass123",
  "first_name": "User",
  "last_name": "Test",
  "role": "STAFF"
  // Missing school_code
}'

# Response: 400 Bad Request
# { "error": "school_code is required for STAFF role" }
```

### 3. School Not Found
```bash
curl -X POST http://localhost:8080/api/auth/sign-up -d '{
  "email": "user@example.com",
  "password": "SecurePass123",
  "first_name": "User",
  "last_name": "Test",
  "role": "STAFF",
  "school_code": "non-existent-school"
}'

# Response: 404 Not Found
# { "error": "School with slug non-existent-school not found" }
```

### 4. SCHOOL_ADMIN Not Yet Supported
```bash
curl -X POST http://localhost:8080/api/auth/sign-up -d '{
  "email": "admin@example.com",
  "password": "SecurePass123",
  "first_name": "Admin",
  "last_name": "User",
  "role": "SCHOOL_ADMIN"
}'

# Response: 400 Bad Request
# { "error": "SCHOOL_ADMIN registration requires manual setup. Please contact the system administrator." }
```

## Testing Checklist

- [x] Role validation
- [x] School code requirement validation
- [x] School lookup by slug
- [x] User existence check
- [x] UserSchoolRole duplicate prevention
- [x] Multi-role support (same user, different schools)
- [x] Response includes all required fields (role, school_id, school_name, user_school_role_id)
- [ ] SCHOOL_ADMIN implementation (deferred to Phase 2)
- [ ] Email verification integration
- [ ] OTP flow integration
- [ ] Role-based access control on other endpoints

## Phase 2 Enhancements

1. **SCHOOL_ADMIN Support**: Implement dedicated school creation flow
2. **Roles Table**: Move from hardcoded UUIDs to database-driven roles
3. **Email Verification**: Send verification email with links
4. **Role-Based Authorization**: Enforce role checks on all endpoints
5. **Admin Dashboard**: View/manage users and their roles
6. **Bulk User Import**: CSV import with role assignment

## Related Files

- [Sign-up Request/Response Models](src/models/auth.rs)
- [User & UserSchoolRole Models](src/models/users.rs)
- [School Model](src/models/organizations.rs)
- [Auth Service Logic](src/services/auth_service.rs)
- [User Repository](src/db/repositories/user_repository.rs)
- [UserSchoolRole Repository](src/db/repositories/user_school_role_repository.rs)
- [School Repository](src/db/repositories/school_repository.rs)
- [Sign-up Handler](src/handlers/auth.rs)

## Status

✅ **Implementation Complete** - Ready for integration testing with full database schema setup

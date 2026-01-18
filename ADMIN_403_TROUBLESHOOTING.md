# Admin 403 Error Troubleshooting Guide

## Problem
The user `admin@demohighschool.edu` should have `SCHOOL_ADMIN` access and be able to access `/admin/**` routes, but is getting a 403 Forbidden error.

## Expected Setup

### 1. Database Roles
The system should have these roles created by `DataInitializationService`:
- `ADMIN` (RoleType.ADMIN) - "System Administrator"
- `SYSTEM_ADMIN` (RoleType.ADMIN) - "Platform System Administrator"  
- `PRINCIPAL` (RoleType.ADMIN) - "School Principal"
- `TEACHER` (RoleType.STAFF) - "Teacher"
- `STUDENT` (RoleType.STUDENT) - "Student"
- `PARENT` (RoleType.PARENT) - "Parent"
- `STAFF` (RoleType.STAFF) - "Staff Member"

### 2. Admin User Creation
The `DataInitializationService` should create:
- **User**: `admin@demohighschool.edu` with password `admin123`
- **School**: "Demo High School" with slug "demo-high-school"
- **UserSchoolRole**: Links admin user to school with `ADMIN` role

### 3. Security Configuration
The `SecurityConfig` allows these roles to access `/admin/**`:
```kotlin
.requestMatchers("/admin/**").hasAnyRole("SYSTEM_ADMIN", "ADMIN", "PRINCIPAL", "STAFF", "TEACHER")
```

### 4. Role Mapping
The `CustomUserDetails` maps database roles to Spring Security authorities:
- `ADMIN` → `ROLE_ADMIN`
- `PRINCIPAL` → `ROLE_PRINCIPAL`
- etc.

## Troubleshooting Steps

### 1. Check Database State
Visit these debug endpoints to verify the database setup:

**Check all roles and admin user:**
```
GET /debug/roles
```

**Check specific user details:**
```
GET /debug/user/admin@demohighschool.edu
```

**Check current authentication state:**
```
GET /debug/auth-info
```

### 2. Verify DataInitializationService Execution
The service should run automatically because:
- It implements `CommandLineRunner`
- It has `@Profile("!prod")` (runs when NOT in production)
- No profile is set in `application.properties`, so it defaults to development

### 3. Check Expected Database Records

**Roles Table:**
```sql
SELECT * FROM roles WHERE name IN ('ADMIN', 'SYSTEM_ADMIN', 'PRINCIPAL');
```

**Users Table:**
```sql
SELECT * FROM users WHERE email = 'admin@demohighschool.edu';
```

**User School Roles:**
```sql
SELECT usr.*, r.name as role_name, s.name as school_name 
FROM user_school_roles usr 
JOIN roles r ON usr.role_id = r.id 
JOIN schools s ON usr.school_id = s.id 
JOIN users u ON usr.user_id = u.id 
WHERE u.email = 'admin@demohighschool.edu';
```

### 4. Common Issues and Solutions

#### Issue 1: DataInitializationService Not Running
**Symptoms:** No roles or admin user in database
**Solutions:**
- Check application logs for initialization messages
- Verify no "prod" profile is active
- Manually trigger service if needed

#### Issue 2: Role Mapping Mismatch
**Symptoms:** User has role in database but wrong authorities in Spring Security
**Check:** 
- `CustomUserDetails.getAuthorities()` logs
- Role name mapping in `CustomUserDetails`
- SecurityConfig role requirements

#### Issue 3: User Not Loading Roles
**Symptoms:** User found but no school roles loaded
**Check:**
- `CustomUserDetailsService.loadUserByUsername()` using `findByEmailWithRoles()`
- Lazy loading issues with User.schoolRoles relationship
- UserSchoolRole.isActive = true

#### Issue 4: Session/Authentication Issues
**Symptoms:** User authenticated but wrong session attributes
**Check:**
- `CustomAuthenticationSuccessHandler` logs
- Session attributes: `selectedSchoolId`, `selectedRole`
- Role selection flow for multi-role users

## Debug Endpoints Added

### `/debug/roles`
Returns:
- All roles in database
- Admin user school roles
- All schools
- Database connection status

### `/debug/user/{email}`
Returns:
- User details from database
- School roles and global roles
- Spring Security authorities
- UserDetails loading status

### `/debug/auth-info`
Returns:
- Current authentication state
- User authorities
- Session attributes

## Quick Fix Commands

If the admin user is missing or has wrong roles:

### 1. Recreate Admin User (SQL)
```sql
-- Find or create ADMIN role
INSERT INTO roles (id, name, role_type, description, is_system_role, created_at, updated_at, is_active) 
VALUES (gen_random_uuid(), 'ADMIN', 'ADMIN', 'System Administrator', false, now(), now(), true)
ON CONFLICT (name) DO NOTHING;

-- Find or create Demo School
INSERT INTO schools (id, name, slug, created_at, updated_at, is_active)
VALUES (gen_random_uuid(), 'Demo High School', 'demo-high-school', now(), now(), true)
ON CONFLICT (slug) DO NOTHING;

-- Create admin user
INSERT INTO users (id, phone_number, password_hash, email, first_name, last_name, status, is_verified, email_verified, created_at, updated_at, is_active)
VALUES (gen_random_uuid(), '+1-555-0100', '$2a$10$encrypted_password_hash', 'admin@demohighschool.edu', 'System', 'Administrator', 'ACTIVE', true, true, now(), now(), true)
ON CONFLICT (email) DO NOTHING;

-- Link user to school with ADMIN role
INSERT INTO user_school_roles (id, user_id, school_id, role_id, is_primary, assigned_at, created_at, updated_at, is_active)
SELECT gen_random_uuid(), u.id, s.id, r.id, true, now(), now(), now(), true
FROM users u, schools s, roles r
WHERE u.email = 'admin@demohighschool.edu' 
  AND s.slug = 'demo-high-school'
  AND r.name = 'ADMIN'
ON CONFLICT (user_id, school_id, role_id) DO NOTHING;
```

### 2. Restart Application
After manual database fixes, restart the application to reload the user data.

## Expected Resolution
After troubleshooting, the admin user should:
1. ✅ Exist in database with `ADMIN` role
2. ✅ Load with `ROLE_ADMIN` authority in Spring Security
3. ✅ Successfully access `/admin/**` routes
4. ✅ Redirect to `/admin/dashboard` after login
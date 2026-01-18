# School Admin Role Restructure

## Overview
Restructured the admin role hierarchy to better reflect the organizational structure:
- **SCHOOL_ADMIN**: Overall school administrator with full access
- **ADMIN**: Functional administrators for specific tasks (Financial, Recruitment, etc.)

## Changes Made

### 1. Role Entity (`Role.kt`)
- Added `SCHOOL_ADMIN` to `RoleType` enum (already existed)

### 2. DataInitializationService (`DataInitializationService.kt`)
**Role Creation:**
```kotlin
// Before
Role("ADMIN", RoleType.ADMIN, "System Administrator")

// After  
Role("SCHOOL_ADMIN", RoleType.SCHOOL_ADMIN, "School Administrator - Full school management access")
Role("ADMIN", RoleType.ADMIN, "Functional Administrator - Specific task access")
```

**Admin User Assignment:**
```kotlin
// Before
val adminRole = roleRepository.findByName("ADMIN").orElseThrow()

// After
val adminRole = roleRepository.findByName("SCHOOL_ADMIN").orElseThrow()
```

### 3. SecurityConfig (`SecurityConfig.kt`)
**Updated URL Access Rules:**
```kotlin
// Before
.requestMatchers("/admin/**").hasAnyRole("SYSTEM_ADMIN", "ADMIN", "PRINCIPAL", "STAFF", "TEACHER")

// After
.requestMatchers("/admin/**").hasAnyRole("SYSTEM_ADMIN", "SCHOOL_ADMIN", "ADMIN", "PRINCIPAL", "STAFF", "TEACHER")
```

### 4. CustomUserDetails (`CustomUserDetails.kt`)
**Added SCHOOL_ADMIN Role Mapping:**
```kotlin
"SCHOOL_ADMIN" -> "SCHOOL_ADMIN"  // Maps to ROLE_SCHOOL_ADMIN
"ADMIN" -> "ADMIN"                // Maps to ROLE_ADMIN
```

### 5. CustomAuthenticationSuccessHandler (`CustomAuthenticationSuccessHandler.kt`)
**Updated Dashboard URL Mapping:**
```kotlin
// Before
"ADMIN", "PRINCIPAL" -> "/admin/dashboard"

// After
"SCHOOL_ADMIN", "ADMIN", "PRINCIPAL" -> "/admin/dashboard"
```

**Updated Access Control Lists:**
- Added `SCHOOL_ADMIN` to all admin role lists
- Maintained `ADMIN` for functional admin access

### 6. RoleSelectionController (`RoleSelectionController.kt`)
**Updated Role-Based Redirects:**
```kotlin
"SCHOOL_ADMIN" -> "/admin/dashboard"
"ADMIN" -> "/admin/dashboard"
```

### 7. AdminDashboardController (`AdminDashboardController.kt`)
**Updated PreAuthorize Annotation:**
```kotlin
@PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'SCHOOL_ADMIN', 'ADMIN', 'PRINCIPAL', 'TEACHER', 'STAFF')")
```

## Role Hierarchy

### System Level
- **SYSTEM_ADMIN**: Platform-wide administrator (multi-school access)

### School Level
- **SCHOOL_ADMIN**: Overall school administrator (full school management)
- **PRINCIPAL**: School principal (administrative access)
- **ADMIN**: Functional administrator (specific task access)

### Operational Level
- **TEACHER**: Teaching staff
- **STAFF**: General staff members
- **PARENT**: Student parents/guardians
- **STUDENT**: Students

## Access Permissions

### `/admin/**` Routes
- SYSTEM_ADMIN ✅
- SCHOOL_ADMIN ✅ (NEW)
- ADMIN ✅
- PRINCIPAL ✅
- STAFF ✅
- TEACHER ✅

### `/staff/**` Routes
- SYSTEM_ADMIN ✅
- SCHOOL_ADMIN ✅ (NEW)
- ADMIN ✅
- PRINCIPAL ✅
- STAFF ✅
- TEACHER ✅

### `/parent/**` Routes
- SYSTEM_ADMIN ✅
- SCHOOL_ADMIN ✅ (NEW)
- ADMIN ✅
- PRINCIPAL ✅
- STAFF ✅
- PARENT ✅

## Database Impact

### New Records Created
When DataInitializationService runs, it will create:
1. **SCHOOL_ADMIN role** with RoleType.SCHOOL_ADMIN
2. **ADMIN role** with RoleType.ADMIN (functional admin)
3. **admin@demohighschool.edu** user assigned to SCHOOL_ADMIN role

### Migration Considerations
For existing databases:
- Existing ADMIN roles will remain as functional admins
- New SCHOOL_ADMIN role will be created for overall school management
- Existing admin users may need role updates if they should be SCHOOL_ADMIN

## Future Use Cases

### SCHOOL_ADMIN
- Complete school management
- User management (all roles)
- Financial oversight
- Academic management
- System configuration

### ADMIN (Functional)
- **Financial Admin**: Fee management, payments, accounting
- **Academic Admin**: Curriculum, assessments, grades
- **Recruitment Admin**: Admissions, enrollment
- **HR Admin**: Staff management, payroll
- **IT Admin**: System maintenance, technical support

## Testing
After these changes:
1. **admin@demohighschool.edu** should have SCHOOL_ADMIN role
2. **Spring Security authority**: ROLE_SCHOOL_ADMIN
3. **Access**: Full access to /admin/** routes
4. **Dashboard**: Redirects to /admin/dashboard

Use `/debug/roles` and `/debug/user/admin@demohighschool.edu` to verify the changes.
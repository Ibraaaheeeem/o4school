# Role Mapping Fix - 403 Error Resolution

## Problem
After implementing role-based dashboard redirection, users were getting 403 Forbidden errors when trying to access their dashboards. The issue was a mismatch between:
- **Database roles**: ADMIN, PRINCIPAL, TEACHER, STAFF, PARENT, STUDENT
- **Security configuration**: Expected SCHOOL_ADMIN (which doesn't exist in DB)
- **Role mapping logic**: Inconsistent mapping between role names

## Root Cause
1. **SecurityConfig.kt** was checking for `SCHOOL_ADMIN` role that doesn't exist in the database
2. **CustomUserDetails.kt** was mapping `PRINCIPAL` and `ADMIN` to `SCHOOL_ADMIN` 
3. **Role-based URL access** was using non-existent role names

## Solution
Updated all components to use the actual database role names consistently:

### 1. SecurityConfig.kt
**Before:**
```kotlin
.requestMatchers("/admin/**").hasAnyRole("SYSTEM_ADMIN", "SCHOOL_ADMIN", "STAFF", "TEACHER")
```

**After:**
```kotlin
.requestMatchers("/admin/**").hasAnyRole("SYSTEM_ADMIN", "ADMIN", "PRINCIPAL", "STAFF", "TEACHER")
```

### 2. CustomUserDetails.kt
**Before:**
```kotlin
"PRINCIPAL" -> "SCHOOL_ADMIN"  // Principal maps to SCHOOL_ADMIN for permissions
"ADMIN" -> "SCHOOL_ADMIN"
```

**After:**
```kotlin
"PRINCIPAL" -> "PRINCIPAL"
"ADMIN" -> "ADMIN"
```

### 3. CustomAuthenticationSuccessHandler.kt
**Before:**
```kotlin
"SCHOOL_ADMIN", "PRINCIPAL", "ADMIN" -> "/admin/dashboard"
```

**After:**
```kotlin
"ADMIN", "PRINCIPAL" -> "/admin/dashboard"
```

### 4. RoleSelectionController.kt
Updated role mappings to match database roles.

## Database Roles (from DataInitializationService)
- **SYSTEM_ADMIN** - Platform System Administrator
- **ADMIN** - System Administrator  
- **PRINCIPAL** - School Principal
- **TEACHER** - Teacher
- **STAFF** - Staff Member
- **PARENT** - Parent
- **STUDENT** - Student

## URL Access Permissions
- **`/system-admin/**`** → SYSTEM_ADMIN only
- **`/admin/**`** → SYSTEM_ADMIN, ADMIN, PRINCIPAL, STAFF, TEACHER
- **`/staff/**`** → SYSTEM_ADMIN, ADMIN, PRINCIPAL, STAFF, TEACHER  
- **`/parent/**`** → SYSTEM_ADMIN, ADMIN, PRINCIPAL, STAFF, PARENT
- **`/student/**`** → SYSTEM_ADMIN, ADMIN, PRINCIPAL, STAFF, PARENT, STUDENT

## Dashboard Redirections
- **ADMIN, PRINCIPAL** → `/admin/dashboard`
- **TEACHER, STAFF** → `/staff/dashboard`
- **PARENT** → `/parent/dashboard`
- **STUDENT** → `/student/dashboard`

## Verification
- ✅ Code compiles successfully
- ✅ Role names are consistent across all components
- ✅ Security configuration matches database roles
- ✅ Dashboard redirections use correct role names

## Testing Recommendations
1. Test login with different user roles
2. Verify proper dashboard redirection
3. Confirm access permissions work correctly
4. Test role selection flow for multi-role users
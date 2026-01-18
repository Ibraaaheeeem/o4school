# User Roles Loading Fix - 403 Error Resolution

## Problem Identified
From the logs, we discovered that users were only getting these authorities:
- `ROLE_USER` (default)
- `FactorGrantedAuthority [authority=FACTOR_PASSWORD]` (authentication factor)

But they were missing their actual role authorities like `ROLE_PARENT`, `ROLE_ADMIN`, etc., which caused 403 errors when trying to access role-specific dashboards.

## Root Cause
The issue was in `CustomUserDetailsService.loadUserByUsername()` method:

**Before (Broken):**
```kotlin
"EMAIL" -> {
    val user = userRepository.findByEmailIgnoreCase(identifier)  // ❌ No roles loaded
    CustomUserDetails(user)
}
"PHONE" -> {
    val user = userRepository.findByPhoneNumberForAuth(identifier)  // ❌ No roles loaded
    CustomUserDetails(user)
}
```

**After (Fixed):**
```kotlin
"EMAIL" -> {
    val user = userRepository.findByEmailWithRoles(identifier)  // ✅ Roles loaded
    CustomUserDetails(user)
}
"PHONE" -> {
    val user = userRepository.findByPhoneNumberWithRoles(identifier)  // ✅ Roles loaded
    CustomUserDetails(user)
}
```

## The Fix

### 1. Updated CustomUserDetailsService.kt
- Changed `findByEmailIgnoreCase()` → `findByEmailWithRoles()`
- Changed `findByPhoneNumberForAuth()` → `findByPhoneNumberWithRoles()`
- Updated student login to also load user with roles
- Added comprehensive logging to track user loading

### 2. Added Logging
**CustomUserDetailsService** now logs:
- Which method is being used to load the user
- How many school roles and global roles are loaded
- User identification details

**CustomUserDetails** logs:
- Authority assignment process
- Role mapping from database to Spring Security authorities
- Final list of authorities assigned

## Expected Behavior After Fix

### 1. During Login
```
=== LOADING USER BY USERNAME: EMAIL:user@example.com ===
User loaded by email: user@example.com with 1 school roles and 0 global roles

=== GETTING AUTHORITIES FOR USER: user@example.com ===
Added default ROLE_USER
Added school authority: ROLE_PARENT for schoolId: uuid
=== FINAL AUTHORITIES FOR user@example.com: [ROLE_USER, ROLE_PARENT] ===
```

### 2. During Dashboard Access
User will now have the correct authorities to access their role-specific dashboard:
- **PARENT** users → Can access `/parent/dashboard`
- **ADMIN** users → Can access `/admin/dashboard`
- **STAFF** users → Can access `/staff/dashboard`
- etc.

## Database Queries Used
The fix ensures these optimized queries are used:
```sql
-- For email login
SELECT u FROM User u 
LEFT JOIN FETCH u.schoolRoles sr 
LEFT JOIN FETCH sr.role 
LEFT JOIN FETCH u.globalRoles gr 
LEFT JOIN FETCH gr.role 
WHERE u.email = :email

-- For phone login  
SELECT u FROM User u 
LEFT JOIN FETCH u.schoolRoles sr 
LEFT JOIN FETCH sr.role 
LEFT JOIN FETCH u.globalRoles gr 
LEFT JOIN FETCH gr.role 
WHERE u.phoneNumber = :phoneNumber
```

## Testing
1. **Login** - Should now load user with all roles
2. **Check logs** - Should see role loading and authority assignment
3. **Dashboard access** - Should work without 403 errors
4. **Debug endpoint** - `/debug/auth-info` should show correct authorities

## Impact
- ✅ Fixes 403 Forbidden errors on dashboard access
- ✅ Ensures proper role-based authorization
- ✅ Maintains performance with optimized queries
- ✅ Adds comprehensive logging for future troubleshooting

This fix addresses the core issue where user roles weren't being loaded from the database during authentication, causing Spring Security to deny access to role-protected endpoints.
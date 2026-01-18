# Debugging Setup for 403 Error Troubleshooting

## Overview
Added comprehensive logging throughout the authentication and authorization flow to troubleshoot the 403 Forbidden error issue.

## Logging Added

### 1. CustomUserDetails.kt
**Purpose**: Track what authorities/roles are being assigned to users
**Logs**:
- User ID and email during authority loading
- Global roles found and processed
- School roles found and processed  
- Final list of authorities assigned
- Error handling for lazy loading issues

**Key Log Messages**:
```
=== GETTING AUTHORITIES FOR USER: user@example.com (ID: uuid) ===
Added global authority: ROLE_SYSTEM_ADMIN
Added school authority: ROLE_ADMIN for schoolId: uuid
=== FINAL AUTHORITIES FOR user@example.com: [ROLE_USER, ROLE_ADMIN] ===
```

### 2. CustomAuthenticationSuccessHandler.kt
**Purpose**: Track the authentication success flow and redirection logic
**Logs**:
- User authentication details
- System admin detection
- Multiple schools/roles detection
- Session attribute setting
- Role-based URL determination
- Final redirect URL

**Key Log Messages**:
```
=== AUTHENTICATION SUCCESS HANDLER STARTED ===
User authenticated: user@example.com (ID: uuid)
User authorities: [ROLE_USER, ROLE_ADMIN]
Role-based redirect URL: /admin/dashboard for role: ADMIN
=== AUTHENTICATION SUCCESS HANDLER COMPLETED ===
```

### 3. AdminDashboardController.kt
**Purpose**: Verify if requests are reaching the dashboard controller
**Logs**:
- Request received confirmation
- User details and authorities
- Session school ID
- Dashboard statistics
- View return confirmation

**Key Log Messages**:
```
=== ADMIN DASHBOARD REQUEST RECEIVED ===
User: user@example.com (ID: uuid)
User authorities: [ROLE_USER, ROLE_ADMIN]
Selected school ID from session: uuid
=== ADMIN DASHBOARD REQUEST COMPLETED ===
```

### 4. DebugController.kt
**Purpose**: Provide debug endpoints for real-time troubleshooting
**Endpoints**:
- `/debug/auth-info` - Shows current authentication state
- `/debug/test-admin` - Tests admin access permissions

**Response Example**:
```json
{
  "authenticated": true,
  "username": "user@example.com",
  "authorities": ["ROLE_USER", "ROLE_ADMIN"],
  "session_attributes": {
    "selectedSchoolId": "uuid",
    "selectedRole": "ADMIN"
  }
}
```

### 5. Application Properties
**Added Spring Security Debug Logging**:
```properties
logging.level.com.haneef._school=DEBUG
logging.level.org.springframework.security=DEBUG
logging.level.org.springframework.security.web.access=DEBUG
logging.level.org.springframework.security.access=DEBUG
```

## Security Configuration Updates
**Updated AdminDashboardController @PreAuthorize**:
```kotlin
@PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'ADMIN', 'PRINCIPAL', 'TEACHER', 'STAFF')")
```
Changed from `SCHOOL_ADMIN` to `ADMIN` and `PRINCIPAL` to match database roles.

## How to Use for Troubleshooting

### 1. Check Application Logs
Look for the log patterns above in your application logs during login attempts.

### 2. Use Debug Endpoints
After login, visit:
- `http://localhost:8080/debug/auth-info` - See current auth state
- `http://localhost:8080/debug/test-admin` - Test admin permissions

### 3. Key Things to Verify
1. **User Authorities**: Are the correct ROLE_* authorities being assigned?
2. **Session Attributes**: Are selectedSchoolId and selectedRole being set?
3. **Redirect URL**: Is the correct dashboard URL being generated?
4. **Controller Access**: Are requests reaching the dashboard controller?
5. **Security Config**: Do the @PreAuthorize annotations match the user's authorities?

### 4. Common Issues to Look For
- **Missing Authorities**: User doesn't have required ROLE_* authorities
- **Session Issues**: selectedSchoolId or selectedRole not set in session
- **Role Mismatch**: @PreAuthorize expects different role names than what user has
- **Lazy Loading**: Database relationships not loading properly

## Next Steps
1. Start the application
2. Attempt login
3. Check logs for the patterns above
4. Use debug endpoints to verify current state
5. Compare expected vs actual authorities and session data

This comprehensive logging should help identify exactly where the 403 error is occurring in the authentication/authorization flow.
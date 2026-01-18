# Security Improvements - IDOR Vulnerability Fixes

## Overview
This document outlines the comprehensive security improvements made to address Insecure Direct Object Reference (IDOR) vulnerabilities in the school management system.

## Vulnerabilities Identified

### Critical Issues Fixed:
1. **AssessmentReportController** - Missing school validation for student and subject access
2. **ScoringSchemeController** - Missing school validation for class, department, and track access  
3. **ParentDashboardController** - Insufficient parent ownership validation
4. **Multiple API endpoints** - Inconsistent authorization patterns

## Security Enhancements Implemented

### 1. Centralized Authorization Service
**File:** `src/main/kotlin/com/haneef/_school/service/AuthorizationService.kt`

- Provides consistent validation methods for all entity types
- Validates school ownership before returning entities
- Throws `AccessDeniedException` for unauthorized access attempts
- Centralizes security logic to prevent inconsistencies

**Key Methods:**
```kotlin
fun validateAndGetStudent(studentId: UUID, schoolId: UUID): Student
fun validateAndGetParent(parentId: UUID, schoolId: UUID): Parent
fun validateAndGetSubject(subjectId: UUID, schoolId: UUID): Subject
// ... and more for all entity types
```

### 2. Controller Security Updates

#### AssessmentReportController
- Added authorization service injection
- Implemented school validation for all student and subject access
- Fixed `/students`, `/student-data`, `/save`, and `/import` endpoints

#### ScoringSchemeController  
- Added validation for track, department, and class access
- Prevents cross-school data manipulation in scoring schemes

#### ParentDashboardController
- Added parent ownership validation
- Ensures parents can only access their own data

### 3. Security Aspect (AOP)
**File:** `src/main/kotlin/com/haneef/_school/config/SecurityAspect.kt`

- Provides automatic security monitoring for controller methods
- Logs potential IDOR attempts
- Acts as a safety net for missed validations

### 4. Secure Repository Methods (Now Active)
**File:** `src/main/kotlin/com/haneef/_school/repository/SecureRepositoryMethods.kt`

- Provides repository methods that include school validation in queries
- **Now actively used** by AuthorizationService for better performance
- Reduces database round trips by combining entity fetch and validation
- Prevents potential timing attacks by failing at the database level

**Key Benefits:**
- **Single Query**: Combines entity lookup and school validation in one database query
- **Better Performance**: Eliminates the need for separate validation calls
- **Database-Level Security**: Validation happens at the query level
- **Consistent Error Messages**: Unified error handling for not found vs unauthorized

**Usage Pattern:**
```kotlin
// Old approach (2 database operations)
val entity = repository.findById(id).orElseThrow()
validateSchoolOwnership(entity, schoolId)

// New approach (1 database operation)  
val entity = repository.findByIdAndSchoolIdSecure(id, schoolId).orElseThrow()
```

### 5. Method-Level Security Service
**File:** `src/main/kotlin/com/haneef/_school/security/SchoolSecurityService.kt`

- Enables Spring Security expression-based authorization
- Can be used with `@PreAuthorize` annotations
- Provides fine-grained access control

**Usage Example:**
```kotlin
@PreAuthorize("@schoolSecurity.canAccessStudent(#studentId, authentication)")
@GetMapping("/students/{studentId}")
fun getStudent(@PathVariable studentId: UUID) { ... }
```

## Implementation Pattern

### Before (Vulnerable):
```kotlin
@GetMapping("/student/{studentId}")
fun getStudent(@PathVariable studentId: UUID, session: HttpSession): String {
    val student = studentRepository.findById(studentId).orElseThrow()
    // No school validation - IDOR vulnerability!
    return processStudent(student)
}
```

### After (Secure + Optimized):
```kotlin
@GetMapping("/student/{studentId}")
fun getStudent(@PathVariable studentId: UUID, session: HttpSession): String {
    val schoolId = authorizationService.validateSchoolAccess(
        session.getAttribute("selectedSchoolId") as? UUID
    )
    // Single database query with built-in school validation
    val student = authorizationService.validateAndGetStudent(studentId, schoolId)
    return processStudent(student)
}
```

**AuthorizationService Implementation:**
```kotlin
fun validateAndGetStudent(studentId: UUID, schoolId: UUID): Student {
    return studentRepository.findByIdAndSchoolIdSecure(studentId, schoolId)
        .orElseThrow { RuntimeException("Student not found or unauthorized access") }
}
```

## Testing

### Unit Tests
**File:** `src/test/kotlin/com/haneef/_school/security/AuthorizationServiceTest.kt`

- Comprehensive tests for authorization service
- Validates both positive and negative scenarios
- Ensures proper exception handling

### Test Coverage:
- ✅ Valid school access returns entity
- ✅ Invalid school access throws `AccessDeniedException`
- ✅ Missing entity throws `RuntimeException`
- ✅ Null school ID validation

## Security Benefits

1. **Prevents Cross-School Data Access**: Users cannot access data from other schools
2. **Consistent Authorization**: Centralized validation ensures uniform security
3. **Optimized Performance**: Single database queries with built-in validation
4. **Database-Level Security**: Validation occurs at the query level
5. **Audit Trail**: Security aspect logs potential violations
6. **Defense in Depth**: Multiple layers of protection
7. **Maintainable**: Centralized security logic is easier to maintain and update

## Migration Guide

### For Existing Controllers:
1. Inject `AuthorizationService` in constructor
2. Replace direct repository calls with authorization service calls
3. Use `validateSchoolAccess()` for session validation
4. Add appropriate error handling

### For New Controllers:
1. Always use `AuthorizationService` for entity access
2. Consider using `@PreAuthorize` annotations with `SchoolSecurityService`
3. Follow the established security patterns

## Monitoring and Alerts

The security aspect logs potential IDOR attempts with the format:
```
SECURITY WARNING: Potential IDOR attempt in [ClassName].[methodName] with UUIDs: [uuidList]
```

Consider setting up alerts for these log messages in production.

## Future Enhancements

1. **Database-Level Security**: Implement Row-Level Security (RLS) in PostgreSQL
2. **API Rate Limiting**: Add rate limiting to prevent brute force attacks
3. **Audit Logging**: Comprehensive audit trail for all data access
4. **Automated Security Testing**: Integration tests for security scenarios

## Compliance

These improvements help ensure compliance with:
- OWASP Top 10 (A01:2021 – Broken Access Control)
- GDPR data protection requirements
- Educational data privacy standards
- General security best practices

## Conclusion

The implemented security improvements provide comprehensive protection against IDOR vulnerabilities while maintaining code maintainability and performance. The centralized authorization approach ensures consistent security across the entire application.
# Staff Class Assignment Fix

## Issue
The backend was failing to assign classes to staff members (and subject teachers/parents) when a previous assignment existed but was inactive (soft deleted). This was causing a unique constraint violation in the database because the system attempted to create a duplicate record instead of reactivating the existing one.

## Fix
Updated the assignment logic in `CommunityController.kt` and added helper methods in the respective repositories to:
1.  Check for *any* existing assignment (active or inactive) using all unique fields.
2.  If an inactive assignment exists, reactivate it (`isActive = true`) instead of creating a new one.
3.  If an active assignment exists, return an error message (as before).

## Changes
### Repositories
-   **`ClassTeacherRepository.kt`**: Added `findByStaffIdAndSchoolClassIdAndAcademicSessionIdAndTermIdAndSchoolId`
-   **`SubjectTeacherRepository.kt`**: Added `findByStaffIdAndSubjectIdAndSchoolClassIdAndAcademicSessionIdAndTermIdAndSchoolId` and fixed a typo in `findBySubjectIdAndIsActive`.
-   **`ParentStudentRepository.kt`**: Added `findByParentIdAndStudentIdAndSchoolId`

### Controller
-   **`CommunityController.kt`**:
    -   Updated `assignClassTeacherHtmx` to handle reactivation.
    -   Updated `assignSubjectTeacherHtmx` to handle reactivation.
    -   Updated `assignParentToStudent` and `assignParentToStudentModal` to handle reactivation (proactive fix).

## Verification
-   Ran `./gradlew compileKotlin` successfully.

# Staff Management In-Place Updates - Backend Implementation Guide

## Overview
The staff management system has been updated to support in-place updates of individual staff cards without reloading the entire page. This document outlines the backend controller methods that need to be implemented.

## Required Backend Endpoints

### 1. Save Staff (Single Card Update)
**Endpoint:** `POST /admin/community/staff/save-htmx-single`

**Purpose:** Save or update a staff member and return only the updated staff card HTML.

**Request Parameters:**
- All existing staff form parameters (id, firstName, lastName, email, designation, etc.)

**Response:**
- **Content-Type:** `text/html`
- **Body:** HTML fragment of the single staff card using the `staff-card-single` template
- For new staff: Return the new card HTML (it will be appended via JavaScript or reload)
- For existing staff: Return the updated card HTML with id `staff-card-{staffId}`

**Example Controller Method:**
```kotlin
@PostMapping("/save-htmx-single")
fun saveStaffSingle(@ModelAttribute staffDto: StaffDto, ...): String {
    val savedStaff = staffService.saveStaff(staffDto)
    model.addAttribute("staff", savedStaff)
    
    // Return only the single card fragment
    return "admin/community/staff/staff-card-single :: single-staff-card(${savedStaff})"
}
```

### 2. Assign Class Teacher (Single Card Update)
**Endpoint:** `POST /admin/community/staff/{staffId}/assign-class-htmx-single`

**Purpose:** Assign a staff member as a class teacher and return the updated staff card.

**Path Variables:**
- `staffId`: ID of the staff member

**Request Parameters:**
- `assignedClassId`: ID of the class to assign

**Response:**
- **Content-Type:** `text/html`
- **Body:** Updated staff card HTML fragment with id `staff-card-{staffId}`

**Example Controller Method:**
```kotlin
@PostMapping("/{staffId}/assign-class-htmx-single")
fun assignClassTeacherSingle(
    @PathVariable staffId: Long,
    @RequestParam assignedClassId: Long,
    ...
): String {
    staffService.assignClassTeacher(staffId, assignedClassId, ...)
    val staff = staffService.getStaffById(staffId)
    model.addAttribute("staff", staff)
    
    return "admin/community/staff/staff-card-single :: single-staff-card(${staff})"
}
```

### 3. Assign Subject Teacher (Single Card Update)
**Endpoint:** `POST /admin/community/staff/{staffId}/assign-subject-htmx-single`

**Purpose:** Assign a staff member as a subject teacher and return the updated staff card.

**Path Variables:**
- `staffId`: ID of the staff member

**Request Parameters:**
- `assignedClassId`: ID of the class
- `subjectId`: ID of the subject to assign

**Response:**
- **Content-Type:** `text/html`
- **Body:** Updated staff card HTML fragment with id `staff-card-{staffId}`

**Example Controller Method:**
```kotlin
@PostMapping("/{staffId}/assign-subject-htmx-single")
fun assignSubjectTeacherSingle(
    @PathVariable staffId: Long,
    @RequestParam assignedClassId: Long,
    @RequestParam subjectId: Long,
    ...
): String {
    staffService.assignSubjectTeacher(staffId, assignedClassId, subjectId, ...)
    val staff = staffService.getStaffById(staffId)
    model.addAttribute("staff", staff)
    
    return "admin/community/staff/staff-card-single :: single-staff-card(${staff})"
}
```

### 4. Remove Class Assignment (Single Card Update)
**Endpoint:** `POST /admin/community/staff/remove-class-assignment-single/{assignmentId}`

**Purpose:** Remove a class teacher assignment and return the updated staff card.

**Path Variables:**
- `assignmentId`: ID of the class teacher assignment to remove

**Response:**
- **Content-Type:** `text/html`
- **Body:** Updated staff card HTML fragment with id `staff-card-{staffId}`

**Example Controller Method:**
```kotlin
@PostMapping("/remove-class-assignment-single/{assignmentId}")
fun removeClassAssignmentSingle(@PathVariable assignmentId: Long, ...): String {
    val assignment = classTeacherAssignmentService.getById(assignmentId)
    val staffId = assignment.staff.id
    
    classTeacherAssignmentService.removeAssignment(assignmentId)
    
    val staff = staffService.getStaffById(staffId)
    model.addAttribute("staff", staff)
    
    return "admin/community/staff/staff-card-single :: single-staff-card(${staff})"
}
```

### 5. Remove Subject Assignment (Single Card Update)
**Endpoint:** `POST /admin/community/staff/remove-subject-assignment-single/{assignmentId}`

**Purpose:** Remove a subject teacher assignment and return the updated staff card.

**Path Variables:**
- `assignmentId`: ID of the subject teacher assignment to remove

**Response:**
- **Content-Type:** `text/html`
- **Body:** Updated staff card HTML fragment with id `staff-card-{staffId}`

**Example Controller Method:**
```kotlin
@PostMapping("/remove-subject-assignment-single/{assignmentId}")
fun removeSubjectAssignmentSingle(@PathVariable assignmentId: Long, ...): String {
    val assignment = subjectTeacherAssignmentService.getById(assignmentId)
    val staffId = assignment.staff.id
    
    subjectTeacherAssignmentService.removeAssignment(assignmentId)
    
    val staff = staffService.getStaffById(staffId)
    model.addAttribute("staff", staff)
    
    return "admin/community/staff/staff-card-single :: single-staff-card(${staff})"
}
```

## Frontend Changes Summary

### Files Modified:
1. **staff-card-single.html** (NEW) - Reusable single staff card fragment
2. **staff-cards.html** - Updated to use the single card fragment
3. **modal-form.html** - Updated form target to specific staff card
4. **assignments-modal.html** - Updated both assignment forms to target specific cards
5. **community.js** - Added in-place assignment removal functions

### Key Features:
- Each staff card has a unique ID: `staff-card-{staffId}`
- HTMX targets specific cards using `hx-target="#staff-card-{staffId}"`
- Uses `hx-swap="outerHTML"` to replace the entire card
- Modal closes automatically after successful updates
- No page reload required for edits or assignment changes

## Testing Checklist

- [ ] Edit existing staff member - card updates in place
- [ ] Add new staff member - card appears or page reloads appropriately
- [ ] Assign class teacher - card updates showing new assignment
- [ ] Assign subject teacher - card updates showing new assignment
- [ ] Remove class assignment - card updates removing the assignment
- [ ] Remove subject assignment - card updates removing the assignment
- [ ] Verify passport photos display correctly after updates
- [ ] Test with multiple staff cards on the page
- [ ] Verify pagination still works correctly
- [ ] Test filter/search functionality

## Notes

- The existing endpoints (`/save-htmx`, `/assign-class-htmx`, etc.) should remain for backward compatibility
- Error handling should return appropriate error messages
- CSRF tokens are automatically included in HTMX requests
- The modal automatically closes on successful operations

# Parent Management In-Place Updates - Backend Implementation Guide

## Overview
The parent management system has been updated to support in-place updates of individual parent cards without reloading the entire page.

## Required Backend Endpoints

### 1. Save Parent (Single Card Update)
**Endpoint:** `POST /admin/community/parents/save-htmx-single`

**Purpose:** Save or update a parent/guardian and return only the updated parent card HTML.

**Request Parameters:**
- All existing parent form parameters (id, firstName, lastName, phoneNumber, email, isPrimaryContact, isEmergencyContact, etc.)

**Response:**
- **Content-Type:** `text/html`
- **Body:** HTML fragment of the single parent card
- For new parent: Return the new card HTML (it will be prepended to the grid)
- For existing parent: Return the updated card HTML with id `parent-card-{parentId}`

**Example Controller Method:**
```kotlin
@PostMapping("/save-htmx-single")
fun saveParentSingle(@ModelAttribute parentDto: ParentDto, ...): String {
    val savedParent = parentService.saveParent(parentDto)
    model.addAttribute("parent", savedParent)
    model.addAttribute("isOob", false)
    
    // Return only the single card fragment wrapped in the parent div
    return "admin/community/parents/parent-cards :: single-parent-card"
}
```

### 2. Assign Student to Parent (Single Card Update)
**Endpoint:** `POST /admin/community/parents/{parentId}/assign-student-single`

**Purpose:** Assign a student to a parent and return the updated parent card.

**Path Variables:**
- `parentId`: ID of the parent

**Request Parameters:**
- `studentId`: ID of the student to assign
- `relationshipType`: Type of relationship (biological, adoptive, guardian, etc.)

**Response:**
- **Content-Type:** `text/html`
- **Body:** Updated parent card HTML with id `parent-card-{parentId}`

**Example Controller Method:**
```kotlin
@PostMapping("/{parentId}/assign-student-single")
fun assignStudentSingle(
    @PathVariable parentId: Long,
    @RequestParam studentId: Long,
    @RequestParam relationshipType: String,
    ...
): String {
    parentService.assignStudent(parentId, studentId, relationshipType, ...)
    val parent = parentService.getParentById(parentId)
    model.addAttribute("parent", parent)
    
    return "admin/community/parents/parent-cards :: single-parent-card"
}
```

### 3. Remove Student from Parent (Single Card Update)
**Endpoint:** `POST /admin/community/parents/{parentId}/remove-student-single/{assignmentId}`

**Purpose:** Remove a student assignment from a parent and return the updated parent card.

**Path Variables:**
- `parentId`: ID of the parent
- `assignmentId`: ID of the parent-student relationship to remove

**Response:**
- **Content-Type:** `text/html`
- **Body:** Updated parent card HTML with id `parent-card-{parentId}`

**Example Controller Method:**
```kotlin
@PostMapping("/{parentId}/remove-student-single/{assignmentId}")
fun removeStudentSingle(
    @PathVariable parentId: Long,
    @PathVariable assignmentId: Long,
    ...
): String {
    parentStudentRelationshipService.removeAssignment(assignmentId)
    val parent = parentService.getParentById(parentId)
    model.addAttribute("parent", parent)
    
    return "admin/community/parents/parent-cards :: single-parent-card"
}
```

**Important Notes:**
1. The fragment should be wrapped in a div with `id="parent-card-{parentId}"` and `class="parent-card"`
2. For new parents, the card will be prepended to the beginning of the grid
3. For existing parents, the card will replace the existing card with the same ID
4. The modal will close automatically after successful save

## Frontend Changes Summary

### Files Modified:
1. **parent-cards.html** - Updated to set unique IDs on parent cards
2. **modal-form.html** - Updated form target to specific parent card

### Key Features:
- Each parent card has a unique ID: `parent-card-{parentId}`
- HTMX targets specific cards using `hx-target="#parent-card-{parentId}"`
- Uses `hx-swap="outerHTML"` to replace the entire card
- Modal closes automatically after successful updates
- No page reload required for edits

## Testing Checklist

- [ ] Edit existing parent - card updates in place
- [ ] Add new parent - card appears at top of list
- [ ] Verify all parent information displays correctly
- [ ] Test with multiple parent cards on the page
- [ ] Verify pagination still works correctly
- [ ] Test filter/search functionality
- [ ] Verify modal closes after successful save
- [ ] Test error handling (invalid data, server errors)

## Example Response HTML

For editing an existing parent:
```html
<div id="parent-card-123" class="parent-card">
    <div class="parent-card-header">
        <!-- Parent info here -->
    </div>
    <div class="parent-card-body">
        <!-- Children assignments here -->
    </div>
    <div class="parent-card-footer">
        <!-- Action buttons here -->
    </div>
</div>
```

The backend should render the `single-parent-card` fragment and wrap it in a div with the appropriate ID and class.

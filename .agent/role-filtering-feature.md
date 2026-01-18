# Role-Based Filtering for Platform User Management

## Overview
Enhanced the Platform User Management page (`/admin/community/approvals`) with role-based filtering functionality, allowing administrators to filter users by their assigned roles.

## Changes Made

### 1. Controller Updates (`SchoolAdminUserManagementController.kt`)

#### Added Role Filter Parameter:
```kotlin
@GetMapping("/approvals")
fun userManagement(
    @RequestParam(required = false) roleFilter: String?,
    model: Model, 
    authentication: Authentication, 
    session: HttpSession
): String
```

#### Implemented Filtering Logic:
- Filters users by role when `roleFilter` parameter is provided
- Returns all users when no filter is selected
- Extracts distinct roles from all users for the dropdown

```kotlin
// Filter by role if specified
val filteredRoles = if (!roleFilter.isNullOrBlank()) {
    allRoles.filter { it.role.name == roleFilter }
} else {
    allRoles
}
```

#### Added Model Attributes:
- `availableRoles`: List of distinct roles available in the school
- `selectedRole`: Currently selected role filter (or "ALL")

### 2. Template Updates (`approvals.html`)

#### Added Role Filter UI:
- **Dropdown Selector**: Allows selection of specific roles
- **Auto-Submit**: Form submits automatically when role is changed
- **Clear Filter Link**: Quick way to return to viewing all users
- **Active Filter Indicator**: Shows which role is currently filtered

#### UI Features:
```html
<select id="roleFilter" name="roleFilter" onchange="this.form.submit()">
    <option value="">All Roles</option>
    <option th:each="role : ${availableRoles}" 
            th:value="${role}" 
            th:text="${role}"
            th:selected="${selectedRole == role}">Role</option>
</select>
```

## User Experience

### Filter Workflow:
1. **View All Users**: Default view shows all users across all roles
2. **Select Role**: Choose a specific role from the dropdown (STAFF, PARENT, STUDENT, etc.)
3. **Filtered View**: Page automatically refreshes showing only users with that role
4. **Clear Filter**: Click "Clear filter" link to return to all users

### Visual Feedback:
- Selected role is highlighted in the dropdown
- "Showing: [ROLE]" indicator appears when a filter is active
- "Clear filter" link provides quick reset option

## Technical Details

### URL Parameters:
- **No Filter**: `/admin/community/approvals`
- **With Filter**: `/admin/community/approvals?roleFilter=STAFF`

### Available Roles:
The filter dynamically populates based on roles present in the school:
- STAFF / TEACHER
- PARENT
- STUDENT
- SCHOOL_ADMIN (if applicable)

### Filter Persistence:
- Filter state is maintained through URL parameters
- Allows bookmarking filtered views
- Works seamlessly with browser back/forward navigation

## Benefits

### For Administrators:
- **Quick Access**: Instantly view all users of a specific role
- **Better Organization**: Easier to manage large user bases
- **Focused Actions**: Perform bulk operations on specific user types
- **Improved Efficiency**: Reduce time spent searching for specific user types

### For User Management:
- **Role-Specific Workflows**: Handle approvals by role type
- **Clearer Overview**: See distribution of users across roles
- **Targeted Communication**: Easily identify users for role-specific notifications

## Examples

### Use Cases:
1. **Approve All Pending Teachers**: Filter by "TEACHER" → Approve pending registrations
2. **Review Parent Accounts**: Filter by "PARENT" → Check linked children status
3. **Manage Student Records**: Filter by "STUDENT" → Verify parent linkages
4. **Staff Overview**: Filter by "STAFF" → Review designations and departments

### Filter Combinations:
- Filter by role + Tab selection (Pending/Active/Inactive)
- Example: "PARENT" role + "Pending Approvals" tab = All pending parent registrations

## Future Enhancements

1. **Multi-Select Filters**: Allow filtering by multiple roles simultaneously
2. **Search Integration**: Combine role filter with text search
3. **Export Filtered Data**: Download CSV of filtered user list
4. **Saved Filters**: Save commonly used filter combinations
5. **Filter Statistics**: Show count of users per role in dropdown
6. **Advanced Filters**: Add filters for verification status, registration date, etc.

## Testing Recommendations

1. **Test All Roles**: Verify filter works for each available role
2. **Test Empty Results**: Ensure proper message when no users match filter
3. **Test Clear Filter**: Verify "Clear filter" link returns to all users
4. **Test URL Direct Access**: Ensure bookmarked filtered URLs work correctly
5. **Test Tab Interaction**: Verify filter persists when switching tabs
6. **Test Approval Actions**: Ensure actions work correctly on filtered views

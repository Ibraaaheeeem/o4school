# Activity Logging Implementation Guide

## Overview

I've implemented a comprehensive activity logging system for your school management application that tracks user actions and displays them on role-specific dashboards. Here's what has been created:

## Components Created

### 1. Core Entities
- **ActivityLog.kt** - Main entity for storing activity records
- **ActivityType.kt** - Enum defining different types of activities

### 2. Repository Layer
- **ActivityLogRepository.kt** - Data access methods for activity logs

### 3. Service Layer
- **ActivityLogService.kt** - Business logic for logging and retrieving activities

### 4. Controller Layer
- **ActivityLogController.kt** - REST endpoints for activity data
- **DashboardController.kt** - Role-based dashboard routing

### 5. UI Components
- **activity-widget.html** - Reusable activity display widget
- **admin/activities/list.html** - Full activity log management page
- **Role-specific dashboards** - Staff, Parent, and Student dashboards

### 6. Styling
- **activity-logs.css** - Comprehensive styling for activity components

## Features Implemented

### Activity Tracking
- **User Management**: Login, logout, profile updates
- **Student Management**: Enrollment, updates, transfers
- **Staff Management**: Hiring, updates, role changes
- **Parent Management**: Adding, updating parent information
- **Academic Activities**: Grade entry, assignment creation, exam scheduling
- **Financial Activities**: Payment processing, invoice generation
- **System Activities**: Backups, maintenance, security alerts

### Dashboard Integration
- **Admin Dashboard**: Shows all recent activities across the school
- **Staff Dashboard**: Shows activities related to their classes and students
- **Parent Dashboard**: Shows activities related to their children
- **Student Dashboard**: Shows their academic activities and updates

### Real-time Updates
- Auto-refresh every 30 seconds
- Manual refresh buttons
- AJAX-based loading without page refresh

## Usage Examples

### Manual Activity Logging in Controllers

```kotlin
// In your controller
@Autowired
private lateinit var activityLogService: ActivityLogService

// Log a student enrollment
activityLogService.logManualActivity(
    schoolId = selectedSchoolId,
    activityType = ActivityType.STUDENT_ENROLLED,
    title = "New student enrolled",
    description = "Student ${student.user.firstName} ${student.user.lastName} enrolled in ${className}",
    authentication = authentication,
    targetUserId = student.user.id,
    entityType = "Student",
    entityId = student.id,
    metadata = mapOf("className" to className),
    request = request
)
```

### Automatic Logging with AOP
The system includes an AspectJ aspect that automatically logs certain operations:
- Staff creation/updates
- Student enrollment/updates  
- Parent addition/updates

### Adding Activity Widgets to Templates

```html
<!-- For recent activities (admin view) -->
<div th:replace="~{fragments/activity-widget :: recent-activity-widget}"></div>

<!-- For user-specific activities -->
<div th:replace="~{fragments/activity-widget :: user-activity-widget}" 
     th:attr="data-user-id=${user?.id}"></div>

<!-- Include the JavaScript -->
<div th:replace="~{fragments/activity-widget :: activity-widget-script}"></div>
```

## API Endpoints

### Get Recent Activities
```
GET /admin/activities/api/recent?limit=10
```

### Get User Activities
```
GET /admin/activities/api/user/{userId}?page=0&size=10
```

### Full Activity Management
```
GET /admin/activities
```

## Database Schema

The ActivityLog table includes:
- Basic audit fields (id, created_at, updated_at)
- Activity metadata (type, title, description)
- User information (actor and target users)
- Context data (IP address, user agent, metadata JSON)
- School isolation (school_id for multi-tenancy)

## Customization

### Adding New Activity Types
1. Add to the `ActivityType` enum
2. Create specific logging methods in `ActivityLogService`
3. Update the icon mapping in the frontend

### Custom Activity Widgets
You can create custom activity widgets by:
1. Extending the fragment template
2. Adding specific API endpoints
3. Implementing custom filtering logic

### Role-based Activity Filtering
Activities are automatically filtered based on:
- User's role in the school
- Relationship to other users (parent-child, teacher-student)
- Permission levels

## Security Considerations

- All activity logs are school-isolated using `schoolId`
- User permissions are checked before displaying activities
- Sensitive information is not logged in plain text
- IP addresses and user agents are captured for security auditing

## Performance Optimization

- Database indexes on frequently queried columns
- Pagination for large activity lists
- Caching of user names to avoid repeated lookups
- Asynchronous logging to avoid blocking main operations

## Next Steps

1. **Enable the system** by ensuring the ActivityLogService is injected where needed
2. **Add manual logging** to critical operations in your controllers
3. **Customize activity types** based on your specific business needs
4. **Configure notifications** for important activities
5. **Set up activity retention policies** for database maintenance

The system is designed to be extensible and can be easily adapted to track any user actions or system events relevant to your school management needw
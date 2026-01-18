package com.haneef._school.entity

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(
    name = "activity_logs",
    indexes = [
        Index(columnList = "school_id,created_at", name = "idx_activity_school_created"),
        Index(columnList = "user_id,created_at", name = "idx_activity_user_created"),
        Index(columnList = "activity_type,created_at", name = "idx_activity_type_created"),
        Index(columnList = "target_user_id,created_at", name = "idx_activity_target_created")
    ]
)
class ActivityLog(
    @Column(name = "activity_type", nullable = false)
    @Enumerated(EnumType.STRING)
    var activityType: ActivityType,
    
    @Column(name = "title", nullable = false)
    var title: String,
    
    @Column(name = "description", columnDefinition = "TEXT")
    var description: String? = null,
    
    @Column(name = "user_id", nullable = false)
    var userId: UUID, // Who performed the action
    
    @Column(name = "user_name", nullable = false)
    var userName: String, // Cache user name for performance
    
    @Column(name = "user_role", nullable = false)
    var userRole: String, // Cache user role for filtering
    
    @Column(name = "target_user_id")
    var targetUserId: UUID? = null, // Who was affected (for student/staff/parent actions)
    
    @Column(name = "target_user_name")
    var targetUserName: String? = null,
    
    @Column(name = "entity_type")
    var entityType: String? = null, // Student, Staff, Parent, Class, etc.
    
    @Column(name = "entity_id")
    var entityId: UUID? = null,
    
    @Column(name = "metadata", columnDefinition = "TEXT")
    var metadata: String? = null, // JSON string for additional data
    
    @Column(name = "ip_address")
    var ipAddress: String? = null,
    
    @Column(name = "user_agent", columnDefinition = "TEXT")
    var userAgent: String? = null
) : TenantAwareEntity()

enum class ActivityType {
    // User Management
    USER_CREATED,
    USER_UPDATED,
    USER_DELETED,
    USER_LOGIN,
    USER_LOGOUT,
    PASSWORD_CHANGED,
    
    // Student Management
    STUDENT_ENROLLED,
    STUDENT_UPDATED,
    STUDENT_TRANSFERRED,
    STUDENT_GRADUATED,
    STUDENT_SUSPENDED,
    
    // Staff Management
    STAFF_HIRED,
    STAFF_UPDATED,
    STAFF_TERMINATED,
    STAFF_PROMOTED,
    
    // Parent Management
    PARENT_ADDED,
    PARENT_UPDATED,
    PARENT_REMOVED,
    
    // Academic
    CLASS_CREATED,
    CLASS_UPDATED,
    SUBJECT_CREATED,
    SUBJECT_UPDATED,
    ASSIGNMENT_CREATED,
    ASSIGNMENT_SUBMITTED,
    GRADE_ENTERED,
    EXAM_CREATED,
    EXAM_SCHEDULED,
    
    // Financial
    FEE_CREATED,
    PAYMENT_RECEIVED,
    PAYMENT_FAILED,
    INVOICE_GENERATED,
    DISCOUNT_APPLIED,
    
    // Communication
    NOTIFICATION_SENT,
    MESSAGE_SENT,
    ANNOUNCEMENT_POSTED,
    
    // System
    SYSTEM_BACKUP,
    SYSTEM_MAINTENANCE,
    DATA_EXPORT,
    DATA_IMPORT,
    
    // Security
    SECURITY_ALERT,
    PERMISSION_CHANGED,
    ROLE_ASSIGNED,
    ROLE_REMOVED
}
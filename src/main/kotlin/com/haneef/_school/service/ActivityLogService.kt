package com.haneef._school.service

import com.haneef._school.entity.ActivityLog
import com.haneef._school.entity.ActivityType
import com.haneef._school.entity.User
import com.haneef._school.repository.ActivityLogRepository
import com.haneef._school.repository.UserRepository
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Service
@Transactional
class ActivityLogService(
    private val activityLogRepository: ActivityLogRepository,
    private val userRepository: UserRepository,
    private val objectMapper: ObjectMapper
) {
    
    /**
     * Log an activity with minimal information
     */
    fun logActivity(
        schoolId: UUID,
        activityType: ActivityType,
        title: String,
        userId: UUID,
        userRole: String,
        description: String? = null,
        targetUserId: UUID? = null,
        entityType: String? = null,
        entityId: UUID? = null,
        metadata: Map<String, Any>? = null,
        request: HttpServletRequest? = null
    ) {
        try {
            val user = userRepository.findById(userId).orElse(null)
            val targetUser = targetUserId?.let { userRepository.findById(it).orElse(null) }
            
            val activity = ActivityLog(
                activityType = activityType,
                title = title,
                description = description,
                userId = userId,
                userName = user?.let { "${it.firstName} ${it.lastName}" } ?: "Unknown User",
                userRole = userRole,
                targetUserId = targetUserId,
                targetUserName = targetUser?.let { "${it.firstName} ${it.lastName}" },
                entityType = entityType,
                entityId = entityId,
                metadata = metadata?.let { objectMapper.writeValueAsString(it) },
                ipAddress = request?.let { getClientIpAddress(it) },
                userAgent = request?.getHeader("User-Agent")
            ).apply {
                this.schoolId = schoolId
            }
            
            activityLogRepository.save(activity)
        } catch (e: Exception) {
            // Log the error but don't fail the main operation
            println("Failed to log activity: ${e.message}")
        }
    }
    
    /**
     * Log user authentication activities
     */
    fun logUserLogin(schoolId: UUID, user: User, request: HttpServletRequest?) {
        logActivity(
            schoolId = schoolId,
            activityType = ActivityType.USER_LOGIN,
            title = "User logged in",
            userId = user.id!!,
            userRole = "USER", // Will be updated with actual role
            description = "User successfully logged into the system",
            request = request
        )
    }
    
    fun logUserLogout(schoolId: UUID, user: User, request: HttpServletRequest?) {
        logActivity(
            schoolId = schoolId,
            activityType = ActivityType.USER_LOGOUT,
            title = "User logged out",
            userId = user.id!!,
            userRole = "USER",
            description = "User logged out of the system",
            request = request
        )
    }
    
    /**
     * Log student-related activities
     */
    fun logStudentEnrolled(schoolId: UUID, actorUserId: UUID, actorRole: String, studentUserId: UUID, className: String, request: HttpServletRequest? = null) {
        logActivity(
            schoolId = schoolId,
            activityType = ActivityType.STUDENT_ENROLLED,
            title = "New student enrolled",
            userId = actorUserId,
            userRole = actorRole,
            description = "Student enrolled in class $className",
            targetUserId = studentUserId,
            entityType = "Student",
            metadata = mapOf("className" to className),
            request = request
        )
    }
    
    fun logStudentUpdated(schoolId: UUID, actorUserId: UUID, actorRole: String, studentUserId: UUID, changes: Map<String, Any>, request: HttpServletRequest? = null) {
        logActivity(
            schoolId = schoolId,
            activityType = ActivityType.STUDENT_UPDATED,
            title = "Student information updated",
            userId = actorUserId,
            userRole = actorRole,
            description = "Student profile information was modified",
            targetUserId = studentUserId,
            entityType = "Student",
            metadata = changes,
            request = request
        )
    }
    
    /**
     * Log staff-related activities
     */
    fun logStaffHired(schoolId: UUID, actorUserId: UUID, actorRole: String, staffUserId: UUID, designation: String, request: HttpServletRequest? = null) {
        logActivity(
            schoolId = schoolId,
            activityType = ActivityType.STAFF_HIRED,
            title = "New staff member hired",
            userId = actorUserId,
            userRole = actorRole,
            description = "New $designation hired",
            targetUserId = staffUserId,
            entityType = "Staff",
            metadata = mapOf("designation" to designation),
            request = request
        )
    }
    
    fun logStaffUpdated(schoolId: UUID, actorUserId: UUID, actorRole: String, staffUserId: UUID, changes: Map<String, Any>, request: HttpServletRequest? = null) {
        logActivity(
            schoolId = schoolId,
            activityType = ActivityType.STAFF_UPDATED,
            title = "Staff information updated",
            userId = actorUserId,
            userRole = actorRole,
            description = "Staff profile information was modified",
            targetUserId = staffUserId,
            entityType = "Staff",
            metadata = changes,
            request = request
        )
    }
    
    /**
     * Log parent-related activities
     */
    fun logParentAdded(schoolId: UUID, actorUserId: UUID, actorRole: String, parentUserId: UUID, studentName: String, request: HttpServletRequest? = null) {
        logActivity(
            schoolId = schoolId,
            activityType = ActivityType.PARENT_ADDED,
            title = "New parent added",
            userId = actorUserId,
            userRole = actorRole,
            description = "Parent linked to student $studentName",
            targetUserId = parentUserId,
            entityType = "Parent",
            metadata = mapOf("studentName" to studentName),
            request = request
        )
    }
    
    /**
     * Log financial activities
     */
    fun logPaymentReceived(schoolId: UUID, actorUserId: UUID, actorRole: String, amount: String, studentName: String, paymentType: String, request: HttpServletRequest? = null) {
        logActivity(
            schoolId = schoolId,
            activityType = ActivityType.PAYMENT_RECEIVED,
            title = "Payment received",
            userId = actorUserId,
            userRole = actorRole,
            description = "$paymentType payment of $amount received for $studentName",
            entityType = "Payment",
            metadata = mapOf(
                "amount" to amount,
                "studentName" to studentName,
                "paymentType" to paymentType
            ),
            request = request
        )
    }
    
    /**
     * Log academic activities
     */
    fun logGradeEntered(schoolId: UUID, actorUserId: UUID, actorRole: String, studentUserId: UUID, subject: String, grade: String, request: HttpServletRequest? = null) {
        logActivity(
            schoolId = schoolId,
            activityType = ActivityType.GRADE_ENTERED,
            title = "Grade entered",
            userId = actorUserId,
            userRole = actorRole,
            description = "Grade $grade entered for $subject",
            targetUserId = studentUserId,
            entityType = "Grade",
            metadata = mapOf(
                "subject" to subject,
                "grade" to grade
            ),
            request = request
        )
    }
    
    /**
     * Get recent activities for dashboard
     */
    fun getRecentActivities(schoolId: UUID, limit: Int = 10): List<ActivityLog> {
        val pageable = PageRequest.of(0, limit)
        val since = LocalDateTime.now().minusDays(7) // Last 7 days
        return activityLogRepository.findRecentActivities(schoolId, since, pageable).content
    }
    
    /**
     * Get activities for a specific user (for their personal dashboard)
     */
    fun getUserActivities(schoolId: UUID, userId: UUID, pageable: Pageable): Page<ActivityLog> {
        return activityLogRepository.findActivitiesRelatedToUser(schoolId, userId, pageable)
    }
    
    /**
     * Get all activities for admin dashboard
     */
    fun getAllActivities(schoolId: UUID, pageable: Pageable): Page<ActivityLog> {
        return activityLogRepository.findBySchoolIdOrderByCreatedAtDesc(schoolId, pageable)
    }
    
    /**
     * Get activities by type
     */
    fun getActivitiesByType(schoolId: UUID, activityType: ActivityType, pageable: Pageable): Page<ActivityLog> {
        return activityLogRepository.findBySchoolIdAndActivityTypeOrderByCreatedAtDesc(schoolId, activityType, pageable)
    }
    
    /**
     * Get activities by user role
     */
    fun getActivitiesByRole(schoolId: UUID, userRole: String, pageable: Pageable): Page<ActivityLog> {
        return activityLogRepository.findBySchoolIdAndUserRoleOrderByCreatedAtDesc(schoolId, userRole, pageable)
    }
    
    /**
     * Get activity statistics for analytics
     */
    fun getActivityStats(schoolId: UUID, days: Long = 30): Map<ActivityType, Long> {
        val since = LocalDateTime.now().minusDays(days)
        val results = activityLogRepository.countActivitiesByType(schoolId, since)
        return results.associate { 
            it[0] as ActivityType to (it[1] as Long)
        }
    }
    
    /**
     * Manual logging method for controllers to call directly
     */
    fun logManualActivity(
        schoolId: UUID,
        activityType: ActivityType,
        title: String,
        description: String? = null,
        authentication: Authentication,
        targetUserId: UUID? = null,
        entityType: String? = null,
        entityId: UUID? = null,
        metadata: Map<String, Any>? = null,
        request: HttpServletRequest? = null
    ) {
        val customUser = authentication.principal as? CustomUserDetails ?: return
        val userRole = customUser.authorities.firstOrNull()?.authority ?: "USER"
        
        logActivity(
            schoolId = schoolId,
            activityType = activityType,
            title = title,
            userId = customUser.user.id!!,
            userRole = userRole,
            description = description,
            targetUserId = targetUserId,
            entityType = entityType,
            entityId = entityId,
            metadata = metadata,
            request = request
        )
    }
    private fun getClientIpAddress(request: HttpServletRequest): String {
        val xForwardedFor = request.getHeader("X-Forwarded-For")
        if (!xForwardedFor.isNullOrBlank()) {
            return xForwardedFor.split(",")[0].trim()
        }
        
        val xRealIp = request.getHeader("X-Real-IP")
        if (!xRealIp.isNullOrBlank()) {
            return xRealIp
        }
        
        return request.remoteAddr ?: "unknown"
    }
}
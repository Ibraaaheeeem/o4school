package com.haneef._school.service

import com.haneef._school.entity.ActivityLog
import com.haneef._school.entity.ActivityType
import com.haneef._school.repository.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Service
@Transactional
class ActivityBackfillService(
    private val activityLogRepository: ActivityLogRepository,
    private val staffRepository: StaffRepository,
    private val studentRepository: StudentRepository,
    private val parentRepository: ParentRepository,
    private val userRepository: UserRepository
) {
    
    /**
     * Backfill historical activities based on existing data
     * Call this once after deploying the activity logging system
     */
    fun backfillHistoricalActivities(schoolId: UUID, systemUserId: UUID) {
        println("Starting historical activity backfill for school: $schoolId")
        
        try {
            backfillStaffActivities(schoolId, systemUserId)
            backfillStudentActivities(schoolId, systemUserId)
            backfillParentActivities(schoolId, systemUserId)
            
            println("Historical activity backfill completed successfully")
        } catch (e: Exception) {
            println("Error during backfill: ${e.message}")
            throw e
        }
    }
    
    private fun backfillStaffActivities(schoolId: UUID, systemUserId: UUID) {
        val staff = staffRepository.findBySchoolIdAndIsActive(schoolId, true)
        
        staff.forEach { staffMember ->
            val activity = ActivityLog(
                activityType = ActivityType.STAFF_HIRED,
                title = "Staff member hired (Historical)",
                description = "${staffMember.designation} hired: ${staffMember.user.firstName} ${staffMember.user.lastName}",
                userId = systemUserId,
                userName = "System Migration",
                userRole = "SYSTEM",
                targetUserId = staffMember.user.id,
                targetUserName = "${staffMember.user.firstName} ${staffMember.user.lastName}",
                entityType = "Staff",
                entityId = staffMember.id,
                metadata = """{"designation": "${staffMember.designation}", "hireDate": "${staffMember.hireDate}", "historical": true}"""
            ).apply {
                this.schoolId = schoolId
                // Use hire date if available, otherwise use created date
                this.createdAt = staffMember.hireDate?.atStartOfDay() ?: staffMember.createdAt
                this.updatedAt = this.createdAt
            }
            
            activityLogRepository.save(activity)
        }
        
        println("Backfilled ${staff.size} staff activities")
    }
    
    private fun backfillStudentActivities(schoolId: UUID, systemUserId: UUID) {
        val students = studentRepository.findBySchoolIdAndIsActive(schoolId, true)
        
        students.forEach { student ->
            // For now, we'll use a generic class name since we don't have enrollment details
            val className = "Unknown Class"
            
            val activity = ActivityLog(
                activityType = ActivityType.STUDENT_ENROLLED,
                title = "Student enrolled (Historical)",
                description = "Student enrolled: ${student.user.firstName} ${student.user.lastName}",
                userId = systemUserId,
                userName = "System Migration",
                userRole = "SYSTEM",
                targetUserId = student.user.id,
                targetUserName = "${student.user.firstName} ${student.user.lastName}",
                entityType = "Student",
                entityId = student.id,
                metadata = """{"studentId": "${student.studentId}", "historical": true}"""
            ).apply {
                this.schoolId = schoolId
                this.createdAt = student.createdAt
                this.updatedAt = this.createdAt
            }
            
            activityLogRepository.save(activity)
        }
        
        println("Backfilled ${students.size} student activities")
    }
    
    private fun backfillParentActivities(schoolId: UUID, systemUserId: UUID) {
        val parents = parentRepository.findBySchoolIdAndIsActive(schoolId, true)
        
        parents.forEach { parent ->
            // Get children names for context
            val childrenNames = parent.studentRelationships
                .filter { it.isActive }
                .map { "${it.student.user.firstName} ${it.student.user.lastName}" }
                .joinToString(", ")
            
            val activity = ActivityLog(
                activityType = ActivityType.PARENT_ADDED,
                title = "Parent added (Historical)",
                description = "Parent added: ${parent.user.firstName} ${parent.user.lastName}" + 
                             if (childrenNames.isNotEmpty()) " (Children: $childrenNames)" else "",
                userId = systemUserId,
                userName = "System Migration",
                userRole = "SYSTEM",
                targetUserId = parent.user.id,
                targetUserName = "${parent.user.firstName} ${parent.user.lastName}",
                entityType = "Parent",
                entityId = parent.id,
                metadata = """{"childrenNames": "$childrenNames", "historical": true}"""
            ).apply {
                this.schoolId = schoolId
                this.createdAt = parent.createdAt
                this.updatedAt = this.createdAt
            }
            
            activityLogRepository.save(activity)
        }
        
        println("Backfilled ${parents.size} parent activities")
    }
    
    /**
     * Create a system initialization activity
     */
    fun createSystemInitializationActivity(schoolId: UUID, systemUserId: UUID) {
        val activity = ActivityLog(
            activityType = ActivityType.SYSTEM_MAINTENANCE,
            title = "Activity logging system initialized",
            description = "Activity logging system has been activated for this school",
            userId = systemUserId,
            userName = "System",
            userRole = "SYSTEM",
            entityType = "System"
        ).apply {
            this.schoolId = schoolId
        }
        
        activityLogRepository.save(activity)
    }
}
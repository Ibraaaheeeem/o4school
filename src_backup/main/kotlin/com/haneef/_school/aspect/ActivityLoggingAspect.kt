package com.haneef._school.aspect

import com.haneef._school.entity.ActivityType
import com.haneef._school.service.ActivityLogService
import com.haneef._school.service.CustomUserDetails
import jakarta.servlet.http.HttpServletRequest
import org.aspectj.lang.JoinPoint
import org.aspectj.lang.annotation.AfterReturning
import org.aspectj.lang.annotation.Aspect
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.util.UUID

@Aspect
@Component
class ActivityLoggingAspect(
    private val activityLogService: ActivityLogService
) {
    
    @AfterReturning("execution(* com.haneef._school.controller.CommunityController.saveStaffHtmx(..))")
    fun logStaffSave(joinPoint: JoinPoint) {
        try {
            val request = getCurrentRequest()
            val authentication = SecurityContextHolder.getContext().authentication
            val customUser = authentication?.principal as? CustomUserDetails ?: return
            
            val args = joinPoint.args
            val id = args.find { it is UUID } as? UUID
            val firstName = getStringArg(args, "firstName") ?: "Unknown"
            val lastName = getStringArg(args, "lastName") ?: "User"
            val designation = getStringArg(args, "designation") ?: "Staff"
            
            val session = request?.session
            val selectedSchoolId = session?.getAttribute("selectedSchoolId") as? UUID ?: return
            
            if (id != null) {
                // Update
                activityLogService.logStaffUpdated(
                    schoolId = selectedSchoolId,
                    actorUserId = customUser.user.id!!,
                    actorRole = customUser.authorities.firstOrNull()?.authority ?: "USER",
                    staffUserId = id,
                    changes = mapOf(
                        "firstName" to firstName,
                        "lastName" to lastName,
                        "designation" to designation
                    ),
                    request = request
                )
            } else {
                // Create - we'll need to get the created user ID from the result
                activityLogService.logActivity(
                    schoolId = selectedSchoolId,
                    activityType = ActivityType.STAFF_HIRED,
                    title = "New staff member hired",
                    userId = customUser.user.id!!,
                    userRole = customUser.authorities.firstOrNull()?.authority ?: "USER",
                    description = "New $designation hired: $firstName $lastName",
                    entityType = "Staff",
                    metadata = mapOf(
                        "firstName" to firstName,
                        "lastName" to lastName,
                        "designation" to designation
                    ),
                    request = request
                )
            }
        } catch (e: Exception) {
            // Log error but don't fail the main operation
            println("Failed to log staff activity: ${e.message}")
        }
    }
    
    @AfterReturning("execution(* com.haneef._school.controller.CommunityController.saveStudentHtmx(..))")
    fun logStudentSave(joinPoint: JoinPoint) {
        try {
            val request = getCurrentRequest()
            val authentication = SecurityContextHolder.getContext().authentication
            val customUser = authentication?.principal as? CustomUserDetails ?: return
            
            val args = joinPoint.args
            val id = args.find { it is UUID } as? UUID
            val firstName = getStringArg(args, "firstName") ?: "Unknown"
            val lastName = getStringArg(args, "lastName") ?: "Student"
            
            val session = request?.session
            val selectedSchoolId = session?.getAttribute("selectedSchoolId") as? UUID ?: return
            
            if (id != null) {
                activityLogService.logStudentUpdated(
                    schoolId = selectedSchoolId,
                    actorUserId = customUser.user.id!!,
                    actorRole = customUser.authorities.firstOrNull()?.authority ?: "USER",
                    studentUserId = id,
                    changes = mapOf(
                        "firstName" to firstName,
                        "lastName" to lastName
                    ),
                    request = request
                )
            } else {
                activityLogService.logActivity(
                    schoolId = selectedSchoolId,
                    activityType = ActivityType.STUDENT_ENROLLED,
                    title = "New student enrolled",
                    userId = customUser.user.id!!,
                    userRole = customUser.authorities.firstOrNull()?.authority ?: "USER",
                    description = "New student enrolled: $firstName $lastName",
                    entityType = "Student",
                    metadata = mapOf(
                        "firstName" to firstName,
                        "lastName" to lastName
                    ),
                    request = request
                )
            }
        } catch (e: Exception) {
            println("Failed to log student activity: ${e.message}")
        }
    }
    
    @AfterReturning("execution(* com.haneef._school.controller.CommunityController.saveParentHtmx(..))")
    fun logParentSave(joinPoint: JoinPoint) {
        try {
            val request = getCurrentRequest()
            val authentication = SecurityContextHolder.getContext().authentication
            val customUser = authentication?.principal as? CustomUserDetails ?: return
            
            val args = joinPoint.args
            val id = args.find { it is UUID } as? UUID
            val firstName = getStringArg(args, "firstName") ?: "Unknown"
            val lastName = getStringArg(args, "lastName") ?: "Parent"
            
            val session = request?.session
            val selectedSchoolId = session?.getAttribute("selectedSchoolId") as? UUID ?: return
            
            if (id != null) {
                activityLogService.logActivity(
                    schoolId = selectedSchoolId,
                    activityType = ActivityType.PARENT_UPDATED,
                    title = "Parent information updated",
                    userId = customUser.user.id!!,
                    userRole = customUser.authorities.firstOrNull()?.authority ?: "USER",
                    description = "Parent profile updated: $firstName $lastName",
                    targetUserId = id,
                    entityType = "Parent",
                    request = request
                )
            } else {
                activityLogService.logActivity(
                    schoolId = selectedSchoolId,
                    activityType = ActivityType.PARENT_ADDED,
                    title = "New parent added",
                    userId = customUser.user.id!!,
                    userRole = customUser.authorities.firstOrNull()?.authority ?: "USER",
                    description = "New parent added: $firstName $lastName",
                    entityType = "Parent",
                    metadata = mapOf(
                        "firstName" to firstName,
                        "lastName" to lastName
                    ),
                    request = request
                )
            }
        } catch (e: Exception) {
            println("Failed to log parent activity: ${e.message}")
        }
    }
    
    private fun getCurrentRequest(): HttpServletRequest? {
        val requestAttributes = RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes
        return requestAttributes?.request
    }
    
    private fun getStringArg(args: Array<Any>, paramName: String): String? {
        // This is a simplified approach - in a real implementation, you might want to use
        // method parameter annotations or reflection to properly match parameter names
        return args.find { it is String && it.isNotBlank() } as? String
    }
}
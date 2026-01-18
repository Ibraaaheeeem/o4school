package com.haneef._school.controller

import com.haneef._school.entity.ActivityType
import com.haneef._school.service.ActivityLogService
import com.haneef._school.service.CustomUserDetails
import jakarta.servlet.http.HttpSession
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import java.util.UUID

@Controller
@RequestMapping("/admin/activities")
@PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'SCHOOL_ADMIN')")
class ActivityLogController(
    private val activityLogService: ActivityLogService
) {
    
    @GetMapping
    fun activityList(
        model: Model,
        authentication: Authentication,
        session: HttpSession,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) type: String?,
        @RequestParam(required = false) role: String?
    ): String {
        val customUser = authentication.principal as CustomUserDetails
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/select-school"

        val pageable = PageRequest.of(page, size, Sort.by("createdAt").descending())
        
        val activitiesPage = when {
            !type.isNullOrBlank() -> {
                val activityType = ActivityType.valueOf(type)
                activityLogService.getActivitiesByType(selectedSchoolId, activityType, pageable)
            }
            !role.isNullOrBlank() -> {
                activityLogService.getActivitiesByRole(selectedSchoolId, role, pageable)
            }
            else -> {
                activityLogService.getAllActivities(selectedSchoolId, pageable)
            }
        }
        
        // Get activity statistics
        val activityStats = activityLogService.getActivityStats(selectedSchoolId)
        
        model.addAttribute("user", customUser.user)
        model.addAttribute("activitiesPage", activitiesPage)
        model.addAttribute("activityStats", activityStats)
        model.addAttribute("activityTypes", ActivityType.values())
        model.addAttribute("selectedType", type)
        model.addAttribute("selectedRole", role)
        model.addAttribute("currentPage", page)
        
        return "admin/activities/list"
    }
    
    @GetMapping("/api/recent")
    @ResponseBody
    fun getRecentActivities(
        authentication: Authentication,
        session: HttpSession,
        @RequestParam(defaultValue = "10") limit: Int
    ): List<Map<String, Any?>> {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return emptyList()
            
        val activities = activityLogService.getRecentActivities(selectedSchoolId, limit)
        
        return activities.map { activity ->
            mapOf(
                "id" to activity.id,
                "title" to activity.title,
                "description" to activity.description,
                "userName" to activity.userName,
                "userRole" to activity.userRole,
                "targetUserName" to activity.targetUserName,
                "activityType" to activity.activityType.name,
                "createdAt" to activity.createdAt,
                "timeAgo" to getTimeAgo(activity.createdAt),
                "icon" to getActivityIcon(activity.activityType)
            )
        }
    }
    
    @GetMapping("/api/user/{userId}")
    @ResponseBody
    fun getUserActivities(
        @PathVariable userId: UUID,
        authentication: Authentication,
        session: HttpSession,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): Map<String, Any> {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return mapOf("error" to "No school selected")
            
        val pageable = PageRequest.of(page, size, Sort.by("createdAt").descending())
        val activitiesPage = activityLogService.getUserActivities(selectedSchoolId, userId, pageable)
        
        val activities = activitiesPage.content.map { activity ->
            mapOf(
                "id" to activity.id,
                "title" to activity.title,
                "description" to activity.description,
                "userName" to activity.userName,
                "userRole" to activity.userRole,
                "targetUserName" to activity.targetUserName,
                "activityType" to activity.activityType.name,
                "createdAt" to activity.createdAt,
                "timeAgo" to getTimeAgo(activity.createdAt),
                "icon" to getActivityIcon(activity.activityType)
            )
        }
        
        return mapOf(
            "activities" to activities,
            "totalElements" to activitiesPage.totalElements,
            "totalPages" to activitiesPage.totalPages,
            "currentPage" to page,
            "hasNext" to activitiesPage.hasNext(),
            "hasPrevious" to activitiesPage.hasPrevious()
        )
    }
    
    private fun getActivityIcon(activityType: ActivityType): String {
        return when (activityType) {
            ActivityType.USER_LOGIN, ActivityType.USER_LOGOUT -> "🔐"
            ActivityType.STUDENT_ENROLLED, ActivityType.STUDENT_UPDATED -> "👨‍🎓"
            ActivityType.STAFF_HIRED, ActivityType.STAFF_UPDATED -> "👨‍🏫"
            ActivityType.PARENT_ADDED, ActivityType.PARENT_UPDATED -> "👨‍👩‍👧‍👦"
            ActivityType.PAYMENT_RECEIVED, ActivityType.INVOICE_GENERATED -> "💰"
            ActivityType.GRADE_ENTERED, ActivityType.ASSIGNMENT_CREATED -> "📝"
            ActivityType.CLASS_CREATED, ActivityType.SUBJECT_CREATED -> "📚"
            ActivityType.EXAM_CREATED, ActivityType.EXAM_SCHEDULED -> "📊"
            ActivityType.NOTIFICATION_SENT, ActivityType.MESSAGE_SENT -> "📧"
            ActivityType.ANNOUNCEMENT_POSTED -> "📢"
            ActivityType.SYSTEM_BACKUP, ActivityType.SYSTEM_MAINTENANCE -> "⚙️"
            ActivityType.SECURITY_ALERT -> "🚨"
            else -> "📋"
        }
    }
    
    private fun getTimeAgo(dateTime: java.time.LocalDateTime): String {
        val now = java.time.LocalDateTime.now()
        val duration = java.time.Duration.between(dateTime, now)
        
        return when {
            duration.toMinutes() < 1 -> "Just now"
            duration.toMinutes() < 60 -> "${duration.toMinutes()} minutes ago"
            duration.toHours() < 24 -> "${duration.toHours()} hours ago"
            duration.toDays() < 7 -> "${duration.toDays()} days ago"
            duration.toDays() < 30 -> "${duration.toDays() / 7} weeks ago"
            else -> "${duration.toDays() / 30} months ago"
        }
    }
}
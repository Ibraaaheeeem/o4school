package com.haneef._school.controller

import com.haneef._school.repository.StudentRepository
import com.haneef._school.repository.ParentRepository
import com.haneef._school.repository.StaffRepository
import com.haneef._school.repository.SchoolClassRepository
import com.haneef._school.repository.ActivityLogRepository
import com.haneef._school.repository.SchoolRepository
import com.haneef._school.service.CustomUserDetailsService
import jakarta.servlet.http.HttpSession
import org.springframework.data.domain.PageRequest
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.UUID

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'SCHOOL_ADMIN', 'ADMIN', 'PRINCIPAL', 'TEACHER', 'STAFF')")
class AdminDashboardController(
    private val userDetailsService: CustomUserDetailsService,
    private val studentRepository: StudentRepository,
    private val parentRepository: ParentRepository,
    private val staffRepository: StaffRepository,
    private val schoolClassRepository: SchoolClassRepository,
    private val activityLogRepository: ActivityLogRepository,
    private val schoolRepository: SchoolRepository
) {

    private val logger = LoggerFactory.getLogger(AdminDashboardController::class.java)

    @GetMapping("/dashboard")
    fun adminDashboard(model: Model, authentication: Authentication, session: HttpSession): String {
        logger.info("=== ADMIN DASHBOARD REQUEST RECEIVED ===")
        
        val customUser = authentication.principal as com.haneef._school.service.CustomUserDetails
        logger.info("User: ${customUser.username} (ID: ${customUser.getUserId()})")
        logger.info("User authorities: ${customUser.authorities.map { it.authority }}")
        
        // Get selected school from session
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
        logger.info("Selected school ID from session: $selectedSchoolId")
        
        if (selectedSchoolId == null) {
            logger.warn("No selected school ID in session, redirecting to school selection")
            return "redirect:/select-school"
        }
        
        // Fetch school details
        val school = schoolRepository.findById(selectedSchoolId).orElse(null)
        logger.info("School found: ${school?.name}")
        
        // Fetch real statistics
        val studentCount = studentRepository.countBySchoolIdAndIsActive(selectedSchoolId, true)
        val parentCount = parentRepository.countBySchoolIdAndIsActive(selectedSchoolId, true)
        val staffCount = staffRepository.countBySchoolIdAndIsActive(selectedSchoolId, true)
        val classCount = schoolClassRepository.countBySchoolIdAndIsActive(selectedSchoolId, true)
        
        logger.info("Dashboard statistics - Students: $studentCount, Parents: $parentCount, Staff: $staffCount, Classes: $classCount")
        
        // Fetch recent activities (last 7 days, limit to 10)
        val sevenDaysAgo = LocalDateTime.now().minusDays(7)
        val recentActivities = activityLogRepository.findRecentActivities(
            selectedSchoolId,
            sevenDaysAgo,
            PageRequest.of(0, 10)
        )
        
        model.addAttribute("user", customUser.user)
        model.addAttribute("school", school)
        model.addAttribute("studentCount", studentCount)
        model.addAttribute("parentCount", parentCount)
        model.addAttribute("staffCount", staffCount)
        model.addAttribute("classCount", classCount)
        model.addAttribute("recentActivities", recentActivities.content)
        model.addAttribute("userRole", "School Administrator")
        model.addAttribute("dashboardType", "school-admin")
        
        logger.info("Returning admin dashboard view")
        logger.info("=== ADMIN DASHBOARD REQUEST COMPLETED ===")
        
        return "dashboard/admin-dashboard"
    }

    @GetMapping("/reports")
    fun redirectToReports(): String {
        return "redirect:/admin/assessments/reports"
    }
}
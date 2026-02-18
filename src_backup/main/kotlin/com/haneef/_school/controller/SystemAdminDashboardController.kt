package com.haneef._school.controller

import com.haneef._school.entity.UserStatus
import com.haneef._school.repository.*
import com.haneef._school.service.CustomUserDetails
import com.haneef._school.service.EmailService
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.time.LocalDateTime
import java.util.UUID

@Controller
@RequestMapping("/system-admin")
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
class SystemAdminDashboardController(
    private val userRepository: UserRepository,
    private val schoolRepository: SchoolRepository,
    private val studentRepository: StudentRepository,
    private val staffRepository: StaffRepository,
    private val parentRepository: ParentRepository,
    private val emailService: EmailService
) {

    @GetMapping("/dashboard")
    fun systemAdminDashboard(model: Model, authentication: Authentication): String {
        val customUser = authentication.principal as CustomUserDetails
        
        model.addAttribute("user", customUser.user)
        model.addAttribute("userRole", "System Administrator")
        model.addAttribute("dashboardType", "system-admin")
        
        // Dashboard Stats
        model.addAttribute("totalSchools", schoolRepository.count())
        model.addAttribute("totalUsers", userRepository.count())
        model.addAttribute("pendingApprovals", userRepository.findByStatus(UserStatus.PENDING).filter { it.approvalStatus == "pending" }.size)
        
        return "dashboard/system-admin-dashboard"
    }

    @GetMapping("/users")
    fun manageUsers(model: Model, authentication: Authentication): String {
        val customUser = authentication.principal as CustomUserDetails
        
        // Pending registrations
        val pendingUsers = userRepository.findByStatus(UserStatus.PENDING)
            .filter { it.approvalStatus == "pending" }
        
        // Approved Schools and their Admins
        val schools = schoolRepository.findAll()
        val schoolData = schools.map { school ->
            val admin = if (school.adminUserId != null) {
                userRepository.findById(school.adminUserId!!).orElse(null)
            } else null
            
            mapOf(
                "school" to school,
                "admin" to admin,
                "studentCount" to studentRepository.countBySchoolIdAndIsActive(school.id!!, true),
                "staffCount" to staffRepository.countBySchoolIdAndIsActive(school.id!!, true),
                "parentCount" to parentRepository.countBySchoolIdAndIsActive(school.id!!, true)
            )
        }
        
        model.addAttribute("user", customUser.user)
        model.addAttribute("pendingUsers", pendingUsers)
        model.addAttribute("schoolData", schoolData)
        model.addAttribute("userRole", "System Administrator")
        model.addAttribute("dashboardType", "system-admin")
        
        return "system-admin/users/list"
    }

    @PostMapping("/users/approve/{id}")
    fun approveUser(
        @PathVariable id: UUID,
        authentication: Authentication,
        redirectAttributes: RedirectAttributes
    ): String {
        val customUser = authentication.principal as CustomUserDetails
        val user = userRepository.findById(id).orElseThrow { RuntimeException("User not found") }
        
        user.status = UserStatus.ACTIVE
        user.approvalStatus = "approved"
        user.approvedAt = LocalDateTime.now()
        user.approvedBy = customUser.user.id
        user.isVerified = true
        
        userRepository.save(user)
        
        // If user is a school admin, activate their school
        val activeRoles = userRepository.findById(id).get().schoolRoles
        activeRoles.forEach { role ->
            if (role.role?.name == "SCHOOL_ADMIN" && role.schoolId != null) {
                schoolRepository.findById(role.schoolId!!).ifPresent { school ->
                    school.status = "active"
                    schoolRepository.save(school)
                }
            }
        }
        
        // Send email
        emailService.sendApprovalEmail(user.email ?: "", user.fullName ?: "User", "SCHOOL_ADMIN")
        
        redirectAttributes.addFlashAttribute("success", "User ${user.fullName ?: "User"} has been approved and notified.")
        return "redirect:/system-admin/users"
    }
}
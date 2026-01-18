package com.haneef._school.controller

import com.haneef._school.repository.SchoolRepository
import com.haneef._school.service.CustomUserDetails
import com.haneef._school.service.UserSchoolRoleService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.web.savedrequest.HttpSessionRequestCache
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.slf4j.LoggerFactory
import java.util.UUID

@Controller
class RoleSelectionController(
    private val userSchoolRoleService: UserSchoolRoleService,
    private val schoolRepository: SchoolRepository
) {
    
    private val logger = LoggerFactory.getLogger(RoleSelectionController::class.java)
    private val requestCache = HttpSessionRequestCache()

    @GetMapping("/select-school")
    fun selectSchool(authentication: Authentication, model: Model): String {
        val customUser = authentication.principal as CustomUserDetails
        val userId = customUser.getUserId() ?: return "redirect:/login?error=true"
        
        // Handle system admin users - they can access all schools
        if (customUser.authorities.any { it.authority == "ROLE_SYSTEM_ADMIN" }) {
            val allSchools = schoolRepository.findAll()
            model.addAttribute("user", customUser.user)
            model.addAttribute("schools", allSchools.associateBy { it.id })
            model.addAttribute("isSystemAdmin", true)
            return "auth/select-school"
        }
        
        val userSchools = userSchoolRoleService.getUserSchoolsWithDetails(userId)
        model.addAttribute("userSchools", userSchools)
        
        return "auth/select-school"
    }

    @PostMapping("/select-school")
    fun handleSchoolSelection(
        @RequestParam schoolId: UUID,
        authentication: Authentication,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): String {
        val customUser = authentication.principal as CustomUserDetails
        val userId = customUser.getUserId() ?: return "redirect:/login?error=true"
        
        // Handle system admin users
        if (customUser.authorities.any { it.authority == "ROLE_SYSTEM_ADMIN" }) {
            request.session.setAttribute("selectedSchoolId", schoolId)
            request.session.setAttribute("selectedRole", "SYSTEM_ADMIN")
            
            val defaultRedirectUrl = "/system-admin/dashboard"
            val targetUrl = getTargetUrlAfterRoleSelection(request, response, defaultRedirectUrl)
            
            return "redirect:$targetUrl"
        }
        
        // Validate that user has access to this school
        val userSchools = userSchoolRoleService.getUserSchools(userId)
        if (!userSchools.contains(schoolId)) {
            return "redirect:/select-school?error=invalid_school"
        }
        
        request.session.setAttribute("selectedSchoolId", schoolId)
        
        // Check if user has multiple roles in this school
        if (userSchoolRoleService.hasMultipleRolesInSchool(userId, schoolId)) {
            return "redirect:/select-role"
        }
        
        // Single role - set it and redirect
        val userSchoolRoles = userSchoolRoleService.getActiveRolesByUserIdAndSchoolId(userId, schoolId)
        if (userSchoolRoles.isNotEmpty()) {
            val userSchoolRole = userSchoolRoles.first()
            request.session.setAttribute("selectedRoleId", userSchoolRole.role?.id)
            request.session.setAttribute("selectedRole", userSchoolRole.role?.name)
            
            val defaultRedirectUrl = getDefaultRedirectUrl(userSchoolRole.role?.name)
            val targetUrl = getTargetUrlAfterRoleSelection(request, response, defaultRedirectUrl)
            
            return "redirect:$targetUrl"
        }
        
        return "redirect:/admin/dashboard"
    }

    @GetMapping("/select-role")
    fun selectRole(authentication: Authentication, request: HttpServletRequest, model: Model): String {
        val customUser = authentication.principal as CustomUserDetails
        val userId = customUser.getUserId() ?: return "redirect:/login?error=true"
        
        val schoolId = request.session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/select-school"
        
        val userRoles = userSchoolRoleService.getActiveRolesByUserIdAndSchoolId(userId, schoolId)
        model.addAttribute("userRoles", userRoles)
        model.addAttribute("schoolId", schoolId)
        
        return "auth/select-role"
    }

    @PostMapping("/select-role")
    fun handleRoleSelection(
        @RequestParam roleId: UUID,
        @RequestParam roleName: String,
        authentication: Authentication,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): String {
        val customUser = authentication.principal as CustomUserDetails
        val userId = customUser.getUserId() ?: return "redirect:/login?error=true"
        
        val schoolId = request.session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/select-school"
        
        // Validate that user has this role in this school
        val userRoles = userSchoolRoleService.getActiveRolesByUserIdAndSchoolId(userId, schoolId)
        val hasRole = userRoles.any { it.role?.id == roleId && it.role?.name == roleName }
        
        if (!hasRole) {
            return "redirect:/select-role?error=invalid_role"
        }
        
        // Set the selected role in session
        request.session.setAttribute("selectedRoleId", roleId)
        request.session.setAttribute("selectedRole", roleName)
        
        val defaultRedirectUrl = getDefaultRedirectUrl(roleName)
        val targetUrl = getTargetUrlAfterRoleSelection(request, response, defaultRedirectUrl)
        
        logger.info("User selected role: $roleName, redirecting to: $targetUrl")
        
        return "redirect:$targetUrl"
    }
    
    private fun getDefaultRedirectUrl(roleName: String?): String {
        return when (roleName) {
            "SYSTEM_ADMIN" -> "/system-admin/dashboard"
            "SCHOOL_ADMIN" -> "/admin/dashboard"
            "ADMIN" -> "/admin/dashboard"
            "PRINCIPAL" -> "/admin/dashboard"
            "STAFF" -> "/staff/dashboard"
            "TEACHER" -> "/staff/dashboard"
            "PARENT" -> "/parent/dashboard"
            "STUDENT" -> "/student/dashboard"
            // Legacy role names for backward compatibility
            "System Administrator" -> "/system-admin/dashboard"
            "Principal" -> "/admin/dashboard"
            "School Admin" -> "/admin/dashboard"
            "Admin" -> "/admin/dashboard"
            "Teacher" -> "/staff/dashboard"
            "Parent" -> "/parent/dashboard"
            "Student" -> "/student/dashboard"
            "Staff" -> "/staff/dashboard"
            "Finance Manager" -> "/admin/dashboard"
            "Librarian" -> "/admin/dashboard"
            "Counselor" -> "/admin/dashboard"
            else -> "/admin/dashboard"
        }
    }
    
    private fun getTargetUrlAfterRoleSelection(
        request: HttpServletRequest,
        response: HttpServletResponse,
        defaultRedirectUrl: String
    ): String {
        // Try to get the original requested URL
        val savedRequest = requestCache.getRequest(request, response)
        
        if (savedRequest != null) {
            val originalUrl = savedRequest.redirectUrl
            logger.debug("Found saved request for URL: $originalUrl")
            
            // Security check: Only redirect to internal URLs
            if (isInternalUrl(originalUrl) && !isAuthenticationUrl(originalUrl)) {
                // Check if user has access to the original URL
                if (hasAccessToUrl(originalUrl, request)) {
                    logger.info("Redirecting to original URL after role selection: $originalUrl")
                    // Clear the saved request
                    requestCache.removeRequest(request, response)
                    return originalUrl
                } else {
                    logger.info("User doesn't have access to original URL: $originalUrl. Using default redirect.")
                }
            } else {
                logger.warn("Original URL is not safe for redirect: $originalUrl. Using default redirect.")
            }
            
            // Clear the saved request even if we're not using it
            requestCache.removeRequest(request, response)
        }
        
        return defaultRedirectUrl
    }
    
    private fun isInternalUrl(url: String): Boolean {
        return url.startsWith("/") || 
               url.startsWith("http://localhost") || 
               url.startsWith("https://localhost") ||
               !url.contains("://")
    }
    
    private fun isAuthenticationUrl(url: String): Boolean {
        val authPaths = listOf("/login", "/logout", "/register", "/activate-account", "/forgot-password", "/select-school", "/select-role")
        return authPaths.any { url.contains(it) }
    }
    
    private fun hasAccessToUrl(url: String, request: HttpServletRequest): Boolean {
        val userRole = request.session.getAttribute("selectedRole") as? String
        val isSystemAdmin = userRole == "SYSTEM_ADMIN"
        
        return when {
            // System admin has access to everything
            isSystemAdmin -> true
            
            // Public URLs are accessible to everyone
            url.startsWith("/public/") || url == "/" -> true
            
            // System admin URLs
            url.startsWith("/system-admin/") -> isSystemAdmin
            
            // Admin URLs - accessible to admin roles
            url.startsWith("/admin/") -> {
                val adminRoles = listOf("SCHOOL_ADMIN", "ADMIN", "PRINCIPAL", "STAFF", "TEACHER")
                adminRoles.any { userRole?.contains(it) == true }
            }
            
            // Staff URLs
            url.startsWith("/staff/") -> {
                val staffRoles = listOf("STAFF", "TEACHER", "SCHOOL_ADMIN", "ADMIN", "PRINCIPAL")
                staffRoles.any { userRole?.contains(it) == true }
            }
            
            // Parent URLs
            url.startsWith("/parent/") -> {
                val parentRoles = listOf("PARENT", "SCHOOL_ADMIN", "ADMIN", "PRINCIPAL", "STAFF")
                parentRoles.any { userRole?.contains(it) == true }
            }
            
            // Student URLs
            url.startsWith("/student/") -> {
                val studentRoles = listOf("STUDENT", "PARENT", "SCHOOL_ADMIN", "ADMIN", "PRINCIPAL", "STAFF")
                studentRoles.any { userRole?.contains(it) == true }
            }
            
            // Default: allow access to other URLs
            else -> true
        }
    }
}
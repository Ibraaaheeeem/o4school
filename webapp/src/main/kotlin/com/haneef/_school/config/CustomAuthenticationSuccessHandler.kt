package com.haneef._school.config

import com.haneef._school.service.ActivityLogService
import com.haneef._school.service.CustomUserDetails
import com.haneef._school.service.UserSchoolRoleService
import java.util.UUID
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.security.web.savedrequest.HttpSessionRequestCache
import org.springframework.security.web.savedrequest.SavedRequest
import org.springframework.stereotype.Component
import org.slf4j.LoggerFactory

@Component
class CustomAuthenticationSuccessHandler(
    private val userSchoolRoleService: UserSchoolRoleService,
    private val activityLogService: ActivityLogService
) : AuthenticationSuccessHandler {

    companion object {
        private val logger = LoggerFactory.getLogger(CustomAuthenticationSuccessHandler::class.java)
        private val ZERO_UUID: UUID = UUID(0L, 0L)
        private val ADMIN_ROLES = setOf("SCHOOL_ADMIN", "ADMIN", "PRINCIPAL", "STAFF", "TEACHER")
        private val STAFF_ROLES = setOf("STAFF", "TEACHER", "SCHOOL_ADMIN", "ADMIN", "PRINCIPAL")
        private val PARENT_ROLES = setOf("PARENT", "SCHOOL_ADMIN", "ADMIN", "PRINCIPAL", "STAFF")
        private val STUDENT_ROLES = setOf("STUDENT", "PARENT", "SCHOOL_ADMIN", "ADMIN", "PRINCIPAL", "STAFF")
    }

    private val requestCache = HttpSessionRequestCache()

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication
    ) {
        logger.debug("Authentication success handler started")
        
        val customUser = authentication.principal as CustomUserDetails
        val userId = customUser.getUserId()
        
        logger.debug("User authenticated: {} (ID: {})", customUser.username, userId)
        
        if (userId == null) {
            logger.error("User ID is null, redirecting to login with error")
            response.sendRedirect("/auth/login?error=true")
            return
        }

        // Check if user is system admin
        val isSystemAdmin = customUser.authorities.any { it.authority == "ROLE_SYSTEM_ADMIN" }
        logger.debug("Is system admin: {}", isSystemAdmin)
        
        if (isSystemAdmin) {
            logger.debug("System admin detected, redirecting to /system-admin/dashboard")
            activityLogService.logUserLogin(ZERO_UUID, customUser.user, "SYSTEM_ADMIN", request)
            handleSuccessfulLogin(request, response, "/system-admin/dashboard")
            return
        }
        
        // Check if user has a forced school context (e.g. from Student Login)
        val forcedSchoolId = customUser.forcedSchoolId
        if (forcedSchoolId != null) {
            logger.info("Forced school context detected: $forcedSchoolId. Using this school.")
            handleSchoolAndRoleRedirection(request, response, userId, forcedSchoolId, customUser)
            return
        }

        // Check if user has multiple schools
        val hasMultipleSchools = userSchoolRoleService.hasMultipleSchools(userId)
        logger.debug("Has multiple schools: {}", hasMultipleSchools)
        
        if (hasMultipleSchools) {
            logger.info("Multiple schools detected, redirecting to school selection")
            response.sendRedirect("/select-school")
            return
        }
        
        // Get user's schools and roles
        val userSchools = userSchoolRoleService.getUserSchools(userId)
        logger.debug("User schools count: {}", userSchools.size)
        
        if (userSchools.isEmpty()) {
            logger.warn("No schools found for user, using fallback redirect")
            handleSuccessfulLogin(request, response, "/admin/dashboard") // Fallback
            return
        }
        
        val schoolId = userSchools.first()
        logger.debug("Selected school ID: {}", schoolId)
        
        handleSchoolAndRoleRedirection(request, response, userId, schoolId, customUser)
        
        logger.debug("Authentication success handler completed")
    }
    
    private fun handleSchoolAndRoleRedirection(
        request: HttpServletRequest,
        response: HttpServletResponse,
        userId: UUID,
        schoolId: UUID,
        customUser: CustomUserDetails
    ) {
        // Check if user has multiple roles in the school
        val hasMultipleRoles = userSchoolRoleService.hasMultipleRolesInSchool(userId, schoolId)
        logger.debug("Has multiple roles in school: {}", hasMultipleRoles)
        
        if (hasMultipleRoles) {
            logger.info("Multiple roles detected, redirecting to role selection")
            // Store school in session and redirect to role selection
            request.session.setAttribute("selectedSchoolId", schoolId)
            response.sendRedirect("/select-role")
            return
        }
        
        // User has single role - redirect directly
        val userSchoolRoles = userSchoolRoleService.getActiveRolesByUserIdAndSchoolId(userId, schoolId)
        logger.debug("Active user school roles count: {}", userSchoolRoles.size)
        
        if (userSchoolRoles.isNotEmpty()) {
            val userSchoolRole = userSchoolRoles.first()
            val roleName = userSchoolRole.role?.name
            
            logger.debug("Setting session attributes for school: {}", schoolId)
            
            request.session.setAttribute("selectedSchoolId", schoolId)
            request.session.setAttribute("selectedRoleId", userSchoolRole.role?.id)
            request.session.setAttribute("selectedRole", roleName)
            
            val defaultRedirectUrl = getRoleBasedDashboardUrl(roleName)
            logger.debug("Role-based redirect URL: {} for role: {}", defaultRedirectUrl, roleName)
            
            // Log active login for single-role user
            activityLogService.logUserLogin(schoolId, customUser.user, roleName ?: "USER", request)
            
            handleSuccessfulLogin(request, response, defaultRedirectUrl)
        } else {
            logger.warn("No active school roles found, using fallback redirect")
            handleSuccessfulLogin(request, response, "/admin/dashboard") // Fallback
        }
    }
    
    private fun getRoleBasedDashboardUrl(roleName: String?): String {
        return when (roleName) {
            "SCHOOL_ADMIN", "ADMIN", "PRINCIPAL" -> "/admin/dashboard"
            "TEACHER", "STAFF" -> "/staff/dashboard"
            "PARENT" -> "/parent/dashboard"
            "STUDENT" -> "/student/dashboard"
            else -> "/admin/dashboard" // Default fallback
        }
    }
    
    private fun handleSuccessfulLogin(
        request: HttpServletRequest,
        response: HttpServletResponse,
        defaultRedirectUrl: String
    ) {
        logger.debug("Handling successful login. Default redirect URL: {}", defaultRedirectUrl)
        
        // Try to get the original requested URL
        val savedRequest = requestCache.getRequest(request, response)
        val targetUrl = getTargetUrl(savedRequest, defaultRedirectUrl, request)
        
        logger.debug("Final target URL: {}", targetUrl)
        
        // Clear the saved request
        requestCache.removeRequest(request, response)
        
        logger.debug("Sending redirect to: {}", targetUrl)
        response.sendRedirect(targetUrl)
        logger.debug("Successful login handling completed")
    }
    
    private fun getTargetUrl(
        savedRequest: SavedRequest?,
        defaultRedirectUrl: String,
        request: HttpServletRequest
    ): String {
        // If no saved request, use default
        if (savedRequest == null) {
            return defaultRedirectUrl
        }
        
        val originalUrl = savedRequest.redirectUrl
        logger.debug("Original requested URL: $originalUrl")
        
        // Security check: Only redirect to internal URLs
        if (!isInternalUrl(originalUrl)) {
            logger.warn("Attempted redirect to external URL: $originalUrl. Using default redirect.")
            return defaultRedirectUrl
        }
        
        // Don't redirect to login, logout, registration pages, or static resources, or API requests
        if (isAuthenticationUrl(originalUrl) || isStaticResource(originalUrl) || isApiRequest(originalUrl)) {
            logger.debug("Original URL was authentication-related, static resource, or API request: $originalUrl. Using default redirect.")
            return defaultRedirectUrl
        }
        
        // Check if user has access to the original URL
        if (!hasAccessToUrl(originalUrl, request)) {
            logger.info("User doesn't have access to original URL: $originalUrl. Using default redirect.")
            return defaultRedirectUrl
        }
        
        return originalUrl
    }
    
    private fun isInternalUrl(url: String): Boolean {
        // Check if URL is relative or belongs to our domain
        return url.startsWith("/") || 
               url.startsWith("http://localhost") || 
               url.startsWith("https://localhost") ||
               !url.contains("://") // Relative URLs
    }
    
    private fun isAuthenticationUrl(url: String): Boolean {
        val authPaths = listOf("/login", "/logout", "/register", "/activate-account", "/forgot-password", "/auth/", "/select-school", "/select-role")
        return authPaths.any { url.contains(it) }
    }
    
    private fun isStaticResource(url: String): Boolean {
        val staticPaths = listOf(
            "/favicon.ico", "/robots.txt", "/sitemap.xml",
            "/css/", "/js/", "/images/", "/fonts/", "/static/",
            "/webjars/", "/actuator/"
        )
        return staticPaths.any { url.contains(it) } || 
               url.endsWith(".css") || url.endsWith(".js") || 
               url.endsWith(".png") || url.endsWith(".jpg") || 
               url.endsWith(".jpeg") || url.endsWith(".gif") || 
               url.endsWith(".ico") || url.endsWith(".svg") ||
               url.endsWith(".woff") || url.endsWith(".woff2") ||
               url.endsWith(".ttf") || url.endsWith(".eot")
    }
    
    private fun isApiRequest(url: String): Boolean {
        return url.contains("/api/") || url.contains("/actuator/")
    }
    
    private fun hasAccessToUrl(url: String, request: HttpServletRequest): Boolean {
        // Basic access check based on URL patterns
        // This is a simplified check - in a more complex system, you might want to 
        // integrate with your authorization service
        
        val userRoles = request.session.getAttribute("selectedRole") as? String
        val isSystemAdmin = userRoles == "SYSTEM_ADMIN"
        
        return when {
            // System admin has access to everything
            isSystemAdmin -> true
            
            // Public URLs are accessible to everyone
            url.startsWith("/public/") || url == "/" -> true
            
            // System admin URLs
            url.startsWith("/system-admin/") -> isSystemAdmin
            
            // Admin URLs - accessible to admin roles
            url.startsWith("/admin/") -> ADMIN_ROLES.any { userRoles?.contains(it) == true }
            
            // Staff URLs
            url.startsWith("/staff/") -> STAFF_ROLES.any { userRoles?.contains(it) == true }
            
            // Parent URLs
            url.startsWith("/parent/") -> PARENT_ROLES.any { userRoles?.contains(it) == true }
            
            // Student URLs
            url.startsWith("/student/") -> STUDENT_ROLES.any { userRoles?.contains(it) == true }
            
            // Default: allow access to other URLs
            else -> true
        }
    }
}
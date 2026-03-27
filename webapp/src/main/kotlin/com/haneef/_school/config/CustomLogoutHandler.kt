package com.haneef._school.config

import com.haneef._school.service.ActivityLogService
import com.haneef._school.service.CustomUserDetails
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.logout.LogoutHandler
import org.springframework.stereotype.Component
import org.slf4j.LoggerFactory
import java.util.UUID

@Component
class CustomLogoutHandler(
    private val activityLogService: ActivityLogService
) : LogoutHandler {

    companion object {
        private val logger = LoggerFactory.getLogger(CustomLogoutHandler::class.java)
        private val ZERO_UUID: UUID = UUID(0L, 0L)
    }

    override fun logout(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication?
    ) {
        if (authentication == null || authentication.principal !is CustomUserDetails) {
            logger.debug("No authentication found during logout, skipping activity logging")
            return
        }

        val customUser = authentication.principal as CustomUserDetails
        val session = request.session
        
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
        val selectedRole = session.getAttribute("selectedRole") as? String ?: "USER"

        logger.info("Logging logout for user: ${customUser.username}, role: $selectedRole, school: $selectedSchoolId")

        if (selectedSchoolId != null) {
            activityLogService.logUserLogout(selectedSchoolId, customUser.user, selectedRole, request)
        } else {
            // For system admins or users without a school context
            activityLogService.logUserLogout(ZERO_UUID, customUser.user, selectedRole, request)
        }
    }
}

package com.haneef._school.config

import com.haneef._school.service.CustomUserDetails
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter


class TestCredentialsFilter : OncePerRequestFilter() {

    private val readOnlyMethods = setOf("GET", "OPTIONS", "HEAD", "TRACE")
    private val testEmails = setOf(
        "test_parent@4school.app",
        "test_admin@4school.app"
    )

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val method = request.method?.uppercase() ?: ""

        if (!readOnlyMethods.contains(method)) {
            val authentication = SecurityContextHolder.getContext().authentication
            if (authentication != null && authentication.isAuthenticated) {
                val principal = authentication.principal
                if (principal is CustomUserDetails) {
                    val email = principal.username
                    if (testEmails.contains(email)) {
                        // Return 403 Forbidden for mutating requests
                        response.sendError(
                            HttpServletResponse.SC_FORBIDDEN,
                            "Test credentials are not allowed to modify data"
                        )
                        return
                    }
                }
            }
        }

        filterChain.doFilter(request, response)
    }
}

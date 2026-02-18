package com.haneef._school.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/**
 * Filter to add contextual information to MDC (Mapped Diagnostic Context)
 * This information will be automatically included in JSON logs
 */
@Component
class LoggingContextFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        try {
            // Generate unique request ID
            val requestId = UUID.randomUUID().toString()
            MDC.put("request_id", requestId)
            
            // Add request details
            MDC.put("request_method", request.method)
            MDC.put("request_uri", request.requestURI)
            MDC.put("remote_addr", request.remoteAddr)
            
            // Add user information if authenticated
            val authentication = SecurityContextHolder.getContext().authentication
            if (authentication != null && authentication.isAuthenticated && authentication.name != "anonymousUser") {
                MDC.put("user_id", authentication.name)
                
                // Add school context if available from session
                val session = request.getSession(false)
                session?.getAttribute("selectedSchoolId")?.let { schoolId ->
                    MDC.put("school_id", schoolId.toString())
                }
            }
            
            // Add response header for request tracking
            response.setHeader("X-Request-ID", requestId)
            
            filterChain.doFilter(request, response)
        } finally {
            // Clean up MDC to prevent memory leaks
            MDC.clear()
        }
    }
}

package com.haneef._school.config

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint
import org.springframework.stereotype.Component

@Component
class CustomAuthenticationEntryPoint : AuthenticationEntryPoint {

    private val logger = org.slf4j.LoggerFactory.getLogger(this::class.java)
    private val delegate = LoginUrlAuthenticationEntryPoint("/auth/login")

    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException
    ) {
        val isHtmx = request.getHeader("HX-Request") != null
        logger.debug("Commence authentication entry point. URI: {}, Is HTMX: {}", request.requestURI, isHtmx)

        if (isHtmx) {
            logger.debug("Handling HTMX unauthorized request with HX-Redirect")
            response.status = HttpServletResponse.SC_OK
            response.setHeader("HX-Redirect", "/auth/login")
            // Also set a body to indicate what happened, though HTMX should handle the redirect immediately
            response.writer.write("Unauthorized - Redirecting to login")
        } else {
            logger.debug("Delegating to standard login entry point")
            delegate.commence(request, response, authException)
        }
    }
}

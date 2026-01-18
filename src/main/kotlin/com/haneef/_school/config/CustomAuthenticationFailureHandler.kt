package com.haneef._school.config

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.DisabledException
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.authentication.AuthenticationFailureHandler
import org.springframework.stereotype.Component
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Component
class CustomAuthenticationFailureHandler : AuthenticationFailureHandler {
    override fun onAuthenticationFailure(
        request: HttpServletRequest,
        response: HttpServletResponse,
        exception: AuthenticationException
    ) {
        val errorMessage = when (exception) {
            is DisabledException -> "Your account is awaiting approval by the system administrator."
            else -> "Invalid email or password."
        }
        
        val encodedMessage = URLEncoder.encode(errorMessage, StandardCharsets.UTF_8.toString())
        response.sendRedirect("/auth/login?error=$encodedMessage")
    }
}

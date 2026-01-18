package com.haneef._school.config

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint
import org.springframework.stereotype.Component

@Component
class CustomAuthenticationEntryPoint : AuthenticationEntryPoint {

    private val delegate = LoginUrlAuthenticationEntryPoint("/auth/login")

    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException
    ) {
        if (request.getHeader("HX-Request") != null) {
            response.status = HttpServletResponse.SC_OK
            response.setHeader("HX-Redirect", "/auth/login")
        } else {
            delegate.commence(request, response, authException)
        }
    }
}

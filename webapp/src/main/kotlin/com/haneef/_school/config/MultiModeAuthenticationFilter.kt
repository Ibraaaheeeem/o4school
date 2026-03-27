package com.haneef._school.config

import com.haneef._school.dto.LoginMethod
import com.haneef._school.service.PhoneNumberService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.AuthenticationServiceException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

class MultiModeAuthenticationFilter(
    private val phoneNumberService: PhoneNumberService
) : UsernamePasswordAuthenticationFilter() {

    override fun attemptAuthentication(request: HttpServletRequest, response: HttpServletResponse): Authentication {
        val loginMethodRaw = request.getParameter("loginMethod")?.trim()
        val identifier = request.getParameter("identifier")?.trim()
        val countryCode = request.getParameter("countryCode")
        val password = request.getParameter("password") ?: ""

        if (loginMethodRaw.isNullOrBlank() || identifier.isNullOrBlank()) {
            throw AuthenticationServiceException("Login method and identifier are required")
        }

        val loginMethod = try {
            LoginMethod.valueOf(loginMethodRaw.uppercase())
        } catch (e: IllegalArgumentException) {
            throw AuthenticationServiceException("Unsupported login method")
        }

        val normalizedUsername = when (loginMethod) {
            LoginMethod.EMAIL -> identifier.lowercase().trim()
            LoginMethod.PHONE -> {
                phoneNumberService.parseAndFormatPhoneNumber(identifier, countryCode)
                    ?: throw AuthenticationServiceException("Invalid phone number")
            }
            LoginMethod.STUDENT -> identifier.uppercase().trim()
        }

        val authRequest = UsernamePasswordAuthenticationToken(normalizedUsername, password)
        setDetails(request, authRequest)

        return authenticationManager.authenticate(authRequest)
    }
}
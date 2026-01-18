package com.haneef._school.config

import com.haneef._school.dto.LoginMethod
import com.haneef._school.service.PhoneNumberService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

class MultiModeAuthenticationFilter(
    private val phoneNumberService: PhoneNumberService
) : UsernamePasswordAuthenticationFilter() {

    override fun attemptAuthentication(request: HttpServletRequest, response: HttpServletResponse): Authentication {
        val loginMethod = request.getParameter("loginMethod")
        val identifier = request.getParameter("identifier")
        val countryCode = request.getParameter("countryCode")
        val password = request.getParameter("password")

        val normalizedUsername = when (LoginMethod.valueOf(loginMethod)) {
            LoginMethod.EMAIL -> identifier.lowercase().trim()
            LoginMethod.PHONE -> {
                phoneNumberService.parseAndFormatPhoneNumber(identifier, countryCode)
                    ?: throw IllegalArgumentException("Invalid phone number")
            }
            LoginMethod.STUDENT -> identifier.uppercase().trim()
        }

        val authRequest = UsernamePasswordAuthenticationToken(normalizedUsername, password)
        setDetails(request, authRequest)

        return authenticationManager.authenticate(authRequest)
    }
}
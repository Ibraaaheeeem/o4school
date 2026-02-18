package com.haneef._school.config

import com.haneef._school.dto.LoginMethod
import com.haneef._school.dto.LoginRequest
import com.haneef._school.service.MultiModeAuthenticationService
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

@Component
class MultiModeAuthenticationProvider(
    private val multiModeAuthenticationService: MultiModeAuthenticationService,
    private val passwordEncoder: PasswordEncoder
) : AuthenticationProvider {

    override fun authenticate(authentication: Authentication): Authentication {
        val username = authentication.name
        val password = authentication.credentials.toString()

        try {
            // For now, we'll use a simple approach - try to determine login method from username format
            val loginMethod = determineLoginMethod(username)
            val loginRequest = LoginRequest(
                loginMethod = loginMethod,
                identifier = username,
                countryCode = if (loginMethod == LoginMethod.PHONE) "NG" else null,
                password = password
            )

            val userDetails = multiModeAuthenticationService.authenticateUser(loginRequest)
            
            if (!passwordEncoder.matches(password, userDetails.password)) {
                throw BadCredentialsException("Invalid credentials")
            }

            return UsernamePasswordAuthenticationToken(
                userDetails.username,
                password,
                userDetails.authorities
            )
        } catch (e: Exception) {
            throw BadCredentialsException("Authentication failed: ${e.message}")
        }
    }

    override fun supports(authentication: Class<*>): Boolean {
        return authentication == UsernamePasswordAuthenticationToken::class.java
    }

    private fun determineLoginMethod(username: String): LoginMethod {
        return when {
            username.contains("@") -> LoginMethod.EMAIL
            username.matches(Regex("^\\+?\\d+$")) -> LoginMethod.PHONE
            else -> LoginMethod.STUDENT
        }
    }
}
package com.haneef._school.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class LoginRequest(
    @field:NotNull(message = "Login method is required")
    val loginMethod: LoginMethod,
    
    @field:NotBlank(message = "Identifier is required")
    val identifier: String,
    
    val countryCode: String? = null, // Required only for phone login
    
    @field:NotBlank(message = "Password is required")
    val password: String
)

enum class LoginMethod {
    EMAIL,
    PHONE,
    STUDENT
}
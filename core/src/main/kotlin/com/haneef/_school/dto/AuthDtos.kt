package com.haneef._school.dto

import java.time.LocalDateTime
import java.util.UUID
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Size

data class EmailRequest(
    val email: String,
    val type: String // "ACTIVATE" or "RESET"
)

data class VerifyOtpRequest(
    val email: String,
    val otp: String,
    val type: String
)

data class ResetPasswordRequest(
    val email: String,
    val otp: String,
    val password: String,
    val confirmPassword: String
)

data class RegistrationDto(
    @field:NotBlank(message = "First name is required")
    val firstName: String = "",

    @field:NotBlank(message = "Last name is required")
    val lastName: String = "",

    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Invalid email format")
    val email: String = "",

    @field:NotBlank(message = "Phone number is required")
    val phoneNumber: String = "",

    @field:NotBlank(message = "Role is required")
    val role: String = "",

    @field:NotBlank(message = "Password is required")
    @field:Size(min = 8, message = "Password must be at least 8 characters")
    val password: String = "",

    @field:NotBlank(message = "Confirm password is required")
    val confirmPassword: String = "",

    val schoolCode: String? = null
)

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

data class LoginResponse(
    val token: String,
    val user: AuthenticatedUserDto?,
    val roles: List<UserSchoolRoleDto> = emptyList(),
    val requiresOtp: Boolean = false,
    val message: String? = null
)

data class AuthenticatedUserDto(
    val id: UUID,
    val firstName: String?,
    val lastName: String?,
    val email: String?,
    val phoneNumber: String?,
    val status: String
)

data class UserSchoolRoleDto(
    val schoolId: UUID?,
    val schoolName: String?,
    val roleName: String,
    val isPrimary: Boolean
)

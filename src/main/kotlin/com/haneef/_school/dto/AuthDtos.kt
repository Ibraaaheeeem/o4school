package com.haneef._school.dto

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
    @field:jakarta.validation.constraints.NotBlank(message = "First name is required")
    val firstName: String,

    @field:jakarta.validation.constraints.NotBlank(message = "Last name is required")
    val lastName: String,

    @field:jakarta.validation.constraints.NotBlank(message = "Email is required")
    @field:jakarta.validation.constraints.Email(message = "Invalid email format")
    val email: String,

    @field:jakarta.validation.constraints.NotBlank(message = "Phone number is required")
    val phoneNumber: String,

    @field:jakarta.validation.constraints.NotBlank(message = "Role is required")
    val role: String,

    @field:jakarta.validation.constraints.NotBlank(message = "Password is required")
    @field:jakarta.validation.constraints.Size(min = 8, message = "Password must be at least 8 characters")
    val password: String,

    @field:jakarta.validation.constraints.NotBlank(message = "Confirm password is required")
    val confirmPassword: String,

    val schoolCode: String? = null
)

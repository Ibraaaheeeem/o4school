package com.haneef.school.data.models

import com.google.gson.annotations.SerializedName

/**
 * User data model
 */
//data class User(
//    @SerializedName("id")
//    val id: String,
//    @SerializedName("full_name")
//    val fullName: String,
//    @SerializedName("email")
//    val email: String,
//    @SerializedName("phone_number")
//    val phoneNumber: String? = null,
//    @SerializedName("role")
//    val role: String,
//    @SerializedName("department")
//    val department: String? = null,
//    @SerializedName("staff_id")
//    val staffId: String? = null,
//    @SerializedName("profile_image_url")
//    val profileImageUrl: String? = null,
//    @SerializedName("is_active")
//    val isActive: Boolean = true,
//    @SerializedName("created_at")
//    val createdAt: String? = null,
//    @SerializedName("updated_at")
//    val updatedAt: String? = null
//)

/**
 * Signup request model
 */
data class SignupRequest(
    @SerializedName("full_name")
    val fullName: String,
    @SerializedName("email")
    val email: String,
    @SerializedName("phone_number")
    val phoneNumber: String,
    @SerializedName("password")
    val password: String,
    @SerializedName("password_confirmation")
    val passwordConfirmation: String,
    @SerializedName("role")
    val role: String = "staff"
)

/**
 * OTP verification request
 */
data class OtpVerificationRequest(
    @SerializedName("email")
    val email: String,
    @SerializedName("otp_code")
    val otpCode: String
)

/**
 * Password reset request
 */
data class PasswordResetRequest(
    @SerializedName("email")
    val email: String
)

/**
 * Update profile request
 */
data class UpdateProfileRequest(
    @SerializedName("full_name")
    val fullName: String,
    @SerializedName("email")
    val email: String,
    @SerializedName("phone_number")
    val phoneNumber: String? = null,
    @SerializedName("department")
    val department: String? = null
)

/**
 * Change password request
 */
data class ChangePasswordRequest(
    @SerializedName("current_password")
    val currentPassword: String,
    @SerializedName("new_password")
    val newPassword: String,
    @SerializedName("new_password_confirmation")
    val newPasswordConfirmation: String
)

/**
 * Model representing a user from the school users list
 */
data class SchoolUser(
    @SerializedName("id")
    val id: String,
    @SerializedName("email")
    val email: String,
    @SerializedName("phone_number")
    val phoneNumber: String?,
    @SerializedName("first_name")
    val firstName: String?,
    @SerializedName("last_name")
    val lastName: String?,
    @SerializedName("status")
    val status: String,
    @SerializedName("is_verified")
    val isVerified: Boolean,
    @SerializedName("is_approved")
    val isApproved: Boolean?,
    @SerializedName("last_login_at")
    val lastLoginAt: String?,
    @SerializedName("created_at")
    val createdAt: String,
    @SerializedName("is_active")
    val isActive: Boolean,
    @SerializedName("role_name")
    val roleName: String
) {
    val fullName: String
        get() = when {
            !firstName.isNullOrBlank() && !lastName.isNullOrBlank() -> "$firstName $lastName"
            !firstName.isNullOrBlank() -> firstName
            !lastName.isNullOrBlank() -> lastName
            else -> email.substringBefore("@")
        }
}
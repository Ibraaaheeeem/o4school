package com.haneef.school.data.models

import com.google.gson.annotations.SerializedName

data class SignUpRequest(
    @SerializedName("email")
    val email: String,
    @SerializedName("password")
    val password: String,
    @SerializedName("first_name")
    val firstName: String,
    @SerializedName("last_name")
    val lastName: String,
    @SerializedName("phone_number")
    val phoneNumber: String? = null,
    @SerializedName("phone_country_code")
    val phoneCountryCode: String? = null,
    @SerializedName("address_line1")
    val addressLine1: String? = null,
    @SerializedName("address_line2")
    val addressLine2: String? = null,
    @SerializedName("city")
    val city: String? = null,
    @SerializedName("state")
    val state: String? = null,
    @SerializedName("country")
    val country: String? = null,
    @SerializedName("role")
    val role: String = "SCHOOL_ADMIN"
)

data class SignUpResponse(
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("email")
    val email: String,
    @SerializedName("role")
    val role: String,
    @SerializedName("school_id")
    val schoolId: String? = null,
    @SerializedName("school_name")
    val schoolName: String? = null,
    @SerializedName("user_school_role_id")
    val userSchoolRoleId: String,
    @SerializedName("message")
    val message: String,
    @SerializedName("next_route")
    val nextRoute: AuthNextRoute
)

//data class LoginRequest(
//    @SerializedName("email")
//    val email: String,
//    @SerializedName("password")
//    val password: String
//)
//
//data class LoginResponse(
//    @SerializedName("success")
//    val success: Boolean,
//    @SerializedName("message")
//    val message: String,
//    @SerializedName("token")
//    val token: String? = null,
//    @SerializedName("user")
//    val user: User? = null
//)
//
//data class User(
//    @SerializedName("id")
//    val id: String,
//    @SerializedName("email")
//    val email: String,
//    @SerializedName("first_name")
//    val firstName: String,
//    @SerializedName("last_name")
//    val lastName: String,
//    @SerializedName("role")
//    val role: String,
//    @SerializedName("phone_number")
//    val phoneNumber: String? = null,
//    @SerializedName("phone_country_code")
//    val phoneCountryCode: String? = null
//)

data class LoginRequest(
    @SerializedName("email")
    val email: String,
    @SerializedName("password")
    val password: String
)

data class LoginResponse(
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("email")
    val email: String,
    @SerializedName("first_name")
    val firstName: String? = null,
    @SerializedName("last_name")
    val lastName: String? = null,
    @SerializedName("access_token")
    val accessToken: String,
    @SerializedName("refresh_token")
    val refreshToken: String? = null,
    @SerializedName("token_type")
    val tokenType: String,
    @SerializedName("expires_in")
    val expiresIn: Long, // seconds
    @SerializedName("message")
    val message: String,
    @SerializedName("next_route")
    val nextRoute: AuthNextRoute,
    @SerializedName("status")
    val status: UserStatus,
    @SerializedName("schools")
    val schools: List<UserSchoolWithRoles>
)

// User status enum
enum class UserStatus(val value: String) {
    @SerializedName("ACTIVE")
    ACTIVE("ACTIVE"),
    
    @SerializedName("PENDING_VERIFICATION")
    PENDING_VERIFICATION("PENDING_VERIFICATION"),
    
    @SerializedName("PENDING_ACTIVATION")
    PENDING_ACTIVATION("PENDING_ACTIVATION"),
    
    @SerializedName("SUSPENDED")
    SUSPENDED("SUSPENDED"),
    
    @SerializedName("INACTIVE")
    INACTIVE("INACTIVE");
    
    companion object {
        fun fromString(value: String): UserStatus {
            return values().find { it.value == value } ?: PENDING_VERIFICATION
        }
    }
}

// User school with roles model
data class UserSchoolWithRoles(
    @SerializedName("id", alternate = ["school_id"])
    val schoolId: String,
    @SerializedName("name", alternate = ["school_name"])
    val schoolName: String,
    @SerializedName("school_code")
    val schoolCode: String? = null,
    @SerializedName("is_primary")
    val isPrimary: Boolean = false,
    @SerializedName("roles")
    val roles: List<SchoolRole>
)

// School role model
data class SchoolRole(
    @SerializedName("id", alternate = ["role_id"])
    val roleId: String,
    @SerializedName("name", alternate = ["role_name"])
    val roleName: String,
    @SerializedName("type", alternate = ["role_type"])
    val roleType: String? = null,
    @SerializedName("permissions")
    val permissions: List<String>? = null,
    @SerializedName("is_active")
    val isActive: Boolean = true
)

// User model for backward compatibility
data class User(
    @SerializedName("id")
    val id: String,
    @SerializedName("email")
    val email: String,
    @SerializedName("first_name")
    val firstName: String,
    @SerializedName("last_name")
    val lastName: String,
    @SerializedName("role")
    val role: String,
    @SerializedName("phone_number")
    val phoneNumber: String? = null,
    @SerializedName("phone_country_code")
    val phoneCountryCode: String? = null
)

data class VerifyOtpRequest(
    @SerializedName("email")
    val email: String,
    @SerializedName("otp_code")
    val otpCode: String,
    @SerializedName("next_route")
    val nextRoute: AuthNextRoute
)

data class ApiError(
    @SerializedName("error")
    val error: String,
    @SerializedName("message")
    val message: String,
    @SerializedName("details")
    val details: List<String>? = null
)

// Validation result
data class ValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null
)

// Auth next route enum to handle navigation after signup
enum class AuthNextRoute(val value: String) {
    @SerializedName("None")
    NONE("None"),
    
    @SerializedName("Activate")
    ACTIVATE("Activate"),
    
    @SerializedName("VerifyOtp")
    VERIFY_OTP("VerifyOtp"),
    
    @SerializedName("VerifyEmail")
    VERIFY_EMAIL("VerifyEmail"),
    
    @SerializedName("ResetPassword")
    RESET_PASSWORD("ResetPassword"),
    
    @SerializedName("SIGN_IN")
    SIGN_IN("SIGN_IN"),
    
    @SerializedName("SIGN_UP")
    SIGN_UP("SIGN_UP"),
    
    @SerializedName("Dashboard")
    DASHBOARD("Dashboard"),
    
    @SerializedName("ProfileComplete")
    PROFILE_COMPLETE("ProfileComplete"),
    
    @SerializedName("SupportContact")
    SUPPORT_CONTACT("SupportContact"),
    
    @SerializedName("SetPassword")
    SET_PASSWORD("SetPassword");
    
    companion object {
        fun fromString(value: String): AuthNextRoute {
            return values().find { it.value == value } ?: NONE
        }
    }
}

// Role enum
enum class UserRole(val value: String) {
    STAFF("STAFF"),
    PARENT("PARENT"),
    ADMIN("ADMIN"),
    SCHOOL_ADMIN("SCHOOL_ADMIN")
}

// Update school data request
data class SchoolData(
    @SerializedName("name")
    val name: String? = null,
    @SerializedName("slug")
    val slug: String? = null,
    @SerializedName("address_line1")
    val addressLine1: String? = null,
    @SerializedName("address_line2")
    val addressLine2: String? = null,
    @SerializedName("admin_email")
    val adminEmail: String? = null,
    @SerializedName("admin_name")
    val adminName: String? = null,
    @SerializedName("admin_phone")
    val adminPhone: String? = null,
    @SerializedName("banner_url")
    val bannerUrl: String? = null,
    @SerializedName("city")
    val city: String? = null,
    @SerializedName("country")
    val country: String? = null,
    @SerializedName("currency")
    val currency: String? = null,
    @SerializedName("language")
    val language: String? = null,
    @SerializedName("logo_url")
    val logoUrl: String? = null,
    @SerializedName("primary_color")
    val primaryColor: String? = null,
    @SerializedName("school_motto")
    val schoolMotto: String? = null,
    @SerializedName("secondary_color")
    val secondaryColor: String? = null,
    @SerializedName("state")
    val state: String? = null,
    @SerializedName("status")
    val status: String? = null,
    @SerializedName("timezone")
    val timezone: String? = null,
    @SerializedName("website")
    val website: String? = null,
    @SerializedName("admission_prefix")
    val admissionPrefix: String? = null,
    @SerializedName("staff_id_prefix")
    val staffIdPrefix: String? = null,
    @SerializedName("postal_code")
    val postalCode: String? = null
)

// Update school data response
data class UpdateSchoolDataResponse(
    @SerializedName("success")
    val success: Boolean,
    @SerializedName("message")
    val message: String,
    @SerializedName("updated")
    val updated: Boolean? = null,
    @SerializedName("data")
    val data: SchoolData? = null
)
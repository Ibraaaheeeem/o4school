package com.haneef.school.models

import com.google.gson.annotations.SerializedName
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class User(
    val id: UUID,
    val email: String,
    @SerializedName("phone_number")
    val phoneNumber: String?,
    @SerializedName("password_hash")
    val passwordHash: String?,
    @SerializedName("first_name")
    val firstName: String?,
    @SerializedName("last_name")
    val lastName: String?,
    @SerializedName("middle_name")
    val middleName: String?,
    @SerializedName("date_of_birth")
    val dateOfBirth: LocalDate?,
    val gender: String?,
    @SerializedName("profile_picture_url")
    val profilePictureUrl: String?,
    @SerializedName("address_line1")
    val addressLine1: String?,
    @SerializedName("address_line2")
    val addressLine2: String?,
    val city: String?,
    val state: String?,
    @SerializedName("postal_code")
    val postalCode: String?,
    val country: String,
    val status: String,
    @SerializedName("is_verified")
    val isVerified: Boolean,
    @SerializedName("is_approved")
    val isApproved: Boolean?,
    @SerializedName("verified_at")
    val verifiedAt: Instant?,
    @SerializedName("approved_at")
    val approvedAt: Instant?,
    @SerializedName("approved_by")
    val approvedBy: UUID?,
    @SerializedName("last_login_at")
    val lastLoginAt: Instant?,
    @SerializedName("otp_code")
    val otpCode: String?,
    @SerializedName("otp_expires")
    val otpExpires: Instant?,
    @SerializedName("last_otp_sent")
    val lastOtpSent: Instant?,
    @SerializedName("created_at")
    val createdAt: Instant,
    @SerializedName("updated_at")
    val updatedAt: Instant,
    @SerializedName("is_active")
    val isActive: Boolean
)


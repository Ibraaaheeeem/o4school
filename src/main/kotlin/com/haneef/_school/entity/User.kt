package com.haneef._school.entity

import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(
    name = "users",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["email"], name = "unique_user_email")
    ]
)
class User(
    @Column(name = "phone_number", nullable = false)
    var phoneNumber: String,
    
    @Column(name = "password_hash")
    var passwordHash: String? = null,
    
    @Column(unique = true)
    var email: String? = null,
    
    @Column(name = "first_name")
    var firstName: String? = null,
    
    @Column(name = "last_name")
    var lastName: String? = null,
    
    @Column(name = "middle_name")
    var middleName: String? = null,
    
    @Column(name = "date_of_birth")
    var dateOfBirth: LocalDate? = null,
    
    var gender: String? = null,
    
    @Column(name = "profile_picture_url")
    var profilePictureUrl: String? = null,
    
    // Address fields
    @Column(name = "address_line1")
    var addressLine1: String? = null,
    
    @Column(name = "address_line2")
    var addressLine2: String? = null,
    
    var city: String? = null,
    var state: String? = null,
    
    @Column(name = "postal_code")
    var postalCode: String? = null,
    
    var country: String = "Nigeria",
    
    // Status fields
    @Enumerated(EnumType.STRING)
    var status: UserStatus = UserStatus.PENDING,
    
    @Column(name = "is_verified")
    var isVerified: Boolean = false,
    
    @Column(name = "verification_status")
    var verificationStatus: String = "unverified",
    
    @Column(name = "approval_status")
    var approvalStatus: String = "pending",
    
    @Column(name = "verified_at")
    var verifiedAt: LocalDateTime? = null,
    
    @Column(name = "approved_at")
    var approvedAt: LocalDateTime? = null,
    
    @Column(name = "approved_by")
    var approvedBy: UUID? = null,
    
    @Column(name = "last_login_at")
    var lastLoginAt: LocalDateTime? = null,
    
    // Email verification
    @Column(name = "email_verified")
    var emailVerified: Boolean = false,
    
    @Column(name = "email_verification_token")
    var emailVerificationToken: String? = null,
    
    @Column(name = "email_verification_expires")
    var emailVerificationExpires: LocalDateTime? = null,
    
    @Column(name = "otp_code")
    var otpCode: String? = null,
    
    @Column(name = "otp_expires")
    var otpExpires: LocalDateTime? = null,
    
    @Column(name = "last_otp_sent")
    var lastOtpSent: LocalDateTime? = null,
    
    @Column(name = "otp_attempts")
    var otpAttempts: Int = 0,
    
    @Column(name = "intended_role")
    var intendedRole: String? = null,
    
    @Column(name = "intended_school_slug")
    var intendedSchoolSlug: String? = null
) : BaseEntity() {
    
    constructor() : this(phoneNumber = "")
    
    val fullName: String?
        get() {
            val names = listOfNotNull(firstName, middleName, lastName).filter { it.isNotBlank() }
            return if (names.isEmpty()) null else names.joinToString(" ")
        }
    
    // Relationships
    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL], fetch = FetchType.EAGER)
    var schoolRoles: MutableSet<UserSchoolRole> = mutableSetOf()

    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL], fetch = FetchType.EAGER)
    var globalRoles: MutableSet<UserGlobalRole> = mutableSetOf()
    
    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL], fetch = FetchType.EAGER)
    var studentProfiles: MutableList<Student> = mutableListOf()
    
    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL], fetch = FetchType.EAGER)
    var parentProfiles: MutableList<Parent> = mutableListOf()
    
    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL], fetch = FetchType.EAGER)
    var staffProfiles: MutableList<Staff> = mutableListOf()
    
    fun getSchools(): List<UUID> {
        return schoolRoles.filter { it.isActive }.mapNotNull { it.schoolId }
    }
}
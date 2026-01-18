package com.haneef._school.service

import com.haneef._school.entity.*
import com.haneef._school.repository.*
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

@Service
class UserService(
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val schoolRepository: SchoolRepository,
    private val userSchoolRoleRepository: UserSchoolRoleRepository,
    private val passwordEncoder: PasswordEncoder,
    private val emailService: EmailService
) {

    @Transactional
    fun requestOtp(email: String, type: String): String {
        val user = userRepository.findByEmail(email).orElseThrow { Exception("User with this email does not exist") }
        
        val otp = (100000..999999).random().toString()
        user.otpCode = otp
        user.otpExpires = LocalDateTime.now().plusMinutes(15)
        userRepository.save(user)
        
        emailService.sendOtpEmail(email, otp)
        return "OTP sent successfully"
    }

    @Transactional
    fun verifyOtp(email: String, otp: String, type: String): User {
        val user = userRepository.findByEmail(email).orElseThrow { Exception("User not found") }
        
        if (user.otpCode != otp) throw Exception("Invalid activation code")
        if (user.otpExpires?.isBefore(LocalDateTime.now()) == true) throw Exception("Activation code has expired")
        
        // We don't clear the OTP here because we need it for the next step (setting password)
        return user
    }

    private fun handleActivationLogic(user: User) {
        if (user.intendedRole == null) {
            user.status = UserStatus.ACTIVE
            user.approvalStatus = "approved"
            return
        }

        when (user.intendedRole) {
            "SCHOOL_ADMIN" -> {
                val schoolAdminRole = roleRepository.findByName("SCHOOL_ADMIN").orElse(null) ?: return
                
                // Check if already has a school role
                if (userSchoolRoleRepository.findByUser(user).isEmpty()) {
                    val schoolName = if (!user.firstName.isNullOrBlank()) "${user.firstName}'s School" else "New School"
                    val schoolSlug = if (!user.firstName.isNullOrBlank()) 
                        "${user.firstName?.lowercase()}-${UUID.randomUUID().toString().take(8)}" 
                        else "school-${UUID.randomUUID().toString().take(8)}"

                    val school = School(
                        name = schoolName,
                        slug = schoolSlug,
                        email = user.email,
                        phone = user.phoneNumber,
                        adminName = user.fullName,
                        adminEmail = user.email,
                        adminPhone = user.phoneNumber,
                        adminUserId = user.id
                    ).apply {
                        this.status = "pending"
                        this.addressLine1 = "Pending Setup"
                        this.city = "Pending"
                        this.state = "Pending"
                        this.postalCode = "000000"
                    }
                    val savedSchool = schoolRepository.save(school)

                    val userSchoolRole = UserSchoolRole(
                        user = user,
                        schoolId = savedSchool.id,
                        role = schoolAdminRole,
                        isPrimary = true
                    ).apply {
                        this.isActive = true
                    }
                    userSchoolRoleRepository.save(userSchoolRole)
                }
            }
            "TEACHER", "PARENT" -> {
                val roleName = user.intendedRole!!
                val role = roleRepository.findByName(roleName).orElse(null) ?: return
                val school = schoolRepository.findBySlug(user.intendedSchoolSlug ?: "").orElse(null) ?: return
                
                if (userSchoolRoleRepository.findByUserAndSchoolId(user, school.id!!).isEmpty()) {
                    val userSchoolRole = UserSchoolRole(
                        user = user,
                        schoolId = school.id,
                        role = role,
                        isPrimary = false
                    ).apply {
                        this.isActive = false
                    }
                    userSchoolRoleRepository.save(userSchoolRole)
                }
                
                user.status = UserStatus.PENDING
                user.approvalStatus = "pending"
            }
            else -> {
                user.status = UserStatus.ACTIVE
                user.approvalStatus = "approved"
            }
        }
    }

    @Transactional
    fun resetPassword(email: String, otp: String, password: String) {
        val user = userRepository.findByEmail(email).orElseThrow { Exception("User not found") }
        
        if (user.otpCode != otp) throw Exception("Invalid or expired session")
        
        user.passwordHash = passwordEncoder.encode(password)
        user.otpCode = null
        user.otpExpires = null
        
        // If this was an activation flow, complete the activation
        if (!user.isVerified) {
            user.emailVerified = true
            user.isVerified = true
            handleActivationLogic(user)
        }
        
        userRepository.save(user)
    }
}

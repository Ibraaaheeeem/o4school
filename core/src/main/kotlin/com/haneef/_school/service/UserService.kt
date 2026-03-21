package com.haneef._school.service

import com.haneef._school.entity.*
import com.haneef._school.exception.BadRequestException
import com.haneef._school.exception.NotFoundException
import com.haneef._school.exception.TooManyRequestsException
import com.haneef._school.repository.*
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.LocalDateTime
import java.util.*
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

@Service
class UserService(
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val schoolRepository: SchoolRepository,
    private val userSchoolRoleRepository: UserSchoolRoleRepository,
    private val passwordEncoder: PasswordEncoder,
    private val emailService: EmailService
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val random = SecureRandom()

    companion object {
        const val OTP_EXPIRY_MINUTES = 15L
        const val OTP_MIN_INTERVAL_SECONDS = 60L
        const val OTP_MAX_ATTEMPTS = 5

        fun slugify(input: String): String {
            return input.lowercase()
                .replace(Regex("[^a-z0-9]+"), "-")
                .trim('-')
        }

        fun hashOtp(otp: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            return digest.digest(otp.toByteArray()).joinToString("") { "%02x".format(it) }
        }
    }

    @Transactional
    fun requestOtp(email: String, type: String): String {
        val userOpt = userRepository.findByEmail(email)
        if (userOpt.isEmpty) {
            // Do not reveal account existence. Log and return success.
            logger.info("OTP requested for non-existing email: {}", email)
            return "OTP sent successfully"
        }

        val user = userOpt.get()

        val now = LocalDateTime.now()
        user.lastOtpSent?.let { last ->
            if (Duration.between(last, now).seconds < OTP_MIN_INTERVAL_SECONDS) {
                throw TooManyRequestsException("OTP requests are too frequent")
            }
        }

        val otp = (random.nextInt(900_000) + 100_000).toString()
        user.otpCode = hashOtp(otp)
        user.otpExpires = now.plusMinutes(OTP_EXPIRY_MINUTES)
        user.lastOtpSent = now
        user.otpAttempts = 0

        userRepository.save(user)

        // Send email after transaction commit when possible to avoid long-running transactions
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() {
                    val (sentOk, info) = emailService.sendOtpEmail(email, otp)
                    if (!sentOk) logger.warn("Failed to send OTP to {}: {}", email, info)
                }
            })
        } else {
            val (sentOk, info) = emailService.sendOtpEmail(email, otp)
            if (!sentOk) logger.warn("Failed to send OTP to {}: {}", email, info)
        }
        return "OTP sent successfully"
    }

    @Transactional
    fun verifyOtp(email: String, otp: String, type: String): User {
        val user = userRepository.findByEmail(email).orElseThrow { NotFoundException("User not found") }

        val now = LocalDateTime.now()

        if (user.otpExpires?.isBefore(now) == true) throw BadRequestException("Activation code has expired")

        if (user.otpAttempts >= OTP_MAX_ATTEMPTS) throw TooManyRequestsException("Too many failed OTP attempts")

        if (user.otpCode != hashOtp(otp)) {
            user.otpAttempts = (user.otpAttempts ?: 0) + 1
            userRepository.save(user)
            throw BadRequestException("Invalid activation code")
        }

        // Keep OTP until password reset step; reset attempt counter
        user.otpAttempts = 0
        userRepository.save(user)
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
                val schoolAdminRole = roleRepository.findByName("SCHOOL_ADMIN").orElse(null) ?: run {
                    logger.warn("SCHOOL_ADMIN role not found while activating user={}", user.email)
                    return
                }

                if (userSchoolRoleRepository.findByUser(user).isEmpty()) {
                    val schoolName = if (!user.firstName.isNullOrBlank()) "${user.firstName}'s School" else "New School"
                    val baseSlug = if (!user.firstName.isNullOrBlank()) slugify(user.firstName!!) else "school"
                    val schoolSlug = "${baseSlug}-${UUID.randomUUID().toString().take(8)}"

                    // Ensure user has an id
                    if (user.id == null) {
                        userRepository.save(user)
                    }

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
                val role = roleRepository.findByName(roleName).orElse(null) ?: run {
                    logger.warn("Role {} not found for user={}", roleName, user.email)
                    return
                }
                val school = schoolRepository.findBySlug(user.intendedSchoolSlug ?: "").orElse(null) ?: run {
                    logger.warn("School {} not found for user={}", user.intendedSchoolSlug, user.email)
                    return
                }

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
        val user = userRepository.findByEmail(email).orElseThrow { NotFoundException("User not found") }

        val now = LocalDateTime.now()
        if (user.otpExpires?.isBefore(now) == true) throw BadRequestException("Invalid or expired session")
        if (user.otpCode != hashOtp(otp)) throw BadRequestException("Invalid or expired session")

        user.passwordHash = passwordEncoder.encode(password)
        user.otpCode = null
        user.otpExpires = null
        user.otpAttempts = 0

        if (!user.isVerified) {
            user.emailVerified = true
            user.isVerified = true
            handleActivationLogic(user)
        }

        userRepository.save(user)
    }
}

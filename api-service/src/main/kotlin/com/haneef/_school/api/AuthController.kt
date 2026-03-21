package com.haneef._school.api

import com.haneef._school.dto.*
import com.haneef._school.entity.*
import com.haneef._school.repository.*
import com.haneef._school.service.*
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime
import java.util.*
import org.slf4j.LoggerFactory

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authenticationManager: AuthenticationManager,
    private val jwtService: JwtService,
    private val multiModeAuthenticationService: MultiModeAuthenticationService,
    private val customUserDetailsService: CustomUserDetailsService,
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val schoolRepository: SchoolRepository,
    private val userSchoolRoleRepository: UserSchoolRoleRepository,
    private val userSchoolRoleService: UserSchoolRoleService,
    private val passwordEncoder: PasswordEncoder,
    private val emailService: EmailService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<LoginResponse> {
        val userDetails = try {
            multiModeAuthenticationService.authenticateUser(request)
        } catch (e: Exception) {
            return ResponseEntity.status(401).body(LoginResponse(token = "", user = null, message = "Authentication failed: ${e.message}"))
        }
        
        // Authenticate with Spring Security
        try {
            authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken(userDetails.username, request.password)
            )
        } catch (e: Exception) {
            return ResponseEntity.status(401).body(LoginResponse(token = "", user = null, message = "Invalid credentials"))
        }

        val customUser = userDetails as CustomUserDetails
        val user = customUser.user

        // If user is not verified, return status and requiresOtp
        if (!user.isVerified) {
             return ResponseEntity.ok(LoginResponse(
                token = "",
                user = user.toDto(),
                requiresOtp = true,
                message = "Account not verified. Please verify your account."
            ))
        }

        val token = jwtService.generateToken(userDetails)
        val roles = userSchoolRoleService.getActiveRolesByUserId(user.id!!)
            .map { it.toDto() }

        return ResponseEntity.ok(LoginResponse(
            token = token,
            user = user.toDto(),
            roles = roles
        ))
    }

    @PostMapping("/register")
    fun register(@Valid @RequestBody request: RegistrationDto): ResponseEntity<Map<String, Any>> {
        if (userRepository.findByEmail(request.email).isPresent) {
            return ResponseEntity.badRequest().body(mapOf("error" to "Email already exists"))
        }

        if (request.password != request.confirmPassword) {
            return ResponseEntity.badRequest().body(mapOf("error" to "Passwords do not match"))
        }

        val role = request.role
        val schoolCode = request.schoolCode

        if ((role == "STAFF" || role == "PARENT") && schoolCode.isNullOrBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to "School code is required"))
        }

        val otp = (100000..999999).random().toString()
        val user = User(
            phoneNumber = request.phoneNumber,
            passwordHash = passwordEncoder.encode(request.password),
            email = request.email,
            firstName = request.firstName,
            lastName = request.lastName
        ).apply {
            this.status = if (role == "SCHOOL_ADMIN") UserStatus.APPROVED else UserStatus.PENDING
            this.approvalStatus = "PENDING"
            this.isVerified = false
            this.emailVerified = false
            this.otpCode = otp
            this.otpExpires = LocalDateTime.now().plusMinutes(15)
            this.intendedRole = role
            this.intendedSchoolSlug = schoolCode
        }
        userRepository.save(user)

        if (role == "SCHOOL_ADMIN") {
            val uniqueId = UUID.randomUUID().toString().substring(0, 8)
            val school = School().apply {
                name = "My School"
                slug = "school-$uniqueId"
                phone = user.phoneNumber
                adminEmail = user.email ?: ""
                adminPhone = user.phoneNumber
                adminName = "${user.firstName ?: ""} ${user.lastName ?: ""}".trim()
            }
            schoolRepository.save(school)
            val adminRole = roleRepository.findByName("SCHOOL_ADMIN")
                .orElseThrow { RuntimeException("Role SCHOOL_ADMIN not found") }

            userSchoolRoleRepository.save(
                UserSchoolRole(
                    user = user,
                    schoolId = school.id,
                    role = adminRole,
                    isPrimary = true
                )
            )
        }

        val (sentOk, info) = emailService.sendOtpEmail(user.email!!, otp)
        if (!sentOk) logger.warn("Failed to send registration OTP to ${user.email}: $info")

        return ResponseEntity.ok(mapOf(
            "message" to "Registration successful. OTP sent to email.",
            "email" to user.email!!
        ))
    }

    @PostMapping("/verify-otp")
    fun verifyOtp(@Valid @RequestBody request: VerifyOtpRequest): ResponseEntity<LoginResponse> {
        val user = userRepository.findByEmailIgnoreCase(request.email)
            ?: return ResponseEntity.badRequest().body(LoginResponse(token = "", user = null, message = "User not found"))

        if (user.otpCode != request.otp || user.otpExpires?.isBefore(LocalDateTime.now()) == true) {
            return ResponseEntity.badRequest().body(LoginResponse(token = "", user = null, message = "Invalid or expired OTP"))
        }

        user.apply {
            isVerified = true
            emailVerified = true
            otpCode = null
            otpExpires = null
        }
        userRepository.save(user)

        val userDetails = customUserDetailsService.createUserDetails(user)
        val token = jwtService.generateToken(userDetails)
        val roles = userSchoolRoleService.getActiveRolesByUserId(user.id!!)
            .map { it.toDto() }

        return ResponseEntity.ok(LoginResponse(
            token = token,
            user = user.toDto(),
            roles = roles,
            message = "Verification successful"
        ))
    }

    @PostMapping("/select-context")
    fun selectContext(
        @RequestBody request: Map<String, String>,
        authentication: org.springframework.security.core.Authentication
    ): ResponseEntity<LoginResponse> {
        val schoolIdStr = request["schoolId"]
        val schoolId = if (!schoolIdStr.isNullOrBlank()) UUID.fromString(schoolIdStr) else null
        val roleName = request["role"]
        
        val customUser = authentication.principal as CustomUserDetails
        val user = customUser.user
        
        // Verify selection is valid for user
        val roles = userSchoolRoleService.getActiveRolesByUserId(user.id!!)
        val selectedRole = roles.find { 
            (schoolId == null || it.schoolId == schoolId) && it.role.name == roleName 
        } ?: return ResponseEntity.badRequest().body(LoginResponse(token = "", user = null, message = "Invalid context selection"))

        // Generate a new token with selected context claims
        val extraClaims = mutableMapOf<String, Any>(
            "role" to selectedRole.role.name
        )
        if (schoolId != null) extraClaims["schoolId"] = schoolId.toString()
        
        val token = jwtService.generateToken(extraClaims, customUser)

        return ResponseEntity.ok(LoginResponse(
            token = token,
            user = user.toDto(),
            roles = roles.map { it.toDto() },
            message = "Context selected successfully"
        ))
    }

    @PostMapping("/logout")
    fun logout(): ResponseEntity<Map<String, String>> {
        return ResponseEntity.ok(mapOf("message" to "Logged out successfully"))
    }

    private fun User.toDto() = AuthenticatedUserDto(
        id = this.id!!,
        firstName = this.firstName,
        lastName = this.lastName,
        email = this.email,
        phoneNumber = this.phoneNumber,
        status = this.status.name
    )

    private fun UserSchoolRole.toDto() = UserSchoolRoleDto(
        schoolId = this.schoolId,
        schoolName = null, // We could fetch school name if needed
        roleName = this.role.name,
        isPrimary = this.isPrimary
    )
}

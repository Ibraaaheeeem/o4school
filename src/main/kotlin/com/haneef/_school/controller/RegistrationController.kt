package com.haneef._school.controller

import com.haneef._school.entity.*
import com.haneef._school.repository.RoleRepository
import com.haneef._school.repository.SchoolRepository
import com.haneef._school.repository.UserRepository
import com.haneef._school.repository.UserSchoolRoleRepository
import com.haneef._school.service.EmailService
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.time.LocalDateTime
import java.util.UUID

@Controller
class RegistrationController(
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val schoolRepository: SchoolRepository,
    private val userSchoolRoleRepository: UserSchoolRoleRepository,
    private val passwordEncoder: PasswordEncoder,
    private val emailService: EmailService,
    private val rateLimitingService: com.haneef._school.service.RateLimitingService
) {

    @GetMapping("/auth/register")
    fun showRegistrationForm(model: Model): String {
        return "auth/register"
    }

    @PostMapping("/auth/register")
    fun processRegistration(
        @jakarta.validation.Valid @ModelAttribute registrationDto: com.haneef._school.dto.RegistrationDto,
        bindingResult: org.springframework.validation.BindingResult,
        redirectAttributes: RedirectAttributes,
        request: jakarta.servlet.http.HttpServletRequest,
        model: Model
    ): String {
        // Rate Limiting
        val bucket = rateLimitingService.resolveRegistrationBucket(request.remoteAddr)
        if (!bucket.tryConsume(1)) {
            val waitTime = rateLimitingService.getFormattedWaitTime(bucket)
            model.addAttribute("error", "Too many registration attempts. Please try again in $waitTime.")
            return "auth/register"
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("error", bindingResult.allErrors.first().defaultMessage)
            return "auth/register"
        }

        val firstName = registrationDto.firstName
        val lastName = registrationDto.lastName
        val email = registrationDto.email
        val phoneNumber = registrationDto.phoneNumber
        val role = registrationDto.role
        val password = registrationDto.password
        val confirmPassword = registrationDto.confirmPassword
        val schoolCode = registrationDto.schoolCode
        if (password != confirmPassword) {
            redirectAttributes.addFlashAttribute("error", "Passwords do not match")
            return "redirect:/auth/register"
        }

        // Validate school code for Staff and Parents
        if ((role == "STAFF" || role == "PARENT") && schoolCode.isNullOrBlank()) {
            redirectAttributes.addFlashAttribute("error", "School code is required for Staff and Parent registration.")
            return "redirect:/auth/register"
        }

        if (!schoolCode.isNullOrBlank() && !schoolRepository.existsBySlug(schoolCode)) {
            redirectAttributes.addFlashAttribute("error", "Invalid school code. Please check with your school administrator.")
            return "redirect:/auth/register"
        }

        if (userRepository.findByEmail(email).isPresent) {
            redirectAttributes.addFlashAttribute("error", "A user with this email address already exists.")
            return "redirect:/auth/register"
        }
        
        if (userRepository.existsByPhoneNumber(phoneNumber)) {
            redirectAttributes.addFlashAttribute("error", "A user with this phone number already exists.")
            return "redirect:/auth/register"
        }

        val otp = (100000..999999).random().toString()

        try {
            val user = User(
                phoneNumber = phoneNumber,
                passwordHash = passwordEncoder.encode(password),
                email = email,
                firstName = firstName,
                lastName = lastName
            ).apply {
                this.status = UserStatus.PENDING
                this.approvalStatus = "pending"
                this.isVerified = false
                this.emailVerified = false
                this.otpCode = otp
                this.otpExpires = LocalDateTime.now().plusMinutes(15)
                this.intendedRole = role
                this.intendedSchoolSlug = schoolCode
            }
            userRepository.save(user)
        } catch (e: Exception) {
            redirectAttributes.addFlashAttribute("error", handleDatabaseError(e, "Error during registration"))
            return "redirect:/auth/register"
        }

        // Ensure role exists in DB
        val roleName = if (role == "SCHOOL_ADMIN") "SCHOOL_ADMIN" else role
        val roleType = when (roleName) {
            "SCHOOL_ADMIN" -> RoleType.SCHOOL_ADMIN
            "STUDENT" -> RoleType.STUDENT
            "PARENT" -> RoleType.PARENT
            "STAFF" -> RoleType.STAFF
            else -> RoleType.STAFF
        }
        
        roleRepository.findByName(roleName).orElseGet {
            roleRepository.save(Role(roleName, roleType, roleName))
        }

        // Send OTP
        emailService.sendOtpEmail(email, otp)

        redirectAttributes.addFlashAttribute("message", "Registration successful! A 6-digit activation code has been sent to your email. Please enter it below to verify your account.")
        return "redirect:/activate-account?email=$email&type=VERIFY"
    }

    private fun handleDatabaseError(e: Exception, defaultMessage: String): String {
        val message = e.message ?: return defaultMessage
        return when {
            message.contains("unique_user_email", ignoreCase = true) -> 
                "A user with this email address already exists."
            message.contains("unique", ignoreCase = true) && message.contains("phone", ignoreCase = true) -> 
                "A user with this phone number already exists."
            else -> "$defaultMessage: ${e.localizedMessage}"
        }
    }
}

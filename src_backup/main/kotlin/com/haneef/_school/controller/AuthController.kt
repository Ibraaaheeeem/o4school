package com.haneef._school.controller

import com.haneef._school.dto.LoginRequest
import com.haneef._school.dto.LoginMethod
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*

import org.springframework.aot.hint.annotation.RegisterReflectionForBinding

@Controller
@RequestMapping("/auth")
@RegisterReflectionForBinding(LoginRequest::class)
class AuthController(
    private val userRepository: com.haneef._school.repository.UserRepository,
    private val emailService: com.haneef._school.service.EmailService,
    private val passwordEncoder: org.springframework.security.crypto.password.PasswordEncoder,
    private val rateLimitingService: com.haneef._school.service.RateLimitingService
) {
    
    @GetMapping("/login")
    fun showLoginForm(
        @RequestParam(required = false) error: String?,
        @RequestParam(required = false) logout: String?,
        model: Model
    ): String {
        model.addAttribute("loginRequest", LoginRequest(
            loginMethod = LoginMethod.EMAIL,
            identifier = "",
            password = ""
        ))
        
        if (error != null) {
            model.addAttribute("error", "Invalid credentials or login method")
        }
        
        if (logout != null) {
            model.addAttribute("message", "You have been logged out successfully")
        }
        
        return "auth/login"
    }
    @GetMapping("/activate-account")
    fun showActivationPage(
        @RequestParam(required = false) email: String?, 
        @RequestParam(required = false, defaultValue = "ACTIVATE") type: String,
        model: Model
    ): String {
        if (email != null) {
            model.addAttribute("email", email)
        }
        model.addAttribute("type", type)
        return "auth/activate-account"
    }

    @GetMapping("/forgot-password")
    fun showForgotPasswordPage(): String {
        return "auth/forgot-password"
    }

    @PostMapping("/request-otp")
    fun requestOtp(
        @RequestParam email: String, 
        @RequestParam(defaultValue = "ACTIVATE") type: String,
        model: Model,
        request: jakarta.servlet.http.HttpServletRequest
    ): String {
        val bucket = rateLimitingService.resolveLoginBucket(request.remoteAddr)
        if (!bucket.tryConsume(1)) {
            val waitTime = rateLimitingService.getFormattedWaitTime(bucket)
            model.addAttribute("error", "Too many requests. Please try again in $waitTime.")
            model.addAttribute("email", email)
            model.addAttribute("type", type)
            return "auth/fragments/auth-steps :: email-form"
        }
        val userOptional = userRepository.findByEmail(email)
        
        if (userOptional.isEmpty) {
             model.addAttribute("error", "Email address not found.")
             model.addAttribute("email", email)
             model.addAttribute("type", type)
             return "auth/fragments/auth-steps :: email-form"
        }

        val user = userOptional.get()
        val otp = (100000..999999).random().toString()
        
        user.otpCode = otp
        user.otpExpires = java.time.LocalDateTime.now().plusMinutes(15)
        userRepository.save(user)
        
        emailService.sendOtpEmail(email, otp)
        
        model.addAttribute("email", email)
        model.addAttribute("type", type)
        return "auth/fragments/auth-steps :: otp-form" 
    }
    
    @PostMapping("/verify-otp")
    fun verifyOtp(
        @RequestParam email: String, 
        @RequestParam otp: String, 
        @RequestParam type: String,
        model: Model,
        request: jakarta.servlet.http.HttpServletRequest
    ): String {
        // Rate limit OTP verification to prevent brute force
        val bucket = rateLimitingService.resolveLoginBucket(request.remoteAddr)
        if (!bucket.tryConsume(1)) {
            val waitTime = rateLimitingService.getFormattedWaitTime(bucket)
            model.addAttribute("error", "Too many attempts. Please try again in $waitTime.")
            model.addAttribute("email", email)
            model.addAttribute("type", type)
            return "auth/fragments/auth-steps :: otp-form"
        }
        val userOptional = userRepository.findByEmail(email)
        
        if (userOptional.isPresent) {
            val user = userOptional.get()
            
            if (user.otpCode == otp && user.otpExpires != null && user.otpExpires!!.isAfter(java.time.LocalDateTime.now())) {
                // OTP Valid
                
                if (type == "VERIFY") {
                     // Just activate the user
                     user.isVerified = true
                     user.emailVerified = true
                     user.status = com.haneef._school.entity.UserStatus.ACTIVE
                     user.otpCode = null
                     user.otpExpires = null
                     userRepository.save(user)
                     
                     model.addAttribute("success", "Account verified successfully! You can now login.")
                     return "auth/fragments/auth-steps :: success-message"
                }

                // Redirect to password reset form for BOTH Activate and Reset flows
                model.addAttribute("email", email)
                model.addAttribute("otp", otp)
                return "auth/fragments/auth-steps :: password-reset-form"
            }
        }
        
        model.addAttribute("error", "Invalid or expired OTP")
        model.addAttribute("email", email)
        model.addAttribute("type", type)
        return "auth/fragments/auth-steps :: otp-form"
    }

    @PostMapping("/reset-password")
    fun resetPassword(
        @RequestParam email: String,
        @RequestParam otp: String,
        @RequestParam password: String,
        @RequestParam confirmPassword: String,
        model: Model
    ): String {
        if (password != confirmPassword) {
            model.addAttribute("error", "Passwords do not match")
            model.addAttribute("email", email)
            model.addAttribute("otp", otp)
            return "auth/fragments/auth-steps :: password-reset-form"
        }

        val userOptional = userRepository.findByEmail(email)
        
        if (userOptional.isPresent) {
            val user = userOptional.get()
            
            // Verify OTP again to ensure security
            if (user.otpCode == otp && user.otpExpires != null && user.otpExpires!!.isAfter(java.time.LocalDateTime.now())) {
                user.passwordHash = passwordEncoder.encode(password)
                
                // Activate user if not already
                if (!user.isVerified) {
                    user.isVerified = true
                    user.emailVerified = true
                    user.status = com.haneef._school.entity.UserStatus.ACTIVE
                }
                
                user.otpCode = null
                user.otpExpires = null
                userRepository.save(user)
                
                model.addAttribute("success", "Password set successfully! You can now login.")
                return "auth/fragments/auth-steps :: success-message"
            }
        }
        
        model.addAttribute("error", "Invalid request or expired session")
        model.addAttribute("email", email)
        model.addAttribute("otp", otp)
        return "auth/fragments/auth-steps :: password-reset-form"
    }
}

@Controller
class LoginRedirectController {
    
    @GetMapping("/login")
    fun redirectToAuthLogin(): String {
        return "redirect:/auth/login"
    }

    @GetMapping("/activate-account")
    fun redirectToAuthActivateAccount(
        @RequestParam(required = false) email: String?,
        @RequestParam(required = false) type: String?
    ): String {
        val params = mutableListOf<String>()
        if (email != null) params.add("email=$email")
        if (type != null) params.add("type=$type")
        
        val queryString = if (params.isNotEmpty()) "?" + params.joinToString("&") else ""
        return "redirect:/auth/activate-account$queryString"
    }

    @GetMapping("/forgot-password")
    fun redirectToAuthForgotPassword(): String {
        return "redirect:/auth/forgot-password"
    }

    @GetMapping("/register")
    fun redirectToAuthRegister(): String {
        return "redirect:/auth/register"
    }
}
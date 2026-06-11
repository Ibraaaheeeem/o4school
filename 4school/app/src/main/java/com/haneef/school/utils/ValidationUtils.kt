package com.haneef.school.utils

import com.haneef.school.data.models.ValidationResult
import com.haneef.school.ui.screens.auth.AuthScreenState
import java.util.regex.Pattern

object ValidationUtils {
    
    private val EMAIL_PATTERN = Pattern.compile(
        "[a-zA-Z0-9\\+\\.\\_\\%\\-\\+]{1,256}" +
                "\\@" +
                "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,64}" +
                "(" +
                "\\." +
                "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,25}" +
                ")+"
    )
    
    private val PHONE_PATTERN = Pattern.compile("^[0-9]{7,15}$")
    
    fun validateEmail(email: String): ValidationResult {
        return when {
            email.isBlank() -> ValidationResult(false, "Email is required")
            !EMAIL_PATTERN.matcher(email).matches() -> ValidationResult(false, "Please enter a valid email address")
            else -> ValidationResult(true)
        }
    }
    
    fun validatePassword(password: String): ValidationResult {
        return when {
            password.isBlank() -> ValidationResult(false, "Password is required")
            password.length < 8 -> ValidationResult(false, "Password must be at least 8 characters long")
            !password.any { it.isUpperCase() } -> ValidationResult(false, "Password must contain at least one uppercase letter")
            !password.any { it.isLowerCase() } -> ValidationResult(false, "Password must contain at least one lowercase letter")
            !password.any { it.isDigit() } -> ValidationResult(false, "Password must contain at least one number")
            else -> ValidationResult(true)
        }
    }
    
    fun validatePasswordMatch(password: String, confirmPassword: String): ValidationResult {
        return when {
            confirmPassword.isBlank() -> ValidationResult(false, "Please confirm your password")
            password != confirmPassword -> ValidationResult(false, "Passwords do not match")
            else -> ValidationResult(true)
        }
    }
    
    fun validateFullName(fullName: String): ValidationResult {
        return when {
            fullName.isBlank() -> ValidationResult(false, "Full name is required")
            fullName.trim().split(" ").size < 2 -> ValidationResult(false, "Please enter both first and last name")
            fullName.length < 2 -> ValidationResult(false, "Name must be at least 2 characters long")
            else -> ValidationResult(true)
        }
    }
    
    fun validatePhoneNumber(phoneNumber: String): ValidationResult {
        return when {
            phoneNumber.isBlank() -> ValidationResult(true) // Phone is optional
            !PHONE_PATTERN.matcher(phoneNumber.replace("\\s".toRegex(), "")).matches() -> 
                ValidationResult(false, "Please enter a valid phone number")
            else -> ValidationResult(true)
        }
    }
    
    fun validateSignupForm(state: AuthScreenState): ValidationResult {
        // Validate email
        val emailValidation = validateEmail(state.email)
        if (!emailValidation.isValid) return emailValidation
        
        // Validate full name
        val nameValidation = validateFullName(state.fullName)
        if (!nameValidation.isValid) return nameValidation
        
        // Validate password
        val passwordValidation = validatePassword(state.password)
        if (!passwordValidation.isValid) return passwordValidation
        
        // Validate password match
        val passwordMatchValidation = validatePasswordMatch(state.password, state.confirmPassword)
        if (!passwordMatchValidation.isValid) return passwordMatchValidation
        
        // Validate phone number (optional)
        if (state.phoneNumber.isNotBlank()) {
            val phoneValidation = validatePhoneNumber(state.phoneNumber)
            if (!phoneValidation.isValid) return phoneValidation
        }
        
        return ValidationResult(true)
    }
    
    fun parseFullName(fullName: String): Pair<String, String> {
        val parts = fullName.trim().split(" ")
        return if (parts.size >= 2) {
            val firstName = parts.first()
            val lastName = parts.drop(1).joinToString(" ")
            Pair(firstName, lastName)
        } else {
            Pair(fullName.trim(), "")
        }
    }
    
    fun validateOtp(otp: String): ValidationResult {
        return when {
            otp.isBlank() -> ValidationResult(false, "Verification code is required")
            otp.length != 6 -> ValidationResult(false, "Verification code must be 6 digits")
            !otp.all { it.isDigit() } -> ValidationResult(false, "Verification code must contain only numbers")
            else -> ValidationResult(true)
        }
    }
}
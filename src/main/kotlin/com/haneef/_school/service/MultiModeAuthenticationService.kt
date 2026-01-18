package com.haneef._school.service

import com.haneef._school.dto.LoginMethod
import com.haneef._school.dto.LoginRequest
import com.haneef._school.repository.UserRepository
import com.haneef._school.repository.StudentRepository
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class MultiModeAuthenticationService(
    private val userRepository: UserRepository,
    private val studentRepository: StudentRepository,
    private val phoneNumberService: PhoneNumberService,
    private val customUserDetailsService: CustomUserDetailsService
) {
    
    fun authenticateUser(loginRequest: LoginRequest): UserDetails {
        val normalizedIdentifier = when (loginRequest.loginMethod) {
            LoginMethod.EMAIL -> {
                validateEmail(loginRequest.identifier)
                loginRequest.identifier.lowercase().trim()
            }
            
            LoginMethod.PHONE -> {
                val countryCode = loginRequest.countryCode 
                    ?: throw IllegalArgumentException("Country code is required for phone login")
                
                val formattedPhone = phoneNumberService.parseAndFormatPhoneNumber(
                    loginRequest.identifier, 
                    countryCode
                ) ?: throw IllegalArgumentException("Invalid phone number format")
                
                formattedPhone
            }
            
            LoginMethod.STUDENT -> {
                validateAdmissionNumber(loginRequest.identifier)
                loginRequest.identifier.uppercase().trim()
            }
        }
        
        return loadUserByIdentifier(normalizedIdentifier, loginRequest.loginMethod)
    }
    
    private fun loadUserByIdentifier(identifier: String, loginMethod: LoginMethod): UserDetails {
        return when (loginMethod) {
            LoginMethod.EMAIL -> {
                val user = userRepository.findByEmailIgnoreCase(identifier)
                    ?: throw UsernameNotFoundException("User not found with email: $identifier")
                customUserDetailsService.createUserDetails(user)
            }
            
            LoginMethod.PHONE -> {
                val user = userRepository.findByPhoneNumberForAuth(identifier)
                    ?: throw UsernameNotFoundException("User not found with phone: $identifier")
                customUserDetailsService.createUserDetails(user)
            }
            
            LoginMethod.STUDENT -> {
                val student = studentRepository.findByAdmissionNumber(identifier)
                    ?: throw UsernameNotFoundException("Student not found with admission number: $identifier")
                
                // Create UserDetails for student
                customUserDetailsService.createStudentUserDetails(student)
            }
        }
    }
    
    private fun validateEmail(email: String) {
        val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".toRegex()
        if (!email.matches(emailRegex)) {
            throw IllegalArgumentException("Invalid email format")
        }
    }
    
    private fun validateAdmissionNumber(admissionNumber: String) {
        if (admissionNumber.isBlank() || admissionNumber.length < 3) {
            throw IllegalArgumentException("Invalid admission number format")
        }
    }
}
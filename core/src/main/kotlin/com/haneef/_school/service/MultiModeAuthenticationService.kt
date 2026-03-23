package com.haneef._school.service

import com.haneef._school.dto.LoginMethod
import com.haneef._school.dto.LoginRequest
import com.haneef._school.repository.UserRepository
import com.haneef._school.repository.StudentRepository
import org.slf4j.LoggerFactory
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class MultiModeAuthenticationService(
    private val userRepository: UserRepository,
    private val studentRepository: StudentRepository,
    private val phoneNumberService: PhoneNumberService,
    private val customUserDetailsService: CustomUserDetailsService
) {

    private val logger = LoggerFactory.getLogger(MultiModeAuthenticationService::class.java)
    
    fun authenticateUser(loginRequest: LoginRequest): UserDetails {
        val normalizedIdentifier = when (loginRequest.loginMethod) {
            LoginMethod.EMAIL -> {
                val normalized = loginRequest.identifier.trim().lowercase()
                validateEmail(normalized)
                normalized
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
                val normalized = loginRequest.identifier.trim().uppercase()
                validateAdmissionNumber(normalized)
                normalized
            }
        }
        
        return loadUserByIdentifier(normalizedIdentifier, loginRequest.loginMethod)
    }
    
    private fun loadUserByIdentifier(identifier: String, loginMethod: LoginMethod): UserDetails {
        return when (loginMethod) {
            LoginMethod.EMAIL -> {
                val user = userRepository.findByEmailIgnoreCase(identifier)
                    ?: throw UsernameNotFoundException("Invalid credentials")

                if (!user.isActive) {
                    throw UsernameNotFoundException("Invalid credentials")
                }

                customUserDetailsService.createUserDetails(user)
            }
            
            LoginMethod.PHONE -> {
                val user = userRepository.findByPhoneNumberForAuth(identifier)
                    ?: throw UsernameNotFoundException("Invalid credentials")

                if (!user.isActive) {
                    throw UsernameNotFoundException("Invalid credentials")
                }

                customUserDetailsService.createUserDetails(user)
            }
            
            LoginMethod.STUDENT -> {
                val students = studentRepository.findByAdmissionNumber(identifier)
                if (students.isEmpty()) {
                    throw UsernameNotFoundException("Invalid credentials")
                }

                if (students.size > 1) {
                    logger.warn("Multiple students matched admission identifier; using prioritized first match")
                }
                
                // Create UserDetails for the first match (prioritized by isActive and createdAt in repo)
                customUserDetailsService.createStudentUserDetails(students.first())
            }
        }
    }
    
    private fun validateEmail(email: String) {
        if (!EMAIL_REGEX.matches(email)) {
            throw IllegalArgumentException("Invalid email format")
        }
    }
    
    private fun validateAdmissionNumber(admissionNumber: String) {
        if (admissionNumber.isBlank() || admissionNumber.length < 3) {
            throw IllegalArgumentException("Invalid admission number format")
        }
    }

    companion object {
        private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    }
}
package com.haneef._school.service

import com.haneef._school.entity.User
import com.haneef._school.entity.Student
import com.haneef._school.entity.UserStatus
import com.haneef._school.entity.RoleType
import com.haneef._school.repository.UserRepository
import com.haneef._school.repository.StudentRepository
import com.haneef._school.service.PhoneNumberService
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.slf4j.LoggerFactory

@Service
class CustomUserDetailsService(
    private val userRepository: UserRepository,
    private val studentRepository: StudentRepository,
    private val phoneNumberService: PhoneNumberService
) : UserDetailsService {

    private val logger = LoggerFactory.getLogger(CustomUserDetailsService::class.java)

    @Transactional(readOnly = true)
    override fun loadUserByUsername(combinedUsername: String): UserDetails {
        logger.info("=== LOADING USER BY USERNAME: $combinedUsername ===")
        
        // Parse the combined username format: "METHOD:identifier" or "PHONE:countryCode:phoneNumber"
        val parts = combinedUsername.split(":")
        
        if (parts.size < 2) {
            logger.debug("Using fallback logic for backward compatibility")
            // Fallback to original logic for backward compatibility
            val user = userRepository.findByEmailWithRoles(combinedUsername)
                .or { userRepository.findByPhoneNumberWithRoles(combinedUsername) }
                .orElseThrow { UsernameNotFoundException("User not found with email or phone: $combinedUsername") }
            
            logger.info("User loaded via fallback: ${user.email} with ${user.schoolRoles.size} school roles and ${user.globalRoles.size} global roles")
            return CustomUserDetails(user)
        }
        
        val method = parts[0]
        val identifier = when (method) {
            "EMAIL" -> parts[1].lowercase().trim()
            "PHONE" -> {
                if (parts.size < 3) throw UsernameNotFoundException("Invalid phone format")
                val countryCode = parts[1]
                val phoneNumber = parts[2]
                phoneNumberService.parseAndFormatPhoneNumber(phoneNumber, countryCode)
                    ?: throw UsernameNotFoundException("Invalid phone number format")
            }
            "STUDENT" -> parts[1].uppercase().trim()
            else -> throw UsernameNotFoundException("Invalid login method: $method")
        }
        
        logger.debug("Parsed method: $method, identifier: $identifier")
        
        return when (method) {
            "EMAIL" -> {
                logger.debug("Loading user by email with roles: $identifier")
                val user = userRepository.findByEmailWithRoles(identifier)
                    .orElseThrow { UsernameNotFoundException("User not found with email: $identifier") }
                logger.info("User loaded by email: ${user.email} with ${user.schoolRoles.size} school roles and ${user.globalRoles.size} global roles")
                CustomUserDetails(user)
            }
            "PHONE" -> {
                logger.debug("Loading user by phone with roles: $identifier")
                val user = userRepository.findByPhoneNumberWithRoles(identifier)
                    .orElseThrow { UsernameNotFoundException("User not found with phone: $identifier") }
                logger.info("User loaded by phone: ${user.phoneNumber} with ${user.schoolRoles.size} school roles and ${user.globalRoles.size} global roles")
                CustomUserDetails(user)
            }
            "STUDENT" -> {
                logger.debug("Loading student by admission number: $identifier")
                val student = studentRepository.findByAdmissionNumber(identifier)
                    ?: throw UsernameNotFoundException("Student not found with admission number: $identifier")
                // Load the student's user with roles
                val userWithRoles = userRepository.findByEmailWithRoles(student.user.email ?: "")
                    .or { userRepository.findByPhoneNumberWithRoles(student.user.phoneNumber) }
                    .orElseThrow { UsernameNotFoundException("User roles not found for student: $identifier") }
                logger.info("Student user loaded: ${userWithRoles.email} with ${userWithRoles.schoolRoles.size} school roles and ${userWithRoles.globalRoles.size} global roles")
                CustomUserDetails(userWithRoles)
            }
            else -> throw UsernameNotFoundException("Invalid login method: $method")
        }
    }
    
    fun createUserDetails(user: User): UserDetails {
        return CustomUserDetails(user)
    }
    
    fun createStudentUserDetails(student: Student): UserDetails {
        // Create a UserDetails for student login using their user account
        return CustomUserDetails(student.user)
    }
}


package com.haneef._school.service

import com.haneef._school.entity.User
import com.haneef._school.entity.UserStatus
import java.util.UUID
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import org.slf4j.LoggerFactory

class CustomUserDetails(
    val user: User,
    val forcedSchoolId: UUID? = null
) : UserDetails {

    companion object {
        private val logger = LoggerFactory.getLogger(CustomUserDetails::class.java)
    }

    override fun getAuthorities(): Collection<GrantedAuthority> {
        val authorityNames = linkedSetOf("ROLE_USER")

        // Add a default user role
        // Add global roles
        try {
            user.globalRoles?.forEach { globalRole ->
                if (globalRole.isActive) {
                    val roleName = when (globalRole.role?.name) {
                        "SYSTEM_ADMIN" -> "SYSTEM_ADMIN"
                        "ADMIN" -> "ADMIN"
                        else -> globalRole.role?.name?.uppercase()?.replace(" ", "_") ?: "USER"
                    }
                    authorityNames.add("ROLE_$roleName")
                }
            }
        } catch (e: Exception) {
            logger.error("Error loading global roles: ${e.message}", e)
        }
        
        // Add roles based on user's school roles
        try {
            user.schoolRoles?.forEach { schoolRole ->
                if (forcedSchoolId != null && schoolRole.schoolId != forcedSchoolId) return@forEach
                if (schoolRole.isActive) {
                    val roleName = when (schoolRole.role?.name) {
                        // New role names (already in correct format)
                        "SYSTEM_ADMIN" -> "SYSTEM_ADMIN"
                        "SCHOOL_ADMIN" -> "SCHOOL_ADMIN"
                        "ADMIN" -> "ADMIN"
                        "PRINCIPAL" -> "PRINCIPAL"
                        "STAFF" -> "STAFF"
                        "TEACHER" -> "TEACHER"
                        "PARENT" -> "PARENT"
                        "STUDENT" -> "STUDENT"
                        
                        // Legacy role names for backward compatibility
                        "System Administrator" -> "SYSTEM_ADMIN"
                        "Principal" -> "PRINCIPAL"
                        "School Admin" -> "SCHOOL_ADMIN"
                        "Admin" -> "ADMIN"
                        "Teacher" -> "TEACHER"
                        "Parent" -> "PARENT"
                        "Student" -> "STUDENT"
                        "Staff" -> "STAFF"
                        "Finance Manager" -> "STAFF"
                        "Librarian" -> "STAFF"
                        "Counselor" -> "STAFF"
                        
                        else -> "USER"
                    }
                    authorityNames.add("ROLE_$roleName")
                }
            }
        } catch (e: Exception) {
            logger.error("Error loading school roles: ${e.message}", e)
        }

        logger.debug("Resolved {} authorities for userId={}", authorityNames.size, user.id)
        return authorityNames.map(::SimpleGrantedAuthority)
    }

    override fun getPassword(): String = user.passwordHash ?: ""

    override fun getUsername(): String = user.email ?: user.phoneNumber ?: user.id?.toString() ?: ""

    override fun isAccountNonExpired(): Boolean = true

    override fun isAccountNonLocked(): Boolean = user.status != UserStatus.SUSPENDED

    override fun isCredentialsNonExpired(): Boolean = true

    override fun isEnabled(): Boolean = user.status == UserStatus.ACTIVE

    fun getUserId(): UUID? = user.id
    
    fun getFullName(): String = user.fullName ?: "User"
    
    fun hasRole(roleName: String): Boolean {
        return authorities.any { it.authority == "ROLE_${roleName.trim().uppercase()}" }
    }
}
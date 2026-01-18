package com.haneef._school.service

import com.haneef._school.entity.User
import com.haneef._school.entity.UserStatus
import java.util.UUID
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import org.slf4j.LoggerFactory

class CustomUserDetails(
    val user: User
) : UserDetails {

    private val logger = LoggerFactory.getLogger(CustomUserDetails::class.java)

    override fun getAuthorities(): Collection<GrantedAuthority> {
        val authorities = mutableListOf<GrantedAuthority>()
        
        logger.info("=== GETTING AUTHORITIES FOR USER: ${user.email} (ID: ${user.id}) ===")
        
        // Add a default user role
        authorities.add(SimpleGrantedAuthority("ROLE_USER"))
        logger.debug("Added default ROLE_USER")

        // Add global roles
        try {
            logger.debug("Checking global roles: ${user.globalRoles?.size ?: 0} found")
            user.globalRoles?.forEach { globalRole ->
                logger.debug("Global role: ${globalRole.role?.name}, active: ${globalRole.isActive}")
                if (globalRole.isActive) {
                    val roleName = when (globalRole.role?.name) {
                        "SYSTEM_ADMIN" -> "SYSTEM_ADMIN"
                        "ADMIN" -> "ADMIN"
                        else -> globalRole.role?.name?.uppercase()?.replace(" ", "_") ?: "USER"
                    }
                    authorities.add(SimpleGrantedAuthority("ROLE_$roleName"))
                    logger.info("Added global authority: ROLE_$roleName")
                }
            }
        } catch (e: Exception) {
            logger.error("Error loading global roles: ${e.message}", e)
        }
        
        // Add roles based on user's school roles
        try {
            logger.debug("Checking school roles: ${user.schoolRoles?.size ?: 0} found")
            user.schoolRoles?.forEach { schoolRole ->
                logger.debug("School role: ${schoolRole.role?.name}, active: ${schoolRole.isActive}, schoolId: ${schoolRole.schoolId}")
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
                    authorities.add(SimpleGrantedAuthority("ROLE_$roleName"))
                    logger.info("Added school authority: ROLE_$roleName for schoolId: ${schoolRole.schoolId}")
                }
            }
        } catch (e: Exception) {
            logger.error("Error loading school roles: ${e.message}", e)
        }
        
        logger.info("=== FINAL AUTHORITIES FOR ${user.email}: ${authorities.map { it.authority }} ===")
        return authorities
    }

    override fun getPassword(): String = user.passwordHash ?: ""

    override fun getUsername(): String = user.email ?: user.phoneNumber

    override fun isAccountNonExpired(): Boolean = true

    override fun isAccountNonLocked(): Boolean = user.status != UserStatus.SUSPENDED

    override fun isCredentialsNonExpired(): Boolean = true

    override fun isEnabled(): Boolean = user.status == UserStatus.ACTIVE

    fun getUserId(): UUID? = user.id
    
    fun getFullName(): String = user.fullName ?: "User"
    
    fun hasRole(roleName: String): Boolean {
        return authorities.any { it.authority == "ROLE_$roleName" }
    }
}
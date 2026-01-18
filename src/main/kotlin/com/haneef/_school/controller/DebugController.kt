package com.haneef._school.controller

import com.haneef._school.service.CustomUserDetails
import com.haneef._school.service.CustomUserDetailsService
import com.haneef._school.repository.UserRepository
import com.haneef._school.repository.RoleRepository
import com.haneef._school.repository.SchoolRepository
import jakarta.servlet.http.HttpSession
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.slf4j.LoggerFactory

@Controller
@RequestMapping("/debug")
class DebugController(
    private val userRepository: UserRepository,
    private val customUserDetailsService: CustomUserDetailsService,
    private val roleRepository: RoleRepository,
    private val schoolRepository: SchoolRepository
) {

    private val logger = LoggerFactory.getLogger(DebugController::class.java)

//    @GetMapping("/auth-info")
//    @ResponseBody
//    fun getAuthInfo(authentication: Authentication?, session: HttpSession): Map<String, Any?> {
//        logger.info("=== DEBUG AUTH INFO REQUEST ===")
//        
//        val result = mutableMapOf<String, Any?>()
//        
//        if (authentication != null) {
//            val customUser = authentication.principal as? CustomUserDetails
//            result["authenticated"] = authentication.isAuthenticated
//            result["username"] = authentication.name
//            result["authorities"] = authentication.authorities.map { it.authority }
//            result["principal_type"] = authentication.principal?.let { it::class.simpleName }
//            
//            if (customUser != null) {
//                result["user_id"] = customUser.getUserId()
//                result["full_name"] = customUser.getFullName()
//                result["user_authorities"] = customUser.authorities.map { it.authority }
//            }
//        } else {
//            result["authenticated"] = false
//            result["message"] = "No authentication found"
//        }
//        
//        // Session attributes
//        result["session_attributes"] = mapOf(
//            "selectedSchoolId" to session.getAttribute("selectedSchoolId"),
//            "selectedRoleId" to session.getAttribute("selectedRoleId"),
//            "selectedRole" to session.getAttribute("selectedRole")
//        )
//        
//        logger.info("Debug auth info: $result")
//        return result
//    }

//    @GetMapping("/debug/roles")
//    @ResponseBody
//    fun debugRoles(): Map<String, Any?> {
//        logger.info("=== DEBUG ROLES ===")
//        
//        val result = mutableMapOf<String, Any?>()
//        
//        try {
//            // Check all roles in database
//            val allRoles = roleRepository.findAll()
//            result["total_roles"] = allRoles.size
//            result["roles"] = allRoles.map { 
//                mapOf(
//                    "id" to it.id,
//                    "name" to it.name,
//                    "roleType" to it.roleType,
//                    "description" to it.description
//                )
//            }
//            
//            // Check admin user specifically
//            val adminUser = userRepository.findByEmailWithRoles("admin@demohighschool.edu").orElse(null)
//            if (adminUser != null) {
//                result["admin_user_found"] = true
//                result["admin_user_id"] = adminUser.id
//                result["admin_school_roles"] = adminUser.schoolRoles.map {
//                    mapOf(
//                        "role_name" to it.role.name,
//                        "school_id" to it.schoolId,
//                        "is_active" to it.isActive,
//                        "is_primary" to it.isPrimary
//                    )
//                }
//            } else {
//                result["admin_user_found"] = false
//            }
//            
//            // Check schools
//            val schools = schoolRepository.findAll()
//            result["total_schools"] = schools.size
//            result["schools"] = schools.map {
//                mapOf(
//                    "id" to it.id,
//                    "name" to it.name,
//                    "slug" to it.slug
//                )
//            }
//            
//        } catch (e: Exception) {
//            result["error"] = e.message
//            logger.error("Error debugging roles", e)
//        }
//        
//        return result
//    }

//    @GetMapping("/debug/user/{email}")
//    @ResponseBody
//    fun debugUser(@PathVariable email: String): Map<String, Any?> {
//        logger.info("=== DEBUG USER: $email ===")
//        
//        val result = mutableMapOf<String, Any?>()
//        
//        try {
//            // Load user from database
//            val user = userRepository.findByEmailWithRoles(email).orElse(null)
//            if (user == null) {
//                result["error"] = "User not found in database"
//                return result
//            }
//            
//            result["user_found"] = true
//            result["user_id"] = user.id
//            result["user_email"] = user.email
//            result["user_status"] = user.status
//            result["user_verified"] = user.isVerified
//            
//            // Check school roles
//            result["school_roles_count"] = user.schoolRoles.size
//            result["school_roles"] = user.schoolRoles.map { 
//                mapOf(
//                    "role_name" to it.role.name,
//                    "school_id" to it.schoolId,
//                    "is_active" to it.isActive,
//                    "is_primary" to it.isPrimary
//                )
//            }
//            
//            // Check global roles
//            result["global_roles_count"] = user.globalRoles.size
//            result["global_roles"] = user.globalRoles.map {
//                mapOf(
//                    "role_name" to it.role?.name,
//                    "is_active" to it.isActive
//                )
//            }
//            
//            // Try to load as UserDetails
//            try {
//                val userDetails = customUserDetailsService.loadUserByUsername("EMAIL:$email")
//                val customUserDetails = userDetails as CustomUserDetails
//                result["authorities"] = customUserDetails.authorities.map { it.authority }
//                result["user_details_loaded"] = true
//            } catch (e: Exception) {
//                result["user_details_error"] = e.message
//                result["user_details_loaded"] = false
//            }
//            
//        } catch (e: Exception) {
//            result["error"] = e.message
//            logger.error("Error debugging user $email", e)
//        }
//        
//        logger.info("Debug user result: $result")
//        return result
//    }
}
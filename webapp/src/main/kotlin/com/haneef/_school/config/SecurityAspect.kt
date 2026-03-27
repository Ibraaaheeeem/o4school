package com.haneef._school.config

import com.haneef._school.service.AuthorizationService
import jakarta.servlet.http.HttpSession
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.slf4j.LoggerFactory
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Security aspect to automatically validate school access for controller methods
 * that accept UUID path variables representing school-owned entities
 */
@Aspect
@Component
class SecurityAspect(
    private val authorizationService: AuthorizationService
) {

    companion object {
        private val logger = LoggerFactory.getLogger(SecurityAspect::class.java)
        private val SAFE_METHOD_KEYWORDS = setOf(
            "home", "dashboard", "index", "list", "filter",
            "getNewModal", "getNewForm", "create", "new"
        )
    }

    /**
     * Intercepts controller methods with @PathVariable UUID parameters
     * and validates school ownership before proceeding
     */
    @Around("@within(org.springframework.stereotype.Controller) && execution(* *(.., @org.springframework.web.bind.annotation.PathVariable java.util.UUID, ..))")
    fun validateEntityAccess(joinPoint: ProceedingJoinPoint): Any? {
        val args = joinPoint.args
        val methodName = joinPoint.signature.name
        
        // Find HttpSession in arguments
        val session = args.find { it is HttpSession } as? HttpSession
        val selectedSchoolId = session?.getAttribute("selectedSchoolId") as? UUID
        
        if (selectedSchoolId == null) {
            throw AccessDeniedException("No school selected")
        }
        
        // Extract UUID path variables
        val uuidArgs = args.filterIsInstance<UUID>()
        
        // Skip validation for certain safe methods or if no UUIDs present
        if (uuidArgs.isEmpty() || isSafeMethod(methodName)) {
            return joinPoint.proceed()
        }
        
        // Enforcement: Validate every UUID in path against the selected school
        uuidArgs.forEach { uuid ->
            try {
                authorizationService.validateAnyUuidOwnership(uuid, selectedSchoolId)
            } catch (e: AccessDeniedException) {
                logger.warn(
                    "Blocked potential IDOR attempt in {}.{} for UUID={} schoolId={}",
                    joinPoint.signature.declaringTypeName,
                    methodName,
                    uuid,
                    selectedSchoolId
                )
                throw e
            }
        }
        
        return joinPoint.proceed()
    }
    
    private fun isSafeMethod(methodName: String): Boolean {
        return SAFE_METHOD_KEYWORDS.any { methodName.contains(it, ignoreCase = true) }
    }
}
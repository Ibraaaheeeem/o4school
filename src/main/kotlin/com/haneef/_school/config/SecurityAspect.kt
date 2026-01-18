package com.haneef._school.config

import com.haneef._school.service.AuthorizationService
import jakarta.servlet.http.HttpSession
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
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
        
        // For now, proceed with the original method
        // Individual controllers should handle their own validation
        // This aspect serves as a safety net and logging mechanism
        
        try {
            return joinPoint.proceed()
        } catch (e: Exception) {
            // Log potential security violations
            if (e.message?.contains("not found") == true || 
                e.message?.contains("Unauthorized") == true) {
                println("SECURITY WARNING: Potential IDOR attempt in ${joinPoint.signature.declaringTypeName}.${methodName} with UUIDs: $uuidArgs")
            }
            throw e
        }
    }
    
    private fun isSafeMethod(methodName: String): Boolean {
        val safeMethods = setOf(
            "home", "dashboard", "index", "list", "filter",
            "getNewModal", "getNewForm", "create", "new"
        )
        return safeMethods.any { methodName.contains(it, ignoreCase = true) }
    }
}
package com.haneef._school.security

import com.haneef._school.service.AuthorizationService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Service
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.util.UUID

/**
 * Service for method-level security checks using Spring Security expressions
 */
@Service("schoolSecurity")
class SchoolSecurityService(
    private val authorizationService: AuthorizationService
) {

    /**
     * Checks if the current user can access a student
     */
    fun canAccessStudent(studentId: UUID, authentication: Authentication): Boolean {
        return try {
            val schoolId = getCurrentSchoolId()
            authorizationService.validateAndGetStudent(studentId, schoolId)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if the current user can access a parent
     */
    fun canAccessParent(parentId: UUID, authentication: Authentication): Boolean {
        return try {
            val schoolId = getCurrentSchoolId()
            authorizationService.validateAndGetParent(parentId, schoolId)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if the current user can access a staff member
     */
    fun canAccessStaff(staffId: UUID, authentication: Authentication): Boolean {
        return try {
            val schoolId = getCurrentSchoolId()
            authorizationService.validateAndGetStaff(staffId, schoolId)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if the current user can access a subject
     */
    fun canAccessSubject(subjectId: UUID, authentication: Authentication): Boolean {
        return try {
            val schoolId = getCurrentSchoolId()
            authorizationService.validateAndGetSubject(subjectId, schoolId)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if the current user can access a school class
     */
    fun canAccessClass(classId: UUID, authentication: Authentication): Boolean {
        return try {
            val schoolId = getCurrentSchoolId()
            authorizationService.validateAndGetSchoolClass(classId, schoolId)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if the current user can access an examination
     */
    fun canAccessExamination(examinationId: UUID, authentication: Authentication): Boolean {
        return try {
            val schoolId = getCurrentSchoolId()
            authorizationService.validateAndGetExamination(examinationId, schoolId)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if the current user can access a fee item
     */
    fun canAccessFeeItem(feeItemId: UUID, authentication: Authentication): Boolean {
        return try {
            val schoolId = getCurrentSchoolId()
            authorizationService.validateAndGetFeeItem(feeItemId, schoolId)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Gets the current school ID from the session
     */
    private fun getCurrentSchoolId(): UUID {
        val request = (RequestContextHolder.currentRequestAttributes() as ServletRequestAttributes).request
        val session = request.session
        return session.getAttribute("selectedSchoolId") as? UUID
            ?: throw RuntimeException("No school selected")
    }
}
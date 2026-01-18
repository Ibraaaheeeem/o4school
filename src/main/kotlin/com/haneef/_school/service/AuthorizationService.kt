package com.haneef._school.service

import com.haneef._school.entity.*
import com.haneef._school.repository.*
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class AuthorizationService(
    private val studentRepository: StudentRepository,
    private val parentRepository: ParentRepository,
    private val staffRepository: StaffRepository,
    private val subjectRepository: SubjectRepository,
    private val schoolClassRepository: SchoolClassRepository,
    private val examinationRepository: ExaminationRepository,
    private val feeItemRepository: FeeItemRepository,
    private val academicSessionRepository: AcademicSessionRepository,
    private val termRepository: TermRepository,
    private val departmentRepository: DepartmentRepository,
    private val educationTrackRepository: EducationTrackRepository
) {

    /**
     * Validates that an entity belongs to the specified school
     */
    fun validateSchoolOwnership(entity: TenantAwareEntity, schoolId: UUID) {
        if (entity.schoolId != schoolId) {
            throw AccessDeniedException("Unauthorized access to resource")
        }
    }

    /**
     * Validates school access and returns the school ID
     */
    fun validateSchoolAccess(schoolId: UUID?): UUID {
        return schoolId ?: throw AccessDeniedException("No school selected")
    }

    /**
     * Validates and retrieves a student ensuring it belongs to the specified school
     */
    fun validateAndGetStudent(studentId: UUID, schoolId: UUID): Student {
        return studentRepository.findByIdAndSchoolIdSecure(studentId, schoolId)
            .orElseThrow { RuntimeException("Student not found or unauthorized access") }
    }

    /**
     * Validates and retrieves a parent ensuring it belongs to the specified school
     */
    fun validateAndGetParent(parentId: UUID, schoolId: UUID): Parent {
        return parentRepository.findByIdAndSchoolIdSecure(parentId, schoolId)
            .orElseThrow { RuntimeException("Parent not found or unauthorized access") }
    }

    /**
     * Validates and retrieves a staff member ensuring it belongs to the specified school
     */
    fun validateAndGetStaff(staffId: UUID, schoolId: UUID): Staff {
        return staffRepository.findByIdAndSchoolIdSecure(staffId, schoolId)
            .orElseThrow { RuntimeException("Staff not found or unauthorized access") }
    }

    /**
     * Validates and retrieves a subject ensuring it belongs to the specified school
     */
    fun validateAndGetSubject(subjectId: UUID, schoolId: UUID): Subject {
        return subjectRepository.findByIdAndSchoolIdSecure(subjectId, schoolId)
            .orElseThrow { RuntimeException("Subject not found or unauthorized access") }
    }

    /**
     * Validates and retrieves a school class ensuring it belongs to the specified school
     */
    fun validateAndGetSchoolClass(classId: UUID, schoolId: UUID): SchoolClass {
        return schoolClassRepository.findByIdAndSchoolIdSecure(classId, schoolId)
            .orElseThrow { RuntimeException("Class not found or unauthorized access") }
    }

    /**
     * Validates and retrieves an examination ensuring it belongs to the specified school
     */
    fun validateAndGetExamination(examinationId: UUID, schoolId: UUID): Examination {
        return examinationRepository.findByIdAndSchoolIdSecure(examinationId, schoolId)
            .orElseThrow { RuntimeException("Examination not found or unauthorized access") }
    }

    /**
     * Validates and retrieves a fee item ensuring it belongs to the specified school
     */
    fun validateAndGetFeeItem(feeItemId: UUID, schoolId: UUID): FeeItem {
        return feeItemRepository.findByIdAndSchoolIdSecure(feeItemId, schoolId)
            .orElseThrow { RuntimeException("Fee item not found or unauthorized access") }
    }

    /**
     * Validates and retrieves an academic session ensuring it belongs to the specified school
     */
    fun validateAndGetAcademicSession(sessionId: UUID, schoolId: UUID): AcademicSession {
        return academicSessionRepository.findByIdAndSchoolIdSecure(sessionId, schoolId)
            .orElseThrow { RuntimeException("Academic session not found or unauthorized access") }
    }

    /**
     * Validates and retrieves a term ensuring it belongs to the specified school
     */
    fun validateAndGetTerm(termId: UUID, schoolId: UUID): Term {
        return termRepository.findByIdAndSchoolIdSecure(termId, schoolId)
            .orElseThrow { RuntimeException("Term not found or unauthorized access") }
    }

    /**
     * Validates and retrieves a department ensuring it belongs to the specified school
     */
    fun validateAndGetDepartment(departmentId: UUID, schoolId: UUID): Department {
        return departmentRepository.findByIdAndSchoolIdSecure(departmentId, schoolId)
            .orElseThrow { RuntimeException("Department not found or unauthorized access") }
    }

    /**
     * Validates and retrieves an education track ensuring it belongs to the specified school
     */
    fun validateAndGetEducationTrack(trackId: UUID, schoolId: UUID): EducationTrack {
        return educationTrackRepository.findByIdAndSchoolIdSecure(trackId, schoolId)
            .orElseThrow { RuntimeException("Education track not found or unauthorized access") }
    }

    /**
     * Validates that a parent has access to a specific student
     */
    fun validateParentStudentAccess(parentId: UUID, studentId: UUID, schoolId: UUID): Boolean {
        val parent = validateAndGetParent(parentId, schoolId)
        val student = validateAndGetStudent(studentId, schoolId)
        
        return parent.activeStudentRelationships.any { 
            it.student.id == studentId && it.isActive 
        }
    }
}
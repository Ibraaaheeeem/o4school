package com.haneef._school.repository

import java.util.UUID

import com.haneef._school.entity.Assessment
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface AssessmentRepository : JpaRepository<Assessment, UUID> {
    fun findByAdmissionNumberAndAcademicSessionIdAndTermIdAndSchoolIdAndIsActive(
        admissionNumber: String,
        academicSessionId: UUID,
        termId: UUID,
        schoolId: UUID,
        isActive: Boolean
    ): Optional<Assessment>

    fun findByStudentIdAndAcademicSessionIdAndTermIdAndSchoolIdAndIsActive(
        studentId: UUID,
        academicSessionId: UUID,
        termId: UUID,
        schoolId: UUID,
        isActive: Boolean
    ): Optional<Assessment>

    fun findByStudentIdAndSchoolIdAndIsActive(
        studentId: UUID,
        schoolId: UUID,
        isActive: Boolean
    ): List<Assessment>
}

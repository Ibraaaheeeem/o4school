package com.haneef._school.repository

import java.util.UUID

import com.haneef._school.entity.Assessment
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface AssessmentRepository : JpaRepository<Assessment, UUID> {
    fun findByAdmissionNumberAndSessionAndTermAndSchoolIdAndIsActive(
        admissionNumber: String,
        session: String,
        term: String,
        schoolId: UUID,
        isActive: Boolean
    ): Optional<Assessment>

    fun findByStudentIdAndSessionAndTermAndSchoolIdAndIsActive(
        studentId: UUID,
        session: String,
        term: String,
        schoolId: UUID,
        isActive: Boolean
    ): Optional<Assessment>
}

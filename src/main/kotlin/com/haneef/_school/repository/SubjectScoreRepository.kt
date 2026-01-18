package com.haneef._school.repository

import java.util.UUID

import com.haneef._school.entity.SubjectScore
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface SubjectScoreRepository : JpaRepository<SubjectScore, UUID> {
    fun findByAssessmentIdAndSubjectIdAndSchoolIdAndIsActive(
        assessmentId: UUID,
        subjectId: UUID,
        schoolId: UUID,
        isActive: Boolean
    ): List<SubjectScore>
}

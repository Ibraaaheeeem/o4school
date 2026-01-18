package com.haneef._school.repository

import com.haneef._school.entity.ExaminationSubmission
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ExaminationSubmissionRepository : JpaRepository<ExaminationSubmission, UUID> {
    fun findByExaminationId(examinationId: UUID): List<ExaminationSubmission>
    

    
    @Query("SELECT es FROM ExaminationSubmission es JOIN FETCH es.student s WHERE es.examination.id = :examinationId ORDER BY s.admissionNumber ASC, es.attemptCount ASC")
    fun findByExaminationIdWithStudent(examinationId: UUID): List<ExaminationSubmission>
}

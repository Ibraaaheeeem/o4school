package com.haneef._school.repository

import com.haneef._school.entity.SchoolReimbursement
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface SchoolReimbursementRepository : JpaRepository<SchoolReimbursement, UUID> {
    fun findBySchoolIdOrderByReimbursementDateDesc(schoolId: UUID): List<SchoolReimbursement>
    fun findByReference(reference: String): SchoolReimbursement?
    
    @Query("SELECT sr FROM SchoolReimbursement sr WHERE sr.school.id = :schoolId AND (:sessionId IS NULL OR sr.academicSession.id = :sessionId) AND (:termId IS NULL OR sr.term.id = :termId) ORDER BY sr.reimbursementDate DESC")
    fun findBySchoolIdAndAcademicSessionIdAndTermIdOrderByReimbursementDateDesc(schoolId: UUID, sessionId: UUID?, termId: UUID?): List<SchoolReimbursement>
}

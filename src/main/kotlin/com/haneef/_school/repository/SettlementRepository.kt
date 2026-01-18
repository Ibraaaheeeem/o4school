package com.haneef._school.repository

import com.haneef._school.entity.Settlement
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface SettlementRepository : JpaRepository<Settlement, UUID> {
    fun findByReference(reference: String): Settlement?
    fun existsByReference(reference: String): Boolean
    fun findByWalletId(walletId: UUID): List<Settlement>
    fun findByWalletIdAndAcademicSessionIdAndTermId(walletId: UUID, sessionId: UUID, termId: UUID): List<Settlement>
    
    // Query by school using schoolId property from TenantAwareEntity
    fun findBySchoolId(schoolId: UUID): List<Settlement>
    
    @Query("SELECT s FROM Settlement s WHERE s.schoolId = :schoolId AND (:sessionId IS NULL OR s.academicSession.id = :sessionId) AND (:termId IS NULL OR s.term.id = :termId)")
    fun findBySchoolIdAndAcademicSessionIdAndTermId(schoolId: UUID, sessionId: UUID?, termId: UUID?): List<Settlement>
    
    fun findBySchoolIdAndStatusAndReimbursed(schoolId: UUID, status: String, reimbursed: Boolean): List<Settlement>
}

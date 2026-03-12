package com.haneef._school.repository

import com.haneef._school.entity.Settlement
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface SettlementRepository : JpaRepository<Settlement, UUID> {
    fun findByReference(reference: String): Settlement?
    fun existsByReference(reference: String): Boolean
    fun findByPaystackWalletId(paystackWalletId: UUID): List<Settlement>
    fun findBySquadWalletId(squadWalletId: UUID): List<Settlement>
    fun findByPaystackWalletIdAndAcademicSessionIdAndTermId(paystackWalletId: UUID, sessionId: UUID, termId: UUID): List<Settlement>
    fun findBySquadWalletIdAndAcademicSessionIdAndTermId(squadWalletId: UUID, sessionId: UUID, termId: UUID): List<Settlement>
    
    // Query by school using schoolId property from TenantAwareEntity
    fun findBySchoolId(schoolId: UUID): List<Settlement>
    
    @Query("SELECT s FROM Settlement s WHERE s.schoolId = :schoolId AND (:sessionId IS NULL OR s.academicSession.id = :sessionId) AND (:termId IS NULL OR s.term.id = :termId)")
    fun findBySchoolIdAndAcademicSessionIdAndTermId(schoolId: UUID, sessionId: UUID?, termId: UUID?): List<Settlement>
    
    fun findBySchoolIdAndStatusAndReimbursed(schoolId: UUID, status: String, reimbursed: Boolean): List<Settlement>

    @Query("SELECT s FROM Settlement s WHERE s.schoolId = :schoolId AND (:sessionId IS NULL OR s.academicSession.id = :sessionId) AND (:termId IS NULL OR s.term.id = :termId) AND (s.transactionDate >= COALESCE(:startDate, s.transactionDate)) AND (s.transactionDate <= COALESCE(:endDate, s.transactionDate))")
    fun findByFilters(schoolId: UUID, sessionId: UUID?, termId: UUID?, startDate: java.time.LocalDateTime?, endDate: java.time.LocalDateTime?): List<Settlement>

    fun findByPayerEmail(payerEmail: String): List<Settlement>

    @Query("""
        SELECT DISTINCT s FROM Settlement s 
        LEFT JOIN s.paystackWallet pw 
        LEFT JOIN s.squadWallet sw 
        LEFT JOIN PaymentAllocation pa ON pa.settlement = s 
        WHERE s.schoolId = :schoolId 
        AND s.status = 'success'
        AND (:sessionId IS NULL OR s.academicSession.id = :sessionId)
        AND (:termId IS NULL OR s.term.id = :termId)
        AND (pw.parent.id = :parentId OR sw.parent.id = :parentId OR pa.student.id IN :studentIds OR s.payerEmail = :email)
    """)
    fun findByParentContext(
        @Param("schoolId") schoolId: UUID,
        @Param("parentId") parentId: UUID,
        @Param("studentIds") studentIds: List<UUID>,
        @Param("email") email: String?,
        @Param("sessionId") sessionId: UUID?,
        @Param("termId") termId: UUID?
    ): List<Settlement>
}

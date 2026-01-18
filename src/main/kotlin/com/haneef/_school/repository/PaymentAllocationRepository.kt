package com.haneef._school.repository

import com.haneef._school.entity.PaymentAllocation
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface PaymentAllocationRepository : JpaRepository<PaymentAllocation, UUID> {
    
    fun findBySettlementIdOrderByAllocationOrder(settlementId: UUID): List<PaymentAllocation>
    
    fun findByStudentIdOrderByAllocationDateDesc(studentId: UUID): List<PaymentAllocation>
    
    @Query("""
        SELECT pa FROM PaymentAllocation pa 
        WHERE pa.student.id = :studentId 
        AND (:sessionId IS NULL OR pa.settlement.academicSession.id = :sessionId)
        AND (:termId IS NULL OR pa.settlement.term.id = :termId)
        ORDER BY pa.allocationDate DESC
    """)
    fun findByStudentIdAndSessionAndTerm(
        @Param("studentId") studentId: UUID,
        @Param("sessionId") sessionId: UUID?,
        @Param("termId") termId: UUID?
    ): List<PaymentAllocation>
    
    @Query("""
        SELECT pa FROM PaymentAllocation pa 
        WHERE pa.settlement.wallet.parent.id = :parentId 
        AND (:sessionId IS NULL OR pa.settlement.academicSession.id = :sessionId)
        AND (:termId IS NULL OR pa.settlement.term.id = :termId)
        ORDER BY pa.allocationDate DESC, pa.allocationOrder ASC
    """)
    fun findByParentIdAndSessionAndTerm(
        @Param("parentId") parentId: UUID,
        @Param("sessionId") sessionId: UUID?,
        @Param("termId") termId: UUID?
    ): List<PaymentAllocation>
    
    @Query("""
        SELECT SUM(pa.allocatedAmount) FROM PaymentAllocation pa 
        WHERE pa.student.id = :studentId 
        AND (:sessionId IS NULL OR pa.settlement.academicSession.id = :sessionId)
        AND (:termId IS NULL OR pa.settlement.term.id = :termId)
    """)
    fun getTotalAllocatedAmountForStudent(
        @Param("studentId") studentId: UUID,
        @Param("sessionId") sessionId: UUID?,
        @Param("termId") termId: UUID?
    ): java.math.BigDecimal?
}
package com.haneef._school.service

import com.haneef._school.entity.*
import com.haneef._school.repository.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.*

@Service
@Transactional
class PaymentDistributionService(
    private val paymentAllocationRepository: PaymentAllocationRepository,
    private val classFeeItemRepository: ClassFeeItemRepository
) {

    private val logger = LoggerFactory.getLogger(PaymentDistributionService::class.java)

    /**
     * Distributes a settlement amount sequentially among the parent's children
     * Sequential method: Pays off each child's outstanding balance completely before moving to the next child
     */
    fun distributePaymentSequentially(settlement: Settlement): List<PaymentAllocation> {
        val parent = settlement.parent ?: settlement.paystackWallet?.parent ?: settlement.squadWallet?.parent ?: throw IllegalStateException("Settlement has no wallet")
        val parentId = parent.id ?: throw IllegalStateException("Settlement parent has no id")
        val paymentAmount = settlement.amount
        val sessionId = settlement.academicSession?.id
        val termId = settlement.term?.id

        if (paymentAmount <= BigDecimal.ZERO) {
            logger.info("Skipping payment distribution for settlement {} due to non-positive amount {}", settlement.id, paymentAmount)
            return emptyList()
        }
        
        // Get all active children for this parent, ordered by enrollment date (or student ID for consistency)
        val children = parent.activeStudentRelationships
            .filter { it.student.isActive }
            .map { it.student }
            .distinctBy { it.id }
            .sortedWith(compareBy<Student>({ it.createdAt }, { it.id?.toString() ?: "" }))
        
        val allocations = mutableListOf<PaymentAllocation>()
        var remainingAmount = paymentAmount
        var allocationOrder = 1
        val feeTotalsCache = mutableMapOf<String, BigDecimal>()
        val allocatedByStudent = paymentAllocationRepository
            .findByParentIdAndSessionAndTerm(parentId, sessionId, termId)
            .groupBy { it.student.id }
            .mapValues { (_, rows) -> rows.fold(BigDecimal.ZERO) { acc, row -> acc + row.allocatedAmount } }
        
        for (child in children) {
            if (remainingAmount <= BigDecimal.ZERO) break
            val childId = child.id ?: continue
            
            // Calculate child's outstanding balance
            val totalFees = calculateTotalFeesForStudent(child, sessionId, termId, feeTotalsCache)
            val previousAllocations = allocatedByStudent[childId] ?: BigDecimal.ZERO

            val outstandingBalance = (totalFees - previousAllocations).max(BigDecimal.ZERO)
            
            if (outstandingBalance > BigDecimal.ZERO) {
                // Allocate payment to this child (up to their outstanding balance)
                val allocationAmount = if (remainingAmount <= outstandingBalance) remainingAmount else outstandingBalance
                val balanceBefore = outstandingBalance
                val balanceAfter = outstandingBalance - allocationAmount
                
                val allocation = PaymentAllocation(
                    settlement = settlement,
                    student = child,
                    allocatedAmount = allocationAmount,
                    allocationOrder = allocationOrder++,
                    allocationMethod = "SEQUENTIAL",
                    remainingBalanceBefore = balanceBefore,
                    remainingBalanceAfter = balanceAfter,
                    allocationDate = settlement.transactionDate,
                    notes = "Sequential distribution - Child ${allocationOrder - 1} of ${children.size}"
                ).apply {
                    this.schoolId = settlement.schoolId
                }
                
                allocations.add(paymentAllocationRepository.save(allocation))
                remainingAmount -= allocationAmount
            }
        }

        logger.info(
            "Distributed settlement {} for parent {} across {} allocation(s); allocated={}, remaining={}",
            settlement.id,
            parentId,
            allocations.size,
            paymentAmount - remainingAmount,
            remainingAmount
        )
        
        return allocations
    }
    
    /**
     * Calculate total fees for a student in a specific session/term
     */
    private fun calculateTotalFeesForStudent(
        student: Student,
        sessionId: UUID?,
        termId: UUID?,
        feeTotalsCache: MutableMap<String, BigDecimal>
    ): BigDecimal {
        val activeEnrollments = student.classEnrollments
            .asSequence()
            .filter { it.isActive }

        val scopedEnrollment = activeEnrollments
            .firstOrNull {
                (sessionId == null || it.academicSession.id == sessionId) &&
                    (termId == null || it.term.id == termId)
            }

        val activeEnrollment = scopedEnrollment
            ?: student.classEnrollments
                .asSequence()
                .filter { it.isActive }
                .firstOrNull()
            ?: return BigDecimal.ZERO
        
        val schoolClass = activeEnrollment.schoolClass
        val classId = schoolClass.id ?: return BigDecimal.ZERO
        val cacheKey = "$classId|${sessionId ?: "none"}|${termId ?: "none"}"

        feeTotalsCache[cacheKey]?.let { return it }
        
        // Get class fee items for the specified session/term
        val classFeeItems = if (sessionId != null && termId != null) {
            classFeeItemRepository.findBySchoolClassIdAndAcademicSessionIdAndTermIdFilters(
                classId, sessionId, termId, true
            )
        } else if (sessionId != null) {
            classFeeItemRepository.findBySchoolClassIdAndAcademicSessionIdAndIsActive(
                classId, sessionId, true
            )
        } else {
            emptyList<ClassFeeItem>()
        }
        
        val total = classFeeItems.sumOf { cfi: ClassFeeItem ->
            cfi.customAmount ?: cfi.feeItem.amount
        }

        feeTotalsCache[cacheKey] = total
        return total
    }
    
    /**
     * Get payment allocation details for a student
     */
    @Transactional(readOnly = true)
    fun getStudentPaymentAllocations(studentId: UUID, sessionId: UUID?, termId: UUID?): List<PaymentAllocation> {
        return paymentAllocationRepository.findByStudentIdAndSessionAndTerm(studentId, sessionId, termId)
    }
    
    /**
     * Get payment allocation details for a parent (all children)
     */
    @Transactional(readOnly = true)
    fun getParentPaymentAllocations(parentId: UUID, sessionId: UUID?, termId: UUID?): List<PaymentAllocation> {
        return paymentAllocationRepository.findByParentIdAndSessionAndTerm(parentId, sessionId, termId)
    }
    
    /**
     * Calculate total allocated amount for a student
     */
    @Transactional(readOnly = true)
    fun getTotalAllocatedForStudent(studentId: UUID, sessionId: UUID?, termId: UUID?): BigDecimal {
        return paymentAllocationRepository.getTotalAllocatedAmountForStudent(studentId, sessionId, termId) 
            ?: BigDecimal.ZERO
    }
}
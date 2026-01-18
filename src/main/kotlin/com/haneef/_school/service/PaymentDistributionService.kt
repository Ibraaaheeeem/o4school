package com.haneef._school.service

import com.haneef._school.entity.*
import com.haneef._school.repository.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*
import kotlin.math.min

@Service
@Transactional
class PaymentDistributionService(
    private val paymentAllocationRepository: PaymentAllocationRepository,
    private val studentRepository: StudentRepository,
    private val classFeeItemRepository: ClassFeeItemRepository
) {

    /**
     * Distributes a settlement amount sequentially among the parent's children
     * Sequential method: Pays off each child's outstanding balance completely before moving to the next child
     */
    fun distributePaymentSequentially(settlement: Settlement): List<PaymentAllocation> {
        val parent = settlement.wallet.parent
        val paymentAmount = settlement.amount
        val sessionId = settlement.academicSession?.id
        val termId = settlement.term?.id
        
        // Get all active children for this parent, ordered by enrollment date (or student ID for consistency)
        val children = parent.activeStudentRelationships
            .filter { it.student.isActive }
            .map { it.student }
            .distinctBy { it.id }
            .sortedBy { it.createdAt?.toString() ?: it.id.toString() } // Fix type inference issue
        
        val allocations = mutableListOf<PaymentAllocation>()
        var remainingAmount = paymentAmount
        var allocationOrder = 1
        
        for (child in children) {
            if (remainingAmount <= BigDecimal.ZERO) break
            
            // Calculate child's outstanding balance
            val totalFees = calculateTotalFeesForStudent(child, sessionId, termId)
            val previousAllocations = paymentAllocationRepository.getTotalAllocatedAmountForStudent(
                child.id!!, sessionId, termId
            ) ?: BigDecimal.ZERO
            
            val outstandingBalance = totalFees - previousAllocations
            
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
        
        return allocations
    }
    
    /**
     * Calculate total fees for a student in a specific session/term
     */
    private fun calculateTotalFeesForStudent(student: Student, sessionId: UUID?, termId: UUID?): BigDecimal {
        val activeEnrollment = student.classEnrollments.find { it.isActive }
            ?: return BigDecimal.ZERO
        
        val schoolClass = activeEnrollment.schoolClass
        
        // Get class fee items for the specified session/term
        val classFeeItems = if (sessionId != null && termId != null) {
            classFeeItemRepository.findBySchoolClassIdAndAcademicSessionIdAndTermIdFilters(
                schoolClass.id!!, sessionId, termId, true
            )
        } else if (sessionId != null) {
            classFeeItemRepository.findBySchoolClassIdAndAcademicSessionIdAndIsActive(
                schoolClass.id!!, sessionId, true
            )
        } else {
            emptyList<ClassFeeItem>()
        }
        
        return classFeeItems.sumOf { cfi: ClassFeeItem ->
            cfi.customAmount ?: cfi.feeItem.amount
        }
    }
    
    /**
     * Get payment allocation details for a student
     */
    fun getStudentPaymentAllocations(studentId: UUID, sessionId: UUID?, termId: UUID?): List<PaymentAllocation> {
        return paymentAllocationRepository.findByStudentIdAndSessionAndTerm(studentId, sessionId, termId)
    }
    
    /**
     * Get payment allocation details for a parent (all children)
     */
    fun getParentPaymentAllocations(parentId: UUID, sessionId: UUID?, termId: UUID?): List<PaymentAllocation> {
        return paymentAllocationRepository.findByParentIdAndSessionAndTerm(parentId, sessionId, termId)
    }
    
    /**
     * Calculate total allocated amount for a student
     */
    fun getTotalAllocatedForStudent(studentId: UUID, sessionId: UUID?, termId: UUID?): BigDecimal {
        return paymentAllocationRepository.getTotalAllocatedAmountForStudent(studentId, sessionId, termId) 
            ?: BigDecimal.ZERO
    }
}
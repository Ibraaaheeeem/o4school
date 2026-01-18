package com.haneef._school.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(
    name = "payment_allocations",
    indexes = [
        Index(columnList = "settlement_id", name = "idx_allocation_settlement"),
        Index(columnList = "student_id", name = "idx_allocation_student"),
        Index(columnList = "allocation_order", name = "idx_allocation_order")
    ]
)
class PaymentAllocation(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "settlement_id", nullable = false)
    var settlement: Settlement,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    var student: Student,

    @Column(name = "allocated_amount", nullable = false)
    var allocatedAmount: BigDecimal,

    @Column(name = "allocation_order", nullable = false)
    var allocationOrder: Int,

    @Column(name = "allocation_method", nullable = false)
    var allocationMethod: String = "SEQUENTIAL", // SEQUENTIAL, PROPORTIONAL, EQUAL

    @Column(name = "remaining_balance_before", nullable = false)
    var remainingBalanceBefore: BigDecimal,

    @Column(name = "remaining_balance_after", nullable = false)
    var remainingBalanceAfter: BigDecimal,

    @Column(name = "allocation_date")
    var allocationDate: LocalDateTime = LocalDateTime.now(),

    @Column(name = "notes")
    var notes: String? = null

) : TenantAwareEntity() {
    
    constructor() : this(
        settlement = Settlement(),
        student = Student(),
        allocatedAmount = BigDecimal.ZERO,
        allocationOrder = 0,
        remainingBalanceBefore = BigDecimal.ZERO,
        remainingBalanceAfter = BigDecimal.ZERO
    )
}
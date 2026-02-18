package com.haneef._school.entity

import jakarta.persistence.*
import java.math.BigDecimal

@Entity
@Table(
    name = "class_fee_items",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["class_id", "fee_item_id", "academic_session_id", "term_id"], name = "unique_class_fee_item_session_term")
    ],
    indexes = [
        Index(columnList = "school_id,class_id", name = "idx_class_fee_school_class"),
        Index(columnList = "fee_item_id", name = "idx_class_fee_item")
    ]
)
class ClassFeeItem(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_id", nullable = false)
    var schoolClass: SchoolClass,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fee_item_id", nullable = false)
    var feeItem: FeeItem,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_session_id")
    var academicSession: AcademicSession? = null,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "term_id")
    var termId: Term? = null,
    
    @Column(name = "academic_year", nullable = false)
    var academicYear: String = "",
    
    @Column(name = "custom_amount", precision = 10, scale = 2)
    var customAmount: BigDecimal? = null,
    
    @Column(name = "is_applicable")
    var isApplicable: Boolean = true,
    
    @Column(name = "is_locked")
    var isLocked: Boolean = false,
    
    var notes: String? = null
) : TenantAwareEntity() {
    
    constructor() : this(
        schoolClass = SchoolClass(),
        feeItem = FeeItem()
    )
    
    // Computed property to get the effective amount
    val effectiveAmount: BigDecimal
        get() = customAmount ?: feeItem.amount
}
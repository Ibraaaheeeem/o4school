package com.haneef._school.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "student_optional_fees",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["student_id", "class_fee_item_id"], name = "unique_student_optional_fee")
    ],
    indexes = [
        Index(columnList = "student_id", name = "idx_student_optional_fee_student"),
        Index(columnList = "class_fee_item_id", name = "idx_student_optional_fee_item")
    ]
)
class StudentOptionalFee(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    var student: Student,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_fee_item_id", nullable = false)
    var classFeeItem: ClassFeeItem,
    
    @Column(name = "opted_in_at")
    var optedInAt: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "opted_in_by")
    var optedInBy: String? = null,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_session_id")
    var academicSession: AcademicSession? = null,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "term_id")
    var term: Term? = null,
    
    @Column(name = "is_locked")
    var isLocked: Boolean = false,

    @Column(name = "custom_amount", precision = 10, scale = 2)
    var customAmount: java.math.BigDecimal? = null,

    @Column(name = "notes")
    var notes: String? = null
) : TenantAwareEntity() {
    
    constructor() : this(
        student = Student(),
        classFeeItem = ClassFeeItem()
    )
}

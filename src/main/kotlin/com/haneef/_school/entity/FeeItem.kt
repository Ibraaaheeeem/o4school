package com.haneef._school.entity

import jakarta.persistence.*
import java.math.BigDecimal

@Entity
@Table(
    name = "fee_items",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["name", "school_id"], name = "unique_fee_item_school")
    ],
    indexes = [
        Index(columnList = "school_id,is_active", name = "idx_fee_item_school_active"),
        Index(columnList = "fee_category", name = "idx_fee_item_category")
    ]
)
class FeeItem(
    @Column(nullable = false)
    var name: String,
    
    @Column(nullable = false, precision = 10, scale = 2)
    var amount: BigDecimal,
    
    @Column(name = "fee_category", nullable = false)
    @Enumerated(EnumType.STRING)
    var feeCategory: FeeCategory,
    
    var description: String? = null,
    
    @Column(name = "is_mandatory")
    var isMandatory: Boolean = true,
    
    @Column(name = "is_recurring")
    var isRecurring: Boolean = false,
    
    @Column(name = "recurrence_type")
    @Enumerated(EnumType.STRING)
    var recurrenceType: RecurrenceType? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_session_id")
    var academicSession: AcademicSession? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "term_id")
    var term: Term? = null,

    @Column(name = "gender_eligibility")
    @Enumerated(EnumType.STRING)
    var genderEligibility: GenderEligibility = GenderEligibility.ALL,

    @Column(name = "student_status_eligibility")
    @Enumerated(EnumType.STRING)
    var studentStatusEligibility: StudentStatusEligibility = StudentStatusEligibility.ALL,

    @Column(name = "staff_discount_type")
    @Enumerated(EnumType.STRING)
    var staffDiscountType: DiscountType = DiscountType.NONE,

    @Column(name = "staff_discount_amount", precision = 10, scale = 2)
    var staffDiscountAmount: BigDecimal = BigDecimal.ZERO
) : TenantAwareEntity() {
    
    constructor() : this(
        name = "",
        amount = BigDecimal.ZERO,
        feeCategory = FeeCategory.TUITION
    )
    
    // Relationships
    @OneToMany(mappedBy = "feeItem", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var classFeeItems: MutableList<ClassFeeItem> = mutableListOf()
}

enum class FeeCategory {
    TUITION,
    REGISTRATION,
    EXAMINATION,
    LIBRARY,
    LABORATORY,
    SPORTS,
    TRANSPORT,
    UNIFORM,
    BOOKS,
    MEALS,
    ACCOMMODATION,
    TECHNOLOGY,
    EXTRACURRICULAR,
    MISCELLANEOUS
}

enum class RecurrenceType {
    ONE_TIME,
    OPTIONAL,
    MONTHLY,
    QUARTERLY,
    TERMLY,
    ANNUALLY
}

enum class GenderEligibility {
    ALL,
    MALE,
    FEMALE
}

enum class StudentStatusEligibility {
    ALL,
    NEW,
    RETURNING
}

enum class DiscountType {
    NONE,
    PERCENTAGE,
    FLAT_AMOUNT
}
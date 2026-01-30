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
        Index(columnList = "school_id,is_active", name = "idx_fee_item_school_active")
    ]
)
class FeeItem(
    @Column(nullable = false)
    var name: String,
    
    @Column(nullable = false, precision = 10, scale = 2)
    var amount: BigDecimal,
    
    var description: String? = null,
    
    @Column(name = "is_mandatory")
    var isMandatory: Boolean = true,

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
        amount = BigDecimal.ZERO
    )
    
    // Relationships
    @OneToMany(mappedBy = "feeItem", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var classFeeItems: MutableList<ClassFeeItem> = mutableListOf()
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
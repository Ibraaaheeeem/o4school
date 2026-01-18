package com.haneef._school.entity

import jakarta.persistence.*

@Entity
@Table(
    name = "fee_structures",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["fee_name", "school_id", "academic_session_id"], name = "unique_fee_school_session")
    ],
    indexes = [
        Index(columnList = "school_id,academic_session_id", name = "idx_fee_school_session"),
        Index(columnList = "school_id,fee_category", name = "idx_fee_school_category")
    ]
)
class FeeStructure(
    @Column(name = "fee_name", nullable = false)
    var feeName: String,
    
    @Column(name = "fee_category", nullable = false)
    var feeCategory: String, // tuition, transport, meal, uniform, etc.
    
    @Column(nullable = false)
    var amount: Int, // Amount in kobo/cents
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_session_id")
    var academicSession: AcademicSession? = null,
    
    var term: String? = null, // null means applies to all terms
    
    @Column(name = "grade_level")
    var gradeLevel: String? = null, // null means applies to all grades
    
    @Column(name = "due_date")
    var dueDate: String? = null, // MM-DD format
    
    var description: String? = null,
    
    @Column(name = "is_mandatory")
    var isMandatory: Boolean = true
) : TenantAwareEntity() {
    
    constructor() : this(
        feeName = "",
        feeCategory = "",
        amount = 0
    )
    
    // Relationships
    @OneToMany(mappedBy = "feeStructure", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var invoiceItems: MutableList<InvoiceItem> = mutableListOf()
}
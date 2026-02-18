package com.haneef._school.entity

import jakarta.persistence.*

@Entity
@Table(
    name = "invoice_items",
    indexes = [
        Index(columnList = "school_id,invoice_id", name = "idx_invoice_item_school")
    ]
)
class InvoiceItem(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    var invoice: Invoice,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fee_structure_id")
    var feeStructure: FeeStructure? = null,
    
    @Column(nullable = false)
    var description: String,
    
    var quantity: Int = 1,
    
    @Column(name = "unit_amount", nullable = false)
    var unitAmount: Int, // Amount in kobo/cents
    
    @Column(name = "total_amount", nullable = false)
    var totalAmount: Int // Amount in kobo/cents
) : TenantAwareEntity() {
    
    constructor() : this(
        invoice = Invoice(),
        description = "",
        unitAmount = 0,
        totalAmount = 0
    )
}
package com.haneef._school.entity

import java.util.UUID
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "payment_notifications",
    indexes = [
        Index(columnList = "school_id,status,created_at", name = "idx_payment_school_status"),
        Index(columnList = "school_id,parent_id", name = "idx_payment_school_parent")
    ]
)
class PaymentNotification(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    var invoice: Invoice,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", nullable = false)
    var parent: Parent,
    
    @Column(nullable = false)
    var amount: Int, // Amount in kobo/cents
    
    @Column(name = "payment_method", nullable = false)
    var paymentMethod: String,
    
    @Column(name = "payment_reference")
    var paymentReference: String? = null,
    
    @Column(name = "proof_of_payment_url")
    var proofOfPaymentUrl: String? = null,
    
    var notes: String? = null,
    
    @Enumerated(EnumType.STRING)
    var status: PaymentStatus = PaymentStatus.PENDING,
    
    @Column(name = "reviewed_by")
    var reviewedBy: UUID? = null,
    
    @Column(name = "reviewed_at")
    var reviewedAt: LocalDateTime? = null,
    
    @Column(name = "review_notes")
    var reviewNotes: String? = null
) : TenantAwareEntity() {
    
    constructor() : this(
        invoice = Invoice(),
        parent = Parent(),
        amount = 0,
        paymentMethod = ""
    )
}
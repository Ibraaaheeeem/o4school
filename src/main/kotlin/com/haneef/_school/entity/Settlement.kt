package com.haneef._school.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

enum class SettlementType {
    AUTO, MANUAL
}

@Entity
@Table(
    name = "settlements",
    indexes = [
        Index(columnList = "reference", name = "idx_settlement_reference"),
        Index(columnList = "wallet_id", name = "idx_settlement_wallet")
    ]
)
class Settlement(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false)
    var wallet: ParentWallet,

    @Column(name = "amount", nullable = false)
    var amount: BigDecimal,

    @Column(name = "currency", nullable = false)
    var currency: String = "NGN",

    @Column(name = "reference", nullable = false, unique = true)
    var reference: String,

    @Column(name = "status", nullable = false)
    var status: String,

    @Column(name = "payment_channel")
    var paymentChannel: String? = null,

    @Column(name = "payer_email")
    var payerEmail: String? = null,
    
    @Column(name = "transaction_date")
    var transactionDate: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "raw_payload", columnDefinition = "TEXT")
    var rawPayload: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_session_id")
    var academicSession: AcademicSession? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "term_id")
    var term: Term? = null,

    @Column(name = "reimbursed", nullable = false)
    var reimbursed: Boolean = false,

    @Column(name = "settlement_type", nullable = true)
    @Enumerated(EnumType.STRING)
    var settlementType: SettlementType? = SettlementType.AUTO

) : TenantAwareEntity() {
    
    constructor() : this(
        wallet = ParentWallet(),
        amount = BigDecimal.ZERO,
        reference = "",
        status = ""
    )
}

package com.haneef._school.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(
    name = "school_wallets",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["school_id"], name = "unique_school_wallet")
    ],
    indexes = [
        Index(columnList = "school_id", name = "idx_wallet_school"),
        Index(columnList = "account_number", name = "idx_wallet_school_account_number"),
        Index(columnList = "customer_code", name = "idx_wallet_school_customer_code")
    ]
)
class SchoolWallet(
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_id", nullable = false)
    var school: School,
    
    @Column(name = "customer_code", nullable = false)
    var customerCode: String,
    
    @Column(name = "account_number", nullable = false, unique = true)
    var accountNumber: String,
    
    @Column(name = "account_name", nullable = false)
    var accountName: String,
    
    @Column(name = "bank_name", nullable = false)
    var bankName: String,
    
    @Column(name = "bank_slug")
    var bankSlug: String? = null,
    
    @Column(name = "bank_id")
    var bankId: Int? = null,
    
    @Column(name = "balance", nullable = false)
    var balance: BigDecimal = BigDecimal.ZERO,
    
    @Column(name = "currency", nullable = false)
    var currency: String = "NGN",
    
    @Column(name = "paystack_account_id")
    var paystackAccountId: Long? = null,
    
    @Column(name = "assigned_at")
    var assignedAt: LocalDateTime? = null
) : BaseEntity() {
    
    constructor() : this(
        school = School(),
        customerCode = "",
        accountNumber = "",
        accountName = "",
        bankName = ""
    )
}

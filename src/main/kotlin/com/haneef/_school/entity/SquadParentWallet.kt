package com.haneef._school.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(
    name = "squad_parent_wallets",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["parent_id"], name = "unique_squad_parent_wallet")
    ],
    indexes = [
        Index(columnList = "parent_id", name = "idx_squad_wallet_parent"),
        Index(columnList = "account_number", name = "idx_squad_wallet_account_number")
    ]
)
class SquadParentWallet(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", nullable = false)
    var parent: Parent,
    
    @Column(name = "customer_identifier")
    var customerIdentifier: String? = null,
    
    @Column(name = "account_number", unique = true)
    var accountNumber: String? = null,
    
    @Column(name = "account_name")
    var accountName: String? = null,
    
    @Column(name = "bank_name")
    var bankName: String? = null,
    
    @Column(name = "balance", nullable = false)
    var balance: BigDecimal = BigDecimal.ZERO,
    
    @Column(name = "currency", nullable = false)
    var currency: String = "NGN",
    
    @Column(name = "assigned_at")
    var assignedAt: LocalDateTime? = null
) : TenantAwareEntity() {
    
    constructor() : this(
        parent = Parent(),
        customerIdentifier = "",
        accountNumber = "",
        accountName = "",
        bankName = ""
    )
    
    /**
     * Calculate debt status based on parent's children fees
     */
    fun getDebtStatus(totalBalance: BigDecimal): DebtStatus {
        return when {
            totalBalance <= BigDecimal.ZERO -> DebtStatus.CLEARED
            totalBalance < BigDecimal(50000) -> DebtStatus.LOW
            totalBalance < BigDecimal(200000) -> DebtStatus.MEDIUM
            else -> DebtStatus.HIGH
        }
    }
}

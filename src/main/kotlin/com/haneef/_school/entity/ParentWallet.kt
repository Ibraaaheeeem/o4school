package com.haneef._school.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(
    name = "parent_wallets",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["parent_id"], name = "unique_parent_wallet")
    ],
    indexes = [
        Index(columnList = "parent_id", name = "idx_wallet_parent"),
        Index(columnList = "account_number", name = "idx_wallet_account_number"),
        Index(columnList = "customer_code", name = "idx_wallet_customer_code")
    ]
)
class ParentWallet(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", nullable = false)
    var parent: Parent,
    
    @Column(name = "customer_code", nullable = false)
    var customerCode: String,
    
    @Column(name = "account_number", unique = true)
    var accountNumber: String? = null,
    
    @Column(name = "account_name")
    var accountName: String? = null,
    
    @Column(name = "bank_name")
    var bankName: String? = null,
    
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
) : TenantAwareEntity() {
    
    constructor() : this(
        parent = Parent(),
        customerCode = "",
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

enum class DebtStatus(val color: String, val label: String) {
    CLEARED("#10B981", "Cleared"),      // Green
    LOW("#3B82F6", "Low Debt"),         // Blue
    MEDIUM("#F59E0B", "Medium Debt"),   // Amber
    HIGH("#EF4444", "High Debt")        // Red
}

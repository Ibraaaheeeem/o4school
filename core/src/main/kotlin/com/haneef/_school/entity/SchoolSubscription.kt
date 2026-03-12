package com.haneef._school.entity

import jakarta.persistence.*
import java.time.LocalDateTime

enum class SubscriptionStatus {
    ACTIVE,
    EXPIRED,
    GRACE_PERIOD
}

@Entity
@Table(
    name = "school_subscriptions",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["school_id"], name = "unique_school_subscription")
    ]
)
class SchoolSubscription(
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_id", nullable = false)
    var school: School,

    @Column(name = "fee_collection_active", nullable = false)
    var feeCollectionActive: Boolean = false,

    @Column(name = "whatsapp_balance", nullable = false)
    var whatsappBalance: Int = 0,

    @Column(name = "sms_balance", nullable = false)
    var smsBalance: Int = 0,

    @Column(name = "ai_token_balance", nullable = false)
    var aiTokenBalance: Int = 0,

    @Column(name = "account_number")
    var accountNumber: String? = null,

    @Column(name = "bank_name")
    var bankName: String? = null,

    @Column(name = "terms_accepted", nullable = false)
    var termsAccepted: Boolean = false,

    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_status", nullable = false)
    var subscriptionStatus: SubscriptionStatus = SubscriptionStatus.ACTIVE,

    @Column(name = "valid_until")
    var validUntil: LocalDateTime? = null,

    @Column(name = "last_updated")
    var lastUpdated: LocalDateTime = LocalDateTime.now()
) : BaseEntity() {

    constructor() : this(
        school = School()
    )
}

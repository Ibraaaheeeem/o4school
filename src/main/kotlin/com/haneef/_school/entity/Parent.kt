package com.haneef._school.entity

import jakarta.persistence.*

@Entity
@Table(
    name = "parents",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["user_id", "school_id"], name = "unique_parent_user_school")
    ],
    indexes = [
        Index(columnList = "school_id,is_active", name = "idx_parent_school_active")
    ]
)
class Parent(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,
    
    @Column(name = "is_primary_contact")
    var isPrimaryContact: Boolean = false,
    
    @Column(name = "is_emergency_contact")
    var isEmergencyContact: Boolean = true,
    
    @Column(name = "is_financially_responsible")
    var isFinanciallyResponsible: Boolean = true,
    
    // Communication preferences
    @Column(name = "receive_academic_updates")
    var receiveAcademicUpdates: Boolean = true,
    
    @Column(name = "receive_financial_updates")
    var receiveFinancialUpdates: Boolean = true,
    
    @Column(name = "receive_disciplinary_updates")
    var receiveDisciplinaryUpdates: Boolean = true,

    @Column(name = "payment_distribution_type")
    var paymentDistributionType: String = "SPREAD", // SPREAD or SEQUENTIAL

    @Column(name = "payment_priority_order", columnDefinition = "TEXT")
    var paymentPriorityOrder: String? = null // Comma-separated Student IDs
) : TenantAwareEntity() {
    
    constructor() : this(
        user = User()
    )
    
    // Relationships
    @OneToMany(mappedBy = "parent", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var studentRelationships: MutableList<ParentStudent> = mutableListOf()
    
    // Computed property for active student relationships
    val activeStudentRelationships: List<ParentStudent>
        get() = studentRelationships.filter { it.isActive }
    
    @OneToMany(mappedBy = "parent", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var paymentNotifications: MutableList<PaymentNotification> = mutableListOf()

    @OneToOne(mappedBy = "parent", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var wallet: ParentWallet? = null

    @Transient
    var totalBalance: java.math.BigDecimal = java.math.BigDecimal.ZERO
}
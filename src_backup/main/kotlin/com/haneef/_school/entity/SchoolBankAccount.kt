package com.haneef._school.entity

import jakarta.persistence.*
import java.util.*

@Entity
@Table(name = "school_bank_accounts")
class SchoolBankAccount(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_id", nullable = false)
    var school: School = School(),

    @Column(name = "bank_name", nullable = false)
    var bankName: String? = "",

    @Column(name = "account_number", nullable = false)
    var accountNumber: String? = "",

    @Column(name = "account_name", nullable = false)
    var accountName: String? = "",

    @Column(name = "bank_code")
    var bankCode: String? = null,

    @Column(name = "recipient_code")
    var recipientCode: String? = null

) : BaseEntity() {
}

package com.haneef._school.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(name = "school_reimbursements")
class SchoolReimbursement(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_id", nullable = false)
    var school: School,

    @Column(name = "amount", nullable = false)
    var amount: BigDecimal,

    @Column(name = "currency", nullable = false)
    var currency: String = "NGN",

    @Column(name = "reference", nullable = false, unique = true)
    var reference: String,

    @Column(name = "status", nullable = false)
    var status: String, // PENDING, COMPLETED, FAILED

    @Column(name = "reimbursement_date")
    var reimbursementDate: LocalDateTime = LocalDateTime.now(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_session_id")
    var academicSession: AcademicSession? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "term_id")
    var term: Term? = null,

    @Column(name = "notes", columnDefinition = "TEXT")
    var notes: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by_id")
    var recordedBy: User? = null

) : BaseEntity() {
    constructor() : this(
        school = School(),
        amount = BigDecimal.ZERO,
        reference = "",
        status = "PENDING"
    )
}

package com.haneef._school.entity

import jakarta.persistence.*
import java.time.LocalDate

@Entity
@Table(
    name = "invoices",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["invoice_number", "school_id"], name = "unique_invoice_school")
    ],
    indexes = [
        Index(columnList = "school_id,student_id,status", name = "idx_invoice_school_student"),
        Index(columnList = "school_id,due_date,status", name = "idx_invoice_school_due"),
        Index(columnList = "school_id,academic_session_id,term", name = "idx_invoice_school_term")
    ]
)
class Invoice(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    var student: Student,
    
    @Column(name = "invoice_number", nullable = false)
    var invoiceNumber: String,
    
    @Column(name = "total_amount", nullable = false)
    var totalAmount: Int, // Amount in kobo/cents
    
    @Column(name = "amount_paid")
    var amountPaid: Int = 0,
    
    @Column(name = "balance_due")
    var balanceDue: Int = 0,
    
    @Column(name = "issue_date", nullable = false)
    var issueDate: LocalDate,
    
    @Column(name = "due_date", nullable = false)
    var dueDate: LocalDate,
    
    @Enumerated(EnumType.STRING)
    var status: InvoiceStatus = InvoiceStatus.DRAFT,
    
    var term: String? = null,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_session_id")
    var academicSession: AcademicSession? = null,
    
    var notes: String? = null
) : TenantAwareEntity() {
    
    constructor() : this(
        student = Student(),
        invoiceNumber = "",
        totalAmount = 0,
        issueDate = LocalDate.now(),
        dueDate = LocalDate.now()
    )
    
    // Relationships
    @OneToMany(mappedBy = "invoice", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var items: MutableList<InvoiceItem> = mutableListOf()
    
    @OneToMany(mappedBy = "invoice", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var paymentNotifications: MutableList<PaymentNotification> = mutableListOf()
}
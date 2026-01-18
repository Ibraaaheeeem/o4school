package com.haneef._school.entity

import java.util.UUID

import jakarta.persistence.*
import java.time.LocalDate

@Entity
@Table(
    name = "student_feedback",
    indexes = [
        Index(columnList = "school_id,student_id,feedback_date", name = "idx_feedback_school_student"),
        Index(columnList = "school_id,staff_id,feedback_date", name = "idx_feedback_school_staff")
    ]
)
class StudentFeedback(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    var student: Student,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id", nullable = false)
    var staff: Staff,
    
    @Column(name = "feedback_type", nullable = false)
    var feedbackType: String, // academic, behavioral, general
    
    var subject: String? = null,
    
    @Column(nullable = false)
    var content: String,
    
    var rating: String? = null, // excellent, good, satisfactory, needs_improvement
    
    @Column(name = "feedback_date")
    var feedbackDate: LocalDate = LocalDate.now(),
    
    var term: String? = null
) : TenantAwareEntity() {
    
    constructor() : this(
        student = Student(),
        staff = Staff(),
        feedbackType = "",
        content = ""
    )
    
    // Backward compatibility
    val teacherId: UUID?
        get() = staff.id
    
    val teacher: Staff
        get() = staff
}
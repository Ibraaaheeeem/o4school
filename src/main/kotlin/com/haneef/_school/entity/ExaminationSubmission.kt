package com.haneef._school.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "examination_submissions",
    indexes = [
        Index(columnList = "examination_id,student_id", name = "idx_submission_exam_student")
    ]
)
class ExaminationSubmission(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "examination_id", nullable = false)
    var examination: Examination,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    var student: Student,
    
    var status: String = "in_progress", // in_progress, submitted, graded
    
    var score: Double? = null,
    
    @Column(name = "attempt_count")
    var attemptCount: Int? = 1,
    
    @Column(name = "started_at")
    var startedAt: LocalDateTime? = null,
    
    @Column(name = "submitted_at")
    var submittedAt: LocalDateTime? = null
) : TenantAwareEntity() {
    
    constructor() : this(
        examination = Examination(),
        student = Student()
    )
}
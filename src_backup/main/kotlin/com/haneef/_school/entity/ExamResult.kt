package com.haneef._school.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "exam_results",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["exam_id", "student_id", "school_id"], name = "unique_exam_student_result")
    ],
    indexes = [
        Index(columnList = "school_id,exam_id", name = "idx_result_school_exam"),
        Index(columnList = "student_id,school_id", name = "idx_result_student_school")
    ]
)
class ExamResult(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_id", nullable = false)
    var exam: Exam,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    var student: Student,
    
    @Column(name = "marks_obtained", nullable = false)
    var marksObtained: Int,
    
    var grade: String? = null, // A+, A, B+, B, C, D, F
    
    var percentage: Int? = null,
    
    var position: Int? = null, // Position in class
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "graded_by", nullable = false)
    var gradedByStaff: Staff,
    
    @Column(name = "graded_at")
    var gradedAt: LocalDateTime = LocalDateTime.now(),
    
    var remarks: String? = null
) : TenantAwareEntity() {
    
    constructor() : this(
        exam = Exam(),
        student = Student(),
        marksObtained = 0,
        gradedByStaff = Staff()
    )
    
    // Backward compatibility
    val gradedByTeacher: Staff
        get() = gradedByStaff
}
package com.haneef._school.entity

import java.util.UUID

import jakarta.persistence.*
import java.time.LocalDate

@Entity
@Table(
    name = "exams",
    indexes = [
        Index(columnList = "school_id,class_id,exam_date", name = "idx_exam_school_class"),
        Index(columnList = "school_id,subject", name = "idx_exam_school_subject")
    ]
)
class Exam(
    @Column(name = "exam_name", nullable = false)
    var examName: String,
    
    @Column(nullable = false)
    var subject: String,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_id", nullable = false)
    var schoolClass: SchoolClass,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id", nullable = false)
    var staff: Staff,
    
    @Column(name = "exam_date", nullable = false)
    var examDate: LocalDate,
    
    @Column(name = "duration_minutes")
    var durationMinutes: Int? = null,
    
    @Column(name = "total_marks", nullable = false)
    var totalMarks: Int,
    
    @Column(name = "passing_marks")
    var passingMarks: Int? = null,
    
    @Column(name = "exam_type", nullable = false)
    var examType: String, // quiz, test, midterm, final, assignment
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "term_id")
    var term: Term? = null
) : TenantAwareEntity() {
    
    constructor() : this(
        examName = "",
        subject = "",
        schoolClass = SchoolClass(),
        staff = Staff(),
        examDate = LocalDate.now(),
        totalMarks = 0,
        examType = ""
    )
    
    // Relationships
    @OneToMany(mappedBy = "exam", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var results: MutableList<ExamResult> = mutableListOf()
    
    // Backward compatibility
    val teacherId: UUID?
        get() = staff.id
    
    val teacher: Staff
        get() = staff
}
package com.haneef._school.entity

import jakarta.persistence.*

@Entity
@Table(
    name = "assessments",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["school_id", "admission_number", "academic_session_id", "term_id"], name = "uq_assessment_school_student_term")
    ],
    indexes = [
        Index(columnList = "school_id,academic_session_id,term_id", name = "idx_assessment_school_session"),
        Index(columnList = "admission_number", name = "idx_assessment_admission")
    ]
)
class Assessment(
    @Column(name = "admission_number", nullable = false)
    var admissionNumber: String,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id")
    var student: Student? = null,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_session_id", nullable = false)
    var academicSession: AcademicSession,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "term_id", nullable = false)
    var term: Term,
    
    var attendance: Int = 0,
    
    // Behavioral assessments (scored 1-5 or similar scale)
    var fluency: Int = 0,
    var handwriting: Int = 0,
    var game: Int = 0,
    var initiative: Int = 0,
    
    @Column(name = "critical_thinking")
    var criticalThinking: Int = 0,
    
    var punctuality: Int = 0,
    var attentiveness: Int = 0,
    var neatness: Int = 0,
    
    @Column(name = "self_discipline")
    var selfDiscipline: Int = 0,
    
    var politeness: Int = 0,
    
    // Comments
    @Column(name = "class_teacher_comment")
    var classTeacherComment: String? = null,
    
    @Column(name = "head_teacher_comment")
    var headTeacherComment: String? = null
) : TenantAwareEntity() {
    
    constructor() : this(
        admissionNumber = "",
        academicSession = AcademicSession(),
        term = Term()
    )
    
    // Relationships
    @OneToMany(mappedBy = "assessment", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var scores: MutableList<SubjectScore> = mutableListOf()
}
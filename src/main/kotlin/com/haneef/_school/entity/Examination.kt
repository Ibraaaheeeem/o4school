package com.haneef._school.entity

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(
    name = "examinations",
    indexes = [
        Index(columnList = "school_id,class_id,subject_id,term,session", name = "idx_exam_school_context")
    ]
)
class Examination(
    @Column(nullable = false)
    var title: String, // e.g., "First Term CA 1 2024/2025"
    
    @Column(name = "exam_type", nullable = false)
    var examType: String, // CA 1, CA 2, Final Examination
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", nullable = false)
    var subject: Subject,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_id", nullable = false)
    var schoolClass: SchoolClass,
    
    @Column(nullable = false)
    var term: String, // First Term, Second Term, Third Term
    
    @Column(nullable = false)
    var session: String, // 2024/2025
    
    @Column(name = "created_by", nullable = false)
    var createdBy: UUID,
    
    @Column(name = "is_published")
    var isPublished: Boolean = false,
    
    @Column(name = "start_time")
    var startTime: LocalDateTime? = null,
    
    @Column(name = "end_time")
    var endTime: LocalDateTime? = null,
    
    @Column(name = "duration_minutes")
    var durationMinutes: Int? = 60,
    
    @Column(name = "total_marks")
    var totalMarks: Int? = null
) : TenantAwareEntity() {
    
    constructor() : this(
        title = "",
        examType = "",
        subject = Subject(),
        schoolClass = SchoolClass(),
        term = "",
        session = "",
        createdBy = UUID.randomUUID() // Placeholder, should be set properly
    )
    
    // Relationships
    @OneToMany(mappedBy = "examination", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC")
    var questions: MutableList<Question> = mutableListOf()
    
    @OneToMany(mappedBy = "examination", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var submissions: MutableList<ExaminationSubmission> = mutableListOf()

    @org.hibernate.annotations.Formula("(SELECT COUNT(es.id) FROM examination_submissions es WHERE es.examination_id = id)")
    var submissionCount: Int = 0
}
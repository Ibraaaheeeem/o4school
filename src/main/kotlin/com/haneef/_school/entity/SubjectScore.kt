package com.haneef._school.entity

import jakarta.persistence.*

@Entity
@Table(
    name = "subject_scores",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["assessment_id", "subject_id"], name = "uq_assessment_subject")
    ],
    indexes = [
        Index(columnList = "assessment_id", name = "idx_subject_score_assessment"),
        Index(columnList = "subject_id", name = "idx_subject_score_subject"),
        Index(columnList = "class_subject_id", name = "idx_subject_score_class_subject")
    ]
)
class SubjectScore(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assessment_id", nullable = false)
    var assessment: Assessment,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", nullable = false)
    var subject: Subject,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_subject_id", nullable = false)
    var classSubject: ClassSubject? = null,
    
    // Assessment scores
    @Column(name = "ca1_score")
    var ca1Score: Int? = null,
    
    @Column(name = "ca2_score")
    var ca2Score: Int? = null,
    
    @Column(name = "exam_score")
    var examScore: Int? = null,
    
    @Column(name = "total_score")
    var totalScore: Int? = null,
    
    @Column(name = "scores_json", columnDefinition = "TEXT")
    var scoresJson: String? = null,
    
    var grade: String? = null,
    var position: Int? = null,
    var remark: String? = null
) : TenantAwareEntity() {
    
    constructor() : this(
        assessment = Assessment(),
        subject = Subject()
    )
}
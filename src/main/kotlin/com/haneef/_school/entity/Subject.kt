package com.haneef._school.entity

import jakarta.persistence.*

@Entity
@Table(
    name = "subjects",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["subject_name", "school_id"], name = "unique_subject_school")
    ],
    indexes = [
        Index(columnList = "school_id,subject_code", name = "idx_subject_school_code")
    ]
)
class Subject(
    @Column(name = "subject_name", nullable = false)
    var subjectName: String,
    
    @Column(name = "subject_code")
    var subjectCode: String? = null,
    
    var description: String? = null,
    
    @Column(name = "is_core_subject")
    var isCoreSubject: Boolean? = false,
    
    @Column(name = "credit_hours")
    var creditHours: Int = 1
) : TenantAwareEntity() {
    
    constructor() : this(subjectName = "")
    
    // Relationships
    @OneToMany(mappedBy = "subject", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var classAssignments: MutableList<ClassSubject> = mutableListOf()
    
    @OneToMany(mappedBy = "subject", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var examinations: MutableList<Examination> = mutableListOf()
    
    @OneToMany(mappedBy = "subject", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var subjectScores: MutableList<SubjectScore> = mutableListOf()
}
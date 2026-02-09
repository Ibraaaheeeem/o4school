package com.haneef._school.entity

import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(
    name = "subject_mappings",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["subject_id", "grade_level"], name = "unique_subject_grade_mapping")
    ],
    indexes = [
        Index(columnList = "subject_id", name = "idx_mapping_subject")
    ]
)
class SubjectMapping(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", nullable = false)
    var subject: Subject,

    @Column(name = "grade_level", nullable = false)
    var gradeLevel: Int, // e.g., 7 for JSS1, 10 for SS1

    @Column(name = "elearner_subject_id", nullable = false)
    var elearnerSubjectId: UUID
) : GlobalEntity() {
    
    constructor() : this(
        subject = Subject(),
        gradeLevel = 0,
        elearnerSubjectId = UUID.randomUUID()
    )
}

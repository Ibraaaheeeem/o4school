package com.haneef._school.entity

import jakarta.persistence.*

@Entity
@Table(
    name = "parent_student_relationships",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["parent_id", "student_id", "school_id"], name = "unique_parent_student_school")
    ],
    indexes = [
        Index(columnList = "school_id,parent_id,student_id", name = "idx_parent_student_school")
    ]
)
class ParentStudent(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", nullable = false)
    var parent: Parent,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    var student: Student,
    
    @Column(name = "relationship_type", nullable = false)
    var relationshipType: String // biological, adoptive, guardian, etc.
) : TenantAwareEntity() {
    
    constructor() : this(
        parent = Parent(),
        student = Student(),
        relationshipType = ""
    )
}
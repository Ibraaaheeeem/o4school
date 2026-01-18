package com.haneef._school.entity

import java.util.UUID

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "class_subjects",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["class_id", "subject_id", "school_id"], name = "unique_class_subject_school")
    ],
    indexes = [
        Index(columnList = "school_id,class_id,subject_id", name = "idx_class_subject_school"),
        Index(columnList = "staff_id,school_id", name = "idx_class_subject_staff")
    ]
)
class ClassSubject(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_id", nullable = false)
    var schoolClass: SchoolClass,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", nullable = false)
    var subject: Subject,
    
    @Column(name = "assigned_by")
    var assignedBy: UUID? = null,
    
    @Column(name = "assigned_at")
    var assignedAt: LocalDateTime = LocalDateTime.now(),
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id")
    var staff: Staff? = null
) : TenantAwareEntity() {
    
    constructor() : this(
        schoolClass = SchoolClass(),
        subject = Subject()
    )
    
    // Backward compatibility properties
    val teacherId: UUID?
        get() = staff?.id
    
    val teacher: Staff?
        get() = staff
}
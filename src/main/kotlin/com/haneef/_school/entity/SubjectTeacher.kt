package com.haneef._school.entity

import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(
    name = "subject_teachers",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["staff_id", "subject_id", "class_id", "academic_session_id", "term_id", "school_id"], name = "unique_subject_teacher_assignment")
    ],
    indexes = [
        Index(columnList = "school_id,staff_id,academic_session_id,term_id", name = "idx_subject_teacher_school_staff"),
        Index(columnList = "class_id,subject_id,academic_session_id,term_id", name = "idx_subject_teacher_class_subject"),
        Index(columnList = "academic_session_id,term_id", name = "idx_subject_teacher_session_term")
    ]
)
class SubjectTeacher(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id", nullable = false)
    var staff: Staff,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", nullable = false)
    var subject: Subject,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_id", nullable = false)
    var schoolClass: SchoolClass,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_session_id", nullable = false)
    var academicSession: AcademicSession,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "term_id", nullable = false)
    var term: Term
) : TenantAwareEntity() {
    
    constructor() : this(
        staff = Staff(),
        subject = Subject(),
        schoolClass = SchoolClass(),
        academicSession = AcademicSession(),
        term = Term()
    )
}
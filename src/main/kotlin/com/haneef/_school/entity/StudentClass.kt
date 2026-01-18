package com.haneef._school.entity

import jakarta.persistence.*
import java.time.LocalDate

@Entity
@Table(
    name = "student_classes",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["student_id", "class_id", "academic_session_id", "term_id", "school_id"], name = "unique_student_class_session_term")
    ],
    indexes = [
        Index(columnList = "school_id,is_active", name = "idx_student_classes_school"),
        Index(columnList = "student_id,is_active", name = "idx_student_classes_student"),
        Index(columnList = "class_id,is_active", name = "idx_student_classes_class"),
        Index(columnList = "academic_session_id,school_id", name = "idx_student_classes_session"),
        Index(columnList = "term_id,school_id", name = "idx_student_classes_term"),
        Index(columnList = "academic_session_id,term_id,school_id", name = "idx_student_classes_session_term")
    ]
)
class StudentClass(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    var student: Student,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_id", nullable = false)
    var schoolClass: SchoolClass,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_session_id", nullable = false)
    var academicSession: AcademicSession,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "term_id", nullable = false)
    var term: Term,
    
    @Column(name = "enrollment_date")
    var enrollmentDate: LocalDate = LocalDate.now()
) : TenantAwareEntity() {
    
    constructor() : this(
        student = Student(),
        schoolClass = SchoolClass(),
        academicSession = AcademicSession(),
        term = Term()
    )
}
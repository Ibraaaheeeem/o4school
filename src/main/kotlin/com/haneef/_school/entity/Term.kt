package com.haneef._school.entity

import jakarta.persistence.*
import java.time.LocalDate

@Entity
@Table(
    name = "terms",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["academic_session_id", "term_name"], name = "unique_session_term_name")
    ],
    indexes = [
        Index(columnList = "school_id,academic_session_id", name = "idx_terms_school_session"),
        Index(columnList = "academic_session_id,is_current_term", name = "idx_terms_session_current")
    ]
)
class Term(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_session_id", nullable = false)
    var academicSession: AcademicSession,
    
    @Column(name = "term_name", nullable = false)
    var termName: String,
    
    @Column(name = "start_date", nullable = false)
    var startDate: LocalDate,
    
    @Column(name = "end_date")
    var endDate: LocalDate? = null,
    
    @Column(name = "is_current_term")
    var isCurrentTerm: Boolean = false,
    
    var status: String = "planned", // planned, active, completed
    
    var description: String? = null
) : TenantAwareEntity() {
    
    constructor() : this(
        academicSession = AcademicSession(),
        termName = "",
        startDate = LocalDate.now(),
        endDate = null
    )
    
    // Relationships
    @OneToMany(mappedBy = "term", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var calendarEvents: MutableList<SchoolCalendar> = mutableListOf()
    
    @OneToMany(mappedBy = "term", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var examinations: MutableList<Exam> = mutableListOf()
    
    @OneToMany(mappedBy = "term", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var assessments: MutableList<Assessment> = mutableListOf()
}
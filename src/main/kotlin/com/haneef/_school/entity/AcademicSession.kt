package com.haneef._school.entity

import jakarta.persistence.*
import java.time.LocalDate

@Entity
@Table(
    name = "academic_sessions",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["school_id", "session_year"], name = "unique_school_session_year")
    ],
    indexes = [
        Index(columnList = "school_id", name = "idx_academic_sessions_school_id"),
        Index(columnList = "session_year", name = "idx_academic_sessions_year"),
        Index(columnList = "is_current_session", name = "idx_academic_sessions_current")
    ]
)
class AcademicSession(
    @Column(name = "session_name", nullable = false)
    var sessionName: String,
    
    @Column(name = "session_year", nullable = false)
    var sessionYear: String,
    
    @Column(name = "start_date", nullable = false)
    var startDate: LocalDate,
    
    @Column(name = "end_date")
    var endDate: LocalDate? = null,
    
    @Column(name = "is_current_session")
    var isCurrentSession: Boolean = false,
    
    var status: String = "active", // active, completed, planned
    
    var notes: String? = null
) : TenantAwareEntity() {
    
    constructor() : this(
        sessionName = "",
        sessionYear = "",
        startDate = LocalDate.now()
    )
    
    // Relationships
    @OneToMany(mappedBy = "academicSession", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var terms: MutableList<Term> = mutableListOf()
    
    @OneToMany(mappedBy = "session", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var calendarEvents: MutableList<SchoolCalendar> = mutableListOf()
}
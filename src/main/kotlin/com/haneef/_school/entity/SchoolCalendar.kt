package com.haneef._school.entity

import jakarta.persistence.*
import java.time.LocalDate

@Entity
@Table(
    name = "school_calendar",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["session_id", "event_name", "start_date"], name = "unique_session_event_date_v2")
    ],
    indexes = [
        Index(columnList = "school_id,session_id", name = "idx_calendar_school_session"),
        Index(columnList = "event_type,start_date", name = "idx_calendar_type_date"),
        Index(columnList = "start_date,end_date", name = "idx_calendar_date_range")
    ]
)
class SchoolCalendar(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    var session: AcademicSession,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "term_id")
    var term: Term? = null,
    
    @Column(name = "event_name", nullable = false)
    var eventName: String,
    
    @Column(name = "event_type", nullable = false)
    @Enumerated(EnumType.STRING)
    var eventType: CalendarEventType,
    
    @Column(name = "start_date", nullable = false)
    var startDate: LocalDate,
    
    @Column(name = "end_date")
    var endDate: LocalDate? = null,
    
    var description: String? = null,
    
    @Column(name = "is_holiday")
    var isHoliday: Boolean = false,
    
    @Column(name = "is_exam_period")
    var isExamPeriod: Boolean = false,
    
    var color: String = "#3b82f6" // Default blue color for calendar display
) : TenantAwareEntity() {
    
    constructor() : this(
        session = AcademicSession(),
        eventName = "",
        eventType = CalendarEventType.TERM,
        startDate = LocalDate.now()
    )
}

enum class CalendarEventType {
    TERM,           // First Term, Second Term, etc.
    HOLIDAY,        // Mid-term break, Christmas holiday, etc.
    EXAM,           // Examination periods
    EVENT,          // School events, sports day, etc.
    RESUMPTION,     // School resumption dates
    VACATION        // School vacation periods
}
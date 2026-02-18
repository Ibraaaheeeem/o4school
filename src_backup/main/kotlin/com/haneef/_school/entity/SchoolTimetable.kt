package com.haneef._school.entity

import jakarta.persistence.*
import java.time.LocalTime

@Entity
@Table(
    name = "school_timetable",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["school_id", "day_of_week", "start_time"], name = "unique_school_day_time")
    ],
    indexes = [
        Index(columnList = "school_id,day_of_week", name = "idx_timetable_school_day"),
        Index(columnList = "start_time,end_time", name = "idx_timetable_time_range"),
        Index(columnList = "is_active", name = "idx_timetable_active")
    ]
)
class SchoolTimetable(
    @Column(name = "day_of_week", nullable = false)
    @Enumerated(EnumType.STRING)
    var dayOfWeek: DayOfWeek,
    
    @Column(name = "start_time", nullable = false)
    var startTime: LocalTime,
    
    @Column(name = "end_time", nullable = false)
    var endTime: LocalTime,
    
    @Column(name = "activity_name", nullable = false)
    var activityName: String,
    
    var description: String? = null,
    
    @Column(name = "activity_type")
    @Enumerated(EnumType.STRING)
    var activityType: TimetableActivityType = TimetableActivityType.ACADEMIC,
    
    @Column(name = "is_break")
    var isBreak: Boolean = false,
    
    @Column(name = "sort_order")
    var sortOrder: Int = 0
) : TenantAwareEntity() {
    
    constructor() : this(
        dayOfWeek = DayOfWeek.MONDAY,
        startTime = LocalTime.of(8, 0),
        endTime = LocalTime.of(9, 0),
        activityName = ""
    )
}

enum class DayOfWeek {
    MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY
}

enum class TimetableActivityType {
    ACADEMIC,       // Regular classes, lessons
    BREAK,          // Break time, lunch
    ASSEMBLY,       // Morning assembly, devotion
    SPORTS,         // Physical education, games
    EXTRACURRICULAR, // Clubs, societies
    ADMINISTRATIVE  // Staff meetings, etc.
}
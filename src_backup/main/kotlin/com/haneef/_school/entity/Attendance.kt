package com.haneef._school.entity

import java.util.UUID

import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalDateTime

enum class AttendanceStatus {
    PRESENT, ABSENT, LATE, EXCUSED
}

@Entity
@Table(
    name = "attendance",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["student_id", "class_id", "attendance_date", "school_id"], name = "unique_student_attendance")
    ],
    indexes = [
        Index(columnList = "school_id,attendance_date", name = "idx_attendance_school_date"),
        Index(columnList = "class_id,attendance_date", name = "idx_attendance_class_date")
    ]
)
class Attendance(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    var student: Student,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_id", nullable = false)
    var schoolClass: SchoolClass,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id", nullable = false)
    var staff: Staff,
    
    @Column(name = "attendance_date", nullable = false)
    var attendanceDate: LocalDate,
    
    @Enumerated(EnumType.STRING)
    var status: AttendanceStatus = AttendanceStatus.PRESENT,
    
    @Column(name = "arrival_time")
    var arrivalTime: LocalDateTime? = null,
    
    @Column(name = "departure_time")
    var departureTime: LocalDateTime? = null,
    
    var notes: String? = null
) : TenantAwareEntity() {
    
    constructor() : this(
        student = Student(),
        schoolClass = SchoolClass(),
        staff = Staff(),
        attendanceDate = LocalDate.now()
    )
    
    // Backward compatibility
    val teacherId: UUID?
        get() = staff.id
    
    val teacher: Staff
        get() = staff
}
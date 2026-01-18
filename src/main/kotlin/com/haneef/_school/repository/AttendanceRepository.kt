package com.haneef._school.repository

import com.haneef._school.entity.Attendance
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.util.UUID

@Repository
interface AttendanceRepository : JpaRepository<Attendance, UUID> {
    fun findBySchoolClassIdAndAttendanceDateAndSchoolIdAndIsActive(
        classId: UUID,
        attendanceDate: LocalDate,
        schoolId: UUID,
        isActive: Boolean
    ): List<Attendance>

    fun findByStudentIdAndSchoolClassIdAndAttendanceDateAndSchoolIdAndIsActive(
        studentId: UUID,
        classId: UUID,
        attendanceDate: LocalDate,
        schoolId: UUID,
        isActive: Boolean
    ): Attendance?

    fun findBySchoolClassIdAndAttendanceDateBetweenAndSchoolIdAndIsActive(
        classId: UUID,
        startDate: LocalDate,
        endDate: LocalDate,
        schoolId: UUID,
        isActive: Boolean
    ): List<Attendance>

    fun findByStudentIdAndSchoolIdAndIsActive(
        studentId: UUID,
        schoolId: UUID,
        isActive: Boolean
    ): List<Attendance>
}

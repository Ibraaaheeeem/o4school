package com.haneef._school.repository

import java.util.UUID

import com.haneef._school.entity.DayOfWeek
import com.haneef._school.entity.SchoolTimetable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalTime

@Repository
interface SchoolTimetableRepository : JpaRepository<SchoolTimetable, UUID> {
    
    fun findBySchoolIdAndIsActive(schoolId: UUID, isActive: Boolean): List<SchoolTimetable>

    fun findBySchoolIdAndDayOfWeekAndStartTime(schoolId: UUID, dayOfWeek: DayOfWeek, startTime: LocalTime): java.util.Optional<SchoolTimetable>
    
    fun findBySchoolIdAndDayOfWeekAndIsActive(schoolId: UUID, dayOfWeek: DayOfWeek, isActive: Boolean): List<SchoolTimetable>
    
    @Query("SELECT st FROM SchoolTimetable st WHERE st.schoolId = :schoolId AND st.dayOfWeek = :dayOfWeek AND st.isActive = :isActive ORDER BY st.startTime")
    fun findBySchoolIdAndDayOfWeekOrderByStartTime(
        @Param("schoolId") schoolId: UUID,
        @Param("dayOfWeek") dayOfWeek: DayOfWeek,
        @Param("isActive") isActive: Boolean
    ): List<SchoolTimetable>
    
    @Query("SELECT st FROM SchoolTimetable st WHERE st.schoolId = :schoolId AND st.isActive = :isActive ORDER BY st.dayOfWeek, st.startTime")
    fun findBySchoolIdAndIsActiveOrderByDayAndTime(@Param("schoolId") schoolId: UUID, @Param("isActive") isActive: Boolean): List<SchoolTimetable>
    
    fun existsBySchoolIdAndDayOfWeekAndStartTimeAndIsActive(
        schoolId: UUID,
        dayOfWeek: DayOfWeek,
        startTime: LocalTime,
        isActive: Boolean
    ): Boolean
    
    @Query("SELECT st FROM SchoolTimetable st WHERE st.schoolId = :schoolId AND st.dayOfWeek = :dayOfWeek AND st.isActive = :isActive AND st.startTime < :endTime AND st.endTime > :startTime")
    fun findOverlappingTimeSlots(
        @Param("schoolId") schoolId: UUID,
        @Param("dayOfWeek") dayOfWeek: DayOfWeek,
        @Param("startTime") startTime: LocalTime,
        @Param("endTime") endTime: LocalTime,
        @Param("isActive") isActive: Boolean
    ): List<SchoolTimetable>
}
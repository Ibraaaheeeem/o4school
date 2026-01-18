package com.haneef._school.repository

import java.util.UUID

import com.haneef._school.entity.CalendarEventType
import com.haneef._school.entity.SchoolCalendar
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface SchoolCalendarRepository : JpaRepository<SchoolCalendar, UUID> {
    
    fun findBySchoolIdAndIsActive(schoolId: UUID, isActive: Boolean): List<SchoolCalendar>
    
    fun findBySchoolIdAndSessionIdAndIsActive(schoolId: UUID, sessionId: UUID, isActive: Boolean): List<SchoolCalendar>
    
    @Query("SELECT sc FROM SchoolCalendar sc WHERE sc.schoolId = :schoolId AND sc.isActive = :isActive AND sc.startDate >= :startDate AND sc.startDate <= :endDate ORDER BY sc.startDate")
    fun findBySchoolIdAndDateRange(
        @Param("schoolId") schoolId: UUID,
        @Param("isActive") isActive: Boolean,
        @Param("startDate") startDate: LocalDate,
        @Param("endDate") endDate: LocalDate
    ): List<SchoolCalendar>
    
    fun findBySchoolIdAndEventTypeAndIsActive(schoolId: UUID, eventType: CalendarEventType, isActive: Boolean): List<SchoolCalendar>
    
    @Query("SELECT sc FROM SchoolCalendar sc JOIN FETCH sc.session WHERE sc.schoolId = :schoolId AND sc.isActive = :isActive ORDER BY sc.startDate")
    fun findBySchoolIdAndIsActiveWithSession(@Param("schoolId") schoolId: UUID, @Param("isActive") isActive: Boolean): List<SchoolCalendar>
    
    @Query("SELECT sc FROM SchoolCalendar sc WHERE sc.schoolId = :schoolId AND sc.isActive = :isActive AND :date BETWEEN sc.startDate AND COALESCE(sc.endDate, sc.startDate)")
    fun findBySchoolIdAndDate(@Param("schoolId") schoolId: UUID, @Param("isActive") isActive: Boolean, @Param("date") date: LocalDate): List<SchoolCalendar>
}
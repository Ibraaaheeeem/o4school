package com.haneef.school.models

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class SchoolCalendar(
    val id: UUID,
    val schoolId: UUID,
    val eventName: String,
    val eventDate: LocalDate,
    val description: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val isActive: Boolean
)

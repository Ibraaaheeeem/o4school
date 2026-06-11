package com.haneef.school.models

import java.time.LocalDateTime
import java.util.UUID

data class EducationTrack(
    val id: UUID,
    val schoolId: UUID,
    val name: String,
    val description: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val isActive: Boolean
)

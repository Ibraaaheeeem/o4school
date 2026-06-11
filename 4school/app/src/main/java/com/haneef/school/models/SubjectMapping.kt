package com.haneef.school.models

import java.time.LocalDateTime
import java.util.UUID

data class SubjectMapping(
    val id: UUID,
    val schoolId: UUID,
    val subjectId: UUID,
    val educationTrackId: UUID,
    val gradeLevel: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val isActive: Boolean
)


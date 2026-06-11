package com.haneef.school.models

import java.time.LocalDateTime
import java.util.UUID

data class ParentStudent(
    val id: UUID,
    val schoolId: UUID,
    val parentId: UUID,
    val studentId: UUID,
    val relationship: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val isActive: Boolean
)

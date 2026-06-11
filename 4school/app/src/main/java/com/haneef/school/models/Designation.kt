package com.haneef.school.models

import java.time.LocalDateTime
import java.util.UUID

data class Designation(
    val id: UUID,
    val schoolId: UUID,
    val name: String,
    val code: String,
    val description: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val isActive: Boolean
)

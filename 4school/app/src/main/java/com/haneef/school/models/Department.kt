package com.haneef.school.models

import java.time.LocalDateTime
import java.util.UUID

data class Department(
    val id: UUID,
    val schoolId: UUID,
    val name: String,
    val code: String,
    val headId: UUID?,
    val description: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val isActive: Boolean
)

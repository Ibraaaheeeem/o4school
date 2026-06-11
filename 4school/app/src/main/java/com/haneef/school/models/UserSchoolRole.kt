package com.haneef.school.models

import com.google.gson.annotations.SerializedName
import java.time.LocalDateTime
import java.util.UUID

data class UserSchoolRole(
    val id: UUID,
    @SerializedName("school_id")
    val schoolId: UUID,
    @SerializedName("user_id")
    val userId: UUID,
    @SerializedName("role_id")
    val roleId: UUID,
    @SerializedName("created_at")
    val createdAt: LocalDateTime,
    @SerializedName("updated_at")
    val updatedAt: LocalDateTime,
    @SerializedName("is_active")
    val isActive: Boolean
)


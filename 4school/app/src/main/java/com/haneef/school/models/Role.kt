package com.haneef.school.models

import com.google.gson.annotations.SerializedName
import java.time.LocalDateTime
import java.util.UUID

data class Role(
    val id: UUID,
    val name: String,
    @SerializedName("role_type")
    val roleType: String,
    val description: String?,
    @SerializedName("is_system_role")
    val isSystemRole: Boolean,
    @SerializedName("created_at")
    val createdAt: LocalDateTime,
    @SerializedName("updated_at")
    val updatedAt: LocalDateTime,
    @SerializedName("is_active")
    val isActive: Boolean
)


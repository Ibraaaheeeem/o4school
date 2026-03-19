package com.haneef._school.dto

import com.haneef._school.config.NativeDto

import java.util.UUID

@NativeDto
data class BroadcastRecipientDTO(
    val userId: UUID,
    val name: String,
    val phoneNumber: String?,
    val type: String, // STAFF or PARENT
    val roles: List<String> = emptyList()
)

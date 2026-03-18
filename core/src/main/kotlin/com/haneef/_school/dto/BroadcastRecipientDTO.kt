package com.haneef._school.dto

import java.util.UUID

data class BroadcastRecipientDTO(
    val userId: UUID,
    val name: String,
    val phoneNumber: String?,
    val type: String, // STAFF or PARENT
    val roles: List<String> = emptyList()
)

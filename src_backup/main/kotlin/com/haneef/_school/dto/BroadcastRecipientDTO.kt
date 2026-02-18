package com.haneef._school.dto

import java.util.UUID

data class BroadcastRecipientDTO(
    val userId: UUID,
    val name: String,
    val phoneNumber: String?,
    val roles: List<String>,
    val type: String // STAFF or PARENT
)

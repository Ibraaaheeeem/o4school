package com.haneef._school.event

import com.haneef._school.service.MultimodalChannel
import java.util.UUID

/**
 * Event published when a message fails (e.g. via webhook) and a fallback should be triggered.
 */
data class MessageFailureEvent(
    val channel: MultimodalChannel,
    val messageId: UUID,
    val recipientPhone: String,
    val schoolId: UUID?,
    val senderUserId: UUID?,
    val templateName: String?,
    val paramsJson: String?,
    val fallbackChannel: MultimodalChannel?,
    val broadcastId: UUID? = null,
    val content: String? = null
)

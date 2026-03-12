package com.haneef._school.entity

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "sms_messages")
class SmsMessage(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    var id: UUID? = null,

    @Column(nullable = false)
    var recipientPhone: String,

    @Column(nullable = false, columnDefinition = "TEXT")
    var content: String,

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    var direction: MessageDirection = MessageDirection.OUTGOING,

    @Column(nullable = false)
    var status: String = "PENDING", // PENDING, SENT, FAILED, DELIVERED

    var externalMessageId: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    var user: User? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_id")
    var school: School? = null,

    @Column(nullable = false)
    var triggerFallback: Boolean = false,

    @Column(nullable = false)
    var isFallback: Boolean = false,

    @Column(name = "template_name")
    var templateName: String? = null,

    @Column(columnDefinition = "TEXT")
    var paramsJson: String? = null,

    @Column(name = "fallback_channel")
    var fallbackChannel: String? = null,

    var broadcastId: UUID? = null,

    @Column(nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()
)

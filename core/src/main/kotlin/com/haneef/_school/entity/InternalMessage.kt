package com.haneef._school.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "internal_messages",
    indexes = [
        Index(columnList = "thread_id,created_at", name = "idx_internal_message_thread_created")
    ]
)
class InternalMessage(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "thread_id", nullable = false)
    var thread: InternalMessageThread,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    var sender: User,

    @Column(columnDefinition = "TEXT", nullable = false)
    var content: String

) : TenantAwareEntity() {

    constructor() : this(
        thread = InternalMessageThread(),
        sender = User(),
        content = ""
    )
}

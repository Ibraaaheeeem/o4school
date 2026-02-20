package com.haneef._school.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "internal_message_participants",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["thread_id", "user_id"], name = "unique_participant_thread_user")
    ],
    indexes = [
        Index(columnList = "user_id,unread_count", name = "idx_internal_participant_user_unread")
    ]
)
class InternalMessageParticipant(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "thread_id", nullable = false)
    var thread: InternalMessageThread,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @Column(name = "unread_count", nullable = false)
    var unreadCount: Int = 0,

    @Column(name = "last_read_at")
    var lastReadAt: LocalDateTime? = null

) : TenantAwareEntity() {

    constructor() : this(
        thread = InternalMessageThread(),
        user = User()
    )
}

package com.haneef._school.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "internal_message_threads",
    indexes = [
        Index(columnList = "school_id,created_at", name = "idx_internal_thread_school_created")
    ]
)
class InternalMessageThread(
    @Column(nullable = false)
    var subject: String,

    @Column(name = "last_message_preview")
    var lastMessagePreview: String? = null

) : TenantAwareEntity() {

    @OneToMany(mappedBy = "thread", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var participants: MutableSet<InternalMessageParticipant> = mutableSetOf()

    @OneToMany(mappedBy = "thread", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var messages: MutableList<InternalMessage> = mutableListOf()

    constructor() : this(subject = "")
}

package com.haneef._school.repository

import com.haneef._school.entity.InternalMessage
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface InternalMessageRepository : JpaRepository<InternalMessage, UUID> {
    fun findByThreadIdOrderByCreatedAtAsc(threadId: UUID): List<InternalMessage>
}

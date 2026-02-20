package com.haneef._school.repository

import com.haneef._school.entity.InternalMessageParticipant
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface InternalMessageParticipantRepository : JpaRepository<InternalMessageParticipant, UUID> {
    
    fun findByUserIdAndThreadSchoolIdOrderByThreadUpdatedAtDesc(userId: UUID, schoolId: UUID): List<InternalMessageParticipant>
    
    fun findByThreadId(threadId: UUID): List<InternalMessageParticipant>
    
    fun findByThreadIdAndUserId(threadId: UUID, userId: UUID): InternalMessageParticipant?

    @Query("SELECT SUM(p.unreadCount) FROM InternalMessageParticipant p WHERE p.user.id = :userId AND p.schoolId = :schoolId")
    fun countTotalUnreadForUser(@Param("userId") userId: UUID, @Param("schoolId") schoolId: UUID): Int?
}

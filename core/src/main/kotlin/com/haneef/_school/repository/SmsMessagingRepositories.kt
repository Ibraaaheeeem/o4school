package com.haneef._school.repository

import com.haneef._school.entity.SmsMessage
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface SmsMessageRepository : JpaRepository<SmsMessage, UUID> {
    fun findByRecipientPhoneOrderByCreatedAtDesc(recipientPhone: String): List<SmsMessage>
    fun findBySchoolIdOrderByCreatedAtDesc(schoolId: UUID): List<SmsMessage>
    fun findByUserIdOrderByCreatedAtDesc(userId: UUID): List<SmsMessage>
    fun findByExternalMessageId(externalMessageId: String): List<SmsMessage>
    fun findByBroadcastId(broadcastId: UUID): List<SmsMessage>
    fun findBySchoolIdAndBroadcastIdIsNotNullOrderByCreatedAtDesc(schoolId: UUID): List<SmsMessage>

    @org.springframework.data.jpa.repository.Query("SELECT COUNT(m) FROM SmsMessage m WHERE m.school.id = :schoolId AND m.direction = com.haneef._school.entity.MessageDirection.INCOMING AND m.status != 'READ'")
    fun countUnreadIncomingBySchoolId(@org.springframework.data.repository.query.Param("schoolId") schoolId: java.util.UUID): Long

    @org.springframework.data.jpa.repository.Query("SELECT COUNT(m) FROM SmsMessage m WHERE m.school.id = :schoolId AND m.recipientPhone = :phone AND m.direction = com.haneef._school.entity.MessageDirection.INCOMING AND m.status != 'READ'")
    fun countUnreadByRecipient(@org.springframework.data.repository.query.Param("schoolId") schoolId: java.util.UUID, @org.springframework.data.repository.query.Param("phone") phone: String): Long
}

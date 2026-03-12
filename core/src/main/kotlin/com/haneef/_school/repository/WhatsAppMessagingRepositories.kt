package com.haneef._school.repository

import com.haneef._school.entity.WhatsAppMessage
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface WhatsAppMessageRepository : JpaRepository<WhatsAppMessage, UUID> {
    fun findByRecipientPhoneOrderByCreatedAtDesc(recipientPhone: String): List<WhatsAppMessage>
    fun findTopByRecipientPhoneAndDirectionOrderByCreatedAtDesc(recipientPhone: String, direction: com.haneef._school.entity.MessageDirection): WhatsAppMessage?
    fun findBySchoolIdOrderByCreatedAtDesc(schoolId: UUID): List<WhatsAppMessage>
    fun findByMetaMessageId(metaMessageId: String): WhatsAppMessage?
    fun findByUserIdOrderByCreatedAtDesc(userId: UUID): List<WhatsAppMessage>
    fun findByBroadcastId(broadcastId: UUID): List<WhatsAppMessage>
    fun findBySchoolIdAndBroadcastIdIsNotNullOrderByCreatedAtDesc(schoolId: UUID): List<WhatsAppMessage>


    @org.springframework.data.jpa.repository.Query("SELECT m FROM WhatsAppMessage m WHERE m.school.id = :schoolId AND m.id IN (SELECT MAX(m2.id) FROM WhatsAppMessage m2 WHERE m2.school.id = :schoolId GROUP BY m2.recipientPhone) ORDER BY m.createdAt DESC")
    fun findLatestMessagesByRecipient(@org.springframework.data.repository.query.Param("schoolId") schoolId: UUID): List<WhatsAppMessage>

    @org.springframework.data.jpa.repository.Query("SELECT COUNT(m) FROM WhatsAppMessage m WHERE m.school.id = :schoolId AND m.direction = com.haneef._school.entity.MessageDirection.INCOMING AND m.status != 'READ'")
    fun countUnreadIncomingBySchoolId(@org.springframework.data.repository.query.Param("schoolId") schoolId: java.util.UUID): Long

    @org.springframework.data.jpa.repository.Query("SELECT COUNT(m) FROM WhatsAppMessage m WHERE m.school.id = :schoolId AND m.recipientPhone = :phone AND m.direction = com.haneef._school.entity.MessageDirection.INCOMING AND m.status != 'READ'")
    fun countUnreadByRecipient(@org.springframework.data.repository.query.Param("schoolId") schoolId: java.util.UUID, @org.springframework.data.repository.query.Param("phone") phone: String): Long
}

@Repository
interface FeeReminderScheduleRepository : JpaRepository<com.haneef._school.entity.FeeReminderSchedule, UUID> {
    fun findBySchoolIdAndIsActive(schoolId: UUID, isActive: Boolean): List<com.haneef._school.entity.FeeReminderSchedule>
    fun findAllByIsActive(isActive: Boolean): List<com.haneef._school.entity.FeeReminderSchedule>
}

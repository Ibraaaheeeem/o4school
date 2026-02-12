package com.haneef._school.repository

import com.haneef._school.entity.WhatsAppMessage
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface WhatsAppMessageRepository : JpaRepository<WhatsAppMessage, UUID> {
    fun findByRecipientPhoneOrderByCreatedAtDesc(recipientPhone: String): List<WhatsAppMessage>
    fun findBySchoolIdOrderByCreatedAtDesc(schoolId: UUID): List<WhatsAppMessage>
    fun findByMetaMessageId(metaMessageId: String): WhatsAppMessage?

    @org.springframework.data.jpa.repository.Query("SELECT m FROM WhatsAppMessage m WHERE m.school.id = :schoolId AND m.id IN (SELECT MAX(m2.id) FROM WhatsAppMessage m2 WHERE m2.school.id = :schoolId GROUP BY m2.recipientPhone)")
    fun findLatestMessagesByRecipient(@org.springframework.data.repository.query.Param("schoolId") schoolId: UUID): List<WhatsAppMessage>
}

@Repository
interface FeeReminderScheduleRepository : JpaRepository<com.haneef._school.entity.FeeReminderSchedule, UUID> {
    fun findBySchoolIdAndIsActive(schoolId: UUID, isActive: Boolean): List<com.haneef._school.entity.FeeReminderSchedule>
    fun findAllByIsActive(isActive: Boolean): List<com.haneef._school.entity.FeeReminderSchedule>
}

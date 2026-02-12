package com.haneef._school.repository

import com.haneef._school.entity.WhatsAppMessage
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface WhatsAppMessageRepository : JpaRepository<WhatsAppMessage, UUID> {
    fun findByRecipientPhoneOrderByCreatedAtDesc(recipientPhone: String): List<WhatsAppMessage>
    fun findBySchoolIdOrderByCreatedAtDesc(schoolId: UUID): List<WhatsAppMessage>
}

@Repository
interface FeeReminderScheduleRepository : JpaRepository<com.haneef._school.entity.FeeReminderSchedule, UUID> {
    fun findBySchoolIdAndIsActive(schoolId: UUID, isActive: Boolean): List<com.haneef._school.entity.FeeReminderSchedule>
    fun findAllByIsActive(isActive: Boolean): List<com.haneef._school.entity.FeeReminderSchedule>
}

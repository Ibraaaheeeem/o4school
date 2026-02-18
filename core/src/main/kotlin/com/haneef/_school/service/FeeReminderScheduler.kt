package com.haneef._school.service

import com.haneef._school.repository.FeeReminderScheduleRepository
import com.haneef._school.repository.ParentRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.util.*

@Service
class FeeReminderScheduler(
    private val feeReminderScheduleRepository: FeeReminderScheduleRepository,
    private val parentRepository: ParentRepository,
    private val financialService: FinancialService,
    private val whatsappService: WhatsAppService
) {

    // Run every hour to check for schedules that need to be triggered
    @Scheduled(cron = "0 0 * * * *")
    fun processScheduledReminders() {
        val activeSchedules = feeReminderScheduleRepository.findAllByIsActive(true)
        val now = LocalDateTime.now()

        for (schedule in activeSchedules) {
            if (shouldRun(schedule, now)) {
                sendRemindersForSchool(schedule.schoolId)
                schedule.lastRunAt = now
                feeReminderScheduleRepository.save(schedule)
            }
        }
    }

    private fun shouldRun(schedule: com.haneef._school.entity.FeeReminderSchedule, now: LocalDateTime): Boolean {
        val lastRun = schedule.lastRunAt
        
        return when (schedule.frequency.uppercase()) {
            "DAILY" -> lastRun == null || lastRun.toLocalDate().isBefore(now.toLocalDate())
            "WEEKLY" -> (lastRun == null || lastRun.plusWeeks(1).isBefore(now)) && now.dayOfWeek == DayOfWeek.MONDAY
            "MONTHLY" -> (lastRun == null || lastRun.plusMonths(1).isBefore(now)) && now.dayOfMonth == 1
            "WEEKENDS" -> (now.dayOfWeek == DayOfWeek.SATURDAY || now.dayOfWeek == DayOfWeek.SUNDAY) && 
                         (lastRun == null || lastRun.toLocalDate().isBefore(now.toLocalDate()))
            else -> false
        }
    }

    private fun sendRemindersForSchool(schoolId: UUID) {
        val parents = parentRepository.findBySchoolIdAndIsActive(schoolId, true)
        
        for (parent in parents) {
            if (parent.receiveFinancialUpdates) {
                val balance = financialService.calculateParentBalance(parent)
                if (balance > java.math.BigDecimal.ZERO) {
                    sendWhatsAppReminder(parent, balance)
                }
            }
        }
    }

    private fun sendWhatsAppReminder(parent: com.haneef._school.entity.Parent, balance: java.math.BigDecimal) {
        val phoneNumber = parent.user.phoneNumber ?: return
        
        // Using a template message for better delivery and compliance
        // Assuming a template named 'fee_reminder' exists with:
        // {{1}} = Parent Name
        // {{2}} = Balance Amount
        // {{3}} = School Name
        
        val components = listOf(
            mapOf(
                "type" to "body",
                "parameters" to listOf(
                    mapOf("type" to "text", "text" to (parent.user.fullName ?: "Parent")),
                    mapOf("type" to "text", "text" to balance.toString()),
                    mapOf("type" to "text", "text" to "4School")
                )
            )
        )
        
        whatsappService.sendTemplateMessage(phoneNumber, "fee_reminder", components = components, user = parent.user)
    }
}

package com.haneef._school.controller

import com.haneef._school.repository.*
import com.haneef._school.service.*
import jakarta.servlet.http.HttpSession
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import java.util.*

@Controller
@RequestMapping("/admin/dashboard/messaging")
@PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'SCHOOL_ADMIN')")
class WhatsAppMessagingController(
    private val whatsappService: WhatsAppService,
    private val messageRepository: WhatsAppMessageRepository,
    private val feeReminderScheduleRepository: FeeReminderScheduleRepository,
    private val parentRepository: ParentRepository,
    private val schoolRepository: SchoolRepository
) {

    @GetMapping
    fun messagingDashboard(model: Model, session: HttpSession): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID ?: return "redirect:/select-school"
        val school = schoolRepository.findById(selectedSchoolId).orElse(null)
        
        val recentMessages = messageRepository.findBySchoolIdOrderByCreatedAtDesc(selectedSchoolId)
        val schedules = feeReminderScheduleRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
        
        model.addAttribute("recentMessages", recentMessages)
        model.addAttribute("schedules", schedules)
        model.addAttribute("school", school)
        model.addAttribute("isSchoolAdmin", true)
        
        return "dashboard/messaging"
    }

    @PostMapping("/broadcast")
    fun sendBroadcast(
        @RequestParam("message") message: String,
        @RequestParam("recipientType") recipientType: String,
        session: HttpSession
    ): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID ?: return "redirect:/select-school"
        
        val recipients = when (recipientType) {
            "ALL_PARENTS" -> parentRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
            else -> emptyList()
        }
        
        recipients.forEach { parent ->
            parent.user.phoneNumber?.let { phone ->
                whatsappService.sendTextMessage(phone, message, parent.user)
            }
        }
        
        return "redirect:/admin/dashboard/messaging?broadcastSuccess=true"
    }

    @PostMapping("/schedule-reminder")
    fun scheduleReminder(
        @RequestParam("frequency") frequency: String,
        session: HttpSession
    ): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID ?: return "redirect:/select-school"
        
        val schedule = com.haneef._school.entity.FeeReminderSchedule(
            schoolId = selectedSchoolId,
            frequency = frequency
        )
        feeReminderScheduleRepository.save(schedule)
        
        return "redirect:/admin/dashboard/messaging?scheduleSuccess=true"
    }
}

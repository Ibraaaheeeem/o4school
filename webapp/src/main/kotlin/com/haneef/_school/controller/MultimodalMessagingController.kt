package com.haneef._school.controller

import com.haneef._school.dto.BroadcastRecipientDTO
import com.haneef._school.dto.BroadcastRecipientFilter
import com.haneef._school.entity.User
import com.haneef._school.repository.*
import com.haneef._school.service.*
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpSession
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import java.time.LocalTime
import java.util.*

@Controller
@RequestMapping("/admin/dashboard/multimodal")
@PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'SCHOOL_ADMIN')")
class MultimodalMessagingController(
    private val multimodalMessagingService: MultimodalMessagingService,
    private val broadcastService: BroadcastService,
    private val whatsappTemplateService: WhatsAppTemplateService,
    private val educationTrackRepository: EducationTrackRepository,
    private val departmentRepository: DepartmentRepository,
    private val schoolClassRepository: SchoolClassRepository,
    private val userRepository: UserRepository,
    private val subscriptionService: SubscriptionService,
    private val schoolRepository: SchoolRepository,
    private val smsMessageRepository: SmsMessageRepository,
    private val whatsappMessageRepository: WhatsAppMessageRepository,
    private val internalMessagingService: InternalMessagingService
) {

    @GetMapping
    fun messagingDashboard(model: Model, session: HttpSession): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID ?: return "redirect:/select-school"
        val school = schoolRepository.findById(selectedSchoolId).orElse(null)
        
        // Add data for broadcast filtering
        model.addAttribute("tracks", educationTrackRepository.findBySchoolIdAndIsActive(selectedSchoolId, true))
        model.addAttribute("classes", schoolClassRepository.findBySchoolIdAndIsActiveWithTrack(selectedSchoolId, true))
        model.addAttribute("departments", departmentRepository.findBySchoolIdAndIsActive(selectedSchoolId, true))
        
        model.addAttribute("school", school)
        model.addAttribute("isSchoolAdmin", true)
        
        try {
            val subscription = subscriptionService.getSubscription(selectedSchoolId)
            model.addAttribute("smsBalance", subscription.smsBalance)
            model.addAttribute("whatsappBalance", subscription.whatsappBalance)
        } catch (e: Exception) {
            model.addAttribute("smsBalance", 0)
            model.addAttribute("whatsappBalance", 0)
        }
        
        return "dashboard/multimodal-messaging"
    }

    @PostMapping("/broadcast")
    fun sendBroadcast(
        @ModelAttribute filter: BroadcastRecipientFilter,
        @RequestParam("strategy") strategy: String, // "SIMULTANEOUS" or "FALLBACK"
        @RequestParam("channelPriority") channelPriorityString: String, // e.g. "WHATSAPP,SMS"
        @RequestParam(value = "templateName", required = false) templateName: String?,
        @RequestParam(value = "pushToInternal", required = false, defaultValue = "false") pushToInternal: Boolean,
        @RequestParam(value = "subject", required = false, defaultValue = "Multimodal Broadcast") subject: String,
        @RequestParam(value = "message", required = false) message: String?,
        request: HttpServletRequest,
        session: HttpSession,
        authentication: Authentication
    ): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID ?: return "redirect:/select-school"
        val recipients = broadcastService.getDetailedRecipients(filter, selectedSchoolId)
        
        val currentUser = userRepository.findByEmail(authentication.name).orElseThrow()
        
        val template = if (!templateName.isNullOrBlank()) {
            whatsappTemplateService.getAllTemplates(selectedSchoolId).find { it.templateName == templateName }
        } else null

        val manualParams = mutableListOf<Map<String, Any>>()
        val namedManualParams = mutableMapOf<String, String>()
        
        // Collect all manual parameters from request (both indexed and named)
        val paramMap = request.parameterMap
        
        // Named manual parameters (from broadcast modal)
        paramMap.keys.filter { it.startsWith("manualParam_") }.forEach { key ->
            val value = paramMap[key]?.firstOrNull() ?: ""
            val variableKey = key.substringAfter("manualParam_")
            namedManualParams[variableKey] = value
        }
        
        // Indexed parameters (legacy fallback)
        if (!templateName.isNullOrBlank() && template?.parameterMapping.isNullOrBlank()) {
            // Check both templateParam_ (old) and manualParam_ (new)
            val combinedKeys = (paramMap.keys.filter { it.startsWith("templateParam_") } + 
                               paramMap.keys.filter { it.startsWith("manualParam_") }).distinct().sortedBy { 
                                   it.substringAfter("_").toIntOrNull() ?: 999 
                               }
            combinedKeys.forEach { key ->
                val value = paramMap[key]?.firstOrNull() ?: ""
                manualParams.add(mapOf("type" to "text", "text" to value))
            }
        }

        val userMap = userRepository.findAllById(recipients.map { it.userId }).associateBy { it.id }
        
        val priorityList = channelPriorityString.split(",").mapNotNull { 
            try { MultimodalChannel.valueOf(it.trim().uppercase()) } catch (e: Exception) { null }
        }

        var messagesSuccessfullySent = 0
        val broadcastId = UUID.randomUUID()

        recipients.forEach { recipient ->
            val user = userMap[recipient.userId]
            val phoneNumber = recipient.phoneNumber
            
            // Check target role for template
            var matchesTarget = true
            if (template != null) {
                val targetRole = template.targetRole
                val userRoles = user?.schoolRoles?.map { it.role.name } ?: emptyList()
                matchesTarget = when (targetRole) {
                    "PARENT" -> userRoles.any { it == "PARENT" }
                    "STAFF" -> userRoles.any { it == "STAFF" || it == "TEACHER" || it == "PRINCIPAL" }
                    "STUDENT" -> userRoles.any { it == "STUDENT" }
                    else -> true
                }
            }

            if (matchesTarget) {
                val results = multimodalMessagingService.sendMultimodalMessage(
                    recipient = user,
                    phoneNumber = phoneNumber,
                    schoolId = selectedSchoolId,
                    senderId = currentUser.id!!,
                    senderUser = currentUser,
                    strategy = strategy,
                    channelPriority = priorityList,
                    template = template,
                    templateName = templateName,
                    namedManualParams = namedManualParams,
                    manualParams = manualParams,
                    broadcastId = broadcastId
                )
                if (results.values.any { it }) {
                    messagesSuccessfullySent++
                }
            }
        }

        // Push to internal if requested
        if (pushToInternal) {
            try {
                internalMessagingService.sendInternalBroadcast(
                    schoolId = selectedSchoolId,
                    senderId = currentUser.id!!,
                    subject = subject,
                    content = message,
                    templateName = templateName,
                    recipients = recipients,
                    extraParams = namedManualParams
                )
            } catch (e: Exception) {
                // Log and continue
            }
        }
        
        return "redirect:/admin/dashboard/multimodal?broadcastSent=true&count=$messagesSuccessfullySent"
    }

    @GetMapping("/broadcasts")
    @ResponseBody
    fun getBroadcasts(
        @RequestParam(required = false) channel: String?,
        @RequestParam(required = false) startDate: String?,
        @RequestParam(required = false) endDate: String?,
        session: HttpSession
    ): List<Map<String, Any>> {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID ?: return emptyList()
        
        val startDateTime = startDate?.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it).atStartOfDay() }
        val endDateTime = endDate?.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it).atTime(LocalTime.MAX) }

        val smsBroadcasts = if (channel == null || channel == "SMS") {
            smsMessageRepository.findBySchoolIdAndBroadcastIdIsNotNullOrderByCreatedAtDesc(selectedSchoolId)
        } else emptyList()
        
        val whatsappBroadcasts = if (channel == null || channel == "WHATSAPP") {
            whatsappMessageRepository.findBySchoolIdAndBroadcastIdIsNotNullOrderByCreatedAtDesc(selectedSchoolId)
        } else emptyList()
        
        val allBroadcasts = (smsBroadcasts + whatsappBroadcasts)
            .groupBy { 
                when(it) {
                    is com.haneef._school.entity.SmsMessage -> it.broadcastId
                    is com.haneef._school.entity.WhatsAppMessage -> it.broadcastId
                    else -> null
                }
            }
            .filter { it.key != null }
            .map { (id, messages) ->
                val templateName = messages.mapNotNull { 
                    when(it) {
                        is com.haneef._school.entity.SmsMessage -> it.templateName
                        is com.haneef._school.entity.WhatsAppMessage -> it.templateName
                        else -> null
                    }
                }.firstOrNull() ?: "Free-form"

                val createdAt = messages.mapNotNull { 
                    when(it) {
                        is com.haneef._school.entity.SmsMessage -> it.createdAt
                        is com.haneef._school.entity.WhatsAppMessage -> it.createdAt
                        else -> null
                    }
                }.maxOrNull() ?: java.time.LocalDateTime.now()

                val phones = messages.map { 
                    when(it) {
                        is com.haneef._school.entity.SmsMessage -> it.recipientPhone
                        is com.haneef._school.entity.WhatsAppMessage -> it.recipientPhone
                        else -> ""
                    }
                }.distinct()

                mapOf(
                    "id" to id!!,
                    "templateName" to templateName,
                    "recipientCount" to phones.size,
                    "createdAt" to createdAt,
                    "channels" to messages.map { if (it is com.haneef._school.entity.SmsMessage) "SMS" else "WHATSAPP" }.distinct()
                )
            }
            .filter { broadcast ->
                val createdAt = broadcast["createdAt"] as java.time.LocalDateTime
                val afterStart = startDateTime?.let { createdAt >= it } ?: true
                val beforeEnd = endDateTime?.let { createdAt <= it } ?: true
                afterStart && beforeEnd
            }
            .sortedByDescending { it["createdAt"] as java.time.LocalDateTime }
            
        return allBroadcasts
    }

    @GetMapping("/broadcast-details/{broadcastId}")
    @ResponseBody
    fun getBroadcastDetails(@PathVariable broadcastId: UUID): List<Map<String, Any>> {
        val smsMessages = smsMessageRepository.findByBroadcastId(broadcastId)
        val whatsappMessages = whatsappMessageRepository.findByBroadcastId(broadcastId)
        
        val recipients = (smsMessages.map { it.recipientPhone } + whatsappMessages.map { it.recipientPhone }).distinct()
        
        return recipients.map { phone ->
            val sms = smsMessages.find { it.recipientPhone == phone }
            val whatsapp = whatsappMessages.find { it.recipientPhone == phone }
            val user = (sms?.user ?: whatsapp?.user)
            val fullName = user?.let { "${it.firstName ?: ""} ${it.lastName ?: ""}".trim() }
            
            mapOf(
                "userName" to (if (fullName.isNullOrBlank()) phone else fullName),
                "phoneNumber" to phone,
                "smsStatus" to (sms?.status ?: "NONE"),
                "whatsappStatus" to (whatsapp?.status ?: "NONE"),
                "content" to (sms?.content ?: whatsapp?.content ?: "")
            )
        }
    }
}

package com.haneef._school.controller

import com.haneef._school.repository.*
import com.haneef._school.service.*
import com.haneef._school.dto.BroadcastRecipientFilter
import com.haneef._school.dto.BroadcastRecipientDTO
import jakarta.servlet.http.HttpSession
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import java.util.*

@Controller
@RequestMapping("/admin/dashboard/whatsapp")
@PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'SCHOOL_ADMIN')")
class WhatsAppMessagingController(
    private val whatsappService: WhatsAppService,
    private val messageRepository: WhatsAppMessageRepository,
    private val feeReminderScheduleRepository: FeeReminderScheduleRepository,
    private val parentRepository: ParentRepository,
    private val schoolRepository: SchoolRepository,
    private val userRepository: UserRepository,
    private val templateService: WhatsAppTemplateService,
    private val broadcastService: BroadcastService,
    private val educationTrackRepository: EducationTrackRepository,
    private val departmentRepository: DepartmentRepository,
    private val schoolClassRepository: SchoolClassRepository,
    private val templateParameterResolver: TemplateParameterResolver,
    private val subscriptionService: SubscriptionService,
    private val internalMessagingService: InternalMessagingService,
    private val activityLogService: ActivityLogService,
    @org.springframework.beans.factory.annotation.Value("\${paystack.public.key:}") private val paystackPublicKey: String,
    @org.springframework.beans.factory.annotation.Value("\${squad.public.key:}") private val squadPublicKey: String,
    @org.springframework.beans.factory.annotation.Value("\${WHATSAPP_SUB_RATE:500}") private val whatsappSubRate: Long
) {
    data class WhatsAppConversationDTO(
        val lastMessage: com.haneef._school.entity.WhatsAppMessage,
        val unreadCount: Long
    )


    @GetMapping
    fun messagingDashboard(model: Model, session: HttpSession): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID ?: return "redirect:/select-school"
        val school = schoolRepository.findById(selectedSchoolId).orElse(null)
        
        val latestConversations = getDeduplicatedConversations(selectedSchoolId)
        val schedules = feeReminderScheduleRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
        
        // Add data for broadcast filtering
        model.addAttribute("tracks", educationTrackRepository.findBySchoolIdAndIsActive(selectedSchoolId, true))
        model.addAttribute("classes", schoolClassRepository.findBySchoolIdAndIsActiveWithTrack(selectedSchoolId, true))
        model.addAttribute("departments", departmentRepository.findBySchoolIdAndIsActive(selectedSchoolId, true))
        
        model.addAttribute("conversations", latestConversations)
        model.addAttribute("schedules", schedules)
        model.addAttribute("school", school)
        model.addAttribute("isSchoolAdmin", true)
        
        try {
            val subscription = subscriptionService.getSubscription(selectedSchoolId)
            model.addAttribute("whatsappBalance", subscription.whatsappBalance)
            model.addAttribute("paystackPublicKey", paystackPublicKey)
            model.addAttribute("squadPublicKey", squadPublicKey)
            model.addAttribute("whatsappSubRate", whatsappSubRate)
        } catch (e: Exception) {
            model.addAttribute("whatsappBalance", 0)
        }
        
        return "dashboard/messaging"
    }

    @GetMapping("/conversations")
    fun getConversations(model: Model, session: HttpSession): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID ?: return "fragments/error :: error-message"
        val conversations = getDeduplicatedConversations(selectedSchoolId)
        model.addAttribute("conversations", conversations)
        return "dashboard/messaging-fragments :: conversation-list"
    }
    
    private fun getDeduplicatedConversations(schoolId: UUID): List<WhatsAppConversationDTO> {
        val rawConversations = messageRepository.findBySchoolIdOrderByCreatedAtDesc(schoolId)
        return rawConversations.groupBy { 
            it.user?.id?.toString() ?: it.recipientPhone.removePrefix("+").takeLast(10)
        }.map { (_, group) -> 
            val lastMessage = group.maxByOrNull { it.createdAt }!!
            val unreadCount = messageRepository.countUnreadByRecipient(schoolId, lastMessage.recipientPhone)
            WhatsAppConversationDTO(lastMessage, unreadCount)
        }.sortedByDescending { it.lastMessage.createdAt }
    }

    @GetMapping("/thread/{recipient}")
    fun getThread(
        @PathVariable recipient: String,
        model: Model,
        session: HttpSession
    ): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID ?: return "fragments/error :: error-message"
        val cleanedRecipient = recipient.removePrefix("+")
        
        val last10 = if (cleanedRecipient.length >= 10) cleanedRecipient.substring(cleanedRecipient.length - 10) else cleanedRecipient
        val user = userRepository.findByPhoneNumber(cleanedRecipient).orElse(null)
            ?: userRepository.findByPhoneNumber("+" + cleanedRecipient).orElse(null)
            ?: userRepository.findAll().firstOrNull { u -> 
                val ph = u.phoneNumber ?: ""
                ph.endsWith(last10)
            }
        
        // Fetch messages for this recipient (and user if matched)
        var rawMessages = if (user != null) {
            messageRepository.findByUserIdOrderByCreatedAtDesc(user.id!!)
        } else {
            emptyList()
        }
        
        rawMessages = rawMessages + messageRepository.findByRecipientPhoneOrderByCreatedAtDesc(cleanedRecipient) +
                      messageRepository.findByRecipientPhoneOrderByCreatedAtDesc("+" + cleanedRecipient)
                      
        val messages = rawMessages.distinctBy { it.id }
            .filter { it.school?.id == selectedSchoolId }
            .sortedBy { it.createdAt }

        // Mark incoming messages as READ
        val unreadIncoming = messages.filter { it.direction == com.haneef._school.entity.MessageDirection.INCOMING && it.status != "READ" }
        if (unreadIncoming.isNotEmpty()) {
            unreadIncoming.forEach { it.status = "READ" }
            messageRepository.saveAll(unreadIncoming)
        }
        
        model.addAttribute("messages", messages)
        val displayName = if (user?.fullName != null) "${user.fullName} ($recipient)" else recipient
        model.addAttribute("recipient", displayName)
        return "dashboard/messaging-fragments :: chat-thread"
    }
    
    @GetMapping("/thread/{recipient}/status")
    @ResponseBody
    fun getThreadStatus(
        @PathVariable recipient: String,
        session: HttpSession
    ): Map<String, Any?> {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID ?: return mapOf("canReply" to false)
        val cleanedRecipient = recipient.removePrefix("+")
        
        val last10 = if (cleanedRecipient.length >= 10) cleanedRecipient.substring(cleanedRecipient.length - 10) else cleanedRecipient
        val user = userRepository.findByPhoneNumber(cleanedRecipient).orElse(null)
            ?: userRepository.findByPhoneNumber("+" + cleanedRecipient).orElse(null)
            ?: userRepository.findAll().firstOrNull { u -> 
                val ph = u.phoneNumber ?: ""
                ph.endsWith(last10)
            }
            
        var rawMessages = if (user != null) {
            messageRepository.findByUserIdOrderByCreatedAtDesc(user.id!!)
        } else {
            emptyList()
        }
        
        rawMessages = rawMessages + messageRepository.findByRecipientPhoneOrderByCreatedAtDesc(cleanedRecipient) +
                      messageRepository.findByRecipientPhoneOrderByCreatedAtDesc("+" + cleanedRecipient)
                      
        val lastIncoming = rawMessages.distinctBy { it.id }
            .filter { it.school?.id == selectedSchoolId && it.direction == com.haneef._school.entity.MessageDirection.INCOMING }
            .maxByOrNull { it.createdAt }
            
        val canReply = lastIncoming != null && lastIncoming.createdAt.isAfter(java.time.LocalDateTime.now().minusHours(24))
        
        return mapOf(
            "canReply" to canReply,
            "lastIncoming" to lastIncoming?.createdAt?.toString()
        )
    }

    @PostMapping("/reply")
    @ResponseBody
    fun sendReply(
        @RequestParam("recipient") recipient: String,
        @RequestParam("message") message: String,
        session: HttpSession,
        authentication: org.springframework.security.core.Authentication
    ): Map<String, Any> {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID ?: return mapOf<String, Any>("success" to false, "error" to "No school selected")
        
        val cleanedRecipient = recipient.removePrefix("+")
        val last10 = if (cleanedRecipient.length >= 10) cleanedRecipient.substring(cleanedRecipient.length - 10) else cleanedRecipient
        val user = userRepository.findByPhoneNumber(cleanedRecipient).orElse(null)
            ?: userRepository.findByPhoneNumber("+" + cleanedRecipient).orElse(null)
            ?: userRepository.findAll().firstOrNull { u -> 
                val ph = u.phoneNumber ?: ""
                ph.endsWith(last10)
            }
            
        // Enforce 24-hour rule
        var rawMessages = if (user != null) {
            messageRepository.findByUserIdOrderByCreatedAtDesc(user.id!!)
        } else {
            emptyList()
        }
        rawMessages = rawMessages + messageRepository.findByRecipientPhoneOrderByCreatedAtDesc(cleanedRecipient) +
                      messageRepository.findByRecipientPhoneOrderByCreatedAtDesc("+" + cleanedRecipient)
                      
        val lastIncoming = rawMessages.distinctBy { it.id }
            .filter { it.school?.id == selectedSchoolId && it.direction == com.haneef._school.entity.MessageDirection.INCOMING }
            .maxByOrNull { it.createdAt }
            
        val canReply = lastIncoming != null && lastIncoming.createdAt.isAfter(java.time.LocalDateTime.now().minusHours(24))
        
        if (!canReply) {
            return mapOf<String, Any>(
                "success" to false, 
                "error" to "Meta API strictly requires the user to have sent a message within the last 24 hours before you can send a free-form reply. Please use a WhatsApp Template instead."
            )
        }
            
        val success = whatsappService.sendTextMessage(recipient, message, user, selectedSchoolId)
        
        if (success) {
            val userRole = (session.getAttribute("selectedRole") as? String) ?: "USER"
            val currentUser = authentication.principal as CustomUserDetails
            activityLogService.logWhatsAppSent(
                selectedSchoolId, currentUser.user.id!!, userRole, recipient, message
            )
        }
        
        return mapOf("success" to success)
    }


    @PostMapping("/broadcast/preview-count")
    @ResponseBody
    fun getPreviewCount(
        @ModelAttribute filter: BroadcastRecipientFilter,
        session: HttpSession
    ): Map<String, Any> {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID ?: return mapOf("count" to 0)
        val recipients = broadcastService.getDetailedRecipients(filter, selectedSchoolId)
        return mapOf("count" to recipients.size)
    }

    @PostMapping("/broadcast/recipients")
    @ResponseBody
    fun getRecipients(
        @ModelAttribute filter: BroadcastRecipientFilter,
        session: HttpSession
    ): List<BroadcastRecipientDTO> {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID ?: return emptyList()
        return broadcastService.getDetailedRecipients(filter, selectedSchoolId)
    }

    @GetMapping("/broadcast/search")
    @ResponseBody
    fun searchRecipients(
        @RequestParam("query") query: String,
        @RequestParam(value = "recipientType", required = false) recipientType: String?,
        session: HttpSession
    ): List<BroadcastRecipientDTO> {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID ?: return emptyList()
        return broadcastService.searchDetailedRecipients(query, selectedSchoolId, recipientType)
    }

    @GetMapping("/broadcast/templates")
    @ResponseBody
    fun getTemplates(
        @RequestParam(value = "recipientType", required = false) recipientType: String?,
        @RequestParam(value = "channel", required = false) channel: String?,
        session: HttpSession
    ): List<Map<String, Any>> {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID ?: return emptyList()
        val templates = templateService.getBroadcastTemplates(recipientType, channel ?: "WHATSAPP")
        
        return templates.map { template ->
            mapOf(
                "name" to template.templateName,
                "language" to template.language,
                "status" to template.status,
                "category" to template.category,
                "parameter_count" to template.parameterCount,
                "mapping" to (template.parameterMapping ?: ""),
                "components" to (template.componentsJson ?: "[]"),
                "target_role" to template.targetRole
            )
        }
    }

    @PostMapping("/broadcast/test")
    @ResponseBody
    fun sendTestBroadcast(
        @RequestParam(value = "templateName", required = false) templateName: String?,
        @RequestParam(value = "message", required = false) message: String?,
        @RequestParam("testPhone") testPhone: String,
        request: jakarta.servlet.http.HttpServletRequest,
        session: HttpSession,
        authentication: Authentication
    ): Map<String, Any> {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID ?: return mapOf("success" to false, "error" to "No school selected")
        val currentUserEmail = authentication.name
        val currentUser = userRepository.findByEmail(currentUserEmail).orElseThrow()
        
        val paramMap = request.parameterMap
        val namedManualParams = mutableMapOf<String, String>()
        paramMap.keys.filter { it.startsWith("manualParam_") }.forEach { key ->
            namedManualParams[key.substringAfter("manualParam_")] = paramMap[key]?.firstOrNull() ?: ""
        }

        val template = if (!templateName.isNullOrBlank()) {
            templateService.getAllTemplates().find { it.templateName == templateName }
        } else null

        return try {
            if (!templateName.isNullOrBlank()) {
                val components = if (template != null) {
                    val resolved = templateParameterResolver.resolveAllParameters(currentUser, selectedSchoolId, template, namedManualParams)
                    if (resolved.isNotEmpty()) listOf(mapOf("type" to "body", "parameters" to resolved)) else emptyList()
                } else emptyList()

                val success = whatsappService.sendTemplateMessage(
                    to = testPhone,
                    templateName = templateName,
                    languageCode = template?.language ?: "en_GB",
                    components = components,
                    user = currentUser,
                    schoolId = selectedSchoolId
                )
                mapOf("success" to success)
            } else if (!message.isNullOrBlank()) {
                val success = whatsappService.sendTextMessage(testPhone, message, user = currentUser, schoolId = selectedSchoolId)
                mapOf("success" to success)
            } else {
                mapOf("success" to false, "error" to "Empty message")
            }
        } catch (e: Exception) {
            mapOf("success" to false, "error" to (e.message ?: "Unknown error"))
        }
    }

    @PostMapping("/broadcast")
    fun sendBroadcast(
        @ModelAttribute filter: BroadcastRecipientFilter,
        @RequestParam(value = "templateName", required = false) templateName: String?,
        @RequestParam(value = "message", required = false) message: String?,
        @RequestParam(value = "pushToInternal", required = false, defaultValue = "false") pushToInternal: Boolean,
        @RequestParam(value = "subject", required = false, defaultValue = "WhatsApp Broadcast") subject: String,
        request: jakarta.servlet.http.HttpServletRequest,
        session: HttpSession,
        authentication: Authentication
    ): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID ?: return "redirect:/select-school"
        val recipients = broadcastService.getDetailedRecipients(filter, selectedSchoolId)
        
        // Fetch current user and their subscription to validate balance
        val currentUserEmail = authentication.name
        val currentUser = userRepository.findByEmail(currentUserEmail).orElseThrow()
        val subscription = subscriptionService.getSubscription(selectedSchoolId)
        
        // Pre-fetch template mapping if applicable
        val template = if (!templateName.isNullOrBlank()) {
            templateService.getAllTemplates().find { it.templateName == templateName }
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
        
        // Pre-calculate valid recipients to ensure proper balance check
        val validRecipients = recipients.filter { recipient ->
            val isQualified = when (template?.targetRole) {
                "PARENT" -> recipient.type == "PARENT"
                "STAFF" -> recipient.type == "STAFF"
                else -> true 
            }
            isQualified && recipient.phoneNumber != null
        }
        
        if (subscription.whatsappBalance < validRecipients.size) {
        return "redirect:/admin/dashboard/whatsapp?broadcastError=insufficient_units"
    }

    var messagesSuccessfullySent = 0
    val broadcastId = UUID.randomUUID()

    validRecipients.forEach { recipient ->
            val phoneNumber = recipient.phoneNumber!!
            val user = userMap[recipient.userId]
            
            if (!templateName.isNullOrBlank()) {
                val components = if (template != null && user != null) {
                    val resolved = templateParameterResolver.resolveAllParameters(user, selectedSchoolId, template, namedManualParams)
                    if (resolved.isNotEmpty()) listOf(mapOf("type" to "body", "parameters" to resolved)) else emptyList()
                } else if (manualParams.isNotEmpty()) {
                    listOf(mapOf("type" to "body", "parameters" to manualParams))
                } else emptyList()

                whatsappService.sendTemplateMessage(
                to = phoneNumber,
                templateName = templateName,
                languageCode = template?.language ?: "en_GB",
                components = components,
                user = user,
                schoolId = selectedSchoolId,
                broadcastId = broadcastId
            )
            messagesSuccessfullySent++
            
            // Log individual WhatsApp in broadcast
            val userRole = (session.getAttribute("selectedRole") as? String) ?: "USER"
            activityLogService.logWhatsAppSent(
                selectedSchoolId, currentUser.id!!, userRole, phoneNumber, "[Template: $templateName]"
            )
        } else if (!message.isNullOrBlank()) {
            val success = whatsappService.sendTextMessage(phoneNumber, message, user = user, schoolId = selectedSchoolId, broadcastId = broadcastId)
            if (success) {
                messagesSuccessfullySent++
                // Log individual WhatsApp in broadcast
                val userRole = (session.getAttribute("selectedRole") as? String) ?: "USER"
                activityLogService.logWhatsAppSent(
                    selectedSchoolId, currentUser.id!!, userRole, phoneNumber, message
                )
            }
        }
    }
        
        // Deduct exactly the number of messages successfully dispatched
        if (messagesSuccessfullySent > 0) {
            try {
                subscriptionService.deductTokens(
                    schoolId = selectedSchoolId,
                    userId = currentUser.id!!,
                    feature = com.haneef._school.entity.ServiceFeature.WHATSAPP_MESSAGING,
                    amount = messagesSuccessfullySent,
                    description = "WhatsApp Broadcast"
                )
            } catch (e: Exception) {
                // Ignore deduction errors post-send to ensure user gets sent confirmation
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

        return "redirect:/admin/dashboard/whatsapp?broadcastSent=true&count=$messagesSuccessfullySent"
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
        
        return "redirect:/admin/dashboard/whatsapp?scheduleSuccess=true"
    }

    @GetMapping("/templates")
    fun listTemplates(model: Model, session: HttpSession): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID ?: return "fragments/error :: error-message"
        val templates = templateService.getAllTemplates().filter { it.status == "APPROVED" }
        model.addAttribute("templates", templates)
        return "dashboard/messaging-fragments :: template-list"
    }

    @PostMapping("/templates/sync")
    @ResponseBody
    fun syncTemplates(session: HttpSession): Map<String, Any> {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID ?: return mapOf("success" to false)
        val success = templateService.syncTemplates()
        return mapOf("success" to success)
    }

    @PostMapping("/templates/{templateId}/mapping")
    @ResponseBody
    fun updateMapping(
        @PathVariable templateId: UUID,
        @RequestParam("mapping") mapping: String,
        session: HttpSession
    ): Map<String, Any> {
        return try {
            templateService.updateParameterMapping(templateId, mapping)
            mapOf("success" to true)
        } catch (e: Exception) {
            mapOf("success" to false, "message" to (e.message ?: "Failed to update mapping"))
        }
    }

    @GetMapping("/templates/{id}")
    fun getTemplate(@PathVariable id: UUID, model: Model, session: HttpSession): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID ?: return "fragments/error :: error-message"
        val template = templateService.getTemplate(id)
        model.addAttribute("template", template)
        return "dashboard/messaging-fragments :: template-view"
    }

    @GetMapping("/templates/new")
    fun newTemplateForm(model: Model): String {
        return "dashboard/messaging-fragments :: template-editor"
    }

    @PostMapping("/templates")
    @ResponseBody
    fun createTemplate(
        @RequestParam("name") name: String,
        @RequestParam("category") category: String,
        @RequestParam("language") language: String,
        @RequestParam("body") bodyText: String,
        @RequestParam(value = "header", required = false) headerText: String?,
        @RequestParam(value = "footer", required = false) footerText: String?,
        @RequestParam(value = "mapping", required = false) mapping: String?,
        request: jakarta.servlet.http.HttpServletRequest,
        session: HttpSession
    ): Map<String, Any> {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID ?: return mapOf("success" to false)
        
        val components = mutableListOf<Map<String, Any>>()
        val paramMap = request.parameterMap

        if (!headerText.isNullOrBlank()) {
            val headerSamples = paramMap.keys.filter { it.startsWith("sample_header_") }
                .sortedBy { it.substringAfterLast("_").toInt() }
                .map { request.getParameter(it) ?: "" }

            val component = mutableMapOf<String, Any>(
                "type" to "HEADER",
                "format" to "TEXT",
                "text" to headerText
            )
            if (headerSamples.isNotEmpty()) {
                component["example"] = mapOf<String, Any>("header_text" to headerSamples)
            }
            components.add(component)
        }

        val bodySamples = paramMap.keys.filter { it.startsWith("sample_body_") }
            .sortedBy { it.substringAfterLast("_").toInt() }
            .map { request.getParameter(it) ?: "" }

        val bodyComponent = mutableMapOf<String, Any>(
            "type" to "BODY",
            "text" to bodyText
        )
        if (bodySamples.isNotEmpty()) {
            bodyComponent["example"] = mapOf<String, Any>("body_text" to listOf<List<String>>(bodySamples))
        }
        components.add(bodyComponent)

        if (!footerText.isNullOrBlank()) {
            components.add(mapOf("type" to "FOOTER", "text" to footerText))
        }

        val success = templateService.createMetaTemplate(name, category, language, components, mapping)
        return mapOf("success" to success)
    }

    @DeleteMapping("/templates/{id}")
    @ResponseBody
    fun deleteTemplate(@PathVariable id: UUID, session: HttpSession): Map<String, Any> {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID ?: return mapOf("success" to false)
        val success = templateService.deleteMetaTemplate(id)
        return mapOf("success" to success)
    }
}

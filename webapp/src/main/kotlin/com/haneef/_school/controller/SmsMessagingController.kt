package com.haneef._school.controller

import com.haneef._school.dto.BroadcastRecipientDTO
import com.haneef._school.dto.BroadcastRecipientFilter
import com.haneef._school.entity.MessageDirection
import com.haneef._school.entity.SmsMessage
import com.haneef._school.repository.*
import com.haneef._school.service.*
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import jakarta.servlet.http.HttpSession
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime
import java.util.*

@Controller
@RequestMapping("/admin/dashboard/sms")
@PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'SCHOOL_ADMIN')")
class SmsMessagingController(
    private val smsMessagingService: SmsMessagingService,
    private val smsMessageRepository: SmsMessageRepository,
    private val parentRepository: ParentRepository,
    private val schoolRepository: SchoolRepository,
    private val userRepository: UserRepository,
    private val whatsappTemplateService: WhatsAppTemplateService, // Reusing templates for SMS content
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
    @org.springframework.beans.factory.annotation.Value("\${SMS_SUB_RATE:5}") private val smsSubRate: Long
) {
    data class SmsConversationDTO(
        val lastMessage: SmsMessage,
        val unreadCount: Long
    )


    @GetMapping
    fun messagingDashboard(model: Model, session: HttpSession): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID ?: return "redirect:/select-school"
        val school = schoolRepository.findById(selectedSchoolId).orElse(null)
        
        val latestConversations = getDeduplicatedConversations(selectedSchoolId)
        
        // Add data for broadcast filtering
        model.addAttribute("tracks", educationTrackRepository.findBySchoolIdAndIsActive(selectedSchoolId, true))
        model.addAttribute("classes", schoolClassRepository.findBySchoolIdAndIsActiveWithTrack(selectedSchoolId, true))
        model.addAttribute("departments", departmentRepository.findBySchoolIdAndIsActive(selectedSchoolId, true))
        
        model.addAttribute("conversations", latestConversations)
        model.addAttribute("school", school)
        model.addAttribute("isSchoolAdmin", true)
        
        try {
            val subscription = subscriptionService.getSubscription(selectedSchoolId)
            model.addAttribute("smsBalance", subscription.smsBalance)
            model.addAttribute("paystackPublicKey", paystackPublicKey)
            model.addAttribute("squadPublicKey", squadPublicKey)
            model.addAttribute("smsSubRate", smsSubRate)
        } catch (e: Exception) {
            model.addAttribute("smsBalance", 0)
        }
        
        return "dashboard/sms-messaging"
    }

    @GetMapping("/conversations")
    fun getConversations(model: Model, session: HttpSession): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID ?: return "fragments/error :: error-message"
        val conversations = getDeduplicatedConversations(selectedSchoolId)
        model.addAttribute("conversations", conversations)
        return "dashboard/sms-fragments :: conversation-list"
    }
    
    private fun getDeduplicatedConversations(schoolId: UUID): List<SmsConversationDTO> {
        val rawConversations = smsMessageRepository.findBySchoolIdOrderByCreatedAtDesc(schoolId)
        return rawConversations.groupBy { 
            it.user?.id?.toString() ?: it.recipientPhone.takeLast(10)
        }.map { (_, group) -> 
            val lastMessage = group.maxByOrNull { it.createdAt }!!
            val unreadCount = smsMessageRepository.countUnreadByRecipient(schoolId, lastMessage.recipientPhone)
            SmsConversationDTO(lastMessage, unreadCount)
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
            smsMessageRepository.findByUserIdOrderByCreatedAtDesc(user.id!!)
        } else {
            emptyList()
        }
        
        rawMessages = rawMessages + smsMessageRepository.findByRecipientPhoneOrderByCreatedAtDesc(cleanedRecipient) +
                      smsMessageRepository.findByRecipientPhoneOrderByCreatedAtDesc("+" + cleanedRecipient)
                      
        val messages = rawMessages.distinctBy { it.id }
            .filter { it is SmsMessage && it.school?.id == selectedSchoolId }
            .sortedBy { it.createdAt }

        // Mark incoming messages as READ
        val unreadIncoming = messages.filter { it.direction == com.haneef._school.entity.MessageDirection.INCOMING && it.status != "READ" }
        if (unreadIncoming.isNotEmpty()) {
            unreadIncoming.forEach { it.status = "READ" }
            smsMessageRepository.saveAll(unreadIncoming)
        }
        
        model.addAttribute("messages", messages)
        model.addAttribute("recipient", recipient)
        return "dashboard/sms-fragments :: chat-thread"
    }

    @PostMapping("/reply")
    @ResponseBody
    fun sendReply(
        @RequestParam("recipient") recipient: String,
        @RequestParam("message") message: String,
        session: HttpSession,
        authentication: Authentication
    ): Map<String, Any> {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID ?: return mapOf("success" to false, "error" to "No school selected")
        val currentUser = userRepository.findByEmail(authentication.name).orElseThrow()
        
        val success = smsMessagingService.sendSms(
            to = recipient,
            content = message,
            schoolId = selectedSchoolId,
            userId = currentUser.id!!,
            user = currentUser
        )
        
        if (success) {
            val userRole = (authentication.principal as CustomUserDetails).authorities.firstOrNull()?.authority ?: "USER"
            activityLogService.logSmsSent(
                selectedSchoolId, currentUser.id!!, userRole, recipient, message
            )
        }
        
        return mapOf("success" to success)
    }

    @PostMapping("/broadcast/recipients")
    @ResponseBody
    fun getBroadcastRecipients(
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
    fun getBroadcastTemplates(
        @RequestParam(value = "recipientType", required = false) recipientType: String?,
        session: HttpSession
    ): List<Map<String, Any>> {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID ?: return emptyList()
        return whatsappTemplateService.getBroadcastTemplates(recipientType, "SMS").map { template ->
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
        val currentUser = userRepository.findByEmail(authentication.name).orElseThrow()
        
        val paramMap = request.parameterMap
        val namedManualParams = mutableMapOf<String, String>()
        paramMap.keys.filter { it.startsWith("manualParam_") }.forEach { key ->
            namedManualParams[key.substringAfter("manualParam_")] = paramMap[key]?.firstOrNull() ?: ""
        }

        val template = if (!templateName.isNullOrBlank()) {
            whatsappTemplateService.getBroadcastTemplates().find { it.templateName == templateName }
        } else null

        return try {
            var content = message ?: ""
            if (!templateName.isNullOrBlank() && template != null) {
                val resolvedParams = templateParameterResolver.resolveAllParameters(currentUser, selectedSchoolId, template, namedManualParams)
                val bodyText = template.componentsJson?.let { json ->
                    val mapper = jacksonObjectMapper()
                    val components = mapper.readValue<List<Map<String, Any>>>(json)
                    var text = components.find { c -> c["type"] == "BODY" }?.get("text") as? String ?: ""
                    resolvedParams.forEach { param ->
                        val pName = param["parameter_name"] as? String ?: ""
                        if (pName.isNotBlank()) {
                            text = text.replace("{{$pName}}", param["text"] as? String ?: "")
                        }
                    }
                    resolvedParams.forEachIndexed { index, param ->
                        text = text.replace("{{${index + 1}}}", param["text"] as? String ?: "")
                    }
                    text
                } ?: ""
                content = bodyText
            }

            if (content.isNotBlank()) {
                val success = smsMessagingService.sendSms(
                    to = testPhone,
                    content = content,
                    schoolId = selectedSchoolId,
                    userId = currentUser.id!!,
                    user = currentUser
                )
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
        @RequestParam(value = "subject", required = false, defaultValue = "SMS Broadcast") subject: String,
        request: jakarta.servlet.http.HttpServletRequest,
        session: HttpSession,
        authentication: Authentication
    ): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID ?: return "redirect:/select-school"
        val recipients = broadcastService.getDetailedRecipients(filter, selectedSchoolId)
        
        val currentUser = userRepository.findByEmail(authentication.name).orElseThrow()
        val subscription = subscriptionService.getSubscription(selectedSchoolId)
        
        val template = if (!templateName.isNullOrBlank()) {
            whatsappTemplateService.getBroadcastTemplates().find { it.templateName == templateName }
        } else null

        val namedManualParams = mutableMapOf<String, String>()
        val paramMap = request.parameterMap
        paramMap.keys.filter { it.startsWith("manualParam_") }.forEach { key ->
            val value = paramMap[key]?.firstOrNull() ?: ""
            val variableKey = key.substringAfter("manualParam_")
            namedManualParams[variableKey] = value
        }

        val userMap = userRepository.findAllById(recipients.map { it.userId }).associateBy { it.id }
        
        val validRecipients = recipients.filter { it.phoneNumber != null }
        
        if (subscription.smsBalance < validRecipients.size) {
            return "redirect:/admin/dashboard/sms?broadcastError=insufficient_units"
        }

        var messagesSuccessfullySent = 0
        val broadcastId = UUID.randomUUID()

        validRecipients.forEach { recipient ->
            val phoneNumber = recipient.phoneNumber!!
            val user = userMap[recipient.userId]
            
            var content = ""
            if (!templateName.isNullOrBlank() && template != null) {
                val targetRole = template.targetRole
                
                if (user != null) {
                    val userRoles = user.schoolRoles.map { it.role.name }
                    val matchesTarget = when (targetRole) {
                        "PARENT" -> userRoles.any { it == "PARENT" }
                        "STAFF" -> userRoles.any { it == "STAFF" || it == "TEACHER" || it == "PRINCIPAL" }
                        "STUDENT" -> userRoles.any { it == "STUDENT" }
                        else -> true
                    }

                    if (matchesTarget) {
                        val resolvedParams = templateParameterResolver.resolveAllParameters(user, selectedSchoolId, template, namedManualParams)
                        val bodyText = template.componentsJson?.let { json ->
                            val mapper = jacksonObjectMapper()
                            val components = mapper.readValue<List<Map<String, Any>>>(json)
                            var text = components.find { c -> c["type"] == "BODY" }?.get("text") as? String ?: ""
                            resolvedParams.forEach { param ->
                                val pName = param["parameter_name"] as? String ?: ""
                                if (pName.isNotBlank()) {
                                    text = text.replace("{{$pName}}", param["text"] as? String ?: "")
                                }
                            }
                            resolvedParams.forEachIndexed { index, param ->
                                text = text.replace("{{${index + 1}}}", param["text"] as? String ?: "")
                            }
                            text
                        } ?: ""
                        content = bodyText
                    }
                }
            }

            if (content.isNotBlank()) {
                val success = smsMessagingService.sendSms(
                    to = phoneNumber,
                    content = content,
                    schoolId = selectedSchoolId,
                    userId = currentUser.id!!,
                    user = currentUser,
                    templateName = templateName,
                    broadcastId = broadcastId
                )
                if (success) {
                    messagesSuccessfullySent++
                    // Log individual SMS in broadcast
                    val userRole = (authentication.principal as CustomUserDetails).authorities.firstOrNull()?.authority ?: "USER"
                    activityLogService.logSmsSent(
                        selectedSchoolId, currentUser.id!!, userRole, phoneNumber, content
                    )
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
        
        return "redirect:/admin/dashboard/sms?broadcastSent=true&count=$messagesSuccessfullySent"
    }
}

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
@RequestMapping("/admin/dashboard/messaging")
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
    private val templateParameterResolver: TemplateParameterResolver
) {

    @GetMapping
    fun messagingDashboard(model: Model, session: HttpSession): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID ?: return "redirect:/select-school"
        val school = schoolRepository.findById(selectedSchoolId).orElse(null)
        
        val latestConversations = messageRepository.findLatestMessagesByRecipient(selectedSchoolId)
        val schedules = feeReminderScheduleRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
        
        // Add data for broadcast filtering
        model.addAttribute("tracks", educationTrackRepository.findBySchoolIdAndIsActive(selectedSchoolId, true))
        model.addAttribute("classes", schoolClassRepository.findBySchoolIdAndIsActiveWithTrack(selectedSchoolId, true))
        model.addAttribute("departments", departmentRepository.findBySchoolIdAndIsActive(selectedSchoolId, true))
        
        model.addAttribute("conversations", latestConversations)
        model.addAttribute("schedules", schedules)
        model.addAttribute("school", school)
        model.addAttribute("isSchoolAdmin", true)
        
        return "dashboard/messaging"
    }

    @GetMapping("/conversations")
    fun getConversations(model: Model, session: HttpSession): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID ?: return "fragments/error :: error-message"
        val conversations = messageRepository.findLatestMessagesByRecipient(selectedSchoolId)
        model.addAttribute("conversations", conversations)
        return "dashboard/messaging-fragments :: conversation-list"
    }

    @GetMapping("/thread/{recipient}")
    fun getThread(
        @PathVariable recipient: String,
        model: Model,
        session: HttpSession
    ): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID ?: return "fragments/error :: error-message"
        val cleanedRecipient = recipient.removePrefix("+")
        
        // Fetch messages for this recipient and school
        val messages = (messageRepository.findByRecipientPhoneOrderByCreatedAtDesc(cleanedRecipient) +
                       messageRepository.findByRecipientPhoneOrderByCreatedAtDesc("+" + cleanedRecipient))
            .distinctBy { it.id }
            .filter { it.school?.id == selectedSchoolId }
            .sortedBy { it.createdAt }
        
        model.addAttribute("messages", messages)
        model.addAttribute("recipient", recipient)
        return "dashboard/messaging-fragments :: chat-thread"
    }

    @PostMapping("/reply")
    @ResponseBody
    fun sendReply(
        @RequestParam("recipient") recipient: String,
        @RequestParam("message") message: String,
        session: HttpSession
    ): Map<String, Any> {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID ?: return mapOf("success" to false)
        
        val user = userRepository.findByPhoneNumber(recipient.removePrefix("+")).orElse(null)
            ?: userRepository.findByPhoneNumber("+" + recipient.removePrefix("+")).orElse(null)
            
        val success = whatsappService.sendTextMessage(recipient, message, user)
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
        session: HttpSession
    ): List<BroadcastRecipientDTO> {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID ?: return emptyList()
        return broadcastService.searchDetailedRecipients(query, selectedSchoolId)
    }

    @GetMapping("/broadcast/templates")
    @ResponseBody
    fun getTemplates(session: HttpSession): List<Map<String, Any>> {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID ?: return emptyList()
        val templates = templateService.getBroadcastTemplates(selectedSchoolId)
        
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

    @PostMapping("/broadcast")
    fun sendBroadcast(
        @ModelAttribute filter: BroadcastRecipientFilter,
        @RequestParam(value = "templateName", required = false) templateName: String?,
        @RequestParam(value = "message", required = false) message: String?,
        request: jakarta.servlet.http.HttpServletRequest,
        session: HttpSession
    ): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID ?: return "redirect:/select-school"
        val recipients = broadcastService.getDetailedRecipients(filter, selectedSchoolId)
        
        // Pre-fetch template mapping if applicable
        val template = if (!templateName.isNullOrBlank()) {
            templateService.getAllTemplates(selectedSchoolId).find { it.templateName == templateName }
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

        recipients.forEach { recipient ->
            // Qualification Check
            val isQualified = when (template?.targetRole) {
                "PARENT" -> recipient.type == "PARENT"
                "STAFF" -> recipient.type == "STAFF"
                else -> true // GENERAL or no template
            }

            val phoneNumber = recipient.phoneNumber
            if (isQualified && phoneNumber != null) {
                val user = userMap[recipient.userId]
                if (!templateName.isNullOrBlank()) {
                    val components = if (template != null && user != null) {
                        // Use the new robust resolver that extracts all placeholders from the template JSON
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
                        schoolId = selectedSchoolId
                    )
                } else if (!message.isNullOrBlank()) {
                    whatsappService.sendTextMessage(phoneNumber, message, user = user, schoolId = selectedSchoolId)
                }
            }
        }

        return "redirect:/admin/dashboard/messaging?broadcastSent=true&count=${recipients.size}"
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

    @GetMapping("/templates")
    fun listTemplates(model: Model, session: HttpSession): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID ?: return "fragments/error :: error-message"
        val templates = templateService.getAllTemplates(selectedSchoolId)
        model.addAttribute("templates", templates)
        return "dashboard/messaging-fragments :: template-list"
    }

    @PostMapping("/templates/sync")
    @ResponseBody
    fun syncTemplates(session: HttpSession): Map<String, Any> {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID ?: return mapOf("success" to false)
        val success = templateService.syncTemplates(selectedSchoolId)
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

        val success = templateService.createMetaTemplate(selectedSchoolId, name, category, language, components, mapping)
        return mapOf("success" to success)
    }

    @DeleteMapping("/templates/{id}")
    @ResponseBody
    fun deleteTemplate(@PathVariable id: UUID, session: HttpSession): Map<String, Any> {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID ?: return mapOf("success" to false)
        val success = templateService.deleteMetaTemplate(selectedSchoolId, id)
        return mapOf("success" to success)
    }
}

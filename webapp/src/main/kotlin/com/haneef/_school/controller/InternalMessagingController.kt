package com.haneef._school.controller

import jakarta.servlet.http.HttpSession
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import com.haneef._school.repository.*
import com.haneef._school.service.*
import com.haneef._school.dto.BroadcastRecipientFilter
import com.haneef._school.dto.BroadcastRecipientDTO
import java.util.*
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails

@Controller
@RequestMapping("/dashboard/messaging")
@PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'SCHOOL_ADMIN', 'STAFF', 'PARENT')")
class InternalMessagingController(
    private val schoolRepository: SchoolRepository,
    private val userRepository: UserRepository,
    private val messagingService: InternalMessagingService,
    private val broadcastService: BroadcastService,
    private val templateService: WhatsAppTemplateService,
    private val educationTrackRepository: EducationTrackRepository,
    private val schoolClassRepository: SchoolClassRepository,
    private val departmentRepository: DepartmentRepository,
    private val whatsappMessageRepository: WhatsAppMessageRepository,
    private val smsMessageRepository: SmsMessageRepository,
    private val activityLogService: ActivityLogService
) {

    @GetMapping
    fun messagingDashboard(model: Model, session: HttpSession, @AuthenticationPrincipal userDetails: UserDetails): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID 
            ?: return "redirect:/select-school"
            
        val school = schoolRepository.findById(selectedSchoolId).orElse(null)
        val user = userRepository.findByEmailIgnoreCase(userDetails.username) ?: return "redirect:/login"
        
        val threads = messagingService.getUserThreads(user.id!!, selectedSchoolId)

        val selectedRole = session.getAttribute("selectedRole") as? String
        
        val dashboardUrl = when (selectedRole) {
            "SYSTEM_ADMIN" -> "/system-admin/dashboard"
            "SCHOOL_ADMIN", "ADMIN" -> "/admin/dashboard"
            "STAFF", "PRINCIPAL", "TEACHER" -> "/staff/dashboard"
            "PARENT" -> "/parent/dashboard"
            "STUDENT" -> "/student/dashboard"
            else -> {
                // Fallback to the previous logic if session role is missing
                when {
                    user.schoolRoles.any { it.role.name == "SYSTEM_ADMIN" } -> "/system-admin/dashboard"
                    user.schoolRoles.any { it.role.name == "SCHOOL_ADMIN" || it.role.name == "ADMIN" } -> "/admin/dashboard"
                    user.schoolRoles.any { it.role.name == "STAFF" || it.role.name == "PRINCIPAL" || it.role.name == "TEACHER" } -> "/staff/dashboard"
                    user.schoolRoles.any { it.role.name == "PARENT" } -> "/parent/dashboard"
                    user.schoolRoles.any { it.role.name == "STUDENT" } -> "/student/dashboard"
                    else -> "/admin/dashboard"
                }
            }
        }

        model.addAttribute("school", school)
        model.addAttribute("threads", threads)
        model.addAttribute("currentUser", user)
        model.addAttribute("dashboardUrl", dashboardUrl)
        
        // Data for broadcast filtering
        model.addAttribute("tracks", educationTrackRepository.findBySchoolIdAndIsActive(selectedSchoolId, true))
        model.addAttribute("classes", schoolClassRepository.findBySchoolIdAndIsActiveWithTrack(selectedSchoolId, true))
        model.addAttribute("departments", departmentRepository.findBySchoolIdAndIsActive(selectedSchoolId, true))
        
        return "dashboard/internal-messaging"
    }

    @GetMapping("/contacts")
    @ResponseBody
    fun getEligibleContacts(session: HttpSession, @AuthenticationPrincipal userDetails: UserDetails): ResponseEntity<List<InternalMessagingService.ContactDTO>> {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID 
            ?: return ResponseEntity.badRequest().build()
            
        val user = userRepository.findByEmailIgnoreCase(userDetails.username) ?: return ResponseEntity.badRequest().build()
        
        val contacts = messagingService.getEligibleContacts(user, selectedSchoolId)
        return ResponseEntity.ok(contacts)
    }

    @GetMapping("/threads/{threadId}")
    fun getThreadMessages(@PathVariable threadId: UUID, session: HttpSession, @AuthenticationPrincipal userDetails: UserDetails, model: Model): String {
        val user = userRepository.findByEmailIgnoreCase(userDetails.username) ?: return "redirect:/login"
        val messages = messagingService.getThreadMessages(threadId, user.id!!)
        
        model.addAttribute("messages", messages)
        model.addAttribute("currentUser", user)
        model.addAttribute("threadId", threadId)
        
        return "dashboard/internal-messaging-fragments :: chat-thread"
    }

    @PostMapping("/threads")
    @ResponseBody
    fun createThread(
        @RequestParam(required = false) subject: String?,
        @RequestParam recipientId: UUID,
        @RequestParam content: String,
        session: HttpSession,
        @AuthenticationPrincipal userDetails: UserDetails
    ): ResponseEntity<Map<String, Any>> {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID 
            ?: return ResponseEntity.badRequest().build()
            
        val user = userRepository.findByEmailIgnoreCase(userDetails.username) ?: return ResponseEntity.badRequest().build()
        
        return try {
            val thread = messagingService.createThread(subject, user.id!!, recipientId, selectedSchoolId, content)
            
            // Log the activity
            val userRole = (session.getAttribute("selectedRole") as? String) ?: "USER"
            val recipient = userRepository.findById(recipientId).orElse(null)
            val recipientName = recipient?.let { "${it.firstName} ${it.lastName}" } ?: "Unknown"
            activityLogService.logInternalMessageSent(
                selectedSchoolId, user.id!!, userRole, recipientName, content
            )

            ResponseEntity.ok(mapOf<String, Any>("success" to true, "threadId" to (thread.id ?: "")))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf<String, Any>("success" to false, "message" to (e.message ?: "Unknown error")))
        }
    }

    @PostMapping("/threads/{threadId}/reply")
    @ResponseBody
    fun replyToThread(
        @PathVariable threadId: UUID,
        @RequestParam content: String,
        session: HttpSession,
        @AuthenticationPrincipal userDetails: UserDetails
    ): ResponseEntity<Map<String, Any>> {
        val user = userRepository.findByEmailIgnoreCase(userDetails.username) ?: return ResponseEntity.badRequest().build()
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID ?: return ResponseEntity.badRequest().build()
        
        return try {
            messagingService.replyToThread(threadId, user.id!!, content)
            
            // Log the activity
            val userRole = (session.getAttribute("selectedRole") as? String) ?: "USER"
            // We'll just log that a reply was sent to the thread
            activityLogService.logInternalMessageSent(
                selectedSchoolId, user.id!!, userRole, "Thread: $threadId", content
            )

            ResponseEntity.ok(mapOf<String, Any>("success" to true))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf<String, Any>("success" to false, "message" to (e.message ?: "Unknown error")))
        }
    }

    @GetMapping("/unread-count")
    @ResponseBody
    fun getUnreadCount(session: HttpSession, @AuthenticationPrincipal userDetails: UserDetails): ResponseEntity<Map<String, Any>> {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID 
            ?: return ResponseEntity.badRequest().build()
            
        val user = userRepository.findByEmailIgnoreCase(userDetails.username) ?: return ResponseEntity.badRequest().build()
        
        val internalCount = messagingService.getTotalUnreadCount(user.id!!, selectedSchoolId)
        val whatsappCount = whatsappMessageRepository.countUnreadIncomingBySchoolId(selectedSchoolId).toInt()
        val smsCount = smsMessageRepository.countUnreadIncomingBySchoolId(selectedSchoolId).toInt()
        
        val total = internalCount + whatsappCount + smsCount
        
        return ResponseEntity.ok(mapOf(
            "count" to total,
            "internal" to internalCount,
            "whatsapp" to whatsappCount,
            "sms" to smsCount,
            "multimodal" to 0 // No incoming multimodal yet
        ))
    }

    @PostMapping("/broadcast/preview-count")
    @ResponseBody
    fun getPreviewCount(
        @ModelAttribute filter: com.haneef._school.dto.BroadcastRecipientFilter,
        session: HttpSession
    ): Map<String, Any> {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID ?: return mapOf("count" to 0)
        val recipients = broadcastService.getDetailedRecipients(filter, selectedSchoolId)
        return mapOf("count" to recipients.size)
    }

    @PostMapping("/broadcast/recipients")
    @ResponseBody
    fun getRecipients(
        @ModelAttribute filter: com.haneef._school.dto.BroadcastRecipientFilter,
        session: HttpSession
    ): List<com.haneef._school.dto.BroadcastRecipientDTO> {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID ?: return emptyList()
        return broadcastService.getDetailedRecipients(filter, selectedSchoolId)
    }

    @GetMapping("/broadcast/search")
    @ResponseBody
    fun searchRecipients(
        @RequestParam("query") query: String,
        @RequestParam(value = "recipientType", required = false) recipientType: String?,
        session: HttpSession
    ): List<com.haneef._school.dto.BroadcastRecipientDTO> {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID ?: return emptyList()
        return broadcastService.searchDetailedRecipients(query, selectedSchoolId, recipientType)
    }

    @GetMapping("/broadcast/templates")
    @ResponseBody
    fun getTemplates(
        @RequestParam(value = "recipientType", required = false) recipientType: String?,
        session: HttpSession
    ): List<Map<String, Any>> {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID ?: return emptyList()
        val templates = templateService.getBroadcastTemplates(recipientType)
        
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
    @ResponseBody
    fun sendBroadcast(
        @ModelAttribute filter: com.haneef._school.dto.BroadcastRecipientFilter,
        @RequestParam(value = "subject", required = false) subject: String?,
        @RequestParam(value = "templateName", required = false) templateName: String?,
        @RequestParam(value = "message", required = false) message: String?,
        session: HttpSession,
        request: jakarta.servlet.http.HttpServletRequest,
        @AuthenticationPrincipal userDetails: UserDetails
    ): ResponseEntity<Map<String, Any>> {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID 
            ?: return ResponseEntity.badRequest().body(mapOf("success" to false, "message" to "No school selected"))
            
        val user = userRepository.findByEmailIgnoreCase(userDetails.username) 
            ?: return ResponseEntity.status(401).body(mapOf("success" to false, "message" to "User not found"))
            
        // Extract manual parameters
        val extraParams = mutableMapOf<String, String>()
        val paramNames = request.parameterNames
        while (paramNames.hasMoreElements()) {
            val name = paramNames.nextElement()
            if (name.startsWith("manualParam_")) {
                val key = name.removePrefix("manualParam_")
                extraParams[key] = request.getParameter(name) ?: ""
            }
        }

        val recipients = broadcastService.getDetailedRecipients(filter, selectedSchoolId)
        
        return try {
            val sentCount = messagingService.sendInternalBroadcast(
                schoolId = selectedSchoolId,
                senderId = user.id!!,
                subject = subject,
                content = message ?: "",
                templateName = templateName,
                recipients = recipients,
                extraParams = extraParams
            )
            
            // Log the activity
            val userRole = (session.getAttribute("selectedRole") as? String) ?: "USER"
            activityLogService.logInternalMessageSent(
                selectedSchoolId, user.id!!, userRole, "${recipients.size} recipients", message ?: "[Broadcast]"
            )

            ResponseEntity.ok(mapOf("success" to true, "count" to sentCount))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("success" to false, "message" to (e.message ?: "Unknown error")))
        }
    }

}

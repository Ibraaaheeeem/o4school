package com.haneef._school.controller

import jakarta.servlet.http.HttpSession
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import com.haneef._school.repository.*
import com.haneef._school.service.InternalMessagingService
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
    private val messagingService: InternalMessagingService
) {

    @GetMapping
    fun messagingDashboard(model: Model, session: HttpSession, @AuthenticationPrincipal userDetails: UserDetails): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID 
            ?: return "redirect:/select-school"
            
        val school = schoolRepository.findById(selectedSchoolId).orElse(null)
        val user = userRepository.findByEmailIgnoreCase(userDetails.username) ?: return "redirect:/login"
        
        val threads = messagingService.getUserThreads(user.id!!, selectedSchoolId)

        val dashboardUrl = when {
            user.schoolRoles.any { it.role.name == "SYSTEM_ADMIN" } -> "/system-admin/dashboard"
            user.schoolRoles.any { it.role.name == "SCHOOL_ADMIN" || it.role.name == "ADMIN" } -> "/admin/dashboard"
            user.schoolRoles.any { it.role.name == "STAFF" || it.role.name == "PRINCIPAL" || it.role.name == "TEACHER" } -> "/staff/dashboard"
            user.schoolRoles.any { it.role.name == "PARENT" } -> "/parent/dashboard"
            user.schoolRoles.any { it.role.name == "STUDENT" } -> "/student/dashboard"
            else -> "/admin/dashboard"
        }

        model.addAttribute("school", school)
        model.addAttribute("threads", threads)
        model.addAttribute("currentUser", user)
        model.addAttribute("dashboardUrl", dashboardUrl)
        
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
        @RequestParam subject: String,
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
        
        return try {
            messagingService.replyToThread(threadId, user.id!!, content)
            ResponseEntity.ok(mapOf<String, Any>("success" to true))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf<String, Any>("success" to false, "message" to (e.message ?: "Unknown error")))
        }
    }

    @GetMapping("/unread-count")
    @ResponseBody
    fun getUnreadCount(session: HttpSession, @AuthenticationPrincipal userDetails: UserDetails): ResponseEntity<Map<String, Int>> {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID 
            ?: return ResponseEntity.badRequest().build()
            
        val user = userRepository.findByEmailIgnoreCase(userDetails.username) ?: return ResponseEntity.badRequest().build()
        
        val count = messagingService.getTotalUnreadCount(user.id!!, selectedSchoolId)
        return ResponseEntity.ok(mapOf("count" to count))
    }

}

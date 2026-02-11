package com.haneef._school.controller

import com.haneef._school.entity.*
import com.haneef._school.repository.*
import com.haneef._school.service.PromotionService
import com.haneef._school.service.CustomUserDetails
import jakarta.servlet.http.HttpSession
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import java.util.UUID
import org.slf4j.LoggerFactory
import java.util.Optional

@Controller
@RequestMapping("/admin/academic/promotion")
@PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'SCHOOL_ADMIN', 'ADMIN')")
class PromotionController(
    private val promotionService: PromotionService,
    private val academicSessionRepository: AcademicSessionRepository,
    private val termRepository: TermRepository,
    private val schoolClassRepository: SchoolClassRepository,
    private val schoolRepository: SchoolRepository,
    private val authorizationService: com.haneef._school.service.AuthorizationService
) {
    private val logger = LoggerFactory.getLogger(PromotionController::class.java)

    @GetMapping
    fun promotionHome(model: Model, authentication: Authentication, session: HttpSession): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/select-school"

        val school = schoolRepository.findById(selectedSchoolId).orElse(null)
        val allSessions = academicSessionRepository.findBySchoolIdAndIsActiveOrderByYearDesc(selectedSchoolId, true)
        val allClasses = schoolClassRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)

        model.addAttribute("school", school)
        model.addAttribute("allSessions", allSessions)
        model.addAttribute("allClasses", allClasses)
        
        return "admin/academic/promotion"
    }

    @GetMapping("/students")
    fun getPromotionStudents(
        @RequestParam sourceClassId: UUID,
        @RequestParam sourceSessionId: UUID,
        @RequestParam sourceTermId: UUID,
        @RequestParam targetSessionId: UUID,
        @RequestParam targetTermId: UUID,
        session: HttpSession,
        model: Model
    ): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "fragments/error :: error-message"

        try {
            val candidates = promotionService.getPromotionCandidates(
                sourceClassId, sourceSessionId, sourceTermId, targetSessionId, targetTermId, selectedSchoolId
            )
            
            // Fetch target term/session details for display
            val targetSession = academicSessionRepository.findById(targetSessionId).get()
            val targetTerm = termRepository.findById(targetTermId).get()

            model.addAttribute("candidates", candidates)
            model.addAttribute("targetSession", targetSession)
            model.addAttribute("targetTerm", targetTerm)
            model.addAttribute("sourceClassId", sourceClassId)
            model.addAttribute("sourceSessionId", sourceSessionId)
            model.addAttribute("sourceTermId", sourceTermId)
            model.addAttribute("targetSessionId", targetSessionId)
            model.addAttribute("targetTermId", targetTermId)

            return "admin/academic/fragments/promotion-student-list :: promotion-student-list"
        } catch (e: Exception) {
            logger.error("Error fetching promotion candidates", e)
            model.addAttribute("error", "Error: ${e.message}")
            return "fragments/error :: error-message"
        }
    }

    @PostMapping("/process")
    fun processPromotion(
        @RequestParam targetSessionId: UUID,
        @RequestParam targetTermId: UUID,
        @RequestParam studentIds: List<UUID>,
        @RequestParam targetClassIds: List<UUID?>,
        session: HttpSession,
        model: Model
    ): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "fragments/error :: error-message"

        try {
            val promotions = studentIds.zip(targetClassIds).toMap()
            promotionService.executePromotion(targetSessionId, targetTermId, promotions, selectedSchoolId)
            
            model.addAttribute("success", "Promotion executed successfully for ${promotions.size} students!")
            return "admin/academic/fragments/promotion-success :: promotion-success"
        } catch (e: Exception) {
            logger.error("Error executing promotion", e)
            model.addAttribute("error", "Error: ${e.message}")
            return "fragments/error :: error-message"
        }
    }

    @GetMapping("/terms-for-session/{sessionId}")
    @ResponseBody
    fun getTermsForSession(@PathVariable sessionId: UUID, session: HttpSession): List<Map<String, Any>> {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID ?: return emptyList()
        return termRepository.findByAcademicSessionIdAndIsActiveOrderByStartDate(sessionId, true)
            .map { mapOf("id" to it.id!!, "name" to it.termName) }
    }
}

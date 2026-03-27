package com.haneef._school.config

import jakarta.servlet.http.HttpServletRequest
import com.haneef._school.repository.AcademicSessionRepository
import com.haneef._school.repository.SchoolRepository
import com.haneef._school.repository.TermRepository
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ModelAttribute
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.UUID

@ControllerAdvice
class GlobalControllerAdvice(
    private val academicSessionRepository: AcademicSessionRepository,
    private val termRepository: TermRepository,
    private val schoolRepository: SchoolRepository
) {

    @ModelAttribute
    fun populateHeaderContext(
        model: Model,
        authentication: org.springframework.security.core.Authentication?,
        session: jakarta.servlet.http.HttpSession
    ) {
        if (authentication != null && authentication.isAuthenticated) {
            val customUser = authentication.principal as? com.haneef._school.service.CustomUserDetails
            val selectedSchoolId = (session.getAttribute("selectedSchoolId") as? UUID)
                ?: customUser?.forcedSchoolId
                
            if (selectedSchoolId != null) {
                // Fetch and add school entity
                schoolRepository.findById(selectedSchoolId).ifPresent { school ->
                    model.addAttribute("school", school)
                }

                // Fetch all active sessions
                val sessions = academicSessionRepository.findBySchoolIdAndIsActiveOrderByYearDesc(selectedSchoolId, true)
                model.addAttribute("headerSessions", sessions)

                // Determine selected session
                val selectedSessionId = session.getAttribute("selectedSessionId") as? UUID
                var contextSession = if (selectedSessionId != null) {
                    sessions.find { it.id == selectedSessionId }
                } else {
                    sessions.find { it.isCurrentSession }
                }
                
                // If no session is selected or found, default to the first one (or null)
                if (contextSession == null && sessions.isNotEmpty()) {
                    contextSession = sessions.first()
                }

                if (contextSession != null) {
                    model.addAttribute("headerContextSession", contextSession)
                    
                    // Fetch terms for the context session
                    val terms = termRepository.findByAcademicSessionIdAndIsActiveOrderByStartDate(contextSession.id!!, true)
                    model.addAttribute("headerTerms", terms)
                    
                    // Determine selected term
                    val selectedTermId = session.getAttribute("selectedTermId") as? UUID
                    var contextTerm = if (selectedTermId != null) {
                        terms.find { it.id == selectedTermId }
                    } else {
                        terms.find { it.isCurrentTerm }
                    }
                    
                    // If no term is selected or found, default to the first one
                     if (contextTerm == null && terms.isNotEmpty()) {
                        contextTerm = terms.first()
                    }
                    
                    if (contextTerm != null) {
                        model.addAttribute("headerContextTerm", contextTerm)
                        
                        // Current Term & Week Logic
                        val now = LocalDate.now()
                        val startDate = contextTerm.startDate
                        
                        // Determine the start of Week 1
                        var week1Start = startDate
                        if (startDate.dayOfWeek != DayOfWeek.SUNDAY) {
                            week1Start = startDate.with(TemporalAdjusters.next(DayOfWeek.SUNDAY))
                        }
                        
                        val weekNum = if (now.isBefore(startDate) || now.isBefore(week1Start)) {
                            0
                        } else {
                            val days = ChronoUnit.DAYS.between(week1Start, now)
                            (days / 7) + 1
                        }
                        model.addAttribute("currentWeekNumber", weekNum)

                        if (contextTerm.isCurrentTerm) {
                            model.addAttribute("isCurrentTermSelected", true)
                        }
                    } else {
                         model.addAttribute("headerContextTermWarning", true)
                    }
                } else {
                     model.addAttribute("headerContextSessionWarning", true)
                }
                
                val authorities = authentication.authorities.map { it.authority }
                model.addAttribute("isSchoolAdmin", authorities.any { it == "ROLE_SCHOOL_ADMIN" || it == "ROLE_ADMIN" })
                model.addAttribute("isParent", authorities.contains("ROLE_PARENT"))
                model.addAttribute("isStudent", authorities.contains("ROLE_STUDENT"))
                model.addAttribute("isStaff", authorities.any { it == "ROLE_STAFF" || it == "ROLE_TEACHER" })
            }
        }
    }

    @ModelAttribute("requestURI")
    fun requestURI(request: HttpServletRequest): String {
        return request.requestURI
    }
}

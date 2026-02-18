package com.haneef._school.config

import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.beans.factory.annotation.Autowired

@ControllerAdvice
class GlobalControllerAdvice {

    @Autowired
    private lateinit var academicSessionRepository: com.haneef._school.repository.AcademicSessionRepository

    @Autowired
    private lateinit var termRepository: com.haneef._school.repository.TermRepository

    @Autowired
    private lateinit var schoolRepository: com.haneef._school.repository.SchoolRepository

    @ModelAttribute
    fun populateHeaderContext(
        model: org.springframework.ui.Model,
        authentication: org.springframework.security.core.Authentication?,
        session: jakarta.servlet.http.HttpSession
    ) {
        if (authentication != null && authentication.isAuthenticated) {
            val customUser = authentication.principal as? com.haneef._school.service.CustomUserDetails
            val selectedSchoolId = (session.getAttribute("selectedSchoolId") as? java.util.UUID)
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
                var selectedSessionId = session.getAttribute("selectedSessionId") as? java.util.UUID
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
                    var selectedTermId = session.getAttribute("selectedTermId") as? java.util.UUID
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
                        val now = java.time.LocalDate.now()
                        val startDate = contextTerm.startDate
                        
                        // Determine the start of Week 1
                        var week1Start = startDate
                        if (startDate.dayOfWeek != java.time.DayOfWeek.SUNDAY) {
                            week1Start = startDate.with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.SUNDAY))
                        }
                        
                        val weekNum = if (now.isBefore(startDate) || now.isBefore(week1Start)) {
                            0
                        } else {
                            val days = java.time.temporal.ChronoUnit.DAYS.between(week1Start, now)
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
            }
        }
    }

    @ModelAttribute("requestURI")
    fun requestURI(request: HttpServletRequest): String {
        return request.requestURI
    }
}

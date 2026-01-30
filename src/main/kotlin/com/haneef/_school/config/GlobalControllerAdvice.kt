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

    @ModelAttribute
    fun populateHeaderContext(
        model: org.springframework.ui.Model,
        authentication: org.springframework.security.core.Authentication?,
        session: jakarta.servlet.http.HttpSession
    ) {
        if (authentication != null && authentication.isAuthenticated && 
            authentication.authorities.any { it.authority == "ROLE_SCHOOL_ADMIN" }) {
            
            val selectedSchoolId = session.getAttribute("selectedSchoolId") as? java.util.UUID
            if (selectedSchoolId != null) {
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
                    } else {
                         model.addAttribute("headerContextTermWarning", true)
                    }
                } else {
                     model.addAttribute("headerContextSessionWarning", true)
                }
                
                model.addAttribute("isSchoolAdmin", true)
            }
        }
    }

    @ModelAttribute("requestURI")
    fun requestURI(request: HttpServletRequest): String {
        return request.requestURI
    }
}

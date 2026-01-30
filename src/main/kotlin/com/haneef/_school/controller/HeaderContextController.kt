package com.haneef._school.controller

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpSession
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import java.util.UUID

@Controller
@RequestMapping("/admin/header")
@PreAuthorize("hasRole('SCHOOL_ADMIN')")
class HeaderContextController {

    @PostMapping("/set-session")
    fun setSession(
        @RequestParam sessionId: UUID,
        session: HttpSession,
        request: HttpServletRequest
    ): String {
        session.setAttribute("selectedSessionId", sessionId)
        // Reset term selection when session changes as terms are session-specific
        session.removeAttribute("selectedTermId")
        
        val referer = request.getHeader("Referer") ?: "/admin/dashboard"
        return "redirect:$referer"
    }

    @PostMapping("/set-term")
    fun setTerm(
        @RequestParam termId: UUID,
        session: HttpSession,
        request: HttpServletRequest
    ): String {
        session.setAttribute("selectedTermId", termId)
        
        val referer = request.getHeader("Referer") ?: "/admin/dashboard"
        return "redirect:$referer"
    }
}

package com.haneef._school.controller

import com.haneef._school.service.CustomUserDetailsService
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping

@Controller
@RequestMapping("/student")
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'PARENT', 'STUDENT')")
class StudentDashboardController(
    private val userDetailsService: CustomUserDetailsService
) {

    @GetMapping("/dashboard")
    fun studentDashboard(model: Model, authentication: Authentication): String {
        val userDetails = userDetailsService.loadUserByUsername(authentication.name)
        val customUser = userDetails as com.haneef._school.service.CustomUserDetails
        
        model.addAttribute("user", customUser.user)
        // School information can be retrieved from user's school roles if needed
        model.addAttribute("userRole", "Student")
        model.addAttribute("dashboardType", "student")
        
        return "dashboard/student-dashboard"
    }
}
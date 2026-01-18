package com.haneef._school.controller

import jakarta.servlet.RequestDispatcher
import jakarta.servlet.http.HttpServletRequest
import org.springframework.boot.web.servlet.error.ErrorController
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.RequestMapping

@Controller
class CustomErrorController : ErrorController {

    @RequestMapping("/error")
    fun handleError(request: HttpServletRequest, model: Model): String {
        val status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)
        
        if (status != null) {
            val statusCode = Integer.valueOf(status.toString())
            
            val (title, message, icon) = when (statusCode) {
                HttpStatus.NOT_FOUND.value() -> Triple(
                    "Page Not Found",
                    "The page you are looking for doesn't exist or has been moved.",
                    "fa-ghost"
                )
                HttpStatus.FORBIDDEN.value() -> Triple(
                    "Access Denied",
                    "You don't have permission to access this resource.",
                    "fa-lock"
                )
                HttpStatus.INTERNAL_SERVER_ERROR.value() -> Triple(
                    "Server Error",
                    "Something went wrong on our end. We're working on fixing it.",
                    "fa-bug"
                )
                HttpStatus.UNAUTHORIZED.value() -> Triple(
                    "Unauthorized",
                    "Please log in to access this page.",
                    "fa-user-shield"
                )
                else -> Triple(
                    "Unexpected Error",
                    "An unexpected error occurred. Please try again later.",
                    "fa-exclamation-triangle"
                )
            }
            
            model.addAttribute("statusCode", statusCode)
            model.addAttribute("errorTitle", title)
            model.addAttribute("errorMessage", message)
            model.addAttribute("errorIcon", icon)
        } else {
            model.addAttribute("statusCode", "Error")
            model.addAttribute("errorTitle", "Unexpected Error")
            model.addAttribute("errorMessage", "An unexpected error occurred.")
            model.addAttribute("errorIcon", "fa-exclamation-triangle")
        }
        
        return "error"
    }
}

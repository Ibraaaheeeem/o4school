package com.haneef._school.config

import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ModelAttribute

@ControllerAdvice
class GlobalControllerAdvice {

    @ModelAttribute("requestURI")
    fun requestURI(request: HttpServletRequest): String {
        return request.requestURI
    }
}

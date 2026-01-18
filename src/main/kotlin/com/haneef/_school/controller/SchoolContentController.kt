package com.haneef._school.controller

import com.haneef._school.repository.SchoolRepository
import com.haneef._school.service.CustomUserDetails
import com.haneef._school.service.SchoolContentService
import jakarta.servlet.http.HttpSession
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import java.util.*

@Controller
@RequestMapping("/admin/school-content")
@PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'SCHOOL_ADMIN')")
class SchoolContentController(
    private val schoolContentService: SchoolContentService,
    private val schoolRepository: SchoolRepository
) {

    @GetMapping
    fun contentManagement(model: Model, authentication: Authentication, session: HttpSession): String {
        val customUser = authentication.principal as CustomUserDetails
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/select-school"

        val school = schoolRepository.findById(selectedSchoolId).orElse(null)
            ?: return "redirect:/select-school"

        val sections = schoolContentService.getCustomizableSections()
        val sectionContents = mutableMapOf<String, String>()
        val hasCustomContent = mutableMapOf<String, Boolean>()

        sections.forEach { section ->
            sectionContents[section] = schoolContentService.getSchoolContent(school, section)
            hasCustomContent[section] = schoolContentService.hasCustomContent(selectedSchoolId, section)
        }

        model.addAttribute("user", customUser.user)
        model.addAttribute("school", school)
        model.addAttribute("sections", sections)
        model.addAttribute("sectionContents", sectionContents)
        model.addAttribute("hasCustomContent", hasCustomContent)
        model.addAttribute("selectedSchoolId", selectedSchoolId)

        return "admin/school-content/manage"
    }

    @GetMapping("/edit/{section}")
    fun editSection(
        @PathVariable section: String,
        model: Model,
        authentication: Authentication,
        session: HttpSession
    ): String {
        val customUser = authentication.principal as CustomUserDetails
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/select-school"

        val school = schoolRepository.findById(selectedSchoolId).orElse(null)
            ?: return "redirect:/select-school"

        val content = schoolContentService.getSchoolContent(school, section)
        val hasCustom = schoolContentService.hasCustomContent(selectedSchoolId, section)

        model.addAttribute("user", customUser.user)
        model.addAttribute("school", school)
        model.addAttribute("section", section)
        model.addAttribute("content", content)
        model.addAttribute("hasCustomContent", hasCustom)

        return "admin/school-content/edit"
    }

    @PostMapping("/save/{section}")
    @ResponseBody
    fun saveSection(
        @PathVariable section: String,
        @RequestParam content: String,
        session: HttpSession
    ): Map<String, Any> {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return mapOf("success" to false, "message" to "No school selected")

        val success = schoolContentService.saveSchoolContent(selectedSchoolId, section, content)
        
        return if (success) {
            mapOf("success" to true, "message" to "Content saved successfully!")
        } else {
            mapOf("success" to false, "message" to "Failed to save content")
        }
    }

    @PostMapping("/reset/{section}")
    @ResponseBody
    fun resetSection(
        @PathVariable section: String,
        session: HttpSession
    ): Map<String, Any> {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return mapOf("success" to false, "message" to "No school selected")

        // Reset by saving empty content (will fallback to default)
        val success = schoolContentService.saveSchoolContent(selectedSchoolId, section, "")
        
        return if (success) {
            mapOf("success" to true, "message" to "Content reset to default!")
        } else {
            mapOf("success" to false, "message" to "Failed to reset content")
        }
    }

    @GetMapping("/preview/{section}")
    @ResponseBody
    fun previewSection(
        @PathVariable section: String,
        authentication: Authentication,
        session: HttpSession
    ): Map<String, Any> {
        val customUser = authentication.principal as CustomUserDetails
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return mapOf("success" to false, "message" to "No school selected")

        val school = schoolRepository.findById(selectedSchoolId).orElse(null)
            ?: return mapOf("success" to false, "message" to "School not found")

        val content = schoolContentService.getSchoolContent(school, section)
        
        return mapOf(
            "success" to true,
            "content" to content,
            "hasCustom" to schoolContentService.hasCustomContent(selectedSchoolId, section)
        )
    }
}
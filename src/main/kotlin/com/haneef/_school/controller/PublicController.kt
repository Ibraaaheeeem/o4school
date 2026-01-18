package com.haneef._school.controller

import com.haneef._school.entity.School
import com.haneef._school.repository.SchoolRepository
import com.haneef._school.service.SchoolContentService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping

import com.haneef._school.repository.StudentRepository

@Controller
@RequestMapping("/")
class PublicController(
    private val schoolRepository: SchoolRepository,
    private val studentRepository: StudentRepository,
    private val schoolContentService: SchoolContentService
) {

    @GetMapping("")
    fun platformHome(model: Model): String {
        // Show platform landing page or redirect to a demo school
        // For now, let's redirect to a demo school if it exists
        val demoSchool = schoolRepository.findBySlugAndIsActive("demo-school", true)
        
        if (demoSchool.isPresent) {
            return "redirect:/demo-school"
        }

        // Fetch real statistics
        val schoolCount = schoolRepository.count()
        val studentCount = studentRepository.count()
        
        model.addAttribute("schoolCount", schoolCount)
        model.addAttribute("studentCount", studentCount)
        
        // Show platform landing page
        return "public/platform-home"
    }

    @GetMapping("/{slug}")
    fun schoolLandingPage(@PathVariable slug: String, model: Model): String {
        // Ignore static resources and favicon that might fall through
        if (slug == "favicon.ico" || slug == "css" || slug == "js" || slug == "images" || slug == "assets" || slug == "register") {
            throw org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND)
        }

        // Find school by slug
        val school = schoolRepository.findBySlugAndIsActive(slug, true).orElse(null)
            ?: throw org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "School not found")

        // Load custom content for each section
        val heroContent = schoolContentService.getSchoolContent(school, "hero-content")
        val aboutContent = schoolContentService.getSchoolContent(school, "about-content")
        val featuresContent = schoolContentService.getSchoolContent(school, "features-content")
        val contactInfo = schoolContentService.getSchoolContent(school, "contact-info")
        val additionalSections = schoolContentService.getSchoolContent(school, "additional-sections")

        model.addAttribute("school", school)
        model.addAttribute("heroContent", heroContent)
        model.addAttribute("aboutContent", aboutContent)
        model.addAttribute("featuresContent", featuresContent)
        model.addAttribute("contactInfo", contactInfo)
        model.addAttribute("additionalSections", additionalSections)
        
        return "public/school-landing-template"
    }
}
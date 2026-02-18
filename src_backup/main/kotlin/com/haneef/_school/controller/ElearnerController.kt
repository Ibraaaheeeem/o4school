package com.haneef._school.controller

import com.haneef._school.repository.*
import com.haneef._school.service.CustomUserDetails
import jakarta.servlet.http.HttpSession
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import java.util.UUID
import com.haneef._school.entity.Subject

data class SubjectViewDto(
    val subject: Subject,
    val elearnerSubjectId: UUID
)

@Controller
@RequestMapping("/elearner")
class ElearnerController(
    private val schoolClassRepository: SchoolClassRepository,
    private val classSubjectRepository: ClassSubjectRepository,
    private val subjectRepository: SubjectRepository,
    private val learningContentService: com.haneef._school.service.LearningContentService
) {

    @GetMapping("/landing")
    @PreAuthorize("hasAnyRole('STUDENT', 'STAFF', 'ADMIN', 'SCHOOL_ADMIN')")
    fun elearnerLanding(
        @RequestParam gradeLevel: Int,
        @RequestParam term: Int,
        @RequestParam(required = false) classId: UUID?,
        @RequestParam(required = false) week: Int?,
        @RequestParam(required = false) subjectId: UUID?,
        @RequestParam(required = false) topicId: Int?,
        model: Model,
        authentication: Authentication,
        session: HttpSession
    ): String {
        val customUser = authentication.principal as CustomUserDetails
        val selectedSchoolId = customUser.forcedSchoolId 
            ?: session.getAttribute("selectedSchoolId") as? UUID
            ?: throw RuntimeException("School context missing")

        // 1. Resolve Subjects for the Class or Grade
        val subjects = if (classId != null) {
            // Precise filtering by Class
            classSubjectRepository.findByClassIdWithRelationships(classId, true)
                .map { it.subject }
                .mapNotNull { subject ->
                    val mapping = subject.mappings.find { it.gradeLevel == gradeLevel }
                    if (mapping != null) SubjectViewDto(subject, mapping.elearnerSubjectId) else null
                }
                .distinctBy { it.subject.id }
        } else {
            // Fallback to Grade Level
            val classes = schoolClassRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
                .filter { it.gradeLevel == gradeLevel }
            val classIds = classes.mapNotNull { it.id }
            
            if (classIds.isNotEmpty()) {
                classSubjectRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
                    .filter { classIds.contains(it.schoolClass.id) }
                    .map { it.subject }
                    .mapNotNull { subject -> 
                        val mapping = subject.mappings.find { it.gradeLevel == gradeLevel }
                        if (mapping != null) SubjectViewDto(subject, mapping.elearnerSubjectId) else null
                    }
                    .distinctBy { it.subject.id }
            } else {
                emptyList()
            }
        }

        // 2. Build Full Term Menu Hierarchy
        val elearnerIds = subjects.map { it.elearnerSubjectId }
        val menuHierarchy = if (elearnerIds.isNotEmpty()) {
            learningContentService.getMenuHierarchy(elearnerIds, term)
        } else {
            emptyList()
        }

        // 3. Selection Logic
        val currentWeek = week ?: 1
        model.addAttribute("week", currentWeek)
        
        // If no subject is explicitly selected, try to pick the first one that has content this week
        var effectiveSubjectId = subjectId
        if (effectiveSubjectId == null && menuHierarchy.isNotEmpty()) {
            val firstWithContent = menuHierarchy.find { it.week == currentWeek }
                ?.subjects?.find { it.topics.isNotEmpty() || it.unassignedLessons.isNotEmpty() }
            effectiveSubjectId = firstWithContent?.id
        }

        // 4. Fetch Selected Content for main view
        if (effectiveSubjectId != null) {
            val contentList = learningContentService.getContentForWeek(listOf(effectiveSubjectId), currentWeek, term)
            val content = contentList.firstOrNull()
            model.addAttribute("content", content)
            
            val selectedSubject = subjects.find { it.elearnerSubjectId == effectiveSubjectId }
            model.addAttribute("selectedSubject", selectedSubject)
        }

        model.addAttribute("subjects", subjects)
        model.addAttribute("menuHierarchy", menuHierarchy)
        model.addAttribute("gradeLevel", gradeLevel)
        model.addAttribute("term", term)
        model.addAttribute("selectedSubjectId", effectiveSubjectId)
        model.addAttribute("selectedTopicId", topicId)
        model.addAttribute("classId", classId)
        model.addAttribute("user", customUser.user)
        val authorities = customUser.authorities.map { it.authority }
        val roleLabel = when {
            authorities.contains("ROLE_STUDENT") -> "STUDENT"
            authorities.contains("ROLE_TEACHER") -> "TEACHER"
            authorities.contains("ROLE_STAFF") -> "STAFF"
            authorities.contains("ROLE_admin") || authorities.contains("ROLE_ADMIN") -> "ADMIN" 
            else -> authorities.firstOrNull()?.replace("ROLE_", "") ?: "USER"
        }
        model.addAttribute("userRole", roleLabel)

        return "elearner/landing"
    }

    @GetMapping("/content")
    @PreAuthorize("hasAnyRole('STUDENT', 'STAFF', 'ADMIN', 'SCHOOL_ADMIN')")
    fun elearnerContent(
        @RequestParam subjectId: UUID,
        @RequestParam(required = false) gradeLevel: Int?,
        @RequestParam term: Int,
        @RequestParam week: Int,
        model: Model,
        authentication: Authentication
    ): String {
        val customUser = authentication.principal as CustomUserDetails
        
        val contentList = learningContentService.getContentForWeek(listOf(subjectId), week, term)
        val content = contentList.firstOrNull()
        
        // Find the mapped subject in myschool to get its display name
        // Find the mapped subject in myschool to get its display name
        val subject = subjectRepository.findAll().find { 
             it.mappings.any { m -> m.elearnerSubjectId == subjectId }
        }

        model.addAttribute("content", content)
        model.addAttribute("subject", subject)
        model.addAttribute("term", term)
        model.addAttribute("week", week)
        model.addAttribute("user", customUser.user)
        val authorities = customUser.authorities.map { it.authority }
        val roleLabel = when {
            authorities.contains("ROLE_STUDENT") -> "STUDENT"
            authorities.contains("ROLE_TEACHER") -> "TEACHER"
            authorities.contains("ROLE_STAFF") -> "STAFF"
            authorities.contains("ROLE_admin") || authorities.contains("ROLE_ADMIN") -> "ADMIN" 
            else -> authorities.firstOrNull()?.replace("ROLE_", "") ?: "USER"
        }
        model.addAttribute("userRole", roleLabel)
        
        return "elearner/content"
    }

    @GetMapping("/lesson/{id}")
    @PreAuthorize("hasAnyRole('STUDENT', 'STAFF', 'ADMIN', 'SCHOOL_ADMIN')")
    fun elearnerLesson(@PathVariable id: Int, model: Model, authentication: Authentication): String {
        val customUser = authentication.principal as CustomUserDetails
        val lesson = learningContentService.getLessonDetails(id) ?: throw RuntimeException("Lesson not found")
        
        model.addAttribute("lesson", lesson)
        model.addAttribute("user", customUser.user)
        val authorities = customUser.authorities.map { it.authority }
        val roleLabel = when {
            authorities.contains("ROLE_STUDENT") -> "STUDENT"
            authorities.contains("ROLE_TEACHER") -> "TEACHER"
            authorities.contains("ROLE_STAFF") -> "STAFF"
            authorities.contains("ROLE_admin") || authorities.contains("ROLE_ADMIN") -> "ADMIN" 
            else -> authorities.firstOrNull()?.replace("ROLE_", "") ?: "USER"
        }
        model.addAttribute("userRole", roleLabel)
        
        return "elearner/lesson"
    }

    @GetMapping("/api/lesson/{id}")
    @PreAuthorize("hasAnyRole('STUDENT', 'STAFF', 'ADMIN', 'SCHOOL_ADMIN')")
    @ResponseBody
    fun getLessonJson(@PathVariable id: Int, authentication: Authentication): Map<String, Any?> {
        val lesson = learningContentService.getLessonDetails(id) ?: throw RuntimeException("Lesson not found")
        val customUser = authentication.principal as CustomUserDetails
        val authorities = customUser.authorities.map { it.authority }
        val isStudent = authorities.contains("ROLE_STUDENT") && !authorities.contains("ROLE_TEACHER") && !authorities.contains("ROLE_STAFF") && !authorities.contains("ROLE_ADMIN")

        if (isStudent) {
            return lesson.filterKeys { key ->
                !key.endsWith("_teacher_notes") && 
                key != "teachers_overview" && 
                key != "teachers_notes" &&
                key != "generated_by_ai" &&
                key != "generation_id" &&
                key != "ai_model_used" &&
                key != "tokens_used"
            }
        }
        
        return lesson
    }
}

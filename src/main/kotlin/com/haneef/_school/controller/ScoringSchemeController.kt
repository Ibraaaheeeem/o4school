package com.haneef._school.controller

import java.util.UUID

import com.haneef._school.entity.*
import com.haneef._school.repository.*
import com.haneef._school.service.CustomUserDetails
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpSession
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*

data class ScoringSchemeItem(
    val id: Int,
    val name: String,
    val max: Int
)

data class ScoringSchemeApplyRequest(
    val scopeType: String, // SCHOOL, TRACK, DEPARTMENT, CLASS
    val scopeId: UUID?,
    val items: List<ScoringSchemeItem>
)

data class ScoringSchemeDetail(
    val classId: UUID,
    val className: String,
    val subjectId: UUID,
    val subjectName: String,
    val scoringScheme: String?
)

@Controller
@RequestMapping("/admin/assessments/scoring-schemes")
@PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'SCHOOL_ADMIN', 'STAFF', 'TEACHER')")
class ScoringSchemeController(
    private val classSubjectRepository: ClassSubjectRepository,
    private val schoolClassRepository: SchoolClassRepository,
    private val departmentRepository: DepartmentRepository,
    private val educationTrackRepository: EducationTrackRepository,
    private val schoolRepository: SchoolRepository,
    private val authorizationService: com.haneef._school.service.AuthorizationService
) {
    private val objectMapper = ObjectMapper().registerModule(com.fasterxml.jackson.module.kotlin.KotlinModule.Builder().build())

    @GetMapping
    fun scoringSchemesHome(model: Model, authentication: Authentication, session: HttpSession): String {
        val customUser = authentication.principal as CustomUserDetails
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/select-school"

        val school = schoolRepository.findById(selectedSchoolId).orElseThrow {
            RuntimeException("School not found")
        }

        val educationTracks = educationTrackRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
        
        model.addAttribute("user", customUser.user)
        model.addAttribute("userRole", "School Administrator")
        model.addAttribute("school", school)
        model.addAttribute("educationTracks", educationTracks)
        model.addAttribute("showFilters", false)

        return "admin/assessments/scoring-schemes"
    }

    @PostMapping("/apply")
    @ResponseBody
    fun applyScoringScheme(
        @RequestBody request: ScoringSchemeApplyRequest,
        session: HttpSession
    ): Map<String, Any> {
        val selectedSchoolId = authorizationService.validateSchoolAccess(
            session.getAttribute("selectedSchoolId") as? UUID
        )

        // Validate total marks
        val totalMax = request.items.sumOf { it.max }
        if (totalMax != 100) {
            return mapOf("success" to false, "message" to "Total maximum marks must equal 100. Current total: $totalMax")
        }

        val jsonScheme = objectMapper.writeValueAsString(request.items)

        val affectedCount = when (request.scopeType) {
            "SCHOOL" -> {
                val classes = schoolClassRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
                classes.forEach { it.scoringScheme = jsonScheme }
                schoolClassRepository.saveAll(classes)
                classes.size
            }
            "TRACK" -> {
                // Validate track belongs to school
                authorizationService.validateAndGetEducationTrack(request.scopeId!!, selectedSchoolId)
                val departments = departmentRepository.findByTrackIdAndIsActive(request.scopeId!!, true)
                val classes = departments.flatMap { dept ->
                    schoolClassRepository.findByDepartmentIdAndIsActive(dept.id!!, true)
                }
                classes.forEach { it.scoringScheme = jsonScheme }
                schoolClassRepository.saveAll(classes)
                classes.size
            }
            "DEPARTMENT" -> {
                // Validate department belongs to school
                authorizationService.validateAndGetDepartment(request.scopeId!!, selectedSchoolId)
                val classes = schoolClassRepository.findByDepartmentIdAndIsActive(request.scopeId!!, true)
                classes.forEach { it.scoringScheme = jsonScheme }
                schoolClassRepository.saveAll(classes)
                classes.size
            }
            "CLASS" -> {
                // Validate class belongs to school
                val schoolClass = authorizationService.validateAndGetSchoolClass(request.scopeId!!, selectedSchoolId)
                schoolClass.scoringScheme = jsonScheme
                schoolClassRepository.save(schoolClass)
                1
            }
            else -> 0
        }

        return mapOf(
            "success" to true,
            "message" to "Successfully applied scoring scheme to $affectedCount subject-class combinations.",
            "affectedCount" to affectedCount
        )
    }

    @GetMapping("/list")
    @ResponseBody
    fun listScoringSchemes(
        @RequestParam scopeType: String,
        @RequestParam(required = false) scopeId: UUID?,
        session: HttpSession
    ): List<ScoringSchemeDetail> {
        val selectedSchoolId = authorizationService.validateSchoolAccess(
            session.getAttribute("selectedSchoolId") as? UUID
        )

        val classes = when (scopeType) {
            "SCHOOL" -> schoolClassRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
            "TRACK" -> {
                // Validate track belongs to school
                authorizationService.validateAndGetEducationTrack(scopeId!!, selectedSchoolId)
                val departments = departmentRepository.findByTrackIdAndIsActive(scopeId!!, true)
                departments.flatMap { dept ->
                    schoolClassRepository.findByDepartmentIdAndIsActive(dept.id!!, true)
                }
            }
            "DEPARTMENT" -> {
                // Validate department belongs to school
                authorizationService.validateAndGetDepartment(scopeId!!, selectedSchoolId)
                schoolClassRepository.findByDepartmentIdAndIsActive(scopeId!!, true)
            }
            "CLASS" -> {
                // Validate class belongs to school
                listOf(authorizationService.validateAndGetSchoolClass(scopeId!!, selectedSchoolId))
            }
            else -> emptyList()
        }

        return classes.map { cls ->
            ScoringSchemeDetail(
                classId = cls.id!!,
                className = cls.className,
                subjectId = UUID.randomUUID(), // Not applicable for class-level scheme
                subjectName = "All Subjects",
                scoringScheme = cls.scoringScheme
            )
        }
    }
}

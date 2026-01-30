package com.haneef._school.controller

import com.haneef._school.repository.*
import com.haneef._school.service.CustomUserDetails
import com.haneef._school.service.LearningContentService
import jakarta.servlet.http.HttpSession
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

@Controller
@RequestMapping("/student")
class StudentProfileController(
    private val studentRepository: StudentRepository,
    private val studentClassRepository: StudentClassRepository,
    private val examinationRepository: ExaminationRepository,
    private val assessmentRepository: AssessmentRepository,
    private val schoolRepository: SchoolRepository,
    private val termRepository: TermRepository,
    private val classSubjectRepository: ClassSubjectRepository,
    private val learningContentService: LearningContentService
) {

    @GetMapping("/view-as/{id}")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'ADMIN', 'STAFF', 'STUDENT')")
    fun viewStudentProfile(
        @PathVariable id: UUID,
        model: Model,
        authentication: Authentication,
        session: HttpSession
    ): String {
        val customUser = authentication.principal as CustomUserDetails
        val userRole = customUser.authorities.firstOrNull()?.authority?.replace("ROLE_", "") ?: "STUDENT"

        // Fetch Student
        val student = studentRepository.findById(id).orElseThrow {
            RuntimeException("Student not found")
        }

        // Security / Access Control
        val selectedSchoolId: UUID
        
        if (userRole == "STUDENT") {
            // Student can only view their own profile
            if (student.user.id != customUser.user.id) {
                return "redirect:/student/dashboard?error=Unauthorized+access"
            }
            selectedSchoolId = student.schoolId ?: throw RuntimeException("Student has no school assigned")
        } else {
            // Admin/Staff must belong to the same school
            // Usage of session for selectedSchoolId for Admins
             val sessionSchoolId = session.getAttribute("selectedSchoolId") as? UUID
             selectedSchoolId = sessionSchoolId ?: student.schoolId ?: throw RuntimeException("School context missing")
                
            if (student.schoolId != selectedSchoolId) {
                // If the user is a system admin, they might be accessing cross-school, 
                // but usually they select a school context.
                // For safety, let's ensure the viewer has rights to this school.
                // Simplified: If not self, warn if school mismatch, but System Admin might override.
                if (userRole != "SYSTEM_ADMIN" && userRole != "ADMIN") {
                     return "redirect:/dashboard?error=Unauthorized+access"
                }
            }
        }

        val school = schoolRepository.findById(selectedSchoolId).orElse(null)

        // Fetch Classes (Current/Active)
        val studentClasses = studentClassRepository.findByStudentIdWithClassAndTrack(id)
        val activeClasses = studentClasses.filter { it.isActive }
        val classIds = activeClasses.mapNotNull { it.schoolClass.id }

        // Fetch Published Exams for these classes
        val publishedExams = if (classIds.isNotEmpty()) {
            examinationRepository.findBySchoolClassIdInAndIsActiveAndIsPublished(classIds, true, true)
        } else {
            emptyList()
        }

        // Fetch Assessments (Gradings/History)
        val assessments = assessmentRepository.findByStudentIdAndSchoolIdAndIsActive(id, selectedSchoolId, true)
            .sortedByDescending { it.session + it.term } // Sort by recent

        model.addAttribute("student", student)
        model.addAttribute("school", school)
        model.addAttribute("activeClasses", activeClasses)
        model.addAttribute("publishedExams", publishedExams)
        model.addAttribute("assessments", assessments)
        
        // --- Classroom / Syllabus Content ---
        var currentWeek = 0
        var currentTermInt = 1
        var classroomContent: List<Any> = emptyList() // Use Any to avoid import issues if DTO is private, but it's public
        
        try {
            val currentTermOpt = termRepository.findBySchoolIdAndIsCurrentTermAndIsActive(selectedSchoolId, true, true)
            if (currentTermOpt.isPresent) {
                val term = currentTermOpt.get()
                val weeksBetween = ChronoUnit.WEEKS.between(term.startDate, LocalDate.now())
                currentWeek = (weeksBetween + 1).toInt()
                if (currentWeek < 1) currentWeek = 1
                
                // Map Term Name to Int (1, 2, 3)
                val termName = term.termName.lowercase()
                currentTermInt = when {
                    termName.contains("first") || termName.contains("1st") -> 1
                    termName.contains("second") || termName.contains("2nd") -> 2
                    termName.contains("third") || termName.contains("3rd") -> 3
                    else -> 1
                }
            } else {
                 // Fallback if no term is active
                 currentWeek = 1
            }
            
            // Get Subjects for active classes
            if (activeClasses.isNotEmpty()) {
                val subjectNames = mutableSetOf<String>()
                activeClasses.forEach { studentClass ->
                    studentClass.schoolClass.id?.let { classId ->
                        val classSubjects = classSubjectRepository.findBySchoolClassIdWithSubject(classId)
                        subjectNames.addAll(classSubjects.map { it.subject.subjectName })
                    }
                }
                
                if (subjectNames.isNotEmpty()) {
                    classroomContent = learningContentService.getContentForWeek(subjectNames.toList(), currentWeek, currentTermInt)
                }
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
            // Graceful failure for classroom content
        }
        
        model.addAttribute("currentWeek", currentWeek)
        model.addAttribute("currentTermInt", currentTermInt)
        model.addAttribute("classroomContent", classroomContent)

        model.addAttribute("user", customUser.user)
        model.addAttribute("userRole", userRole)
        
        return "student/profile-view"
    }

    @GetMapping("/lesson/{id}")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'ADMIN', 'STAFF', 'STUDENT')")
    fun viewLesson(@PathVariable id: Int, model: Model): String {
        val lesson = learningContentService.getLessonDetails(id) ?: throw RuntimeException("Lesson not found")
        model.addAttribute("lesson", lesson)
        return "student/lesson-view"
    }
}

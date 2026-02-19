package com.haneef._school.controller

import com.haneef._school.repository.EducationTrackRepository
import com.haneef._school.repository.SchoolClassRepository
import com.haneef._school.repository.StudentRepository
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
    private val userDetailsService: CustomUserDetailsService,
    private val studentRepository: StudentRepository,
    private val educationTrackRepository: EducationTrackRepository,
    private val schoolClassRepository: SchoolClassRepository,
    private val studentClassRepository: com.haneef._school.repository.StudentClassRepository,
    private val academicSessionRepository: com.haneef._school.repository.AcademicSessionRepository,
    private val examinationRepository: com.haneef._school.repository.ExaminationRepository,
    private val examinationSubmissionRepository: com.haneef._school.repository.ExaminationSubmissionRepository,
    private val termRepository: com.haneef._school.repository.TermRepository
) {

    @GetMapping("/dashboard")
    fun studentDashboard(model: Model, authentication: Authentication): String {
        val customUser = authentication.principal as com.haneef._school.service.CustomUserDetails
        val user = customUser.user
        
        model.addAttribute("user", user)
        model.addAttribute("userRole", "Student")
        model.addAttribute("dashboardType", "student")
        
        // Find the student profile for this user matching the selected school context
        val forcedSchoolId = customUser.forcedSchoolId
        val student = if (forcedSchoolId != null) {
            user.studentProfiles.firstOrNull { it.schoolId == forcedSchoolId && it.isActive }
        } else {
            user.studentProfiles.firstOrNull { it.isActive }
        }

        if (student != null) {
            model.addAttribute("student", student)
            val schoolId = student.schoolId
            
            if (schoolId != null) {
                // Fetch current active academic session and term
                val currentSession = academicSessionRepository.findBySchoolIdAndIsCurrentSessionAndIsActive(schoolId, true, true)
                val currentTerm = termRepository.findBySchoolIdAndIsCurrentTermAndIsActive(schoolId, true, true).orElse(null)
                
                if (currentSession != null && currentTerm != null) {
                    // Fetch student's enrollments for the current session and term
                    val enrollments = studentClassRepository.findByStudentIdAndAcademicSessionIdAndTermIdAndIsActive(student.id!!, currentSession.id!!, currentTerm.id!!, true)
                    val enrolledClasses = enrollments.map { it.schoolClass }
                    val classIds = enrolledClasses.map { it.id!! }
                    
                    // Fetch all published and active examinations for the school with relationships
                    val allExams = examinationRepository.findBySchoolIdAndIsActiveAndIsPublishedWithRelationships(schoolId, true, true)
                    val studentExams = allExams.filter { classIds.contains(it.schoolClass.id) }
                    
                    val now = java.time.LocalDateTime.now()
                    val trackGroups = mutableMapOf<String, MutableMap<String, Any>>()

                    // Function to get or create group
                    fun getGroup(track: com.haneef._school.entity.EducationTrack?): MutableMap<String, Any> {
                        val trackId = track?.id?.toString() ?: "general"
                        return trackGroups.getOrPut(trackId) {
                            mutableMapOf(
                                "name" to (track?.name ?: "General"),
                                "classes" to mutableListOf<com.haneef._school.entity.SchoolClass>(),
                                "ongoing" to mutableListOf<com.haneef._school.entity.Examination>(),
                                "upcoming" to mutableListOf<com.haneef._school.entity.Examination>(),
                                "past" to mutableListOf<com.haneef._school.entity.Examination>()
                            )
                        }
                    }

                    // Populate classes
                    enrolledClasses.forEach { schoolClass ->
                        val group = getGroup(schoolClass.track)
                        (group["classes"] as MutableList<com.haneef._school.entity.SchoolClass>).add(schoolClass)
                    }

                    // Populate assessments
                    studentExams.forEach { exam ->
                        val group = getGroup(exam.schoolClass.track)
                        val category = when {
                            exam.startTime != null && exam.endTime != null && now.isAfter(exam.startTime) && now.isBefore(exam.endTime) -> "ongoing"
                            exam.endTime != null && now.isAfter(exam.endTime) -> "past"
                            else -> "upcoming"
                        }
                        (group[category] as MutableList<com.haneef._school.entity.Examination>).add(exam)
                    }
                    
                    // Fetch submissions for these exams
                    val examIds = studentExams.mapNotNull { it.id }
                    val submissions = if (examIds.isNotEmpty()) {
                        examinationSubmissionRepository.findByStudentIdAndExaminationIdIn(student.id!!, examIds)
                    } else {
                        emptyList()
                    }
                    val submissionMap = submissions.associateBy { it.examination.id }
                    
                    model.addAttribute("trackGroups", trackGroups)
                    model.addAttribute("submissions", submissionMap)
                    
                    // Stats calculation
                    model.addAttribute("subjectCount", enrolledClasses.sumOf { it.subjectAssignments.size })
                    model.addAttribute("upcomingExams", trackGroups.values.sumOf { (it["upcoming"] as List<*>).size })

                    // E-Learner Context (using already fetched currentTerm)
                    val weeksBetween = java.time.temporal.ChronoUnit.WEEKS.between(currentTerm.startDate, java.time.LocalDate.now())
                    val currentWeek = (weeksBetween + 1).toInt().coerceAtLeast(1)
                    model.addAttribute("currentTermNumber", currentTerm.termNumber ?: 1)
                    model.addAttribute("currentWeekNumber", currentWeek)
                } else {
                    model.addAttribute("trackGroups", emptyMap<String, Any>())
                    model.addAttribute("subjectCount", 0)
                    model.addAttribute("upcomingExams", 0)
                }
            }
        }
        
        return "dashboard/student-dashboard"
    }
}
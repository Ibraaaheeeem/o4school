@file:Suppress("DEPRECATION") // Legacy ca1Score/ca2Score/examScore fields accessed intentionally for backward compat
package com.haneef._school.controller

import com.haneef._school.repository.*
import com.haneef._school.service.CustomUserDetails
import com.haneef._school.service.LearningContentService
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpSession
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import com.haneef._school.entity.Assessment
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
    private val learningContentService: LearningContentService,
    private val subjectScoreRepository: SubjectScoreRepository,
    private val academicSessionRepository: AcademicSessionRepository,
    private val objectMapper: ObjectMapper
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
            .sortedWith(compareByDescending<Assessment> { it.academicSession.sessionYear }.thenByDescending { it.term.termName }) // Sort by recent
        
        // Eagerly fetch parent relationships to avoid LazyInitializationException in view
        // A simple way is to access the collection within the transaction or fetch join
        // Here we rely on Hibernate session being open or triggering it. 
        // For safety, we can initialize it.
        student.parentRelationships.forEach { pr -> 
            pr.parent.user.fullName // Access nested property to initialize
        }

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
            
                // Get Elearner Subject IDs for active classes with grade level resolution
                val elearnerSubjectIds = mutableSetOf<UUID>()
                activeClasses.forEach { studentClass ->
                    studentClass.schoolClass.let { schoolClass ->
                        if (schoolClass.id != null) {
                            val classSubjects = classSubjectRepository.findBySchoolClassIdWithSubject(schoolClass.id!!)
                            
                            classSubjects.forEach { cs ->
                                val subject = cs.subject
                                // First check for specific grade level mapping
                                val gradeLevel = schoolClass.gradeLevel ?: 0
                                
                                val specificMapping = subject.mappings.find { it.gradeLevel == gradeLevel }
                                
                                if (specificMapping != null) {
                                    elearnerSubjectIds.add(specificMapping.elearnerSubjectId)
                                }
                            }
                        }
                    }
                }
                
                if (elearnerSubjectIds.isNotEmpty()) {
                    classroomContent = learningContentService.getContentForWeek(elearnerSubjectIds.toList(), currentWeek, currentTermInt)
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

    @GetMapping("/report-card/{studentId}")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'ADMIN', 'STAFF', 'STUDENT')")
    fun viewReportCard(
        @PathVariable studentId: UUID,
        @RequestParam(required = false) session: String?,
        @RequestParam(required = false) term: String?,
        model: Model,
        authentication: Authentication,
        httpSession: HttpSession
    ): String {
        val customUser = authentication.principal as CustomUserDetails
        val userRole = customUser.authorities.firstOrNull()?.authority?.replace("ROLE_", "") ?: "STUDENT"
        
        // Security check
        val student = studentRepository.findById(studentId).orElseThrow { RuntimeException("Student not found") }
        
        if (userRole == "STUDENT") {
            if (student.user.id != customUser.user.id) {
                return "redirect:/student/dashboard?error=Unauthorized+access"
            }
        }
        
        val selectedSchoolId = student.schoolId ?: throw RuntimeException("Student has no school assigned")
        val school = schoolRepository.findById(selectedSchoolId).orElse(null)

        // Resolve Session/Term IDs and Names
        var effectiveSessionId: UUID? = null
        var effectiveTermId: UUID? = null
        var effectiveSessionName: String = ""
        var effectiveTermName: String = ""
        
        // 1. Resolve Session
        if (session != null) {
            val sessionEntity = academicSessionRepository.findBySchoolIdAndSessionYearAndIsActive(selectedSchoolId, session, true)
            if (sessionEntity != null) {
                effectiveSessionId = sessionEntity.id
                effectiveSessionName = sessionEntity.sessionYear
            }
        } else {
            effectiveSessionId = httpSession.getAttribute("selectedSessionId") as? UUID
        }
        
        // 2. Resolve Term
        if (effectiveSessionId != null) {
            if (term != null) {
                val termEntity = termRepository.findByAcademicSessionIdAndTermNameAndIsActive(effectiveSessionId, term, true).orElse(null)
                if (termEntity != null) {
                    effectiveTermId = termEntity.id
                    effectiveTermName = termEntity.termName
                }
            } else {
                 effectiveTermId = httpSession.getAttribute("selectedTermId") as? UUID
            }
        }
        
        // 3. Fallback to Defaults (Current Active)
        if (effectiveSessionId == null) {
             val currentSession = academicSessionRepository.findBySchoolIdAndIsCurrentSessionAndIsActive(selectedSchoolId, true, true)
             effectiveSessionId = currentSession?.id 
        }
        
        if (effectiveTermId == null && effectiveSessionId != null) {
             val currentTerm = termRepository.findByAcademicSessionIdAndIsCurrentTermAndIsActive(effectiveSessionId, true, true)
             effectiveTermId = currentTerm.orElse(null)?.id
             
             if (effectiveTermId == null) {
                  val terms = termRepository.findByAcademicSessionIdAndIsActiveOrderByStartDate(effectiveSessionId, true)
                  if (terms.isNotEmpty()) effectiveTermId = terms[0].id
             }
        }
        
        if (effectiveSessionId == null || effectiveTermId == null) {
            model.addAttribute("error", "Could not resolve academic session or term.")
             model.addAttribute("student", student)
             model.addAttribute("subjects", emptyList<Map<String, Any?>>())
            return "student/report-card"
        }

        // Fetch Assessment Data
        // Use Names for Assessment Lookup (as per Entity definition)
        // Use IDs for Class Lookup (as per Entity definition)
        
        // Fetch Session and Term Names for display and query
        val sessionName = academicSessionRepository.findById(effectiveSessionId).map { it.sessionYear }.orElse("Unknown Session")
        val termName = termRepository.findById(effectiveTermId).map { it.termName }.orElse("Unknown Term")

        // Get student enrollment to find class (uses IDs)
        // Try to find enrollment for the SPECIFIC session/term first
        var studentEnrollment = studentClassRepository.findByStudentIdAndAcademicSessionIdAndTermIdAndIsActive(
            studentId, effectiveSessionId, effectiveTermId, true
        ).filter { it.schoolId == selectedSchoolId }.firstOrNull()
        
        // If not found, try session-level enrollment (some schools enroll per session, not term)
        if (studentEnrollment == null) {
             studentEnrollment = studentClassRepository.findByStudentIdAndAcademicSessionIdAndIsActive(
                studentId, effectiveSessionId, true
            ).filter { it.schoolId == selectedSchoolId }.firstOrNull()
        }
        
        // If STILL null, we might be looking at a past/future session where they weren't enrolled.
        // Or maybe they are enrolled in the CURRENT session, but we are viewing a specific report.
        // User Requirement: "list the subjects with empty scores if the data is not available"
        // Determining "subjects" requires knowing the Class. If we can't find enrollment for the target session, 
        // we can try to fall back to their *current active* enrollment to at least show "what they would take",
        // OR just show an error if we strictly can't determine class.
        // However, usually report cards are tied to a class. If they weren't in a class that term, they have no report.
        // But let's be lenient: if we can't find enrollment for that specific term, check if they have ANY active enrollment in the school
        // and use that to display subjects (assuming they haven't changed class/track drastically).
        
        if (studentEnrollment == null) {
             // Fallback: Get most recent active enrollment
             studentEnrollment = studentClassRepository.findByStudentIdAndIsActive(studentId, true)
                .filter { it.schoolId == selectedSchoolId }
                .maxByOrNull { it.academicSession.startDate } // Assuming access to session start date, validation needed
        }
            
        var className = "N/A"
        var trackName = "N/A"
        var subjectDataList: List<Map<String, Any?>> = emptyList()
        var assessmentDetails: Assessment? = null
        
        if (studentEnrollment != null) {
            val classId = studentEnrollment!!.schoolClass.id!!
            className = studentEnrollment!!.schoolClass.className
            trackName = studentEnrollment!!.schoolClass.department?.track?.name ?: ""
            
            val classSubjects = classSubjectRepository.findBySchoolClassIdAndIsActive(classId, true)
            
            // Find Assessment (uses Names)
            // Note: effectiveSessionName/effectiveTermName might be needed if they differ from what was resolved by ID
            // accessing sessionName/termName variables which are effectively the names.
            
            val assessment = assessmentRepository.findByStudentIdAndAcademicSessionIdAndTermIdAndSchoolIdAndIsActive(
                studentId, effectiveSessionId, effectiveTermId, selectedSchoolId, true
            ).orElse(null)
            
            assessmentDetails = assessment
            
            subjectDataList = classSubjects.map { cs ->
                var ca1: Int? = null
                var ca2: Int? = null
                var exam: Int? = null
                var total: Int? = null
                var grade: String? = null
                var remark: String? = null
                
                if (assessment != null && cs.subject.id != null) {
                    val subjectScores = subjectScoreRepository.findByAssessmentIdAndSubjectIdAndSchoolIdAndIsActive(
                        assessment.id!!, cs.subject.id!!, selectedSchoolId, true
                    )
                    if (subjectScores.isNotEmpty()) {
                        val ss = subjectScores[0]
                        total = ss.getTotalScore()
                        grade = ss.grade
                        remark = ss.remark

                        // Sync from JSON map (Source of Truth)
                        if (!ss.scoresJson.isNullOrBlank()) {
                            try {
                                val scoresMap = objectMapper.readValue(ss.scoresJson, object : com.fasterxml.jackson.core.type.TypeReference<Map<String, Int?>>() {})
                                scoresMap["1st CA"]?.let { ca1 = it }
                                scoresMap["2nd CA"]?.let { ca2 = it }
                                scoresMap["Exam"]?.let { exam = it }
                            } catch (e: Exception) {
                                // Fallback to existing ca1, ca2, exam values if JSON parsing fails
                            }
                        }
                    }
                }
                
                val cs_mapped = mapOf(
                    "subjectName" to cs.subject.subjectName,
                    "ca1" to ca1,
                    "ca2" to ca2,
                    "exam" to exam,
                    "total" to total,
                    "grade" to grade,
                    "remark" to remark
                )
                cs_mapped
            }
            
            // Calculate summary statistics
            val totals = subjectDataList.mapNotNull { it["total"] as? Int }
            val totalScore = totals.sum()
            val totalAverage = if (totals.isNotEmpty()) totalScore.toDouble() / totals.size else 0.0
            
            // For highest and lowest, we calculate from each subject's scores if available
            val allScores = subjectDataList.mapNotNull { subject ->
                val ca1 = (subject["ca1"] as? Int) ?: 0
                val ca2 = (subject["ca2"] as? Int) ?: 0
                val exam = (subject["exam"] as? Int) ?: 0
                Triple(ca1, ca2, exam)
            }
            
            val allScoresList = allScores.flatMap { (ca1, ca2, exam) -> listOf(ca1, ca2, exam) }.filter { it > 0 }
            val highestScoresAvg = if (allScoresList.isNotEmpty()) allScoresList.maxOrNull()?.toDouble() ?: 0.0 else 0.0
            val lowestScoresAvg = if (allScoresList.isNotEmpty()) allScoresList.minOrNull()?.toDouble() ?: 0.0 else 0.0
            
            // Determine performance grade based on average
            val performanceGrade = when {
                totalAverage >= 90 -> "A"
                totalAverage >= 80 -> "B"
                totalAverage >= 70 -> "C"
                totalAverage >= 60 -> "D"
                totalAverage >= 50 -> "E"
                else -> "F"
            }
            
            model.addAttribute("totalScore", totalScore)
            model.addAttribute("totalAverage", String.format("%.1f", totalAverage))
            model.addAttribute("highestScoresAvg", String.format("%.1f", highestScoresAvg))
            model.addAttribute("lowestScoresAvg", String.format("%.1f", lowestScoresAvg))
            model.addAttribute("performanceGrade", performanceGrade)
            
        } else {
             // If we really can't find any class info, we can't list subjects.
             model.addAttribute("error", "Student Class information not found. Cannot generate report.")
        }
        
        // Add parent info for the report card header
         student.parentRelationships.forEach { pr -> 
            pr.parent.user.fullName // Initialize
        }

        model.addAttribute("student", student)
        model.addAttribute("school", school)
        model.addAttribute("className", className)
        model.addAttribute("trackName", trackName)
        model.addAttribute("sessionName", sessionName)
        model.addAttribute("termName", termName)
        model.addAttribute("subjects", subjectDataList)
        model.addAttribute("assessment", assessmentDetails)

        return "student/report-card"
    }
}

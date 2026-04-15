@file:Suppress("DEPRECATION") // Legacy ca1Score/ca2Score/examScore fields accessed intentionally for backward compat
package com.haneef._school.controller

import java.util.UUID

import com.haneef._school.entity.*
import com.haneef._school.repository.*
import com.haneef._school.dto.*
import com.haneef._school.service.CustomUserDetails
import com.haneef._school.service.ReportPdfGenerator
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpSession
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime
import java.io.ByteArrayOutputStream
import org.slf4j.LoggerFactory


@Controller
@RequestMapping("/admin/assessments/reports")
@PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'SCHOOL_ADMIN', 'STAFF', 'TEACHER')")
class AssessmentReportController(
    private val studentRepository: StudentRepository,
    private val studentClassRepository: StudentClassRepository,
    private val schoolClassRepository: SchoolClassRepository,
    private val academicSessionRepository: AcademicSessionRepository,
    private val termRepository: TermRepository,
    private val classSubjectRepository: ClassSubjectRepository,
    private val assessmentRepository: AssessmentRepository,
    private val subjectScoreRepository: SubjectScoreRepository,
    private val subjectRepository: SubjectRepository,
    private val examinationRepository: ExaminationRepository,
    private val schoolRepository: SchoolRepository,
    private val educationTrackRepository: EducationTrackRepository,
    private val departmentRepository: DepartmentRepository,
    private val authorizationService: com.haneef._school.service.AuthorizationService,
    private val staffRepository: StaffRepository,
    private val classTeacherRepository: ClassTeacherRepository,
    private val subjectTeacherRepository: SubjectTeacherRepository,
    private val reportPdfGenerator: ReportPdfGenerator
) {
    private val objectMapper = ObjectMapper().registerModule(com.fasterxml.jackson.module.kotlin.KotlinModule.Builder().build())
    private val logger = LoggerFactory.getLogger(AssessmentReportController::class.java)

    @GetMapping
    fun reportsHome(
        model: Model, 
        authentication: Authentication, 
        session: HttpSession,
        @RequestParam(required = false) trackId: UUID?,
        @RequestParam(required = false) departmentId: UUID?,
        @RequestParam(required = false) classId: UUID?,
        @RequestParam(required = false) termId: UUID?,
        @RequestParam(required = false) sessionId: UUID?
    ): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/select-school"

        logger.info("DEBUG: reportsHome - Request Params: classId=$classId, termId=$termId, sessionId=$sessionId")
        logger.info("DEBUG: reportsHome - Session Attrs: selectedSessionId=${session.getAttribute("selectedSessionId")}, selectedTermId=${session.getAttribute("selectedTermId")}")

        val customUser = authentication.principal as CustomUserDetails
        val school = schoolRepository.findById(selectedSchoolId).orElseThrow { RuntimeException("School not found") }
        val academicSessions = academicSessionRepository.findBySchoolIdAndIsActiveOrderByYearDesc(selectedSchoolId, true)
        val educationTracks = educationTrackRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
        val departments = departmentRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
        val classes = schoolClassRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
        
        // Resolve Effective Session
        var resolvedSessionId = session.getAttribute("selectedSessionId") as? UUID
        
        // Fallback to active current session if still null
        if (resolvedSessionId == null) {
             val currentSession = academicSessionRepository.findBySchoolIdAndIsCurrentSessionAndIsActive(selectedSchoolId, true, true)
             resolvedSessionId = currentSession?.id
             if (resolvedSessionId != null) {
                 session.setAttribute("selectedSessionId", resolvedSessionId)
                 logger.info("DEBUG: reportsHome - Set initialized selectedSessionId=$resolvedSessionId")
             }
        }
        logger.info("DEBUG: reportsHome - resolvedSessionId=$resolvedSessionId")
        
        // Get terms for the selected session (only if session is selected)
        val terms = if (resolvedSessionId != null) {
            termRepository.findByAcademicSessionIdAndIsActiveOrderByStartDate(resolvedSessionId, true)
        } else {
            emptyList()
        }
        
        // Resolve Effective Term
        var resolvedTermId = session.getAttribute("selectedTermId") as? UUID
        logger.info("DEBUG: reportsHome - Initial resolvedTermId from session=$resolvedTermId")

        // Validate that the resolved term actually belongs to the resolved session
        if (resolvedTermId != null && resolvedSessionId != null) {
            val isTermInSession = terms.any { it.id == resolvedTermId }
            if (!isTermInSession && terms.isNotEmpty()) {
                logger.warn("DEBUG: reportsHome - Term $resolvedTermId does not belong to Session $resolvedSessionId. Resetting.")
                resolvedTermId = null
                // Clean up session attribute to prevent recurring mismatch
                session.removeAttribute("selectedTermId")
            }
        }
        
        // Get current term if not provided/found and session is selected
        if (resolvedTermId == null && resolvedSessionId != null) {
             val currentTerm = termRepository.findByAcademicSessionIdAndIsCurrentTermAndIsActive(resolvedSessionId, true, true)
                .orElse(null)
             resolvedTermId = currentTerm?.id
             
             // If no current term active, pick the first one
             if (resolvedTermId == null && terms.isNotEmpty()) {
                 resolvedTermId = terms[0].id
             }
             
             if (resolvedTermId != null) {
                 session.setAttribute("selectedTermId", resolvedTermId)
                 logger.info("DEBUG: reportsHome - Set initialized selectedTermId=$resolvedTermId")
             }
        }
        logger.info("DEBUG: reportsHome - resolvedTermId=$resolvedTermId")

        model.addAttribute("user", customUser.user)
        model.addAttribute("userRole", "School Administrator")
        model.addAttribute("school", school)
        model.addAttribute("academicSessions", academicSessions)
        model.addAttribute("educationTracks", educationTracks)
        model.addAttribute("departments", departments)
        model.addAttribute("classes", classes)
        model.addAttribute("terms", terms)
        
        model.addAttribute("selectedTrackId", trackId)
        model.addAttribute("selectedDepartmentId", departmentId)
        model.addAttribute("selectedClassId", classId)
        model.addAttribute("selectedTermId", resolvedTermId)
        model.addAttribute("selectedSessionId", resolvedSessionId)
        
        model.addAttribute("showFilters", true)
        model.addAttribute("hideSubjectFilter", true)
        model.addAttribute("hideExamTypeFilter", true)

        // Get assessment statistics
        val totalExaminations = examinationRepository.countBySchoolIdAndIsActive(selectedSchoolId, true)
        val publishedExaminations = examinationRepository.countBySchoolIdAndIsActiveAndIsPublished(selectedSchoolId, true, true)
        model.addAttribute("assessmentStats", mapOf(
            "totalExaminations" to totalExaminations,
            "publishedExaminations" to publishedExaminations
        ))

        // If class, session and term are selected, load students
        if (classId != null && resolvedSessionId != null && resolvedTermId != null) {
            val enrollments = studentClassRepository.findBySchoolClassIdAndAcademicSessionIdAndTermIdAndIsActive(
                classId, resolvedSessionId, resolvedTermId, true
            ).filter { it.schoolId == selectedSchoolId }
            logger.info("DEBUG: Main endpoint - Found ${enrollments.size} students for class $classId, session $resolvedSessionId, term $resolvedTermId")
            
            model.addAttribute("students", enrollments.map { it.student })
        }

        return "admin/assessments/reports"
    }

    @GetMapping("/filter")
    fun filterReports(
        model: Model,
        authentication: Authentication,
        session: HttpSession,
        @RequestParam(required = false) trackId: UUID?,
        @RequestParam(required = false) departmentId: UUID?,
        @RequestParam(required = false) classId: UUID?,
        @RequestParam(required = false) term: String?,
        @RequestParam(required = false) termId: UUID?,
        @RequestParam(required = false) sessionYear: String?
    ): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "fragments/error :: error-message"

        model.addAttribute("selectedTrackId", trackId)
        model.addAttribute("selectedDepartmentId", departmentId)
        model.addAttribute("selectedClassId", classId)
        model.addAttribute("selectedTerm", term)
        model.addAttribute("selectedTermId", termId)
        model.addAttribute("selectedSession", sessionYear)

        if (classId != null && sessionYear != null) {
            val sessionEntity = academicSessionRepository.findBySchoolIdAndSessionYearAndIsActive(
                selectedSchoolId, sessionYear, true
            )

            if (sessionEntity != null) {
                // If termId is provided, use it directly
                val enrollments = if (termId != null) {
                    println("DEBUG: Filter endpoint - Fetching for Term ID: $termId")
                    session.setAttribute("selectedTermId", termId)
                    session.setAttribute("selectedSessionId", sessionEntity.id)
                    studentClassRepository.findBySchoolClassIdAndAcademicSessionIdAndTermIdAndIsActive(
                        classId, sessionEntity.id!!, termId, true
                    ).filter { it.schoolId == selectedSchoolId }
                } else if (!term.isNullOrBlank()) {
                    // fall back to resolving string
                    val termEntity = termRepository.findByAcademicSessionIdAndTermNameAndIsActive(
                        sessionEntity.id!!, term, true
                    ).orElse(null)
                    
                    if (termEntity != null) {
                        println("DEBUG: Filter endpoint - Fetching for Term: ${termEntity.termName}")
                        session.setAttribute("selectedTermId", termEntity.id)
                        session.setAttribute("selectedSessionId", sessionEntity.id)
                        studentClassRepository.findBySchoolClassIdAndAcademicSessionIdAndTermIdAndIsActive(
                            classId, sessionEntity.id!!, termEntity.id!!, true
                        ).filter { it.schoolId == selectedSchoolId }
                    } else {
                         println("DEBUG: Filter endpoint - Term $term not found")
                        emptyList()
                    }
                } else {
                     println("DEBUG: Filter endpoint - No term provided, fetching by session")
                     studentClassRepository.findBySchoolClassIdAndAcademicSessionIdAndIsActive(
                        classId, sessionEntity.id!!, true
                    ).filter { it.schoolId == selectedSchoolId }
                }
                
                println("DEBUG: Filter endpoint - Found ${enrollments.size} students")
                model.addAttribute("students", enrollments.map { it.student })
            }
        }

        return "admin/assessments/reports :: reports-content"
    }

    @GetMapping("/students")
    @ResponseBody
    fun getStudentsByClass(
        @RequestParam classId: UUID,
        @RequestParam session: String,
        @RequestParam(required = false) term: String?,
        session_http: HttpSession
    ): List<StudentReportInfo> {
        val selectedSchoolId = authorizationService.validateSchoolAccess(
            session_http.getAttribute("selectedSchoolId") as? UUID
        )

        // Validate class belongs to school
        authorizationService.validateAndGetSchoolClass(classId, selectedSchoolId)

        val sessionEntity = academicSessionRepository.findBySchoolIdAndSessionYearAndIsActive(
            selectedSchoolId, session, true
        )
        
        if (sessionEntity != null) {
             if (!term.isNullOrBlank()) {
                 val termEntity = termRepository.findByAcademicSessionIdAndTermNameAndIsActive(
                    sessionEntity.id!!, term, true
                ).orElse(null)
                
                if (termEntity != null) {
                    return studentClassRepository.findBySchoolClassIdAndAcademicSessionIdAndTermIdAndIsActive(
                        classId, sessionEntity.id!!, termEntity.id!!, true
                    ).filter { it.schoolId == selectedSchoolId }
                    .map { enrollment ->
                        StudentReportInfo(
                            id = enrollment.student.id!!,
                            admissionNumber = enrollment.student.admissionNumber ?: "",
                            fullName = enrollment.student.user.fullName ?: "User"
                        )
                    }
                }
             }

            // Fallback to session if no term provided or term not found
            return studentClassRepository.findBySchoolClassIdAndAcademicSessionIdAndIsActive(
                classId, sessionEntity.id!!, true
            ).filter { it.schoolId == selectedSchoolId }
             .map { enrollment ->
                StudentReportInfo(
                    id = enrollment.student.id!!,
                    admissionNumber = enrollment.student.admissionNumber ?: "",
                    fullName = enrollment.student.user.fullName ?: "User"
                )
            }
        }

        return emptyList()
    }

    @GetMapping("/api/classes/{classId}/students")
    @ResponseBody
    fun getStudentsByClassApi(
        @PathVariable classId: UUID,
        @RequestParam(required = false) sessionId: UUID?,
        @RequestParam(required = false) termId: UUID?,
        session_http: HttpSession
    ): List<Map<String, Any?>> {
        val selectedSchoolId = authorizationService.validateSchoolAccess(
            session_http.getAttribute("selectedSchoolId") as? UUID
        )

        // Validate class belongs to school
        authorizationService.validateAndGetSchoolClass(classId, selectedSchoolId)

        return try {
            // Resolve effective Session/Term
            val effectiveSessionId = sessionId ?: session_http.getAttribute("selectedSessionId") as? UUID
            val effectiveTermId = termId ?: session_http.getAttribute("selectedTermId") as? UUID

            // Get students enrolled in the class for the specified session/term
            val studentClasses = when {
                effectiveSessionId != null && effectiveTermId != null -> {
                    val results = studentClassRepository.findBySchoolClassIdAndAcademicSessionIdAndTermIdAndIsActive(
                        classId, effectiveSessionId, effectiveTermId, true
                    ).filter { it.schoolId == selectedSchoolId }
                    println("DEBUG: Found ${results.size} students for specific session $effectiveSessionId and term $effectiveTermId")
                    
                    // If no students found for specific session/term, try just session (if frontend didn't restrict)
                    // Wait, user request says "ensure you select ... same as header session/term context"
                    // So we should be strict.
                    if (results.isEmpty() && termId == null) {
                         // Only fallback if term wasn't explicitly requested by frontend? 
                         // But here we are using effective IDs.
                         // Let's stick to returning what matches the context.
                         results
                    } else {
                        results
                    }
                }
                effectiveSessionId != null -> {
                    val results = studentClassRepository.findBySchoolClassIdAndAcademicSessionIdAndIsActive(
                        classId, effectiveSessionId, true
                    ).filter { it.schoolId == selectedSchoolId }
                    println("DEBUG: Found ${results.size} students for session $effectiveSessionId")
                    results
                }
                else -> {
                    // Fallback to current ACTIVE session/term if absolutely nothing is found in context
                    // This mirrors reportsHome logic roughly without model population
                    val currentSession = academicSessionRepository.findBySchoolIdAndIsCurrentSessionAndIsActive(selectedSchoolId, true, true)
                    if (currentSession != null) {
                         val currentTerm = termRepository.findByAcademicSessionIdAndIsCurrentTermAndIsActive(currentSession.id!!, true, true).orElse(null)
                         if (currentTerm != null) {
                              studentClassRepository.findBySchoolClassIdAndAcademicSessionIdAndTermIdAndIsActive(
                                classId, currentSession.id!!, currentTerm.id!!, true
                             ).filter { it.schoolId == selectedSchoolId }
                         } else {
                             studentClassRepository.findBySchoolClassIdAndAcademicSessionIdAndIsActive(
                                classId, currentSession.id!!, true
                             ).filter { it.schoolId == selectedSchoolId }
                         }
                    } else {
                        // Truly nothing
                        emptyList()
                    }
                }
            }

            studentClasses.map { studentClass ->
                val student = studentClass.student
                mapOf<String, Any?>(
                    "id" to student.id!!,
                    "name" to (student.user.fullName ?: "User"),
                    "admissionNumber" to (student.admissionNumber ?: ""),
                    "studentId" to student.studentId
                )
            }.sortedBy { (it["name"] as? String) ?: "" }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    @GetMapping("/api/sessions/{sessionId}/terms")
    @ResponseBody
    fun getTermsForSession(
        @PathVariable sessionId: UUID,
        session_http: HttpSession
    ): List<Map<String, String>> {
        val selectedSchoolId = authorizationService.validateSchoolAccess(
            session_http.getAttribute("selectedSchoolId") as? UUID
        )
        
        // Validate session belongs to school
        val academicSession = authorizationService.validateAndGetAcademicSession(sessionId, selectedSchoolId)
        
        val terms = termRepository.findByAcademicSessionIdAndIsActiveOrderByStartDate(sessionId, true)
        
        return terms.map { term ->
            mapOf(
                "id" to term.id.toString(),
                "name" to term.termName,
                "isCurrent" to term.isCurrentTerm.toString()
            )
        }
    }

    @GetMapping("/api/tracks/{trackId}/departments")
    @ResponseBody
    fun getDepartmentsByTrack(
        @PathVariable trackId: UUID,
        session_http: HttpSession
    ): List<Map<String, Any>> {
        val selectedSchoolId = authorizationService.validateSchoolAccess(
            session_http.getAttribute("selectedSchoolId") as? UUID
        )
        
        val departments = departmentRepository.findByTrackIdAndIsActive(trackId, true)
            .filter { it.schoolId == selectedSchoolId }
        
        return departments.map { dept ->
            mapOf(
                "id" to dept.id!!,
                "name" to dept.name
            )
        }
    }

    @GetMapping("/api/departments/{departmentId}/classes")
    @ResponseBody
    fun getClassesByDepartment(
        @PathVariable departmentId: UUID,
        session_http: HttpSession
    ): List<Map<String, Any>> {
        val selectedSchoolId = authorizationService.validateSchoolAccess(
            session_http.getAttribute("selectedSchoolId") as? UUID
        )
        
        val classes = schoolClassRepository.findByDepartmentIdAndIsActive(departmentId, true)
            .filter { it.schoolId == selectedSchoolId }
        
        return classes.map { cls ->
            mapOf(
                "id" to cls.id!!,
                "name" to cls.className
            )
        }
    }

    @GetMapping("/student-data")
    @ResponseBody
    fun getStudentAssessmentData(
        @RequestParam studentId: UUID,
        @RequestParam classId: UUID,
        @RequestParam(required = false) sessionId: UUID?,
        @RequestParam(required = false) termId: UUID?,
        @RequestParam(required = false) session: String?,
        @RequestParam(required = false) term: String?,
        session_http: HttpSession
    ): AssessmentReportData {
        val selectedSchoolId = authorizationService.validateSchoolAccess(
            session_http.getAttribute("selectedSchoolId") as? UUID
        )

        // Resolve Effective Session
        val sessionAttributeId = session_http.getAttribute("selectedSessionId") as? UUID
        var resolvedSessionId = sessionId 
            ?: if (!session.isNullOrBlank()) {
                academicSessionRepository.findBySchoolIdAndSessionYearAndIsActive(selectedSchoolId, session, true)?.id
            } else null
            ?: sessionAttributeId
        
        // Fallback to active current session logic (reused)
        if (resolvedSessionId == null) {
             val currentSession = academicSessionRepository.findBySchoolIdAndIsCurrentSessionAndIsActive(selectedSchoolId, true, true)
             resolvedSessionId = currentSession?.id // Could still be null if no session exists, but unlikely
        }
        
        // Resolve Effective Term
        val termAttributeId = session_http.getAttribute("selectedTermId") as? UUID
        var resolvedTermId = termId 
            ?: if (resolvedSessionId != null && !term.isNullOrBlank()) {
                termRepository.findByAcademicSessionIdAndTermNameAndIsActive(resolvedSessionId!!, term, true).map { it.id }.orElse(null)
            } else null
            ?: termAttributeId
        
        // Fallback Term logic
         if (resolvedTermId == null && resolvedSessionId != null) {
             val currentTerm = termRepository.findByAcademicSessionIdAndIsCurrentTermAndIsActive(resolvedSessionId, true, true)
                .orElse(null)
             resolvedTermId = currentTerm?.id
             
             if (resolvedTermId == null) {
                 // Try first term
                 val terms = termRepository.findByAcademicSessionIdAndIsActiveOrderByStartDate(resolvedSessionId, true)
                 if (terms.isNotEmpty()) resolvedTermId = terms[0].id
             }
        }
        
        if (resolvedSessionId == null || resolvedTermId == null) {
            throw RuntimeException("Academic Session or Term context could not be resolved.")
        }
        
        val effectiveSessionId = resolvedSessionId!!
        val effectiveTermId = resolvedTermId!!

        // Resolve Session and Term Names to ensure consistency with Assessment entity
        val sessionName = academicSessionRepository.findById(effectiveSessionId).map { it.sessionYear }.orElse(effectiveSessionId.toString())
        val termName = termRepository.findById(effectiveTermId).map { it.termName }.orElse(effectiveTermId.toString())

        // Validate student and class belong to school
        val student = authorizationService.validateAndGetStudent(studentId, selectedSchoolId)
        authorizationService.validateAndGetSchoolClass(classId, selectedSchoolId)
        
        // Get all subjects for this class
        val classSubjects = classSubjectRepository.findBySchoolClassIdAndIsActive(classId, true)
        
        // Get existing assessment if any
        val assessmentOpt = assessmentRepository.findByStudentIdAndAcademicSessionIdAndTermIdAndSchoolIdAndIsActive(
            studentId, effectiveSessionId, effectiveTermId, selectedSchoolId, true
        )

        val assessment = assessmentOpt.orElse(null)

        val subjectDataList = classSubjects.map { cs ->
            var ca1: Int? = null
            var ca2: Int? = null
            var exam: Int? = null
            var total: Int? = null
            var grade: String? = null
            var remark: String? = null

            var scoresMap: Map<String, Int?> = HashMap()

            if (assessment != null) {
                val subjectScores = subjectScoreRepository.findByAssessmentIdAndSubjectIdAndSchoolIdAndIsActive(
                    assessment.id!!, cs.subject.id!!, selectedSchoolId, true
                )
                if (subjectScores.isNotEmpty()) {
                    val ss = subjectScores[0]
                    total = ss.getTotalScore()
                    grade = ss.grade
                    remark = ss.remark
                    
                    // Extract CA1/CA2/Exam scores from scoresJson (source of truth)
                    if (!ss.scoresJson.isNullOrBlank()) {
                        try {
                            scoresMap = objectMapper.readValue(ss.scoresJson, object : com.fasterxml.jackson.core.type.TypeReference<Map<String, Int?>>() {})
                            // Sync legacy variables from map if they exist for consistent DTO response
                            scoresMap.forEach { (key, value) ->
                                if (value != null) {
                                    when (key.lowercase()) {
                                        "ca 1", "ca1", "1st ca", "1st continuous assessment", "ca" -> ca1 = value
                                        "ca 2", "ca2", "2nd ca", "2nd continuous assessment" -> ca2 = value
                                        "exam", "examination", "exam score" -> exam = value
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            println("Error parsing scoresJson for subject ${cs.subject.subjectName}: ${e.message}")
                        }
                    }
                }
            }

            SubjectAssessmentData(
                subjectId = cs.subject.id!!,
                subjectName = cs.subject.subjectName,
                ca1 = ca1,
                ca2 = ca2,
                exam = exam,
                total = total,
                grade = grade,
                remark = remark,
                scoringScheme = cs.schoolClass.scoringScheme,
                scores = scoresMap
            )
        }

        // Get enrollment to find track name
        // Prioritize enrollment that matches the requested classId
        val enrollments = studentClassRepository.findByStudentIdAndAcademicSessionIdAndTermIdAndIsActive(
            studentId, effectiveSessionId, effectiveTermId, true
        ).filter { it.schoolId == selectedSchoolId }
        
        val enrollment = enrollments.find { it.schoolClass.id == classId } 
            ?: enrollments.firstOrNull()
            ?: studentClassRepository.findByStudentIdAndAcademicSessionIdAndIsActive(
                studentId, effectiveSessionId, true
            ).filter { it.schoolId == selectedSchoolId }.firstOrNull()
        
        val className = enrollment?.schoolClass?.className ?: "Unknown Class"
        val trackName = enrollment?.schoolClass?.department?.track?.name ?: "Unknown Track"

        return AssessmentReportData(
            studentId = student.id!!,
            studentName = student.user.fullName ?: "User",
            admissionNumber = student.admissionNumber ?: "",
            className = className,
            trackName = trackName,
            subjects = subjectDataList,
            attendance = assessment?.attendance ?: 0,
            fluency = assessment?.fluency ?: 0,
            handwriting = assessment?.handwriting ?: 0,
            game = assessment?.game ?: 0,
            initiative = assessment?.initiative ?: 0,
            criticalThinking = assessment?.criticalThinking ?: 0,
            punctuality = assessment?.punctuality ?: 0,
            attentiveness = assessment?.attentiveness ?: 0,
            neatness = assessment?.neatness ?: 0,
            selfDiscipline = assessment?.selfDiscipline ?: 0,
            politeness = assessment?.politeness ?: 0,
            classTeacherComment = assessment?.classTeacherComment,
            headTeacherComment = assessment?.headTeacherComment
        )
    }

    @PostMapping("/save")
    @ResponseBody
    fun saveAssessment(
        @RequestBody request: SaveAssessmentRequest,
        session_http: HttpSession,
        authentication: Authentication
    ): Map<String, Any> {
        val selectedSchoolId = authorizationService.validateSchoolAccess(
            session_http.getAttribute("selectedSchoolId") as? UUID
        )
        val customUser = authentication.principal as CustomUserDetails

        // Check if user is admin
        val isAdmin = customUser.authorities.any { it.authority == "ROLE_SYSTEM_ADMIN" || it.authority == "ROLE_SCHOOL_ADMIN" }
        
        var staffId: UUID? = null
        if (!isAdmin) {
             val staff = staffRepository.findByUserIdAndSchoolId(customUser.user.id!!, selectedSchoolId)
             if (staff == null || !staff.isActive) {
                 throw org.springframework.security.access.AccessDeniedException("You are not an active staff member of this school")
             }
             staffId = staff.id
        }

        // Resolve Effective Session
        val sessionAttributeId = session_http.getAttribute("selectedSessionId") as? UUID
        val requestSession = request.session
        var resolvedSessionId = request.sessionId 
            ?: if (!requestSession.isNullOrBlank()) {
                academicSessionRepository.findBySchoolIdAndSessionYearAndIsActive(selectedSchoolId, requestSession, true)?.id
            } else null
            ?: sessionAttributeId
        
        // Fallback to active current session logic
        if (resolvedSessionId == null) {
             val currentSession = academicSessionRepository.findBySchoolIdAndIsCurrentSessionAndIsActive(selectedSchoolId, true, true)
             resolvedSessionId = currentSession?.id 
        }
        
        // Resolve Effective Term
        val termAttributeId = session_http.getAttribute("selectedTermId") as? UUID
        val requestTerm = request.term
        var resolvedTermId = request.termId
            ?: if (resolvedSessionId != null && !requestTerm.isNullOrBlank()) {
                termRepository.findByAcademicSessionIdAndTermNameAndIsActive(resolvedSessionId!!, requestTerm, true).map { it.id }.orElse(null)
            } else null
            ?: termAttributeId
        
        // Fallback Term logic
         if (resolvedTermId == null && resolvedSessionId != null) {
             val currentTerm = termRepository.findByAcademicSessionIdAndIsCurrentTermAndIsActive(resolvedSessionId, true, true)
                .orElse(null)
             resolvedTermId = currentTerm?.id
             
             if (resolvedTermId == null) {
                 val terms = termRepository.findByAcademicSessionIdAndIsActiveOrderByStartDate(resolvedSessionId, true)
                 if (terms.isNotEmpty()) resolvedTermId = terms[0].id
             }
        }
        
        if (resolvedSessionId == null || resolvedTermId == null) {
            throw RuntimeException("Academic Session or Term context could not be resolved.")
        }
        
        val effectiveSessionId = resolvedSessionId!!
        val effectiveTermId = resolvedTermId!!

        // Check authorization in BOTH requested term AND current active term (header context)
        // Re-use logic from getEffectiveSessionAndTerm internal equivalent
        val headerSessionId = session_http.getAttribute("selectedSessionId") as? UUID
        val headerTermId = session_http.getAttribute("selectedTermId") as? UUID
        
        // Resolve Session and Term Names for Assessment entity
        val sessionName = academicSessionRepository.findById(effectiveSessionId).map { it.sessionYear }.orElse(effectiveSessionId.toString())
        val termName = termRepository.findById(effectiveTermId).map { it.termName }.orElse(effectiveTermId.toString())

        // Validate student belongs to school
        val student = authorizationService.validateAndGetStudent(request.studentId, selectedSchoolId)

        // Get student enrollment to find class
        // Fetch all potential enrollments first
        var enrollments = studentClassRepository.findByStudentIdAndAcademicSessionIdAndTermIdAndIsActive(
            request.studentId, effectiveSessionId, effectiveTermId, true
        ).filter { it.schoolId == selectedSchoolId }

        if (enrollments.isEmpty()) {
             enrollments = studentClassRepository.findByStudentIdAndAcademicSessionIdAndIsActive(
                request.studentId, effectiveSessionId, true
            ).filter { it.schoolId == selectedSchoolId }
        }
        
        if (enrollments.isEmpty()) {
            throw RuntimeException("Student enrollment not found")
        }

        // Determine the correct enrollment (Class) to use
        val studentEnrollment = if (enrollments.size == 1) {
            enrollments[0]
        } else {
            if (request.scores.isNotEmpty()) {
                // If scores are being saved, pick the class that supports these subjects
                val subjectIds = request.scores.map { it.subjectId }.toSet()
                
                // For each enrollment, count how many of the requested subjects are valid for that class
                enrollments.maxByOrNull { enrollment ->
                    val clsId = enrollment.schoolClass.id!!
                    // Check if this class has ClassSubjects for the requested subjects
                    classSubjectRepository.findBySchoolClassIdAndIsActive(clsId, true)
                        .count { it.subject.id in subjectIds }
                } ?: enrollments[0]
            } else {
                 // Fallback: Pick the one with the most subjects overall (likely main class)
                 enrollments.maxByOrNull { enrollment ->
                    val clsId = enrollment.schoolClass.id!!
                    classSubjectRepository.findBySchoolClassIdAndIsActive(clsId, true).size
                 } ?: enrollments[0]
            }
        }
            
        val classId = studentEnrollment.schoolClass.id!!

        // Check if class teacher (Check both effective context AND requested context)
        var isClassTeacher = false
        if (staffId != null) {
             val isClassTeacherForRequested = classTeacherRepository.existsByStaffIdAndSchoolClassIdAndAcademicSessionIdAndTermIdAndSchoolIdAndIsActive(
                 staffId, classId, effectiveSessionId, effectiveTermId, selectedSchoolId, true
             )
             val isClassTeacherInHeader = if (headerSessionId != null && headerTermId != null) {
                 classTeacherRepository.existsByStaffIdAndSchoolClassIdAndAcademicSessionIdAndTermIdAndSchoolIdAndIsActive(
                     staffId, classId, headerSessionId, headerTermId, selectedSchoolId, true
                 )
             } else false
             
             isClassTeacher = isClassTeacherForRequested || isClassTeacherInHeader
        }

        val sessionEntity = academicSessionRepository.findById(effectiveSessionId).orElseThrow { RuntimeException("Session not found") }
        val termEntity = termRepository.findById(effectiveTermId).orElseThrow { RuntimeException("Term not found") }

        val assessment = assessmentRepository.findByStudentIdAndAcademicSessionIdAndTermIdAndSchoolIdAndIsActive(
            request.studentId, effectiveSessionId, effectiveTermId, selectedSchoolId, true
        ).orElseGet {
            Assessment(
                admissionNumber = student.admissionNumber ?: "",
                student = student,
                academicSession = sessionEntity,
                term = termEntity
            ).apply {
                this.schoolId = selectedSchoolId
            }
        }
        
        // Only Admin or Class Teacher can update assessment-level fields (attendance, comments, etc.)
        if (isAdmin || isClassTeacher) {
            assessment.apply {
                attendance = request.attendance
                fluency = request.fluency
                handwriting = request.handwriting
                game = request.game
                initiative = request.initiative
                criticalThinking = request.criticalThinking
                punctuality = request.punctuality
                attentiveness = request.attentiveness
                neatness = request.neatness
                selfDiscipline = request.selfDiscipline
                politeness = request.politeness
                classTeacherComment = request.classTeacherComment
                headTeacherComment = request.headTeacherComment
            }
        }
        
        assessmentRepository.save(assessment)

        request.scores.forEach { scoreInput ->
            println("\n========== PROCESSING SUBJECT SCORE ==========")
            println("DEBUG: Processing score for subject ID: ${scoreInput.subjectId}")
            println("DEBUG: Incoming scores map: ${scoreInput.scores}")
            println("DEBUG: Legacy scores - CA1: ${scoreInput.ca1}, CA2: ${scoreInput.ca2}, Exam: ${scoreInput.exam}")
            
            // Validate subject belongs to school
            val subject = authorizationService.validateAndGetSubject(scoreInput.subjectId, selectedSchoolId)
            println("DEBUG: Subject found: ${subject.subjectName} (ID: ${subject.id})")
            
            // Check permission for this subject (Check both contexts)
            if (!isAdmin && !isClassTeacher) {
                 val isSubjectTeacherInRequested = subjectTeacherRepository.existsByStaffIdAndSubjectIdAndSchoolClassIdAndAcademicSessionIdAndTermIdAndSchoolIdAndIsActive(
                     staffId!!, scoreInput.subjectId, classId, effectiveSessionId, effectiveTermId, selectedSchoolId, true
                 )
                 val isSubjectTeacherInHeader = if (headerSessionId != null && headerTermId != null) {
                     subjectTeacherRepository.existsByStaffIdAndSubjectIdAndSchoolClassIdAndAcademicSessionIdAndTermIdAndSchoolIdAndIsActive(
                         staffId!!, scoreInput.subjectId, classId, headerSessionId, headerTermId, selectedSchoolId, true
                     )
                 } else false
                 
                 if (!isSubjectTeacherInRequested && !isSubjectTeacherInHeader) {
                     // Skip subjects the user is not authorized to grade
                     return@forEach
                 }
            }
            
            // Find the ClassSubject for this subject and the student's class
            // We already fetched studentEnrollment above, so we can use classId
            
            val classSubject = classSubjectRepository.findBySchoolClassIdAndSubjectIdAndIsActive(
                classId, scoreInput.subjectId, true
            ) ?: throw RuntimeException("ClassSubject not found for subject ${subject.subjectName} (ID: ${scoreInput.subjectId}) in class ${studentEnrollment.schoolClass.className} (ID: $classId)")
            
            val subjectScore = subjectScoreRepository.findByAssessmentIdAndSubjectIdAndSchoolIdAndIsActive(
                assessment.id!!, scoreInput.subjectId, selectedSchoolId, true
            ).firstOrNull() ?: SubjectScore(
                assessment = assessment,
                subject = subject,
                classSubject = classSubject
            ).apply {
                this.schoolId = selectedSchoolId
            }

            // Ensure classSubject is set for existing records too
            if (subjectScore.classSubject == null) {
                subjectScore.classSubject = classSubject
            }

            // Parse scoring scheme to get alias mappings
            val aliasMappings = parseScoringSchemeAliases(studentEnrollment.schoolClass.scoringScheme)

            // Start with the main scores map
            val workingScores = scoreInput.scores.toMutableMap()
            
            // Merge in any legacy fields that have values and aren't already in the map
            if (scoreInput.ca1 != null && scoreInput.ca1!! > 0 && !workingScores.any { it.key.lowercase().contains("ca 1") || it.key.lowercase().contains("ca i") }) {
                workingScores["CA I"] = scoreInput.ca1
                println("DEBUG: Added legacy CA1 to scores map: ${scoreInput.ca1}")
            }
            if (scoreInput.ca2 != null && scoreInput.ca2!! > 0 && !workingScores.any { it.key.lowercase().contains("ca 2") || it.key.lowercase().contains("ca ii") }) {
                workingScores["CA II"] = scoreInput.ca2
                println("DEBUG: Added legacy CA2 to scores map: ${scoreInput.ca2}")
            }
            if (scoreInput.exam != null && scoreInput.exam!! > 0 && !workingScores.any { it.key.lowercase().contains("exam") }) {
                workingScores["Exam"] = scoreInput.exam
                println("DEBUG: Added legacy Exam to scores map: ${scoreInput.exam}")
            }

            // Normalize score keys using aliases from scoring scheme
            val normalizedScores = if (workingScores.isNotEmpty()) {
                println("DEBUG: Normalizing working scores: $workingScores")
                normalizeScoreKeys(workingScores, aliasMappings)
            } else {
                println("DEBUG: No scores found for normalization")
                emptyMap()
            }
            
            println("DEBUG: After normalization, scores = $normalizedScores")
            println("DEBUG: Total score = ${normalizedScores.values.filterNotNull().sumOf { it }}")

            // Only update scores that were actually provided (not null)
            // Source of Truth: JSON Map with aliases
            if (normalizedScores.isNotEmpty()) {
                subjectScore.scoresJson = objectMapper.writeValueAsString(normalizedScores)
            } else {
                subjectScore.scoresJson = null
            }
            
            // Calculate grade only if there are entered scores
            val total = subjectScore.getTotalScore()
            if (total != null) {
                subjectScore.grade = when {
                    total >= 70 -> "A"
                    total >= 60 -> "B"
                    total >= 50 -> "C"
                    total >= 45 -> "D"
                    total >= 40 -> "E"
                    else -> "F"
                }
                
                subjectScore.remark = when {
                    total >= 70 -> "Excellent"
                    total >= 60 -> "Very Good"
                    total >= 50 -> "Good"
                    total >= 45 -> "Fair"
                    total >= 40 -> "Pass"
                    else -> "Fail"
                }
            } else {
                // No scores entered yet
                subjectScore.grade = null
                subjectScore.remark = null
            }

            println("DEBUG: SAVING TO DATABASE:")
            println("  - SubjectScore ID: ${subjectScore.id}")
            println("  - Student ID: ${subjectScore.assessment.student?.id}")
            println("  - Subject: ${subjectScore.subject.subjectName} (ID: ${subjectScore.subject.id})")
            println("  - Assessment ID: ${subjectScore.assessment.id}")
            println("  - scoresJson: ${subjectScore.scoresJson}")
            println("  - totalScore (computed): ${subjectScore.getTotalScore()}")
            println("  - Grade: ${subjectScore.grade}")
            println("  - Remark: ${subjectScore.remark}")
            println("========== END SUBJECT SCORE ==========\n")

            subjectScoreRepository.save(subjectScore)
        }

        return mapOf("success" to true, "message" to "Assessment saved successfully")
    }

    @PostMapping("/import")
    @ResponseBody
    fun importScores(
        @RequestBody request: ImportAssessmentRequest,
        session_http: HttpSession,
        authentication: Authentication
    ): Map<String, Any> {
        val selectedSchoolId = authorizationService.validateSchoolAccess(
            session_http.getAttribute("selectedSchoolId") as? UUID
        )
        val customUser = authentication.principal as CustomUserDetails

        // Validate class belongs to school
        authorizationService.validateAndGetSchoolClass(request.classId, selectedSchoolId)

        val isAdmin = authentication.authorities.any { it.authority == "ROLE_SYSTEM_ADMIN" || it.authority == "ROLE_SCHOOL_ADMIN" }
        val staffId = staffRepository.findByUserIdAndSchoolId(customUser.getUserId()!!, selectedSchoolId)?.id

        // Resolve session and term entities once
        val sessionEntity = academicSessionRepository.findBySchoolIdAndSessionYearAndIsActive(
            selectedSchoolId, request.session, true
        )
        val termEntity = if (sessionEntity != null) {
            termRepository.findByAcademicSessionIdAndTermNameAndIsActive(sessionEntity.id!!, request.term, true).orElse(null)
        } else null

        if (sessionEntity == null || termEntity == null) {
            return mapOf("success" to false, "message" to "Session or Term not found")
        }

        val effectiveSessionId = sessionEntity.id!!
        val effectiveTermId = termEntity.id!!

        // Check authorization in BOTH requested term AND current active term (header context)
        val headerSessionId = session_http.getAttribute("selectedSessionId") as? UUID
        val headerTermId = session_http.getAttribute("selectedTermId") as? UUID

        var isClassTeacher = false
        if (staffId != null) {
             val isClassTeacherForRequested = classTeacherRepository.existsByStaffIdAndSchoolClassIdAndAcademicSessionIdAndTermIdAndSchoolIdAndIsActive(
                 staffId, request.classId, effectiveSessionId, effectiveTermId, selectedSchoolId, true
             )
             val isClassTeacherInHeader = if (headerSessionId != null && headerTermId != null) {
                 classTeacherRepository.existsByStaffIdAndSchoolClassIdAndAcademicSessionIdAndTermIdAndSchoolIdAndIsActive(
                     staffId, request.classId, headerSessionId, headerTermId, selectedSchoolId, true
                 )
             } else false
             
             isClassTeacher = isClassTeacherForRequested || isClassTeacherInHeader
        }

        // If not admin or class teacher, we'll check subject-level permission inside the loop
        if (!isAdmin && !isClassTeacher && staffId == null) {
            return mapOf("success" to false, "message" to "Access denied to this class")
        }

        val studentId = request.studentId
        val students = if (studentId != null) {
            // Validate student belongs to school
            listOf(authorizationService.validateAndGetStudent(studentId, selectedSchoolId))
        } else {
             studentClassRepository.findBySchoolClassIdAndAcademicSessionIdAndIsActive(
                request.classId, sessionEntity.id!!, true
             ).map { it.student }
        }

        var importedCount = 0
        students.forEach { student ->
            val assessment = assessmentRepository.findByStudentIdAndAcademicSessionIdAndTermIdAndSchoolIdAndIsActive(
                student.id!!, effectiveSessionId, effectiveTermId, selectedSchoolId, true
            ).orElseGet {
                Assessment(
                    admissionNumber = student.admissionNumber ?: "",
                    student = student,
                    academicSession = sessionEntity,
                    term = termEntity
                ).apply {
                    this.schoolId = selectedSchoolId
                }
            }
            assessmentRepository.save(assessment)

            // Find all subjects for this class
            val classSubjects = classSubjectRepository.findBySchoolClassIdAndIsActive(request.classId, true)
            
            classSubjects.forEach { cs ->
                // Check permission for this subject (Check both contexts)
                if (!isAdmin && !isClassTeacher) {
                     val isSubjectTeacherInRequested = subjectTeacherRepository.existsByStaffIdAndSubjectIdAndSchoolClassIdAndAcademicSessionIdAndTermIdAndSchoolIdAndIsActive(
                         staffId!!, cs.subject.id!!, request.classId, effectiveSessionId, effectiveTermId, selectedSchoolId, true
                     )
                     val isSubjectTeacherInHeader = if (headerSessionId != null && headerTermId != null) {
                         subjectTeacherRepository.existsByStaffIdAndSubjectIdAndSchoolClassIdAndAcademicSessionIdAndTermIdAndSchoolIdAndIsActive(
                             staffId!!, cs.subject.id!!, request.classId, headerSessionId, headerTermId, selectedSchoolId, true
                         )
                     } else false
                     
                     if (!isSubjectTeacherInRequested && !isSubjectTeacherInHeader) {
                         // Skip subjects the user is not authorized to grade
                         return@forEach
                     }
                }

                // Determine target max score for the component
                var targetMax = 100 // Default
                val scoringScheme = cs.schoolClass.scoringScheme
                if (!scoringScheme.isNullOrBlank()) {
                    try {
                        val scheme = objectMapper.readValue(scoringScheme, object : com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Any>>>() {})
                        val item = scheme.find { 
                            (it["alias"] as? String) == request.componentName || (it["name"] as? String) == request.componentName 
                        }
                        if (item != null) {
                            targetMax = (item["max"] as? Int) ?: 100
                        }
                    } catch (e: Exception) {
                        // Ignore parsing error, use default
                    }
                }

                var totalWeightedScore = 0.0
                var hasAnyScore = false
                var singleExamMax = 0

                // Iterate over sources and sum up
                request.sources.forEach { source ->
                    val exams = examinationRepository.findBySubjectIdAndSchoolClassIdAndTermIdAndAcademicSessionIdAndExamTypeAndIsActive(
                        cs.subject.id!!, request.classId, termEntity.id!!, sessionEntity.id!!, source.examType, true
                    )

                    if (exams.isNotEmpty()) {
                        val exam = exams[0]
                        if (request.sources.size == 1) {
                            singleExamMax = exam.totalMarks ?: 100
                        }
                        
                        val submission = exam.submissions.find { it.student.id == student.id && it.status == "submitted" }
                        if (submission?.score != null) {
                            totalWeightedScore += submission.score!! * source.factor
                            hasAnyScore = true
                        }
                    }
                }

                if (hasAnyScore) {
                    var finalScore = 0
                    
                    if (request.sources.size == 1) {
                        // Auto-scale logic: (Score / ExamMax) * TargetMax
                        // Factor is ignored or assumed 1.0 in this specific auto-scale mode unless we want to combine them?
                        // The requirement says "Where only one examination is used, the score values will be made to correspond to the max score of the scoring component"
                        // This implies simple scaling.
                        // However, if the user explicitly provided a factor != 1.0, maybe they want that?
                        // Let's assume if they use the UI for "Single Exam", we send factor 1.0 and expect auto-scaling.
                        // If they use "Formula", they might send factor.
                        // But to be safe and follow the requirement strictly:
                        if (singleExamMax > 0) {
                            finalScore = ((totalWeightedScore / singleExamMax) * targetMax).toInt()
                        } else {
                            finalScore = totalWeightedScore.toInt()
                        }
                    } else {
                        // Formula logic: Sum / Divisor
                        // Result should be clamped to targetMax? Or left as is?
                        // "make up to the score of the component"
                        finalScore = (totalWeightedScore / request.divisor).toInt()
                        if (finalScore > targetMax) finalScore = targetMax // Safety clamp
                    }

                    val subjectScore = subjectScoreRepository.findByAssessmentIdAndSubjectIdAndSchoolIdAndIsActive(
                        assessment.id!!, cs.subject.id!!, selectedSchoolId, true
                    ).firstOrNull() ?: SubjectScore(
                        assessment = assessment,
                        subject = cs.subject,
                        classSubject = cs
                    ).apply {
                        this.schoolId = selectedSchoolId
                    }

                    // Ensure classSubject is set for existing records too
                    if (subjectScore.classSubject == null) {
                        subjectScore.classSubject = cs
                    }

                    // Load existing scores map or create new
                    val scoresMap = if (!subjectScore.scoresJson.isNullOrBlank()) {
                        try {
                            objectMapper.readValue(subjectScore.scoresJson, object : com.fasterxml.jackson.core.type.TypeReference<MutableMap<String, Int?>>() {})
                        } catch (e: Exception) {
                            mutableMapOf<String, Int?>()
                        }
                    } else {
                        mutableMapOf<String, Int?>()
                    }

                    scoresMap[request.componentName] = finalScore
                    
                    subjectScore.scoresJson = objectMapper.writeValueAsString(scoresMap)
                    subjectScoreRepository.save(subjectScore)
                    importedCount++
                }
            }
        }

        return mapOf("success" to true, "message" to "Successfully imported scores for $importedCount students.")
    }

    @GetMapping("/download")
    fun downloadReport(
        @RequestParam type: String,
        @RequestParam id: UUID,
        @RequestParam(required = false) classId: UUID?,
        @RequestParam(required = false, defaultValue = "csv") format: String,
        authentication: Authentication,
        session: HttpSession,
        response: HttpServletResponse
    ) {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: throw RuntimeException("School not found")

        val school = schoolRepository.findById(selectedSchoolId).orElseThrow { RuntimeException("School not found") }
        
        val sessionId = session.getAttribute("selectedSessionId") as? UUID
        val termId = session.getAttribute("selectedTermId") as? UUID
        
        // Generate content based on format
        when (format.lowercase()) {
            "pdf" -> {
                // Generate PDF report
                val pdfContent = when (type.lowercase()) {
                    "student" -> generateStudentReportPDF(id, selectedSchoolId, classId, sessionId, termId)
                    "class" -> generateClassReportPDF(id, selectedSchoolId, sessionId, termId)
                    "track" -> generateTrackReportPDF(id, selectedSchoolId, sessionId, termId)
                    else -> throw IllegalArgumentException("Invalid report type: $type")
                }
                
                val filename = when (type.lowercase()) {
                    "student" -> {
                        val student = studentRepository.findById(id).orElseThrow { RuntimeException("Student not found") }
                        "student_report_${student.user.fullName?.replace(" ", "_") ?: "student"}.pdf"
                    }
                    "class" -> {
                        val clazz = schoolClassRepository.findById(id).orElseThrow { RuntimeException("Class not found") }
                        "class_report_${clazz.className.replace(" ", "_")}.pdf"
                    }
                    "track" -> {
                        val track = educationTrackRepository.findById(id).orElseThrow { RuntimeException("Track not found") }
                        "track_report_${track.name.replace(" ", "_")}.pdf"
                    }
                    else -> "report.pdf"
                }
                
                response.contentType = "application/pdf"
                response.setHeader("Content-Disposition", """attachment; filename="$filename"""")
                response.outputStream.write(pdfContent)
                response.outputStream.flush()
            }
            else -> {
                // Generate CSV report (default)
                val filename = when (type.lowercase()) {
                    "student" -> {
                        val student = studentRepository.findById(id).orElseThrow { RuntimeException("Student not found") }
                        "student_report_${student.user.fullName?.replace(" ", "_") ?: "student"}.csv"
                    }
                    "class" -> {
                        val clazz = schoolClassRepository.findById(id).orElseThrow { RuntimeException("Class not found") }
                        "class_report_${clazz.className.replace(" ", "_")}.csv"
                    }
                    "track" -> {
                        val track = educationTrackRepository.findById(id).orElseThrow { RuntimeException("Track not found") }
                        "track_report_${track.name.replace(" ", "_")}.csv"
                    }
                    else -> throw IllegalArgumentException("Invalid report type: $type")
                }
                val csvContent = when (type.lowercase()) {
                    "student" -> generateStudentReportCSV(id, selectedSchoolId, classId, sessionId, termId)
                    "class" -> generateClassReportCSV(id, selectedSchoolId, sessionId, termId)
                    "track" -> generateTrackReportCSV(id, selectedSchoolId, sessionId, termId)
                    else -> ""
                }

                response.contentType = "text/csv;charset=UTF-8"
                response.setHeader("Content-Disposition", """attachment; filename="$filename"""")
                response.writer.write(csvContent)
                response.writer.flush()
            }
        }
    }
    
    private fun generateStudentReportPDF(studentId: UUID, schoolId: UUID, classId: UUID?, sessionId: UUID?, termId: UUID?): ByteArray {
        val student = studentRepository.findById(studentId).orElseThrow { RuntimeException("Student not found") }
        
        val studentClass = when {
            classId != null && sessionId != null && termId != null -> {
                studentClassRepository.findByStudentIdAndAcademicSessionIdAndTermIdAndIsActive(
                    studentId, sessionId, termId, true
                ).filter { it.schoolClass.id == classId }.firstOrNull()
            }
            classId != null -> {
                studentClassRepository.findByStudentIdAndIsActive(studentId, true)
                    .filter { it.schoolClass.id == classId }.firstOrNull()
            }
            sessionId != null && termId != null -> {
                studentClassRepository.findByStudentIdAndAcademicSessionIdAndTermIdAndIsActive(
                    studentId, sessionId, termId, true
                ).firstOrNull()
            }
            else -> {
                studentClassRepository.findByStudentIdAndIsActive(studentId, true)
                    .maxByOrNull { it.createdAt ?: LocalDateTime.MIN }
            }
        }
        
        val assessment = if (sessionId != null && termId != null) {
            assessmentRepository.findByStudentIdAndAcademicSessionIdAndTermIdAndSchoolIdAndIsActive(
                studentId, sessionId, termId, schoolId, true
            ).orElse(null)
        } else {
            assessmentRepository.findByStudentIdAndSchoolIdAndIsActive(studentId, schoolId, true)
                .maxByOrNull { it.createdAt ?: LocalDateTime.MIN }
        }
        
        val scores = if (assessment != null) {
            subjectScoreRepository.findByAssessmentIdAndSchoolIdAndIsActive(assessment.id!!, schoolId, true)
        } else {
            emptyList()
        }
        
        val aliasMappings = if (studentClass != null) parseScoringSchemeAliases(studentClass.schoolClass.scoringScheme) else emptyMap()
        val components = getScoreComponents(aliasMappings)
        
        return reportPdfGenerator.generateStudentReportPdf(
            student = student,
            assessment = assessment,
            scores = scores,
            schoolId = schoolId,
            components = components,
            aliasMappings = aliasMappings,
            extractScoreFn = { score, alias -> extractScoreFromJson(score, alias) },
            calculateTotalFn = { score -> calculateTotalScore(score) }
        )
    }
    
    private fun generateClassReportPDF(classId: UUID, schoolId: UUID, sessionId: UUID?, termId: UUID?): ByteArray {
        // For class report, we'll generate a simple PDF with summary
        // This is a simplified version - you can expand it similarly to class CSV
        val pdfStream = ByteArrayOutputStream()
        val writer = com.itextpdf.kernel.pdf.PdfWriter(pdfStream)
        val pdfDoc = com.itextpdf.kernel.pdf.PdfDocument(writer)
        val document = com.itextpdf.layout.Document(pdfDoc)
        
        val schoolClass = schoolClassRepository.findById(classId).orElseThrow { RuntimeException("Class not found") }
        document.add(com.itextpdf.layout.element.Paragraph("CLASS REPORT - ${schoolClass.className}").setFont(com.itextpdf.kernel.font.PdfFontFactory.createFont(com.itextpdf.io.font.constants.StandardFonts.HELVETICA_BOLD)))
        document.add(com.itextpdf.layout.element.Paragraph("PDF class reports are currently simplified. Use CSV for detailed data."))
        document.close()
        
        return pdfStream.toByteArray()
    }
    
    private fun generateTrackReportPDF(trackId: UUID, schoolId: UUID, sessionId: UUID?, termId: UUID?): ByteArray {
        // For track report, similar to class
        val pdfStream = ByteArrayOutputStream()
        val writer = com.itextpdf.kernel.pdf.PdfWriter(pdfStream)
        val pdfDoc = com.itextpdf.kernel.pdf.PdfDocument(writer)
        val document = com.itextpdf.layout.Document(pdfDoc)
        
        val track = educationTrackRepository.findById(trackId).orElseThrow { RuntimeException("Track not found") }
        document.add(com.itextpdf.layout.element.Paragraph("TRACK REPORT - ${track.name}").setFont(com.itextpdf.kernel.font.PdfFontFactory.createFont(com.itextpdf.io.font.constants.StandardFonts.HELVETICA_BOLD)))
        document.add(com.itextpdf.layout.element.Paragraph("PDF track reports are currently simplified. Use CSV for detailed data."))
        document.close()
        
        return pdfStream.toByteArray()
    }

    private fun generateStudentReportCSV(studentId: UUID, schoolId: UUID, classId: UUID?, sessionId: UUID?, termId: UUID?): String {
        val sb = StringBuilder()
        
        val student = studentRepository.findById(studentId).orElseThrow { RuntimeException("Student not found") }
        
        // Get the student's class for the specified session/term
        // When classId is provided, verify the student is in that class for the session/term
        val studentClass = when {
            classId != null && sessionId != null && termId != null -> {
                // Get all student classes for this student in the session/term and filter by classId
                studentClassRepository.findByStudentIdAndAcademicSessionIdAndTermIdAndIsActive(
                    studentId, sessionId, termId, true
                ).filter { it.schoolClass.id == classId }.firstOrNull()
            }
            classId != null -> {
                // Get all student classes for this student and filter by classId
                studentClassRepository.findByStudentIdAndIsActive(studentId, true)
                    .filter { it.schoolClass.id == classId }.firstOrNull()
            }
            sessionId != null && termId != null -> {
                // Get student's class for the session/term
                studentClassRepository.findByStudentIdAndAcademicSessionIdAndTermIdAndIsActive(
                    studentId, sessionId, termId, true
                ).firstOrNull()
            }
            else -> {
                // Get the most recent class
                studentClassRepository.findByStudentIdAndIsActive(studentId, true)
                    .maxByOrNull { it.createdAt ?: LocalDateTime.MIN }
            }
        }
        
        // Get the assessment for the specified session/term
        val assessment = if (sessionId != null && termId != null) {
            assessmentRepository.findByStudentIdAndAcademicSessionIdAndTermIdAndSchoolIdAndIsActive(
                studentId, sessionId, termId, schoolId, true
            ).orElse(null)
        } else {
            assessmentRepository.findByStudentIdAndSchoolIdAndIsActive(studentId, schoolId, true)
                .maxByOrNull { it.createdAt ?: LocalDateTime.MIN }
        }
        
        println("DEBUG generateStudentReportCSV: classId=$classId, sessionId=$sessionId, termId=$termId, studentClass=${studentClass?.schoolClass?.className}, assessment found: ${assessment != null}")

        sb.append("STUDENT REPORT SHEET\n\n")
        sb.append("Student Name,${student.user.fullName ?: "N/A"}\n")
        sb.append("Admission No,${student.admissionNumber ?: ""}\n")
        if (studentClass != null) {
            sb.append("Class,${studentClass.schoolClass.className}\n")
        }
        if (assessment != null) {
            sb.append("Attendance,${assessment.attendance ?: 0}\n")
        }

        if (assessment != null) {
            val scores = subjectScoreRepository.findByAssessmentIdAndSchoolIdAndIsActive(assessment.id!!, schoolId, true)
            val aliasMappings = if (studentClass != null) parseScoringSchemeAliases(studentClass.schoolClass.scoringScheme) else emptyMap()
            val components = getScoreComponents(aliasMappings)
            
            println("DEBUG: Found ${scores.size} subject scores for assessment ${assessment.id}")
            println("DEBUG: Components: $components")
            
            // Build dynamic headers
            val headers = mutableListOf("Subject")
            headers.addAll(components.map { it.second }) // Add component aliases
            headers.add("Total")
            sb.append(headers.joinToString(",") + "\n")
            
            scores.forEach { score ->
                println("DEBUG: Processing score for subject ${score.subject.subjectName}, scoresJson: ${score.scoresJson}")
                val extractedScores = mutableListOf(score.subject.subjectName)
                
                // Extract score for each component dynamically
                components.forEach { (_, alias) ->
                    val componentScore = extractScoreFromJson(score, alias) ?: 0
                    extractedScores.add(componentScore.toString())
                    println("DEBUG: Component '$alias' = $componentScore")
                }
                
                // Calculate total from all components
                val total = calculateTotalScore(score)
                extractedScores.add(total.toString())
                println("DEBUG: Total = $total")
                sb.append(extractedScores.joinToString(",") + "\n")
            }
        } else {
            // No assessment, add headers with components anyway if available
            val aliasMappings = if (studentClass != null) parseScoringSchemeAliases(studentClass.schoolClass.scoringScheme) else emptyMap()
            val components = getScoreComponents(aliasMappings)
            val headers = mutableListOf("Subject")
            headers.addAll(components.map { it.second })
            headers.add("Total")
            sb.append(headers.joinToString(",") + "\n")
        }

        return sb.toString()
    }

    private fun generateClassReportCSV(classId: UUID, schoolId: UUID, sessionId: UUID?, termId: UUID?): String {
        val sb = StringBuilder()
        
        val schoolClass = schoolClassRepository.findById(classId).orElseThrow { RuntimeException("Class not found") }
        
        // Filter students by session/term if specified
        val studentClasses = if (sessionId != null && termId != null) {
            studentClassRepository.findBySchoolClassIdAndAcademicSessionIdAndTermIdAndIsActive(classId, sessionId, termId, true)
        } else {
            studentClassRepository.findBySchoolClassIdAndIsActive(classId, true)
        }
        val aliasMappings = parseScoringSchemeAliases(schoolClass.scoringScheme)

        // Add session and term info to report if available
        sb.append("CLASS REPORT SHEET - ${schoolClass.className}\n")
        if (sessionId != null) {
            val session = academicSessionRepository.findById(sessionId).orElse(null)
            if (session != null) sb.append("Session: ${session.sessionYear}\n")
        }
        if (termId != null) {
            val term = termRepository.findById(termId).orElse(null)
            if (term != null) sb.append("Term: ${term.termName}\n")
        }
        sb.append("\n")
        sb.append("Student Name,Admission No,Attendance,Avg Score,Status\n")
        
        println("DEBUG generateClassReportCSV: Found ${studentClasses.size} students, sessionId=$sessionId, termId=$termId")
        
        studentClasses.forEach { sc ->
            val student = sc.student
            val assessment = if (sessionId != null && termId != null) {
                assessmentRepository.findByStudentIdAndAcademicSessionIdAndTermIdAndSchoolIdAndIsActive(
                    student.id!!, sessionId, termId, schoolId, true
                ).orElse(null)
            } else {
                assessmentRepository.findByStudentIdAndSchoolIdAndIsActive(student.id!!, schoolId, true)
                    .maxByOrNull { it.createdAt ?: LocalDateTime.MIN }
            }
            
            var avgScore = 0
            if (assessment != null) {
                val scores = subjectScoreRepository.findByAssessmentIdAndSchoolIdAndIsActive(assessment.id!!, schoolId, true)
                avgScore = if (scores.isNotEmpty()) {
                    val totalScores = scores.map { calculateTotalScore(it) }
                    totalScores.average().toInt()
                } else {
                    0
                }
            }

            val status = if (avgScore >= 40) "Pass" else "Fail"
            sb.append("${student.user.fullName ?: "N/A"},${student.admissionNumber ?: ""},${assessment?.attendance ?: ""},$avgScore,$status\n")
        }

        return sb.toString()
    }

    private fun extractScoreFromJson(score: SubjectScore, componentAlias: String): Int? {
        if (score.scoresJson.isNullOrBlank()) {
            return null
        }
        
        try {
            val scoresMap = objectMapper.readValue(score.scoresJson, object : com.fasterxml.jackson.core.type.TypeReference<Map<String, Int?>>() {})
            println("DEBUG extractScoreFromJson: Looking for alias '$componentAlias' in scoresMap: ${scoresMap.keys.joinToString(", ")}")
            val value = scoresMap[componentAlias]
            if (value != null) {
                println("DEBUG extractScoreFromJson: Found score for '$componentAlias': $value")
            } else {
                println("DEBUG extractScoreFromJson: No score found for '$componentAlias'")
            }
            return value
        } catch (e: Exception) {
            println("DEBUG extractScoreFromJson: Error extracting score: ${e.message}")
            return null
        }
    }

    private fun calculateTotalScore(score: SubjectScore): Int {
        if (score.scoresJson.isNullOrBlank()) {
            return 0
        }
        
        try {
            val scoresMap = objectMapper.readValue(score.scoresJson, object : com.fasterxml.jackson.core.type.TypeReference<Map<String, Int?>>() {})
            val total = scoresMap.values.filterNotNull().sumOf { it }
            println("DEBUG calculateTotalScore: Calculated total from all components: $total")
            return total
        } catch (e: Exception) {
            println("DEBUG calculateTotalScore: Error calculating total: ${e.message}")
            return 0
        }
    }

    private fun getScoreComponents(aliasMappings: Map<String, String>): List<Pair<String, String>> {
        // Returns list of (componentName, alias) pairs from the scoring scheme
        // This makes components dynamically available for report generation
        val components = aliasMappings.map { (name, alias) -> name to alias }
        println("DEBUG getScoreComponents: Found ${components.size} components: $components")
        return components
    }

    private fun generateTrackReportCSV(trackId: UUID, schoolId: UUID, sessionId: UUID?, termId: UUID?): String {
        val sb = StringBuilder()
        
        val track = educationTrackRepository.findById(trackId).orElseThrow { RuntimeException("Track not found") }
        val classes = schoolClassRepository.findBySchoolIdAndIsActive(schoolId, true)
            .filter { it.track?.id == trackId }

        sb.append("TRACK REPORT SHEET - ${track.name}\n")
        if (sessionId != null) {
            val session = academicSessionRepository.findById(sessionId).orElse(null)
            if (session != null) sb.append("Session: ${session.sessionYear}\n")
        }
        if (termId != null) {
            val term = termRepository.findById(termId).orElse(null)
            if (term != null) sb.append("Term: ${term.termName}\n")
        }
        sb.append("\n")
        sb.append("Class,Total Students,Avg Performance,Pass Rate\n")
        
        println("DEBUG generateTrackReportCSV: Found ${classes.size} classes, sessionId=$sessionId, termId=$termId")
        
        classes.forEach { schoolClass ->
            val studentClasses = if (sessionId != null && termId != null) {
                studentClassRepository.findBySchoolClassIdAndAcademicSessionIdAndTermIdAndIsActive(schoolClass.id!!, sessionId, termId, true)
            } else {
                studentClassRepository.findBySchoolClassIdAndIsActive(schoolClass.id!!, true)
            }
            val aliasMappings = parseScoringSchemeAliases(schoolClass.scoringScheme)
            
            var totalAvg = 0
            var passCount = 0
            
            studentClasses.forEach { sc ->
                val assessment = if (sessionId != null && termId != null) {
                    assessmentRepository.findByStudentIdAndAcademicSessionIdAndTermIdAndSchoolIdAndIsActive(
                        sc.student.id!!, sessionId, termId, schoolId, true
                    ).orElse(null)
                } else {
                    assessmentRepository.findByStudentIdAndSchoolIdAndIsActive(sc.student.id!!, schoolId, true)
                        .maxByOrNull { it.createdAt ?: LocalDateTime.MIN }
                }
                
                if (assessment != null) {
                    val scores = subjectScoreRepository.findByAssessmentIdAndSchoolIdAndIsActive(assessment.id!!, schoolId, true)
                    val avgScore = if (scores.isNotEmpty()) {
                        val totalScores = scores.map { calculateTotalScore(it) }
                        totalScores.average().toInt()
                    } else {
                        0
                    }
                    totalAvg += avgScore
                    if (avgScore >= 40) passCount++
                }
            }
            
            val classAvg = if (studentClasses.isNotEmpty()) totalAvg / studentClasses.size else 0
            val passRate = if (studentClasses.isNotEmpty()) (passCount * 100) / studentClasses.size else 0

            sb.append("${schoolClass.className},${studentClasses.size},$classAvg,$passRate%\n")
        }

        return sb.toString()
    }

    private fun parseScoringSchemeAliases(scoringSchemeJson: String?): Map<String, String> {
        if (scoringSchemeJson.isNullOrBlank()) {
            println("DEBUG parseScoringSchemeAliases: Scheme is null or blank, returning empty map")
            return emptyMap()
        }
        
        try {
            println("DEBUG parseScoringSchemeAliases: Parsing scheme: $scoringSchemeJson")
            val scheme = objectMapper.readValue(scoringSchemeJson, object : com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Any>>>() {})
            val aliases = mutableMapOf<String, String>()
            scheme.forEach { item ->
                val name = (item["name"] as? String) ?: return@forEach
                val alias = (item["alias"] as? String) ?: name
                aliases[name.lowercase()] = alias
                println("DEBUG parseScoringSchemeAliases: Added mapping '${name.lowercase()}' -> '$alias'")
            }
            
            // Ensure we have mappings for standard components even if not explicitly in scheme
            // if (!aliases.containsKey("ca 1") && !aliases.containsKey("ca1")) {
            //     aliases["ca 1"] = "CA 1"
            //     println("DEBUG parseScoringSchemeAliases: Added default mapping 'ca 1' -> 'CA 1'")
            // }
            // if (!aliases.containsKey("ca 2") && !aliases.containsKey("ca2")) {
            //     aliases["ca 2"] = "CA 2"
            //     println("DEBUG parseScoringSchemeAliases: Added default mapping 'ca 2' -> 'CA 2'")
            // }
            // if (!aliases.containsKey("exam")) {
            //     aliases["exam"] = "Exam"
            //     println("DEBUG parseScoringSchemeAliases: Added default mapping 'exam' -> 'Exam'")
            // }
            
            println("DEBUG parseScoringSchemeAliases: Final mappings = $aliases")
            return aliases
        } catch (e: Exception) {
            println("DEBUG ERROR parseScoringSchemeAliases: Error parsing scoring scheme: ${e.message}")
            e.printStackTrace()
            return emptyMap()
        }
    }

    private fun normalizeScoreKeys(scores: Map<String, Int?>, aliasMappings: Map<String, String>): Map<String, Int?> {
        val normalized = mutableMapOf<String, Int?>()
        
        println("DEBUG normalizeScoreKeys: Input scores = $scores")
        println("DEBUG normalizeScoreKeys: Alias mappings = $aliasMappings")
        
        scores.forEach { (key, value) ->
            val keyLower = key.lowercase()
            
            // First, try to find exact match with mapping keys (component names)
            var normalizedKey = aliasMappings[keyLower]
            
            // If not found, try to find if the key itself is already an alias
            if (normalizedKey == null) {
                // Check if incoming key matches any of the alias values
                normalizedKey = aliasMappings.values.find { it.lowercase() == keyLower }
            }
            
            // If still not found, try flexible substring matching as last resort
            if (normalizedKey == null) {
                normalizedKey = aliasMappings.entries.find { (name, alias) ->
                    keyLower.contains(name) || name.contains(keyLower) ||
                    keyLower.contains(alias.lowercase()) || alias.lowercase().contains(keyLower)
                }?.value
            }
            
            // Final fallback: use the original key
            val finalKey = normalizedKey ?: key
            
            println("DEBUG normalizeScoreKeys: Mapping '$key' -> '$finalKey'")
            normalized[finalKey] = value
        }
        
        println("DEBUG normalizeScoreKeys: Output normalized scores = $normalized")
        return normalized
    }
}



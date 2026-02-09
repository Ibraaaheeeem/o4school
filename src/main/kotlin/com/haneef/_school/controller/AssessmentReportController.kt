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
import java.time.LocalDateTime
import org.slf4j.LoggerFactory

data class StudentReportInfo(
    val id: UUID,
    val admissionNumber: String,
    val fullName: String
)

data class SubjectAssessmentData(
    val subjectId: UUID,
    val subjectName: String,
    val ca1: Int? = null,
    val ca2: Int? = null,
    val exam: Int? = null,
    val total: Int? = null,
    val grade: String? = null,
    val remark: String? = null,
    val scoringScheme: String? = null,
    val scores: Map<String, Int?> = HashMap()
)



data class AssessmentReportData(
    val studentId: UUID,
    val studentName: String,
    val admissionNumber: String,
    val className: String,
    val trackName: String,
    val subjects: List<SubjectAssessmentData>,
    val attendance: Int = 0,
    val fluency: Int = 0,
    val handwriting: Int = 0,
    val game: Int = 0,
    val initiative: Int = 0,
    val criticalThinking: Int = 0,
    val punctuality: Int = 0,
    val attentiveness: Int = 0,
    val neatness: Int = 0,
    val selfDiscipline: Int = 0,
    val politeness: Int = 0,
    val classTeacherComment: String? = null,
    val headTeacherComment: String? = null
)

data class SaveAssessmentRequest(
    val studentId: UUID,
    val sessionId: UUID? = null,
    val termId: UUID? = null,
    val scores: List<SubjectScoreInput>,
    val attendance: Int = 0,
    val fluency: Int = 0,
    val handwriting: Int = 0,
    val game: Int = 0,
    val initiative: Int = 0,
    val criticalThinking: Int = 0,
    val punctuality: Int = 0,
    val attentiveness: Int = 0,
    val neatness: Int = 0,
    val selfDiscipline: Int = 0,
    val politeness: Int = 0,
    val classTeacherComment: String? = null,
    val headTeacherComment: String? = null
)

data class SubjectScoreInput(
    val subjectId: UUID,
    val ca1: Int? = null,
    val ca2: Int? = null,
    val exam: Int? = null,
    val scores: Map<String, Int?> = HashMap()
)

data class ImportAssessmentRequest(
    val classId: UUID,
    val session: String,
    val term: String,
    val componentName: String,
    val sources: List<ImportSourceConfig>,
    val divisor: Double = 1.0,
    val studentId: UUID? = null // Optional: if null, import for entire class
)

data class ImportSourceConfig(
    val examType: String,
    val factor: Double = 1.0
)

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
    private val subjectTeacherRepository: SubjectTeacherRepository
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
        session_http: HttpSession
    ): AssessmentReportData {
        val selectedSchoolId = authorizationService.validateSchoolAccess(
            session_http.getAttribute("selectedSchoolId") as? UUID
        )

        // Resolve Effective Session
        val sessionAttributeId = session_http.getAttribute("selectedSessionId") as? UUID
        var resolvedSessionId = sessionId ?: sessionAttributeId
        
        // Fallback to active current session logic (reused)
        if (resolvedSessionId == null) {
             val currentSession = academicSessionRepository.findBySchoolIdAndIsCurrentSessionAndIsActive(selectedSchoolId, true, true)
             resolvedSessionId = currentSession?.id // Could still be null if no session exists, but unlikely
        }
        
        // Resolve Effective Term
        val termAttributeId = session_http.getAttribute("selectedTermId") as? UUID
        var resolvedTermId = termId ?: termAttributeId
        
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
        val assessmentOpt = assessmentRepository.findByStudentIdAndSessionAndTermAndSchoolIdAndIsActive(
            studentId, sessionName, termName, selectedSchoolId, true
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
                    ca1 = ss.ca1Score
                    ca2 = ss.ca2Score
                    exam = ss.examScore
                    total = ss.totalScore
                    grade = ss.grade
                    remark = ss.remark
                    
                    if (!ss.scoresJson.isNullOrBlank()) {
                        try {
                            scoresMap = objectMapper.readValue(ss.scoresJson, object : com.fasterxml.jackson.core.type.TypeReference<Map<String, Int?>>() {})
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
        var resolvedSessionId = request.sessionId ?: sessionAttributeId
        
        // Fallback to active current session logic
        if (resolvedSessionId == null) {
             val currentSession = academicSessionRepository.findBySchoolIdAndIsCurrentSessionAndIsActive(selectedSchoolId, true, true)
             resolvedSessionId = currentSession?.id 
        }
        
        // Resolve Effective Term
        val termAttributeId = session_http.getAttribute("selectedTermId") as? UUID
        var resolvedTermId = request.termId ?: termAttributeId
        
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

        // Check if class teacher
        var isClassTeacher = false
        if (staffId != null) {
             isClassTeacher = classTeacherRepository.existsByStaffIdAndSchoolClassIdAndAcademicSessionIdAndTermIdAndSchoolIdAndIsActive(
                 staffId, classId, effectiveSessionId, effectiveTermId, selectedSchoolId, true
             )
        }

        val assessment = assessmentRepository.findByStudentIdAndSessionAndTermAndSchoolIdAndIsActive(
            request.studentId, sessionName, termName, selectedSchoolId, true
        ).orElseGet {
            Assessment(
                admissionNumber = student.admissionNumber ?: "",
                student = student,
                session = sessionName,
                term = termName
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
            // Validate subject belongs to school
            val subject = authorizationService.validateAndGetSubject(scoreInput.subjectId, selectedSchoolId)
            
            // Check permission for this subject
            if (!isAdmin && !isClassTeacher) {
                 val isSubjectTeacher = subjectTeacherRepository.existsByStaffIdAndSubjectIdAndSchoolClassIdAndAcademicSessionIdAndTermIdAndSchoolIdAndIsActive(
                     staffId!!, scoreInput.subjectId, classId, effectiveSessionId, effectiveTermId, selectedSchoolId, true
                 )
                 if (!isSubjectTeacher) {
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

            // Only update scores that were actually provided (not null)
            subjectScore.ca1Score = scoreInput.ca1
            subjectScore.ca2Score = scoreInput.ca2
            subjectScore.examScore = scoreInput.exam
            
            // Save dynamic scores as JSON
            if (scoreInput.scores.isNotEmpty()) {
                subjectScore.scoresJson = objectMapper.writeValueAsString(scoreInput.scores)
                subjectScore.totalScore = scoreInput.scores.values.filterNotNull().sumOf { it }
            } else {
                val ca1 = scoreInput.ca1 ?: 0
                val ca2 = scoreInput.ca2 ?: 0
                val exam = scoreInput.exam ?: 0
                
                if (scoreInput.ca1 != null || scoreInput.ca2 != null || scoreInput.exam != null) {
                    subjectScore.totalScore = ca1 + ca2 + exam
                } else {
                    subjectScore.totalScore = null
                }
            }
            
            // Calculate grade only if there are entered scores
            val total = subjectScore.totalScore
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

        val students = if (request.studentId != null) {
            // Validate student belongs to school
            listOf(authorizationService.validateAndGetStudent(request.studentId, selectedSchoolId))
        } else {
             studentClassRepository.findBySchoolClassIdAndAcademicSessionIdAndIsActive(
                request.classId, sessionEntity.id!!, true
             ).map { it.student }
        }

        var importedCount = 0
        students.forEach { student ->
            val assessment = assessmentRepository.findByStudentIdAndSessionAndTermAndSchoolIdAndIsActive(
                student.id!!, request.session, request.term, selectedSchoolId, true
            ).orElseGet {
                Assessment(
                    admissionNumber = student.admissionNumber ?: "",
                    student = student,
                    session = request.session,
                    term = request.term
                ).apply {
                    this.schoolId = selectedSchoolId
                }
            }
            assessmentRepository.save(assessment)

            // Find all subjects for this class
            val classSubjects = classSubjectRepository.findBySchoolClassIdAndIsActive(request.classId, true)
            
            classSubjects.forEach { cs ->
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
                        mutableMapOf<String, Int?>().apply {
                            if ((subjectScore.ca1Score ?: 0) > 0) put("CA 1", subjectScore.ca1Score)
                            if ((subjectScore.ca2Score ?: 0) > 0) put("CA 2", subjectScore.ca2Score)
                            if ((subjectScore.examScore ?: 0) > 0) put("Exam", subjectScore.examScore)
                        }
                    }

                    scoresMap[request.componentName] = finalScore

                    // Update legacy fields
                    when (request.componentName.lowercase()) {
                        "ca 1", "ca1", "continuous assessment 1" -> subjectScore.ca1Score = finalScore
                        "ca 2", "ca2", "continuous assessment 2" -> subjectScore.ca2Score = finalScore
                        "exam", "examination" -> subjectScore.examScore = finalScore
                    }
                    
                    subjectScore.scoresJson = objectMapper.writeValueAsString(scoresMap)
                    subjectScore.totalScore = scoresMap.values.filterNotNull().sumOf { it }
                    subjectScoreRepository.save(subjectScore)
                    importedCount++
                }
            }
        }

        return mapOf("success" to true, "message" to "Successfully imported scores for $importedCount students.")
    }
}

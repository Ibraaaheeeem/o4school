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
    val sessionId: UUID,
    val termId: UUID,
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

        val customUser = authentication.principal as CustomUserDetails
        val school = schoolRepository.findById(selectedSchoolId).orElseThrow { RuntimeException("School not found") }
        val academicSessions = academicSessionRepository.findBySchoolIdAndIsActiveOrderByYearDesc(selectedSchoolId, true)
        val educationTracks = educationTrackRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
        val departments = departmentRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
        val classes = schoolClassRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
        
        // Get current session if not provided
        val currentSession = academicSessionRepository.findBySchoolIdAndIsCurrentSessionAndIsActive(selectedSchoolId, true, true)
        val resolvedSessionId = sessionId ?: currentSession?.id
        
        // Get terms for the selected session (only if session is selected)
        val terms = if (resolvedSessionId != null) {
            termRepository.findByAcademicSessionIdAndIsActiveOrderByStartDate(resolvedSessionId, true)
        } else {
            emptyList()
        }
        
        // Get current term if not provided and session is selected
        val currentTerm = if (resolvedSessionId != null) {
            termRepository.findByAcademicSessionIdAndIsCurrentTermAndIsActive(resolvedSessionId, true, true)
                .orElse(null)
        } else null
        val resolvedTermId = termId ?: currentTerm?.id

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
            println("DEBUG: Main endpoint - Found ${enrollments.size} students for class $classId, session $resolvedSessionId, term $resolvedTermId")
            
            // If no students found for specific session/term, try just session
            if (enrollments.isEmpty()) {
                val sessionEnrollments = studentClassRepository.findBySchoolClassIdAndAcademicSessionIdAndIsActive(
                    classId, resolvedSessionId, true
                ).filter { it.schoolId == selectedSchoolId }
                println("DEBUG: Main endpoint - Found ${sessionEnrollments.size} students for class $classId and session $resolvedSessionId")
                
                // If still no students, try all students in class
                if (sessionEnrollments.isEmpty()) {
                    val allEnrollments = studentClassRepository.findBySchoolClassIdAndIsActive(classId, true)
                        .filter { it.schoolId == selectedSchoolId }
                    println("DEBUG: Main endpoint - Found ${allEnrollments.size} students in class (all sessions)")
                    model.addAttribute("students", allEnrollments.map { it.student })
                } else {
                    model.addAttribute("students", sessionEnrollments.map { it.student })
                }
            } else {
                model.addAttribute("students", enrollments.map { it.student })
            }
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
        @RequestParam(required = false) sessionYear: String?
    ): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "fragments/error :: error-message"

        model.addAttribute("selectedTrackId", trackId)
        model.addAttribute("selectedDepartmentId", departmentId)
        model.addAttribute("selectedClassId", classId)
        model.addAttribute("selectedTerm", term)
        model.addAttribute("selectedSession", sessionYear)

        if (classId != null && sessionYear != null) {
            // Try new foreign key approach first
            val sessionEntity = academicSessionRepository.findBySchoolIdAndSessionYearAndIsActive(
                selectedSchoolId, sessionYear, true
            )
            val enrollments = if (sessionEntity != null) {
                studentClassRepository.findBySchoolClassIdAndAcademicSessionIdAndIsActive(
                    classId, sessionEntity.id!!, true
                ).filter { it.schoolId == selectedSchoolId }
            } else {
                emptyList()
            }
            
            println("DEBUG: Filter endpoint - Found ${enrollments.size} students for class $classId and session $sessionYear")
            
            // If no students found for specific session, try to get all students in the class
            if (enrollments.isEmpty()) {
                val allEnrollments = studentClassRepository.findBySchoolClassIdAndIsActive(classId, true)
                    .filter { it.schoolId == selectedSchoolId }
                println("DEBUG: Filter endpoint - Found ${allEnrollments.size} students in class (all sessions)")
                model.addAttribute("students", allEnrollments.map { it.student })
            } else {
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

        val enrollments = if (sessionEntity != null) {
            studentClassRepository.findBySchoolClassIdAndAcademicSessionIdAndIsActive(
                classId, sessionEntity.id!!, true
            )
        } else {
            emptyList()
        }

        return enrollments.map { enrollment ->
            StudentReportInfo(
                id = enrollment.student.id!!,
                admissionNumber = enrollment.student.admissionNumber ?: "",
                fullName = enrollment.student.user.fullName ?: "User"
            )
        }
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
            // Get students enrolled in the class for the specified session/term
            val studentClasses = when {
                sessionId != null && termId != null -> {
                    val results = studentClassRepository.findBySchoolClassIdAndAcademicSessionIdAndTermIdAndIsActive(
                        classId, sessionId, termId, true
                    ).filter { it.schoolId == selectedSchoolId }
                    println("DEBUG: Found ${results.size} students for specific session $sessionId and term $termId")
                    
                    // If no students found for specific session/term, try just session
                    if (results.isEmpty()) {
                        val sessionResults = studentClassRepository.findBySchoolClassIdAndAcademicSessionIdAndIsActive(
                            classId, sessionId, true
                        ).filter { it.schoolId == selectedSchoolId }
                        println("DEBUG: Found ${sessionResults.size} students for session $sessionId")
                        sessionResults
                    } else {
                        results
                    }
                }
                sessionId != null -> {
                    val results = studentClassRepository.findBySchoolClassIdAndAcademicSessionIdAndIsActive(
                        classId, sessionId, true
                    ).filter { it.schoolId == selectedSchoolId }
                    println("DEBUG: Found ${results.size} students for session $sessionId")
                    results
                }
                else -> {
                    val results = studentClassRepository.findBySchoolClassIdAndIsActive(classId, true)
                        .filter { it.schoolId == selectedSchoolId }
                    println("DEBUG: Found ${results.size} students in class (all sessions)")
                    results
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
        @RequestParam sessionId: UUID,
        @RequestParam termId: UUID,
        session_http: HttpSession
    ): AssessmentReportData {
        val selectedSchoolId = authorizationService.validateSchoolAccess(
            session_http.getAttribute("selectedSchoolId") as? UUID
        )

        // Validate student and class belong to school
        val student = authorizationService.validateAndGetStudent(studentId, selectedSchoolId)
        authorizationService.validateAndGetSchoolClass(classId, selectedSchoolId)
        
        // Get all subjects for this class
        val classSubjects = classSubjectRepository.findBySchoolClassIdAndIsActive(classId, true)
        
        // Get existing assessment if any
        val assessmentOpt = assessmentRepository.findByStudentIdAndSessionAndTermAndSchoolIdAndIsActive(
            studentId, sessionId.toString(), termId.toString(), selectedSchoolId, true
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
        val enrollment = studentClassRepository.findByStudentIdAndAcademicSessionIdAndTermIdAndIsActive(
            studentId, sessionId, termId, true
        ).filter { it.schoolId == selectedSchoolId }.firstOrNull()
            ?: studentClassRepository.findByStudentIdAndAcademicSessionIdAndIsActive(
                studentId, sessionId, true
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

        // Validate student belongs to school
        val student = authorizationService.validateAndGetStudent(request.studentId, selectedSchoolId)

        // Get student enrollment to find class
        val studentEnrollment = studentClassRepository.findByStudentIdAndAcademicSessionIdAndTermIdAndIsActive(
            request.studentId, request.sessionId, request.termId, true
        ).filter { it.schoolId == selectedSchoolId }.firstOrNull()
            ?: studentClassRepository.findByStudentIdAndAcademicSessionIdAndIsActive(
                request.studentId, request.sessionId, true
            ).filter { it.schoolId == selectedSchoolId }.firstOrNull()
            ?: throw RuntimeException("Student enrollment not found")
            
        val classId = studentEnrollment.schoolClass.id!!

        // Check if class teacher
        var isClassTeacher = false
        if (staffId != null) {
             isClassTeacher = classTeacherRepository.existsByStaffIdAndSchoolClassIdAndAcademicSessionIdAndTermIdAndSchoolIdAndIsActive(
                 staffId, classId, request.sessionId, request.termId, selectedSchoolId, true
             )
        }

        val assessment = assessmentRepository.findByStudentIdAndSessionAndTermAndSchoolIdAndIsActive(
            request.studentId, request.sessionId.toString(), request.termId.toString(), selectedSchoolId, true
        ).orElseGet {
            Assessment(
                admissionNumber = student.admissionNumber ?: "",
                student = student,
                session = request.sessionId.toString(),
                term = request.termId.toString()
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
                     staffId!!, scoreInput.subjectId, classId, request.sessionId, request.termId, selectedSchoolId, true
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
            ) ?: throw RuntimeException("ClassSubject not found for subject ${subject.subjectName}")
            
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

        val students = if (request.studentId != null) {
            // Validate student belongs to school
            listOf(authorizationService.validateAndGetStudent(request.studentId, selectedSchoolId))
        } else {
            val sessionEntity = academicSessionRepository.findBySchoolIdAndSessionYearAndIsActive(
                selectedSchoolId, request.session, true
            )
            if (sessionEntity != null) {
                studentClassRepository.findBySchoolClassIdAndAcademicSessionIdAndIsActive(
                    request.classId, sessionEntity.id!!, true
                ).map { it.student }
            } else {
                emptyList()
            }
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
                    val exams = examinationRepository.findBySubjectIdAndSchoolClassIdAndTermAndSessionAndExamTypeAndIsActive(
                        cs.subject.id!!, request.classId, request.term, request.session, source.examType, true
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

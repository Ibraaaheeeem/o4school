package com.haneef._school.controller

import java.util.UUID
import com.haneef._school.entity.*
import com.haneef._school.dto.*
import com.haneef._school.repository.*
import com.haneef._school.service.CustomUserDetailsService
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpSession
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import java.time.LocalDateTime
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import java.util.Collections.reverseOrder

@Controller
@RequestMapping("/staff")
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'TEACHER')")
class StaffDashboardController(
    private val userDetailsService: CustomUserDetailsService,
    private val schoolRepository: SchoolRepository,
    private val staffRepository: StaffRepository,
    private val classTeacherRepository: ClassTeacherRepository,
    private val subjectTeacherRepository: SubjectTeacherRepository,
    private val schoolClassRepository: SchoolClassRepository,
    private val studentClassRepository: StudentClassRepository,
    private val classSubjectRepository: ClassSubjectRepository,
    private val examinationRepository: ExaminationRepository,
    private val questionRepository: QuestionRepository,
    private val attendanceRepository: AttendanceRepository,
    private val termRepository: TermRepository,
    private val studentRepository: StudentRepository,
    private val academicSessionRepository: AcademicSessionRepository,
    private val assessmentRepository: AssessmentRepository,
    private val subjectScoreRepository: SubjectScoreRepository,
    private val subjectRepository: SubjectRepository,
    private val educationTrackRepository: EducationTrackRepository,
    private val departmentRepository: DepartmentRepository,
    private val parentStudentRepository: ParentStudentRepository,
    private val htmlSanitizerService: com.haneef._school.service.HtmlSanitizerService,
    private val examinationSubmissionRepository: ExaminationSubmissionRepository
) {
    private val objectMapper = ObjectMapper().registerModule(com.fasterxml.jackson.module.kotlin.KotlinModule.Builder().build())
    private val logger = LoggerFactory.getLogger(StaffDashboardController::class.java)

    @GetMapping("/dashboard")
    fun staffDashboard(model: Model, authentication: Authentication, session: HttpSession): String {
        val userDetails = userDetailsService.loadUserByUsername(authentication.name)
        val customUser = userDetails as com.haneef._school.service.CustomUserDetails
        
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
        
        if (selectedSchoolId != null) {
            val staff = staffRepository.findByUserIdAndSchoolId(customUser.getUserId()!!, selectedSchoolId)
            populateDashboardModel(model, staff, selectedSchoolId)
        }
        
        model.addAttribute("user", customUser.user)
        model.addAttribute("userRole", "Staff Member")
        model.addAttribute("dashboardType", "staff")
        
        return "dashboard/staff-dashboard"
    }

    @GetMapping("/view-as/{staffId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHOOL_ADMIN')")
    fun viewStaffDashboardAsAdmin(
        @org.springframework.web.bind.annotation.PathVariable staffId: UUID,
        model: Model,
        authentication: Authentication,
        session: HttpSession
    ): String {
        val staff = staffRepository.findById(staffId).orElseThrow { IllegalArgumentException("Staff not found") }
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
        
        populateDashboardModel(model, staff, selectedSchoolId)
        
        // Add user info for the header (showing the admin is viewing)
        val userDetails = userDetailsService.loadUserByUsername(authentication.name)
        val customUser = userDetails as com.haneef._school.service.CustomUserDetails
        model.addAttribute("user", customUser.user)
        model.addAttribute("userRole", "Admin (Viewing as Staff)")
        model.addAttribute("dashboardType", "staff")
        model.addAttribute("isViewAs", true)
        model.addAttribute("viewingAsName", staff.user.fullName)
        
        return "dashboard/staff-dashboard"
    }

    private fun populateDashboardModel(model: Model, staff: com.haneef._school.entity.Staff?, selectedSchoolId: UUID?) {
        if (selectedSchoolId != null) {
            val school = schoolRepository.findById(selectedSchoolId).orElse(null)
            model.addAttribute("school", school)
            
            if (staff != null && staff.isActive) {
                // Get current academic session and term
                val currentSession = academicSessionRepository.findBySchoolIdAndIsCurrentSessionAndIsActive(selectedSchoolId, true, true)
                val currentTerm = currentSession?.let { session ->
                    termRepository.findByAcademicSessionIdAndIsCurrentTermAndIsActive(session.id!!, true, true).orElse(null)
                }

                if (currentSession != null && currentTerm != null) {
                    // Get all classes where this staff is a class teacher or subject teacher for the current session and term
                    val classTeacherAssignments = classTeacherRepository.findByStaffIdAndAcademicSessionIdAndTermIdAndIsActive(
                        staff.id!!, currentSession.id!!, currentTerm.id!!, true
                    )
                    val subjectTeacherAssignments = subjectTeacherRepository.findByStaffIdAndAcademicSessionIdAndTermIdAndIsActive(
                        staff.id!!, currentSession.id!!, currentTerm.id!!, true
                    )
                    
                    // Get unique class IDs
                    val classIds = mutableSetOf<UUID>()
                    classTeacherAssignments.forEach { it.schoolClass.id?.let { id -> classIds.add(id) } }
                    subjectTeacherAssignments.forEach { it.schoolClass.id?.let { id -> classIds.add(id) } }
                    
                    model.addAttribute("classCount", classIds.size)
                } else {
                    model.addAttribute("classCount", 0)
                }
            } else {
                model.addAttribute("classCount", 0)
            }
        }
    }
    
    @GetMapping("/classes")
    fun staffClasses(model: Model, authentication: Authentication, session: HttpSession): String {
        val userDetails = userDetailsService.loadUserByUsername(authentication.name)
        val customUser = userDetails as com.haneef._school.service.CustomUserDetails
        
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
        if (selectedSchoolId != null) {
            val school = schoolRepository.findById(selectedSchoolId).orElse(null)
            model.addAttribute("school", school)
            
            // Get staff record for the current user
            val staff = staffRepository.findByUserIdAndSchoolId(customUser.getUserId()!!, selectedSchoolId)
            
            if (staff != null && staff.isActive) {
                // Get current academic session and term
                val currentSession = academicSessionRepository.findBySchoolIdAndIsCurrentSessionAndIsActive(selectedSchoolId, true, true)
                val currentTerm = currentSession?.let { session ->
                    termRepository.findByAcademicSessionIdAndIsCurrentTermAndIsActive(session.id!!, true, true).orElse(null)
                }

                if (currentSession != null && currentTerm != null) {
                    // Get all classes where this staff is a class teacher for the current session and term
                    val classTeacherAssignments = classTeacherRepository.findByStaffIdAndAcademicSessionIdAndTermIdAndIsActive(
                        staff.id!!, currentSession.id!!, currentTerm.id!!, true
                    )
                    
                    // Get all classes where this staff is a subject teacher for the current session and term
                    val subjectTeacherAssignments = subjectTeacherRepository.findByStaffIdAndAcademicSessionIdAndTermIdAndIsActive(
                        staff.id!!, currentSession.id!!, currentTerm.id!!, true
                    )
                    
                    // Create a map of class ID to class info with roles
                    val classMap = mutableMapOf<UUID, StaffClassInfo>()
                    
                    // Add class teacher assignments
                    classTeacherAssignments.forEach { ct ->
                        ct.schoolClass.id?.let { classId ->
                            classMap[classId] = StaffClassInfo(
                                schoolClass = ct.schoolClass,
                                isClassTeacher = true,
                                subjects = mutableListOf()
                            )
                        }
                    }
                    
                    // Add subject teacher assignments
                    subjectTeacherAssignments.forEach { st ->
                        st.schoolClass.id?.let { classId ->
                            val classInfo = classMap.getOrPut(classId) {
                                StaffClassInfo(
                                    schoolClass = st.schoolClass,
                                    isClassTeacher = false,
                                    subjects = mutableListOf()
                                )
                            }
                            classInfo.subjects.add(st.subject)
                        }
                    }
                    
                    model.addAttribute("classes", classMap.values.toList())
                } else {
                    model.addAttribute("classes", emptyList<Any>())
                }
            } else {
                model.addAttribute("classes", emptyList<Any>())
            }
        }
        
        model.addAttribute("user", customUser.user)
        model.addAttribute("userRole", "Staff Member")
        
        return "staff/classes"
    }

    @GetMapping("/students/{studentId}")
    fun getStudentProfile(
        @PathVariable studentId: UUID,
        model: Model,
        authentication: Authentication,
        session: HttpSession
    ): String {
        val userDetails = userDetailsService.loadUserByUsername(authentication.name)
        val customUser = userDetails as com.haneef._school.service.CustomUserDetails
        
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/select-school"
            
        val school = schoolRepository.findById(selectedSchoolId).orElse(null)
            ?: return "redirect:/select-school"
            
        // Get student
        val student = studentRepository.findById(studentId).orElse(null)
        if (student == null || student.schoolId != selectedSchoolId) {
            return "redirect:/staff/dashboard"
        }
        
        // Get current enrollments
        val currentSession = academicSessionRepository.findBySchoolIdAndIsCurrentSessionAndIsActive(selectedSchoolId, true, true)
        val enrollments = if (currentSession != null) {
             studentClassRepository.findByStudentIdAndAcademicSessionIdAndIsActive(studentId, currentSession.id!!, true)
                .filter { it.schoolClass.schoolId == selectedSchoolId }
        } else {
            emptyList()
        }
        
        // Get attendance stats
        val attendanceRecords = attendanceRepository.findByStudentIdAndSchoolIdAndIsActive(studentId, selectedSchoolId, true)
        val presentCount = attendanceRecords.count { it.status == AttendanceStatus.PRESENT }
        val absentCount = attendanceRecords.count { it.status == AttendanceStatus.ABSENT }
        val lateCount = attendanceRecords.count { it.status == AttendanceStatus.LATE }
        val totalAttendance = attendanceRecords.size
        val attendancePercentage = if (totalAttendance > 0) (presentCount.toDouble() / totalAttendance * 100).toInt() else 0
        
        // Get parents/guardians
        val parentRelationships = parentStudentRepository.findByStudentIdWithParentDetails(studentId)
        
        // Get all tracks
        val allTracks = educationTrackRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
        
        // Get all classes
        val allClasses = schoolClassRepository.findBySchoolIdAndIsActiveWithTrack(selectedSchoolId, true)
        val classesByTrackId = allClasses.filter { it.track != null }.groupBy { it.track!!.id }
        
        model.addAttribute("student", student)
        model.addAttribute("enrollments", enrollments)
        model.addAttribute("school", school)
        model.addAttribute("attendancePercentage", attendancePercentage)
        model.addAttribute("presentCount", presentCount)
        model.addAttribute("absentCount", absentCount)
        model.addAttribute("lateCount", lateCount)
        model.addAttribute("totalAttendance", totalAttendance)
        model.addAttribute("parentRelationships", parentRelationships)
        model.addAttribute("allTracks", allTracks)
        model.addAttribute("classesByTrackId", classesByTrackId)
        
        return "staff/student-profile"
    }
    
    @GetMapping("/classes/{classId}/details")
    fun getClassDetails(
        @org.springframework.web.bind.annotation.PathVariable classId: UUID,
        model: Model,
        authentication: Authentication,
        session: HttpSession
    ): String {
        val userDetails = userDetailsService.loadUserByUsername(authentication.name)
        val customUser = userDetails as com.haneef._school.service.CustomUserDetails
        
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
        if (selectedSchoolId != null) {
            // Get the class
            val schoolClass = schoolClassRepository.findById(classId).orElse(null)
            
            // Security Check: Ensure class belongs to the selected school
            if (schoolClass != null && schoolClass.schoolId == selectedSchoolId) {
                model.addAttribute("schoolClass", schoolClass)
                
                // Get current academic session and term
                val currentSession = academicSessionRepository.findBySchoolIdAndIsCurrentSessionAndIsActive(selectedSchoolId, true, true)
                val currentTerm = currentSession?.let { session ->
                    termRepository.findByAcademicSessionIdAndIsCurrentTermAndIsActive(session.id!!, true, true).orElse(null)
                }
                
                model.addAttribute("currentAcademicSession", currentSession)
                model.addAttribute("currentTerm", currentTerm)
                
                // Get staff record to check if user is class teacher
                val staff = staffRepository.findByUserIdAndSchoolId(
                    customUser.getUserId()!!, selectedSchoolId
                )
                
                if (staff != null && staff.isActive && currentSession != null && currentTerm != null) {
                    val isClassTeacher = classTeacherRepository
                        .existsByStaffIdAndSchoolClassIdAndAcademicSessionIdAndTermIdAndSchoolIdAndIsActive(
                            staff.id!!, classId, currentSession.id!!, currentTerm.id!!, selectedSchoolId, true
                        )
                    
                    model.addAttribute("isClassTeacher", isClassTeacher)
                    
                    // Get subjects taught by this teacher in this class
                    val subjectsTaught = subjectTeacherRepository
                        .findByStaffIdAndAcademicSessionIdAndTermIdAndIsActive(
                            staff.id!!, currentSession.id!!, currentTerm.id!!, true
                        )
                        .filter { it.schoolClass.id == classId }
                        .map { it.subject }
                    
                    model.addAttribute("subjectsTaught", subjectsTaught)
                }
                
                // Get students in this class
                val studentEnrollments = studentClassRepository
                    .findBySchoolClassIdAndIsActive(classId, true)
                
                model.addAttribute("students", studentEnrollments.map { it.student })
                
                // Get all subjects for this class
                val classSubjects = classSubjectRepository
                    .findBySchoolClassIdAndIsActive(classId, true)
                
                model.addAttribute("classSubjects", classSubjects)
                
                // Get today's attendance
                val today = LocalDate.now()
                val todaysAttendance = attendanceRepository.findBySchoolClassIdAndAttendanceDateAndSchoolIdAndIsActive(
                    classId, today, selectedSchoolId, true
                )
                
                if (todaysAttendance.isNotEmpty()) {
                    model.addAttribute("attendanceTakenToday", true)
                    model.addAttribute("todaysAttendance", todaysAttendance)
                    
                    // Calculate stats
                    val presentCount = todaysAttendance.count { it.status == AttendanceStatus.PRESENT }
                    val absentCount = todaysAttendance.count { it.status == AttendanceStatus.ABSENT }
                    val lateCount = todaysAttendance.count { it.status == AttendanceStatus.LATE }
                    val excusedCount = todaysAttendance.count { it.status == AttendanceStatus.EXCUSED }
                    
                    model.addAttribute("presentCount", presentCount)
                    model.addAttribute("absentCount", absentCount)
                    model.addAttribute("lateCount", lateCount)
                    model.addAttribute("excusedCount", excusedCount)
                } else {
                    model.addAttribute("attendanceTakenToday", false)
                }
            }
        }
        
        return "staff/class-details :: class-detail-fragment"
    }
    
    @GetMapping("/classes/{classId}/assessments")
    fun getClassAssessments(
        @org.springframework.web.bind.annotation.PathVariable classId: UUID,
        model: Model,
        authentication: Authentication,
        session: HttpSession
    ): String {
        val userDetails = userDetailsService.loadUserByUsername(authentication.name)
        val customUser = userDetails as com.haneef._school.service.CustomUserDetails
        
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
        if (selectedSchoolId != null) {
            // Security Check: Ensure class belongs to the selected school
            val schoolClass = schoolClassRepository.findById(classId).orElse(null)
            if (schoolClass == null || schoolClass.schoolId != selectedSchoolId) {
                return "staff/class-assessments :: assessments-content" // Return empty/error content
            }

            // Get staff record to determine what examinations they can see
            val staff = staffRepository.findByUserIdAndSchoolId(
                customUser.getUserId()!!, selectedSchoolId
            )
            
            // Get current academic session and term
            val currentSession = academicSessionRepository.findBySchoolIdAndIsCurrentSessionAndIsActive(selectedSchoolId, true, true)
            val currentTerm = currentSession?.let { session ->
                termRepository.findByAcademicSessionIdAndIsCurrentTermAndIsActive(session.id!!, true, true).orElse(null)
            }

            if (staff != null && staff.isActive && currentSession != null && currentTerm != null) {
                val isClassTeacher = classTeacherRepository
                    .existsByStaffIdAndSchoolClassIdAndAcademicSessionIdAndTermIdAndSchoolIdAndIsActive(
                        staff.id!!, classId, currentSession.id!!, currentTerm.id!!, selectedSchoolId, true
                    )
                
                val examinations = if (isClassTeacher) {
                    // Class teacher sees all examinations for this class
                    examinationRepository.findBySchoolIdAndFilters(
                        selectedSchoolId, true, null, classId, null, null, null
                    )
                } else {
                    // Subject teacher sees only examinations for subjects they teach
                    val subjectsTaught = subjectTeacherRepository
                        .findByStaffIdAndAcademicSessionIdAndTermIdAndIsActive(
                            staff.id!!, currentSession.id!!, currentTerm.id!!, true
                        )
                        .filter { it.schoolClass.id == classId }
                        .map { it.subject.id }
                    
                    if (subjectsTaught.isNotEmpty()) {
                        subjectsTaught.flatMap { subjectId ->
                            examinationRepository.findBySchoolIdAndFilters(
                                selectedSchoolId, true, subjectId, classId, null, null, null
                            )
                        }.distinctBy { it.id }
                    } else {
                        emptyList()
                    }
                }
                
                model.addAttribute("examinations", examinations)
                model.addAttribute("isClassTeacher", isClassTeacher)
                model.addAttribute("classId", classId)
            }
        }
        
        return "staff/class-assessments :: assessments-content"
    }
    
    @GetMapping("/classes/{classId}/questions")
    fun getClassQuestions(
        @org.springframework.web.bind.annotation.PathVariable classId: UUID,
        model: Model,
        authentication: Authentication,
        session: HttpSession,
        @org.springframework.web.bind.annotation.RequestParam(required = false) subjectId: UUID?
    ): String {
        val userDetails = userDetailsService.loadUserByUsername(authentication.name)
        val customUser = userDetails as com.haneef._school.service.CustomUserDetails
        
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/select-school"
        
        val school = schoolRepository.findById(selectedSchoolId).orElse(null)
        val schoolClass = schoolClassRepository.findById(classId).orElse(null)
        
        // Security Check: Ensure class belongs to the selected school
        if (school != null && schoolClass != null && schoolClass.schoolId == selectedSchoolId) {
            // Get staff record to determine permissions
            val staff = staffRepository.findByUserIdAndSchoolId(
                customUser.getUserId()!!, selectedSchoolId
            )
            
            // Get current academic session and term
            val currentSession = academicSessionRepository.findBySchoolIdAndIsCurrentSessionAndIsActive(selectedSchoolId, true, true)
            val currentTerm = currentSession?.let { session ->
                termRepository.findByAcademicSessionIdAndIsCurrentTermAndIsActive(session.id!!, true, true).orElse(null)
            }

            if (staff != null && staff.isActive && currentSession != null && currentTerm != null) {
                val isClassTeacher = classTeacherRepository
                    .existsByStaffIdAndSchoolClassIdAndAcademicSessionIdAndTermIdAndSchoolIdAndIsActive(
                        staff.id!!, classId, currentSession.id!!, currentTerm.id!!, selectedSchoolId, true
                    )
                
                // Get subjects taught by this teacher in this class
                val subjectsTaught = if (isClassTeacher) {
                    // Class teacher can see all subjects
                    classSubjectRepository.findBySchoolClassIdAndIsActive(classId, true)
                        .map { it.subject }
                } else {
                    // Subject teacher sees only their subjects
                    subjectTeacherRepository
                        .findByStaffIdAndAcademicSessionIdAndTermIdAndIsActive(
                            staff.id!!, currentSession.id!!, currentTerm.id!!, true
                        )
                        .filter { it.schoolClass.id == classId }
                        .map { it.subject }
                }
                
                // Get examinations based on role and subject filter
                val examinations = if (subjectId != null) {
                    // Filter by specific subject
                    examinationRepository.findBySchoolIdAndFilters(
                        selectedSchoolId, true, subjectId, classId, null, null, null
                    )
                } else if (isClassTeacher) {
                    // Class teacher sees all examinations for this class
                    examinationRepository.findBySchoolIdAndFilters(
                        selectedSchoolId, true, null, classId, null, null, null
                    )
                } else {
                    // Subject teacher sees examinations for their subjects
                    val subjectIds = subjectsTaught.map { it.id }
                    subjectIds.flatMap { subjId ->
                        examinationRepository.findBySchoolIdAndFilters(
                            selectedSchoolId, true, subjId, classId, null, null, null
                        )
                    }.distinctBy { it.id }
                }
                
                // Get questions for each examination
                val examinationsWithQuestions = examinations.map { exam ->
                    StaffExaminationWithQuestions(
                        examination = exam,
                        questions = questionRepository.findByExaminationIdOrderByCreatedAt(exam.id!!)
                    )
                }
                
                model.addAttribute("school", school)
                model.addAttribute("schoolClass", schoolClass)
                model.addAttribute("isClassTeacher", isClassTeacher)
                model.addAttribute("subjectsTaught", subjectsTaught)
                model.addAttribute("examinationsWithQuestions", examinationsWithQuestions)
                model.addAttribute("selectedSubjectId", subjectId)
                model.addAttribute("classId", classId)
                
                // Calculate statistics
                val totalQuestions = examinationsWithQuestions.sumOf { it.questions.size }
                val totalExaminations = examinations.size
                model.addAttribute("totalQuestions", totalQuestions)
                model.addAttribute("totalExaminations", totalExaminations)
            }
        }
        
        return "staff/class-questions"
    }
    
    @GetMapping("/classes/{classId}/questions/{questionId}")
    @org.springframework.web.bind.annotation.ResponseBody
    fun getQuestionDetails(
        @org.springframework.web.bind.annotation.PathVariable classId: UUID,
        @org.springframework.web.bind.annotation.PathVariable questionId: UUID,
        authentication: Authentication,
        session: HttpSession
    ): Map<String, Any?> {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return mapOf("error" to "Unauthorized")

        val question = questionRepository.findById(questionId).orElse(null)
        
        // Security Check: Ensure question belongs to the selected school
        if (question != null && question.schoolId == selectedSchoolId) {
            return mapOf(
                "id" to question.id,
                "instruction" to question.instruction,
                "questionText" to question.questionText,
                "optionA" to question.optionA,
                "optionB" to question.optionB,
                "optionC" to question.optionC,
                "optionD" to question.optionD,
                "optionE" to question.optionE,
                "correctAnswer" to question.correctAnswer,
                "marks" to question.marks,
                "examinationId" to question.examination.id,
                "examinationTitle" to question.examination.title
            )
        } else {
            return mapOf("error" to "Question not found or unauthorized")
        }
    }
    
    @GetMapping("/classes/{classId}/examinations/{examId}/questions")
    fun manageExaminationQuestions(
        @org.springframework.web.bind.annotation.PathVariable classId: UUID,
        @org.springframework.web.bind.annotation.PathVariable examId: UUID,
        model: Model,
        authentication: Authentication,
        session: HttpSession
    ): String {
        val userDetails = userDetailsService.loadUserByUsername(authentication.name)
        val customUser = userDetails as com.haneef._school.service.CustomUserDetails
        
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/select-school"
        
        val school = schoolRepository.findById(selectedSchoolId).orElse(null)
        val schoolClass = schoolClassRepository.findById(classId).orElse(null)
        val examination = examinationRepository.findById(examId).orElse(null)
        
        // Security Check: Ensure class and examination belong to the selected school
        if (school != null && schoolClass != null && examination != null && 
            schoolClass.schoolId == selectedSchoolId && examination.schoolId == selectedSchoolId) {
            
            // Verify staff has permission to manage this examination
            val staff = staffRepository.findByUserIdAndSchoolId(
                customUser.getUserId()!!, selectedSchoolId
            )
            
            if (staff != null && staff.isActive) {
                // Resolve session and term from examination
                val sessionEntity = academicSessionRepository.findBySchoolIdAndSessionYearAndIsActive(selectedSchoolId, examination.session, true)
                val termEntity = if (sessionEntity != null) {
                    termRepository.findByAcademicSessionIdAndTermNameAndIsActive(sessionEntity.id!!, examination.term, true).orElse(null)
                } else null
                
                val isClassTeacher = if (sessionEntity != null && termEntity != null) {
                    classTeacherRepository.existsByStaffIdAndSchoolClassIdAndAcademicSessionIdAndTermIdAndSchoolIdAndIsActive(
                        staff.id!!, classId, sessionEntity.id!!, termEntity.id!!, selectedSchoolId, true
                    )
                } else {
                    false
                }
                
                val canManageExamination = if (sessionEntity != null && termEntity != null) {
                    if (isClassTeacher) {
                        // Class teacher can manage all examinations for their class
                        examination.schoolClass.id == classId
                    } else {
                        // Subject teacher can only manage examinations for subjects they teach
                        val subjectsTaught = subjectTeacherRepository
                            .findByStaffIdAndAcademicSessionIdAndTermIdAndIsActive(
                                staff.id!!, sessionEntity.id!!, termEntity.id!!, true
                            )
                            .filter { it.schoolClass.id == classId }
                            .map { it.subject.id }
                        
                        examination.schoolClass.id == classId && subjectsTaught.contains(examination.subject.id)
                    }
                } else {
                    false
                }
                
                if (canManageExamination) {
                    model.addAttribute("school", school)
                    model.addAttribute("schoolClass", schoolClass)
                    model.addAttribute("examination", examination)
                    model.addAttribute("isClassTeacher", isClassTeacher)
                    model.addAttribute("classId", classId)
                    model.addAttribute("questions", examination.questions)
                    
                    return "staff/examination-questions :: questions-management-content"
                }
            }
        }
        
        // If we get here, access is denied or data not found
        model.addAttribute("error", "Access denied or examination not found")
        return "staff/examination-questions :: error-content"
    }
    
    @PostMapping("/classes/{classId}/examinations/{examId}/questions/save")
    @org.springframework.web.bind.annotation.ResponseBody
    fun saveExaminationQuestions(
        @org.springframework.web.bind.annotation.PathVariable classId: UUID,
        @org.springframework.web.bind.annotation.PathVariable examId: UUID,
        @org.springframework.web.bind.annotation.ModelAttribute form: QuestionListDto,
        authentication: Authentication,
        session: HttpSession
    ): Map<String, Any> {
        try {
            val userDetails = userDetailsService.loadUserByUsername(authentication.name)
            val customUser = userDetails as com.haneef._school.service.CustomUserDetails
            
            val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
                ?: return mapOf("success" to false, "message" to "School not selected")
            
            val examination = examinationRepository.findById(examId).orElse(null)
                ?: return mapOf("success" to false, "message" to "Examination not found")
            
            // Security Check: Ensure examination belongs to the selected school
            if (examination.schoolId != selectedSchoolId) {
                return mapOf("success" to false, "message" to "Unauthorized access")
            }

            // Verify permissions
            val staff = staffRepository.findByUserIdAndSchoolId(customUser.getUserId()!!, selectedSchoolId)
            if (staff == null || !staff.isActive) {
                return mapOf("success" to false, "message" to "Access denied")
            }
            
            // Resolve session and term from examination
            val sessionEntity = academicSessionRepository.findBySchoolIdAndSessionYearAndIsActive(selectedSchoolId, examination.session, true)
            val termEntity = if (sessionEntity != null) {
                termRepository.findByAcademicSessionIdAndTermNameAndIsActive(sessionEntity.id!!, examination.term, true).orElse(null)
            } else null
            
            if (sessionEntity == null || termEntity == null) {
                return mapOf("success" to false, "message" to "Invalid session or term configuration")
            }
            
            val isClassTeacher = classTeacherRepository
                .existsByStaffIdAndSchoolClassIdAndAcademicSessionIdAndTermIdAndSchoolIdAndIsActive(
                    staff.id!!, classId, sessionEntity.id!!, termEntity.id!!, selectedSchoolId, true
                )
            
            val canManageExamination = if (isClassTeacher) {
                examination.schoolClass.id == classId
            } else {
                val subjectsTaught = subjectTeacherRepository
                    .findByStaffIdAndAcademicSessionIdAndTermIdAndIsActive(
                        staff.id!!, sessionEntity.id!!, termEntity.id!!, true
                    )
                    .filter { it.schoolClass.id == classId }
                    .map { it.subject.id }
                examination.schoolClass.id == classId && subjectsTaught.contains(examination.subject.id)
            }
            
            if (!canManageExamination) {
                return mapOf("success" to false, "message" to "Access denied")
            }
            
            // Save questions
            form.questions.forEach { qData ->
                val sanitizedQuestionText = htmlSanitizerService.sanitize(qData.questionText)
                if (sanitizedQuestionText.isNotBlank()) {
                    val sanitizedInstruction = htmlSanitizerService.sanitize(qData.instruction)
                    val sanitizedOptionA = htmlSanitizerService.sanitize(qData.optionA)
                    val sanitizedOptionB = htmlSanitizerService.sanitize(qData.optionB)
                    val sanitizedOptionC = htmlSanitizerService.sanitize(qData.optionC)
                    val sanitizedOptionD = htmlSanitizerService.sanitize(qData.optionD)
                    val sanitizedOptionE = htmlSanitizerService.sanitize(qData.optionE)
                    
                    val question = com.haneef._school.entity.Question(
                        examination = examination,
                        instruction = sanitizedInstruction,
                        questionText = sanitizedQuestionText,
                        optionA = sanitizedOptionA,
                        optionB = sanitizedOptionB,
                        optionC = sanitizedOptionC,
                        optionD = sanitizedOptionD,
                        optionE = sanitizedOptionE,
                        correctAnswer = qData.correctAnswer,
                        marks = qData.marks
                    ).apply {
                        this.schoolId = examination.schoolId
                    }
                    examination.questions.add(question)
                }
            }
            
            examinationRepository.save(examination)
            return mapOf("success" to true, "message" to "Questions saved successfully!", "count" to form.questions.size)
            
        } catch (e: Exception) {
            return mapOf("success" to false, "message" to "Error saving questions. Please check your input.")
        }
    }
    
    @PostMapping("/classes/{classId}/examinations/{examId}/questions/{questionId}/update")
    @org.springframework.web.bind.annotation.ResponseBody
    fun updateExaminationQuestion(
        @org.springframework.web.bind.annotation.PathVariable classId: UUID,
        @org.springframework.web.bind.annotation.PathVariable examId: UUID,
        @org.springframework.web.bind.annotation.PathVariable questionId: UUID,
        @org.springframework.web.bind.annotation.RequestParam instruction: String?,
        @org.springframework.web.bind.annotation.RequestParam questionText: String,
        @org.springframework.web.bind.annotation.RequestParam optionA: String,
        @org.springframework.web.bind.annotation.RequestParam optionB: String,
        @org.springframework.web.bind.annotation.RequestParam optionC: String?,
        @org.springframework.web.bind.annotation.RequestParam optionD: String?,
        @org.springframework.web.bind.annotation.RequestParam optionE: String?,
        @org.springframework.web.bind.annotation.RequestParam correctAnswer: String,
        @org.springframework.web.bind.annotation.RequestParam marks: Double,
        authentication: Authentication,
        session: HttpSession
    ): Map<String, Any> {
        try {
            val question = questionRepository.findById(questionId).orElse(null)
                ?: return mapOf("success" to false, "message" to "Question not found")
            
            if (question.examination.id != examId) {
                return mapOf("success" to false, "message" to "Question does not belong to this examination")
            }
            
            // Verify permissions (similar to save method)
            val userDetails = userDetailsService.loadUserByUsername(authentication.name)
            val customUser = userDetails as com.haneef._school.service.CustomUserDetails
            val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
                ?: return mapOf("success" to false, "message" to "School not selected")
            
            // Security Check: Ensure question belongs to the selected school
            if (question.schoolId != selectedSchoolId) {
                return mapOf("success" to false, "message" to "Unauthorized access")
            }

            val staff = staffRepository.findByUserIdAndSchoolId(customUser.getUserId()!!, selectedSchoolId)
            if (staff == null || !staff.isActive) {
                return mapOf("success" to false, "message" to "Access denied")
            }
            
            val examination = question.examination
            // Resolve session and term from examination
            val sessionEntity = academicSessionRepository.findBySchoolIdAndSessionYearAndIsActive(selectedSchoolId, examination.session, true)
            val termEntity = if (sessionEntity != null) {
                termRepository.findByAcademicSessionIdAndTermNameAndIsActive(sessionEntity.id!!, examination.term, true).orElse(null)
            } else null
            
            if (sessionEntity == null || termEntity == null) {
                return mapOf("success" to false, "message" to "Invalid session or term configuration")
            }
            
            val isClassTeacher = classTeacherRepository
                .existsByStaffIdAndSchoolClassIdAndAcademicSessionIdAndTermIdAndSchoolIdAndIsActive(
                    staff.id!!, classId, sessionEntity.id!!, termEntity.id!!, selectedSchoolId, true
                )
            
            val canManageExamination = if (isClassTeacher) {
                examination.schoolClass.id == classId
            } else {
                val subjectsTaught = subjectTeacherRepository
                    .findByStaffIdAndAcademicSessionIdAndTermIdAndIsActive(
                        staff.id!!, sessionEntity.id!!, termEntity.id!!, true
                    )
                    .filter { it.schoolClass.id == classId }
                    .map { it.subject.id }
                examination.schoolClass.id == classId && subjectsTaught.contains(examination.subject.id)
            }
            
            if (!canManageExamination) {
                return mapOf("success" to false, "message" to "Access denied")
            }
            
            // Update question
            question.apply {
                this.instruction = htmlSanitizerService.sanitize(instruction)
                this.questionText = htmlSanitizerService.sanitize(questionText)
                this.optionA = htmlSanitizerService.sanitize(optionA)
                this.optionB = htmlSanitizerService.sanitize(optionB)
                this.optionC = htmlSanitizerService.sanitize(optionC)
                this.optionD = htmlSanitizerService.sanitize(optionD)
                this.optionE = htmlSanitizerService.sanitize(optionE)
                this.correctAnswer = correctAnswer
                this.marks = marks
            }
            
            questionRepository.save(question)
            return mapOf("success" to true, "message" to "Question updated successfully!")
            
        } catch (e: Exception) {
            return mapOf("success" to false, "message" to "Error updating question. Please check your input.")
        }
    }
    
    @PostMapping("/classes/{classId}/examinations/{examId}/questions/{questionId}/delete")
    @org.springframework.web.bind.annotation.ResponseBody
    fun deleteExaminationQuestion(
        @org.springframework.web.bind.annotation.PathVariable classId: UUID,
        @org.springframework.web.bind.annotation.PathVariable examId: UUID,
        @org.springframework.web.bind.annotation.PathVariable questionId: UUID,
        authentication: Authentication,
        session: HttpSession
    ): Map<String, Any> {
        try {
            val question = questionRepository.findById(questionId).orElse(null)
                ?: return mapOf("success" to false, "message" to "Question not found")
            
            if (question.examination.id != examId) {
                return mapOf("success" to false, "message" to "Question does not belong to this examination")
            }
            
            // Verify permissions (similar to save method)
            val userDetails = userDetailsService.loadUserByUsername(authentication.name)
            val customUser = userDetails as com.haneef._school.service.CustomUserDetails
            val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
                ?: return mapOf("success" to false, "message" to "School not selected")
            
            // Security Check: Ensure question belongs to the selected school
            if (question.schoolId != selectedSchoolId) {
                return mapOf("success" to false, "message" to "Unauthorized access")
            }

            val staff = staffRepository.findByUserIdAndSchoolId(customUser.getUserId()!!, selectedSchoolId)
            if (staff == null || !staff.isActive) {
                return mapOf("success" to false, "message" to "Access denied")
            }
            
            val examination = question.examination
            val isClassTeacher = classTeacherRepository
                .findByStaffIdAndIsActive(staff.id!!, true)
                .any { it.schoolClass.id == classId }
            
            val canManageExamination = if (isClassTeacher) {
                examination.schoolClass.id == classId
            } else {
                val subjectsTaught = subjectTeacherRepository
                    .findByStaffIdAndIsActive(staff.id!!, true)
                    .filter { it.schoolClass.id == classId }
                    .map { it.subject.id }
                examination.schoolClass.id == classId && subjectsTaught.contains(examination.subject.id)
            }
            
            if (!canManageExamination) {
                return mapOf("success" to false, "message" to "Access denied")
            }
            
            questionRepository.delete(question)
            return mapOf("success" to true, "message" to "Question deleted successfully!")
            
        } catch (e: Exception) {
            return mapOf("success" to false, "message" to "Error deleting question.")
        }
    }

    @GetMapping("/classes/{classId}/attendance/take")
    fun takeAttendance(
        @PathVariable classId: UUID,
        @RequestParam(required = false) date: String?,
        model: Model,
        authentication: Authentication,
        session: HttpSession
    ): String {
        val userDetails = userDetailsService.loadUserByUsername(authentication.name)
        val customUser = userDetails as com.haneef._school.service.CustomUserDetails
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID ?: return "fragments/error :: error-message"

        val attendanceDate = if (date != null) LocalDate.parse(date) else LocalDate.now()
        val schoolClass = schoolClassRepository.findById(classId).orElse(null) ?: return "fragments/error :: error-message"

        // Security Check: Ensure class belongs to the selected school
        if (schoolClass.schoolId != selectedSchoolId) {
            return "fragments/error :: error-message"
        }

        // Get students in this class
        val studentEnrollments = studentClassRepository.findBySchoolClassIdAndIsActive(classId, true)
        val students = studentEnrollments.map { it.student }

        // Get existing attendance for this date
        val existingAttendance = attendanceRepository.findBySchoolClassIdAndAttendanceDateAndSchoolIdAndIsActive(
            classId, attendanceDate, selectedSchoolId, true
        ).associateBy { it.student.id }

        model.addAttribute("schoolClass", schoolClass)
        model.addAttribute("students", students)
        model.addAttribute("attendanceDate", attendanceDate)
        model.addAttribute("existingAttendance", existingAttendance)
        model.addAttribute("classId", classId)

        return "staff/take-attendance :: attendance-content"
    }

    @GetMapping("/classes/{classId}/attendance/history")
    fun viewAttendanceHistory(
        @PathVariable classId: UUID,
        model: Model,
        session: HttpSession
    ): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID ?: return "fragments/error :: error-message"
        
        // Security Check: Ensure class belongs to the selected school
        val schoolClass = schoolClassRepository.findById(classId).orElse(null)
        if (schoolClass == null || schoolClass.schoolId != selectedSchoolId) {
            return "fragments/error :: error-message"
        }

        val currentTerm = termRepository.findBySchoolIdAndIsCurrentTermAndIsActive(selectedSchoolId, true, true).orElse(null)
        
        if (currentTerm != null) {
            val attendanceRecords = attendanceRepository.findBySchoolClassIdAndAttendanceDateBetweenAndSchoolIdAndIsActive(
                classId, currentTerm.startDate, LocalDate.now(), selectedSchoolId, true
            )

            // Group by date and calculate stats
            val dailyStats = attendanceRecords.groupBy { it.attendanceDate }
                .mapValues { (_, records) ->
                    val present = records.count { it.status == AttendanceStatus.PRESENT }
                    val total = records.size
                    val percentage = if (total > 0) (present.toDouble() / total * 100).toInt() else 0
                    mapOf("present" to present, "total" to total, "percentage" to percentage)
                }.toSortedMap(reverseOrder())

            model.addAttribute("dailyStats", dailyStats)
            model.addAttribute("currentTerm", currentTerm)
        }
        
        model.addAttribute("classId", classId)
        return "staff/attendance-history :: attendance-history-content"
    }

    @GetMapping("/classes/{classId}/attendance/report")
    fun generateAttendanceReport(
        @PathVariable classId: UUID,
        model: Model,
        session: HttpSession
    ): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID ?: return "fragments/error :: error-message"
        
        // Security Check: Ensure class belongs to the selected school
        val schoolClass = schoolClassRepository.findById(classId).orElse(null)
        if (schoolClass == null || schoolClass.schoolId != selectedSchoolId) {
            return "fragments/error :: error-message"
        }

        val currentTerm = termRepository.findBySchoolIdAndIsCurrentTermAndIsActive(selectedSchoolId, true, true).orElse(null)
        
        if (currentTerm != null) {
            val attendanceRecords = attendanceRepository.findBySchoolClassIdAndAttendanceDateBetweenAndSchoolIdAndIsActive(
                classId, currentTerm.startDate, LocalDate.now(), selectedSchoolId, true
            )

            // Group by student and calculate stats
            val studentStats = attendanceRecords.groupBy { it.student }
                .mapValues { (_, records) ->
                    val present = records.count { it.status == AttendanceStatus.PRESENT }
                    val absent = records.count { it.status == AttendanceStatus.ABSENT }
                    val total = records.size
                    val percentage = if (total > 0) (present.toDouble() / total * 100).toInt() else 0
                    mapOf("present" to present, "absent" to absent, "total" to total, "percentage" to percentage)
                }.toSortedMap(compareBy { it.user.lastName })

            // Group by date for daily breakdown
            val dailyBreakdown = attendanceRecords.groupBy { it.attendanceDate }
                .toSortedMap(reverseOrder())

            model.addAttribute("studentStats", studentStats)
            model.addAttribute("dailyBreakdown", dailyBreakdown)
            model.addAttribute("currentTerm", currentTerm)
        }
        
        model.addAttribute("classId", classId)
        return "staff/attendance-report :: attendance-report-content"
    }

    @GetMapping("/classes/{classId}/examinations/{examId}/submissions")
    fun viewSubmissions(
        @PathVariable classId: UUID,
        @PathVariable examId: UUID,
        model: Model,
        authentication: Authentication,
        session: HttpSession
    ): String {
        val userDetails = userDetailsService.loadUserByUsername(authentication.name)
        val customUser = userDetails as com.haneef._school.service.CustomUserDetails
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "fragments/error :: error-message"

        val examination = examinationRepository.findById(examId).orElseThrow {
            RuntimeException("Examination not found")
        }
        
        // Verify permissions
        val staff = staffRepository.findByUserIdAndSchoolId(customUser.getUserId()!!, selectedSchoolId)
        if (staff == null || !staff.isActive) {
            return "fragments/error :: error-message"
        }
        
        // Check if staff has access to this class/exam
        // Resolve session and term from examination
        val sessionEntity = academicSessionRepository.findBySchoolIdAndSessionYearAndIsActive(selectedSchoolId, examination.session, true)
        val termEntity = if (sessionEntity != null) {
            termRepository.findByAcademicSessionIdAndTermNameAndIsActive(sessionEntity.id!!, examination.term, true).orElse(null)
        } else null
        
        if (sessionEntity == null || termEntity == null) {
            return "fragments/error :: error-message"
        }
        
        val isClassTeacher = classTeacherRepository
            .existsByStaffIdAndSchoolClassIdAndAcademicSessionIdAndTermIdAndSchoolIdAndIsActive(
                staff.id!!, classId, sessionEntity.id!!, termEntity.id!!, selectedSchoolId, true
            )
            
        val hasAccess = if (isClassTeacher) {
            examination.schoolClass.id == classId
        } else {
            val subjectsTaught = subjectTeacherRepository
                .findByStaffIdAndAcademicSessionIdAndTermIdAndIsActive(
                    staff.id!!, sessionEntity.id!!, termEntity.id!!, true
                )
                .filter { it.schoolClass.id == classId }
                .map { it.subject.id }
            examination.schoolClass.id == classId && subjectsTaught.contains(examination.subject.id)
        }
        
        if (!hasAccess) {
             return "fragments/error :: error-message"
        }

        val submissions = examinationSubmissionRepository.findByExaminationIdWithStudent(examId)
        
        model.addAttribute("examination", examination)
        model.addAttribute("submissions", submissions)
        model.addAttribute("isAdmin", false) // Staff cannot delete

        return "admin/assessments/submissions-modal :: submissions-content"
    }
    
    @GetMapping("/reports/class")
    fun classReportsHome(
        model: Model,
        authentication: Authentication,
        session: HttpSession,
        @RequestParam(required = false) trackId: UUID?,
        @RequestParam(required = false) departmentId: UUID?,
        @RequestParam(required = false) classId: UUID?,
        @RequestParam(required = false) term: String?,
        @RequestParam(required = false) sessionYear: String?
    ): String {
        val userDetails = userDetailsService.loadUserByUsername(authentication.name)
        val customUser = userDetails as com.haneef._school.service.CustomUserDetails
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID ?: return "redirect:/select-school"

        val school = schoolRepository.findById(selectedSchoolId).orElseThrow { RuntimeException("School not found") }
        val staff = staffRepository.findByUserIdAndSchoolId(customUser.getUserId()!!, selectedSchoolId) ?: return "redirect:/staff/dashboard"

        // Staff only sees their assigned classes
        // Determine session to use for assignments
        val targetSession = if (sessionYear != null) {
            academicSessionRepository.findBySchoolIdAndSessionYearAndIsActive(selectedSchoolId, sessionYear, true)
        } else {
            academicSessionRepository.findBySchoolIdAndIsCurrentSessionAndIsActive(selectedSchoolId, true, true)
        }
        
        val assignedClassIds = if (targetSession != null) {
            // Get assignments for the specific session (we don't filter by term here as reports might cover any term in the session)
            val classTeacherAssignments = classTeacherRepository.findByStaffIdAndAcademicSessionIdAndIsActive(
                staff.id!!, targetSession.id!!, true
            )
            val subjectTeacherAssignments = subjectTeacherRepository.findByStaffIdAndAcademicSessionIdAndIsActive(
                staff.id!!, targetSession.id!!, true
            )
            (classTeacherAssignments.map { it.schoolClass.id } + subjectTeacherAssignments.map { it.schoolClass.id }).filterNotNull().toSet()
        } else {
            emptySet()
        }
        
        val academicSessions = academicSessionRepository.findBySchoolIdAndIsActiveOrderByYearDesc(selectedSchoolId, true)
        val educationTracks = educationTrackRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
        val departments = departmentRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
        val classes = schoolClassRepository.findAllById(assignedClassIds).filter { it.isActive }
        val terms = listOf("First Term", "Second Term", "Third Term")

        model.addAttribute("school", school)
        model.addAttribute("academicSessions", academicSessions)
        model.addAttribute("educationTracks", educationTracks)
        model.addAttribute("departments", departments)
        model.addAttribute("classes", classes)
        model.addAttribute("terms", terms)
        
        model.addAttribute("selectedTrackId", trackId)
        model.addAttribute("selectedDepartmentId", departmentId)
        model.addAttribute("selectedClassId", classId)
        model.addAttribute("selectedTerm", term)
        model.addAttribute("selectedSession", sessionYear)
        
        model.addAttribute("showFilters", true)
        model.addAttribute("hideSubjectFilter", true)
        model.addAttribute("hideExamTypeFilter", true)

        if (classId != null && sessionYear != null && assignedClassIds.contains(classId)) {
            val session = academicSessionRepository.findBySchoolIdAndSessionYearAndIsActive(selectedSchoolId, sessionYear, true)
            if (session != null) {
                val enrollments = studentClassRepository.findBySchoolClassIdAndAcademicSessionIdAndIsActive(
                    classId, session.id!!, true
                )
                model.addAttribute("students", enrollments.map { it.student })
            } else {
                model.addAttribute("students", emptyList<com.haneef._school.entity.Student>())
            }
        }

        return "staff/class-reports"
    }

    @GetMapping("/reports/class/filter")
    fun filterClassReports(
        model: Model,
        authentication: Authentication,
        session: HttpSession,
        @RequestParam(required = false) trackId: UUID?,
        @RequestParam(required = false) departmentId: UUID?,
        @RequestParam(required = false) classId: UUID?,
        @RequestParam(required = false) term: String?,
        @RequestParam(required = false) sessionYear: String?
    ): String {
        try {
            println("DEBUG: filterClassReports called with classId=$classId, sessionYear=$sessionYear")
            val userDetails = userDetailsService.loadUserByUsername(authentication.name)
            val customUser = userDetails as com.haneef._school.service.CustomUserDetails
            val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID ?: return "fragments/error :: error-message"

            logger.info("Filtering reports: classId={}, sessionYear={}, schoolId={}", classId, sessionYear, selectedSchoolId)
            
            val staff = staffRepository.findByUserIdAndSchoolId(customUser.getUserId()!!, selectedSchoolId)
            if (staff == null) {
                logger.warn("Staff record not found for userId={} and schoolId={}", customUser.getUserId(), selectedSchoolId)
                return "fragments/error :: error-message"
            }

            // Determine session to use for assignments
            val targetSession = if (sessionYear != null) {
                academicSessionRepository.findBySchoolIdAndSessionYearAndIsActive(selectedSchoolId, sessionYear, true)
            } else {
                academicSessionRepository.findBySchoolIdAndIsCurrentSessionAndIsActive(selectedSchoolId, true, true)
            }
            
            val assignedClassIds = if (targetSession != null) {
                val classTeacherAssignments = classTeacherRepository.findByStaffIdAndAcademicSessionIdAndIsActive(
                    staff.id!!, targetSession.id!!, true
                )
                val subjectTeacherAssignments = subjectTeacherRepository.findByStaffIdAndAcademicSessionIdAndIsActive(
                    staff.id!!, targetSession.id!!, true
                )
                (classTeacherAssignments.map { it.schoolClass.id } + subjectTeacherAssignments.map { it.schoolClass.id }).filterNotNull().toSet()
            } else {
                emptySet()
            }
            
            logger.info("Assigned class IDs for staff {}: {}", staff.id, assignedClassIds)

            model.addAttribute("selectedTrackId", trackId)
            model.addAttribute("selectedDepartmentId", departmentId)
            model.addAttribute("selectedClassId", classId)
            model.addAttribute("selectedTerm", term)
            model.addAttribute("selectedSession", sessionYear)

            val isAssigned = assignedClassIds.contains(classId)
            logger.info("Is class {} assigned? {}", classId, isAssigned)

            if (classId != null && sessionYear != null && isAssigned) {
                val allEnrollments = studentClassRepository.findBySchoolClassIdAndIsActive(classId, true)
                logger.info("Total enrollments for class {} (any year): {}", classId, allEnrollments.size)
                
                val session = academicSessionRepository.findBySchoolIdAndSessionYearAndIsActive(selectedSchoolId, sessionYear, true)
                
                if (session != null) {
                    val enrollments = studentClassRepository.findBySchoolClassIdAndAcademicSessionIdAndIsActive(
                        classId, session.id!!, true
                    )
                    logger.info("Found {} enrollments for class {} and year {}", enrollments.size, classId, sessionYear)
                    model.addAttribute("students", enrollments.map { it.student })
                } else {
                    logger.warn("Session not found for year: {}", sessionYear)
                    model.addAttribute("students", emptyList<com.haneef._school.entity.Student>())
                }
            } else {
                logger.warn("Condition not met: classId={}, sessionYear={}, isAssigned={}", classId, sessionYear, isAssigned)
                model.addAttribute("students", emptyList<com.haneef._school.entity.Student>())
            }

            return "staff/class-reports :: student-selector"
        } catch (e: Exception) {
            logger.error("Error in filterClassReports", e)
            e.printStackTrace()
            return "fragments/error :: error-message"
        }
    }

    @PostMapping("/reports/class/save")
    @ResponseBody
    @Transactional
    fun saveClassAssessment(
        @RequestBody request: StaffSaveAssessmentRequest,
        authentication: Authentication,
        session_http: HttpSession
    ): Map<String, Any> {
        val selectedSchoolId = session_http.getAttribute("selectedSchoolId") as? UUID ?: throw RuntimeException("School not selected")
        val userDetails = userDetailsService.loadUserByUsername(authentication.name)
        val customUser = userDetails as com.haneef._school.service.CustomUserDetails

        val staff = staffRepository.findByUserIdAndSchoolId(customUser.getUserId()!!, selectedSchoolId) ?: throw RuntimeException("Staff record not found")
        
        // Resolve Session
        val sessionEntity = academicSessionRepository.findBySchoolIdAndSessionYearAndIsActive(selectedSchoolId, request.session, true)
            ?: throw RuntimeException("Session '${request.session}' not found")

        // Resolve Term
        val sessionTerms = termRepository.findByAcademicSessionIdAndIsActiveOrderByStartDate(sessionEntity.id!!, true)
        val termEntity = sessionTerms.find { it.termName.equals(request.term, ignoreCase = true) }
            ?: try {
                val termId = UUID.fromString(request.term)
                sessionTerms.find { it.id == termId }
            } catch (e: IllegalArgumentException) {
                null
            }
            ?: throw RuntimeException("Term '${request.term}' not found in session '${request.session}'")

        val sessionId = sessionEntity.id!!
        val termId = termEntity.id!!
        val classId = request.classId

        // Verify student is enrolled in this class for this session
        val enrollment = studentClassRepository.findByStudentIdAndAcademicSessionIdAndTermIdAndIsActive(
            request.studentId, sessionId, termId, true
        ).find { it.schoolClass.id == classId && it.schoolId == selectedSchoolId }
            ?: studentClassRepository.findByStudentIdAndAcademicSessionIdAndIsActive(
                request.studentId, sessionId, true
            ).find { it.schoolClass.id == classId && it.schoolId == selectedSchoolId }
            ?: throw RuntimeException("Student enrollment not found for this class and session")
        
        val isClassTeacher = classTeacherRepository.existsByStaffIdAndSchoolClassIdAndAcademicSessionIdAndTermIdAndSchoolIdAndIsActive(
            staff.id!!, classId, sessionId, termId, selectedSchoolId, true
        )
        val subjectsTaught = subjectTeacherRepository.findByStaffIdAndAcademicSessionIdAndTermIdAndIsActive(
            staff.id!!, sessionId, termId, true
        ).filter { it.schoolClass.id == classId }.map { it.subject.id }

        val student = studentRepository.findById(request.studentId).orElseThrow { RuntimeException("Student not found") }

        val assessment = assessmentRepository.findByStudentIdAndSessionAndTermAndSchoolIdAndIsActive(
            request.studentId, request.session, request.term, selectedSchoolId, true
        ).orElseGet {
            val a = Assessment(
                admissionNumber = student.admissionNumber ?: "",
                student = student,
                session = request.session,
                term = request.term
            )
            a.schoolId = selectedSchoolId
            a
        }
        
        // Only class teacher can update behavioral traits and comments
        if (isClassTeacher) {
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
            // Verify staff can grade this subject
            if (!isClassTeacher && !subjectsTaught.contains(scoreInput.subjectId)) {
                logger.warn("Skipping score for subject {} - User not authorized (isClassTeacher={}, subjectsTaught={})", scoreInput.subjectId, isClassTeacher, subjectsTaught)
                return@forEach
            }

            val subject = subjectRepository.findById(scoreInput.subjectId).orElseThrow { RuntimeException("Subject not found") }
            val subjectScore = subjectScoreRepository.findByAssessmentIdAndSubjectIdAndSchoolIdAndIsActive(
                assessment.id!!, scoreInput.subjectId, selectedSchoolId, true
            ).firstOrNull() ?: {
                // Find the ClassSubject for this subject and the student's class
                val classSubject = classSubjectRepository.findBySchoolClassIdAndSubjectIdAndIsActive(
                    classId, scoreInput.subjectId, true
                ) ?: throw RuntimeException("ClassSubject not found for subject ${subject.subjectName}")
                
                SubjectScore(
                    assessment = assessment,
                    subject = subject,
                    classSubject = classSubject
                ).apply {
                    this.schoolId = selectedSchoolId
                }
            }()

            // Ensure classSubject is set for existing records too
            if (subjectScore.classSubject == null) {
                val classSubject = classSubjectRepository.findBySchoolClassIdAndSubjectIdAndIsActive(
                    classId, scoreInput.subjectId, true
                ) ?: throw RuntimeException("ClassSubject not found for subject ${subject.subjectName}")
                subjectScore.classSubject = classSubject
            }

            subjectScore.ca1Score = scoreInput.ca1
            subjectScore.ca2Score = scoreInput.ca2
            subjectScore.examScore = scoreInput.exam
            
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
                subjectScore.grade = null
                subjectScore.remark = null
            }

            subjectScoreRepository.save(subjectScore)
        }

        return mapOf("success" to true, "message" to "Assessment saved successfully")
    }

    @GetMapping("/reports/class/student-data")
    @ResponseBody
    fun getStudentAssessmentData(
        @RequestParam studentId: UUID,
        @RequestParam classId: UUID,
        @RequestParam session: String,
        @RequestParam term: String,
        authentication: Authentication,
        session_http: HttpSession
    ): AssessmentReportData {
        val selectedSchoolId = session_http.getAttribute("selectedSchoolId") as? UUID ?: throw RuntimeException("School not selected")
        val userDetails = userDetailsService.loadUserByUsername(authentication.name)
        val customUser = userDetails as com.haneef._school.service.CustomUserDetails

        val staff = staffRepository.findByUserIdAndSchoolId(customUser.getUserId()!!, selectedSchoolId) ?: throw RuntimeException("Staff record not found")
        
        // Verify staff has access to this class
        val sessionEntity = academicSessionRepository.findBySchoolIdAndSessionYearAndIsActive(selectedSchoolId, session, true)
            ?: throw RuntimeException("Session not found")
            
        // Fetch all active terms for this session to find the matching one
        val sessionTerms = termRepository.findByAcademicSessionIdAndIsActiveOrderByStartDate(sessionEntity.id!!, true)
        
        val termEntity = sessionTerms.find { it.termName.equals(term, ignoreCase = true) }
            ?: try {
                // Try to parse as UUID if name match fails
                val termId = UUID.fromString(term)
                sessionTerms.find { it.id == termId }
            } catch (e: IllegalArgumentException) {
                null
            }
            ?: throw RuntimeException("Term '$term' not found in session '$session'. Available terms: ${sessionTerms.joinToString { it.termName }}")
        
        logger.info("Checking access for Staff: ${staff.id}, Class: $classId, Session: ${sessionEntity.id}, Term: ${termEntity.id}")

        // Verify staff has access to this class
        val isClassTeacher = classTeacherRepository.existsByStaffIdAndSchoolClassIdAndAcademicSessionIdAndTermIdAndSchoolIdAndIsActive(
            staff.id!!, classId, sessionEntity.id!!, termEntity.id!!, selectedSchoolId, true
        )
        
        val subjectsTaught = subjectTeacherRepository.findByStaffIdAndAcademicSessionIdAndTermIdAndIsActive(
            staff.id!!, sessionEntity.id!!, termEntity.id!!, true
        ).filter { it.schoolClass.id == classId }.map { it.subject.id }
        
        logger.info("Access Check Result - Is Class Teacher: $isClassTeacher, Subjects Taught Count: ${subjectsTaught.size}")
        if (subjectsTaught.isNotEmpty()) {
             logger.info("Subjects Taught IDs: $subjectsTaught")
        }

        if (!isClassTeacher && subjectsTaught.isEmpty()) {
            logger.error("ACCESS DENIED: Staff ${staff.id} is neither a class teacher nor a subject teacher for Class $classId in Session ${sessionEntity.id} Term ${termEntity.id}")
            throw RuntimeException("Access denied to this class")
        }

        val student = studentRepository.findById(studentId).orElseThrow { RuntimeException("Student not found") }
        
        // Get all subjects for this class
        val classSubjects = classSubjectRepository.findBySchoolClassIdAndIsActive(classId, true)
        
        // Get existing assessment if any
        val assessmentOpt = assessmentRepository.findByStudentIdAndSessionAndTermAndSchoolIdAndIsActive(
            studentId, session, term, selectedSchoolId, true
        )

        val assessment = assessmentOpt.orElse(null)

        val subjectDataList = classSubjects.map { cs ->
            var ca1: Int? = null
            var ca2: Int? = null
            var exam: Int? = null
            var total: Int? = null
            var grade: String? = null
            var remark: String? = null

            var scoresMap = emptyMap<String, Int?>()

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

        // Get class details directly from the requested classId
        val schoolClass = schoolClassRepository.findById(classId).orElseThrow { RuntimeException("Class not found") }
        
        val className = schoolClass.className
        val trackName = schoolClass.department?.track?.name ?: "Unknown Track"

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

    @PostMapping("/reports/class/import")
    @ResponseBody
    fun importClassScores(
        @RequestBody request: ImportAssessmentRequest,
        authentication: Authentication,
        session_http: HttpSession
    ): Map<String, Any> {
        val selectedSchoolId = session_http.getAttribute("selectedSchoolId") as? UUID ?: throw RuntimeException("School not selected")
        val userDetails = userDetailsService.loadUserByUsername(authentication.name)
        val customUser = userDetails as com.haneef._school.service.CustomUserDetails

        val staff = staffRepository.findByUserIdAndSchoolId(customUser.getUserId()!!, selectedSchoolId) ?: throw RuntimeException("Staff record not found")
        val sessionEntity = academicSessionRepository.findBySchoolIdAndSessionYearAndIsActive(selectedSchoolId, request.session, true)
            ?: throw RuntimeException("Session not found")
        val termEntity = termRepository.findByAcademicSessionIdAndTermNameAndIsActive(sessionEntity.id!!, request.term, true)
            .orElseThrow { RuntimeException("Term not found") }

        val isClassTeacher = classTeacherRepository.existsByStaffIdAndSchoolClassIdAndAcademicSessionIdAndTermIdAndSchoolIdAndIsActive(
            staff.id!!, request.classId, sessionEntity.id!!, termEntity.id!!, selectedSchoolId, true
        )
        val subjectsTaught = subjectTeacherRepository.findByStaffIdAndAcademicSessionIdAndTermIdAndIsActive(
            staff.id!!, sessionEntity.id!!, termEntity.id!!, true
        ).filter { it.schoolClass.id == request.classId }.map { it.subject.id }

        if (!isClassTeacher && subjectsTaught.isEmpty()) {
            throw RuntimeException("Access denied to this class")
        }

        val students = if (request.studentId != null) {
            listOf(studentRepository.findById(request.studentId).orElseThrow { RuntimeException("Student not found") })
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
                val a = Assessment(
                    admissionNumber = student.admissionNumber ?: "",
                    student = student,
                    session = request.session,
                    term = request.term
                )
                a.schoolId = selectedSchoolId
                a
            }
            assessmentRepository.save(assessment)

            val classSubjects = classSubjectRepository.findBySchoolClassIdAndIsActive(request.classId, true)
            
            classSubjects.forEach { cs ->
                // Verify staff can import for this subject
                if (!isClassTeacher && !subjectsTaught.contains(cs.subject.id)) {
                    return@forEach
                }

                var targetMax = 100
                val scoringScheme = cs.schoolClass.scoringScheme
                if (!scoringScheme.isNullOrBlank()) {
                    try {
                        val scheme = objectMapper.readValue(scoringScheme, object : com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Any>>>() {})
                        val item = scheme.find { (it["alias"] as? String) == request.componentName || (it["name"] as? String) == request.componentName }
                        if (item != null) targetMax = (item["max"] as? Int) ?: 100
                    } catch (e: Exception) {}
                }

                var totalWeightedScore = 0.0
                var hasAnyScore = false
                var singleExamMax = 0

                request.sources.forEach { source ->
                    val exams = examinationRepository.findBySubjectIdAndSchoolClassIdAndTermAndSessionAndExamTypeAndIsActive(
                        cs.subject.id!!, request.classId, request.term, request.session, source.examType, true
                    )

                    if (exams.isNotEmpty()) {
                        val exam = exams[0]
                        if (request.sources.size == 1) singleExamMax = exam.totalMarks ?: 100
                        
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
                        finalScore = if (singleExamMax > 0) ((totalWeightedScore / singleExamMax) * targetMax).toInt() else totalWeightedScore.toInt()
                    } else {
                        finalScore = (totalWeightedScore / request.divisor).toInt()
                        if (finalScore > targetMax) finalScore = targetMax
                    }

                    val subjectScore = subjectScoreRepository.findByAssessmentIdAndSubjectIdAndSchoolIdAndIsActive(
                        assessment.id!!, cs.subject.id!!, selectedSchoolId, true
                    ).firstOrNull() ?: SubjectScore(
                        assessment = assessment,
                        subject = cs.subject
                    ).apply {
                        this.schoolId = selectedSchoolId
                    }

                    val scoresMap = if (!subjectScore.scoresJson.isNullOrBlank()) {
                        try {
                            objectMapper.readValue(subjectScore.scoresJson, object : com.fasterxml.jackson.core.type.TypeReference<MutableMap<String, Int?>>() {})
                        } catch (e: Exception) { mutableMapOf<String, Int?>() }
                    } else {
                        mutableMapOf<String, Int?>().apply {
                            if ((subjectScore.ca1Score ?: 0) > 0) put("CA 1", subjectScore.ca1Score)
                            if ((subjectScore.ca2Score ?: 0) > 0) put("CA 2", subjectScore.ca2Score)
                            if ((subjectScore.examScore ?: 0) > 0) put("Exam", subjectScore.examScore)
                        }
                    }

                    scoresMap[request.componentName] = finalScore
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


    
    @GetMapping("/classes/{classId}/students/export")
    fun exportStudentsList(
        @org.springframework.web.bind.annotation.PathVariable classId: UUID,
        authentication: Authentication,
        session: HttpSession,
        response: jakarta.servlet.http.HttpServletResponse
    ) {
        val userDetails = userDetailsService.loadUserByUsername(authentication.name)
        val customUser = userDetails as com.haneef._school.service.CustomUserDetails
        
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: throw IllegalStateException("School not selected")
        
        // Verify staff has access to this class
        val staff = staffRepository.findByUserIdAndSchoolId(customUser.getUserId()!!, selectedSchoolId)
            ?: throw IllegalStateException("Staff record not found")
        
        // Get current academic session and term
        val currentSession = academicSessionRepository.findBySchoolIdAndIsCurrentSessionAndIsActive(selectedSchoolId, true, true)
        val currentTerm = currentSession?.let { session ->
            termRepository.findByAcademicSessionIdAndIsCurrentTermAndIsActive(session.id!!, true, true).orElse(null)
        }
        
        if (currentSession == null || currentTerm == null) {
            throw IllegalStateException("Current session or term not found")
        }

        val hasAccess = classTeacherRepository.existsByStaffIdAndSchoolClassIdAndAcademicSessionIdAndTermIdAndSchoolIdAndIsActive(
                staff.id!!, classId, currentSession.id!!, currentTerm.id!!, selectedSchoolId, true
            ) || subjectTeacherRepository.findByStaffIdAndAcademicSessionIdAndTermIdAndIsActive(
                staff.id!!, currentSession.id!!, currentTerm.id!!, true
            ).any { it.schoolClass.id == classId }
        
        if (!hasAccess) {
            throw IllegalStateException("Access denied to this class")
        }
        
        // Get class and students
        val schoolClass = schoolClassRepository.findById(classId).orElse(null)
            ?: throw IllegalStateException("Class not found")
        
        val studentEnrollments = studentClassRepository.findBySchoolClassIdAndIsActive(classId, true)
        val students = studentEnrollments.map { it.student }
        
        // Set response headers
        response.contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        response.setHeader("Content-Disposition", "attachment; filename=\"${schoolClass.className}_students_list.xlsx\"")
        
        // Create Excel workbook using FastExcel
        val os = response.outputStream
        val wb = org.dhatim.fastexcel.Workbook(os, "4School", "1.0")
        val ws = wb.newWorksheet("Students List")
        
        // Create header row
        val headers = listOf("S/N", "Admission Number", "First Name", "Last Name", "Email", "Phone", "Date of Birth", "Gender")
        headers.forEachIndexed { index, header ->
            ws.value(0, index, header)
        }
        
        // Style header (Bold)
        ws.range(0, 0, 0, headers.size - 1).style().bold().set()
        
        // Add student data
        students.forEachIndexed { index: Int, student: Student ->
            val r = index + 1
            ws.value(r, 0, (index + 1).toDouble())
            
            val admissionNumber: String = student.admissionNumber ?: ""
            ws.value(r, 1, admissionNumber)
            
            val firstName: String = student.user.firstName ?: ""
            ws.value(r, 2, firstName)
            
            val lastName: String = student.user.lastName ?: ""
            ws.value(r, 3, lastName)
            
            val email: String = student.user.email ?: ""
            ws.value(r, 4, email)
            
            ws.value(r, 5, student.user.phoneNumber)
            
            val dateOfBirth: String = student.dateOfBirth?.toString() ?: ""
            ws.value(r, 6, dateOfBirth)
            
            val gender: String = student.gender?.toString() ?: ""
            ws.value(r, 7, gender)
        }
        
        wb.finish()
    }
    
    @PostMapping("/classes/{classId}/attendance/save")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'TEACHER')")
    @ResponseBody
    fun saveAttendance(
        @org.springframework.web.bind.annotation.PathVariable classId: UUID,
        @org.springframework.web.bind.annotation.RequestBody request: AttendanceSubmissionRequest,
        authentication: Authentication,
        session: HttpSession,
        model: Model
    ): Map<String, Any> {
        try {
            val userDetails = userDetailsService.loadUserByUsername(authentication.name)
            val customUser = userDetails as com.haneef._school.service.CustomUserDetails
            
            val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
                ?: return mapOf("success" to false, "message" to "School not selected")
            
            // Verify staff has access to this class
            val staff = staffRepository.findByUserIdAndSchoolId(customUser.getUserId()!!, selectedSchoolId)
                ?: return mapOf("success" to false, "message" to "Staff record not found")
            
            // Get current academic session and term
            val currentSession = academicSessionRepository.findBySchoolIdAndIsCurrentSessionAndIsActive(selectedSchoolId, true, true)
            val currentTerm = currentSession?.let { session ->
                termRepository.findByAcademicSessionIdAndIsCurrentTermAndIsActive(session.id!!, true, true).orElse(null)
            }
            
            if (currentSession == null || currentTerm == null) {
                return mapOf("success" to false, "message" to "Current session or term not found")
            }

            val hasAccess = classTeacherRepository.existsByStaffIdAndSchoolClassIdAndAcademicSessionIdAndTermIdAndSchoolIdAndIsActive(
                    staff.id!!, classId, currentSession.id!!, currentTerm.id!!, selectedSchoolId, true
                ) || subjectTeacherRepository.findByStaffIdAndAcademicSessionIdAndTermIdAndIsActive(
                    staff.id!!, currentSession.id!!, currentTerm.id!!, true
                ).any { it.schoolClass.id == classId }
            
            if (!hasAccess) {
                return mapOf("success" to false, "message" to "Access denied to this class")
            }
            
            val schoolClass = schoolClassRepository.findById(classId).orElse(null)
                ?: return mapOf("success" to false, "message" to "Class not found")
            
            val attendanceDateParsed = java.time.LocalDate.parse(request.date)
            
            // Process attendance data
            request.attendance.forEach { (studentIdStr, statusStr) ->
                try {
                    val studentId = UUID.fromString(studentIdStr)
                    val status = com.haneef._school.entity.AttendanceStatus.valueOf(statusStr)
                    
                    // Check if attendance already exists for this date
                    val existingAttendance = attendanceRepository.findByStudentIdAndSchoolClassIdAndAttendanceDateAndSchoolIdAndIsActive(
                        studentId, classId, attendanceDateParsed, selectedSchoolId, true
                    )
                    
                    if (existingAttendance != null) {
                        // Update existing attendance
                        existingAttendance.status = status
                        existingAttendance.staff = staff
                        attendanceRepository.save(existingAttendance)
                    } else {
                        // Create new attendance record
                        val student = studentRepository.findById(studentId).orElse(null)
                        if (student != null) {
                            val attendance = com.haneef._school.entity.Attendance(
                                student = student,
                                schoolClass = schoolClass,
                                staff = staff,
                                attendanceDate = attendanceDateParsed,
                                status = status
                            ).apply {
                                this.schoolId = selectedSchoolId
                            }
                            attendanceRepository.save(attendance)
                        }
                    }
                } catch (e: Exception) {
                    logger.error("Error processing attendance for student $studentIdStr", e)
                }
            }
            
            return mapOf("success" to true, "message" to "Attendance saved successfully!")
            
        } catch (e: Exception) {
            logger.error("Error saving attendance", e)
            return mapOf("success" to false, "message" to "Error saving attendance: ${e.message}")
        }
    }
}

data class AttendanceSubmissionRequest(
    val date: String,
    val attendance: Map<String, String> // studentId -> status
)
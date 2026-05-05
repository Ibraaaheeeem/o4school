package com.haneef._school.controller

import java.util.UUID
import com.haneef._school.entity.*
import com.haneef._school.dto.*
import com.haneef._school.repository.*
import com.haneef._school.service.CustomUserDetails
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
import org.springframework.http.ResponseEntity
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.font.PdfFont
import com.itextpdf.kernel.font.PdfFontFactory
import java.io.File

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
    private val parentRepository: com.haneef._school.repository.ParentRepository,
    private val htmlSanitizerService: com.haneef._school.service.HtmlSanitizerService,
    private val examinationSubmissionRepository: ExaminationSubmissionRepository,
    private val aiService: com.haneef._school.service.AiService,
    private val authorizationService: com.haneef._school.service.AuthorizationService
) {
    private val objectMapper = ObjectMapper().registerModule(com.fasterxml.jackson.module.kotlin.KotlinModule.Builder().build())
    private val logger = LoggerFactory.getLogger(StaffDashboardController::class.java)
    
    // Create a font that supports Arabic and other Unicode characters
    private fun getArabicFont(): com.itextpdf.kernel.font.PdfFont? {
        return try {
            // Try to use DejaVuSans which supports Arabic
            PdfFontFactory.createFont("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", "Identity-H", com.itextpdf.kernel.font.PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED)
        } catch (e: Exception) {
            try {
                // Fallback to Liberation font
                PdfFontFactory.createFont("/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf", "Identity-H", com.itextpdf.kernel.font.PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED)
            } catch (e2: Exception) {
                try {
                    // Fallback to Noto Sans
                    PdfFontFactory.createFont("/usr/share/fonts/opentype/noto/NotoSans-Regular.ttf", "Identity-H", com.itextpdf.kernel.font.PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED)
                } catch (e3: Exception) {
                    logger.warn("Could not load Arabic-supporting font, will use default", e3)
                    null
                }
            }
        }
    }

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
                    model.addAttribute("currentTerm", currentTerm)
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
        
        // Resolve academic context (session/term)
        val (effectiveSession, effectiveTerm) = getEffectiveSessionAndTerm(session, selectedSchoolId)
        
        // Get enrollments with track information, filtered by session and term
        val enrollments = if (effectiveSession != null && effectiveTerm != null) {
            studentClassRepository.findByStudentIdAndAcademicSessionIdAndTermIdWithClassAndTrack(
                studentId, effectiveSession.id!!, effectiveTerm.id!!
            )
        } else {
            // Fallback to all active enrollments if context cannot be fully resolved
            studentClassRepository.findByStudentIdWithClassAndTrack(studentId)
        }
        
        // Get attendance stats
        val attendanceRecords = attendanceRepository.findByStudentIdAndSchoolIdAndIsActive(studentId, selectedSchoolId, true)
        val presentCount = attendanceRecords.count { it.status == AttendanceStatus.PRESENT }
        val absentCount = attendanceRecords.count { it.status == AttendanceStatus.ABSENT }
        val lateCount = attendanceRecords.count { it.status == AttendanceStatus.LATE }
        val totalAttendance = 122
        val attendancePercentage = if (totalAttendance > 0) (presentCount.toDouble() / totalAttendance * 100).toInt() else 0
        
        // Get parents/guardians
        val parentRelationships = parentStudentRepository.findByStudentIdWithParentDetails(studentId)
        
        
        model.addAttribute("student", student)
        model.addAttribute("enrollments", enrollments)
        model.addAttribute("school", school)
        model.addAttribute("attendancePercentage", attendancePercentage)
        model.addAttribute("presentCount", presentCount)
        model.addAttribute("absentCount", absentCount)
        model.addAttribute("lateCount", lateCount)
        model.addAttribute("totalAttendance", totalAttendance)
        model.addAttribute("parentRelationships", parentRelationships)
        
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
                val academicContext = getEffectiveSessionAndTerm(session, selectedSchoolId)
                val currentSession = academicContext.first
                val currentTerm = academicContext.second
                
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
                
                // Get students in this class (Filtered by session and term if possible)
                val studentsPage = if (currentSession != null && currentTerm != null) {
                    studentClassRepository.findBySchoolClassIdAndAcademicSessionIdAndTermIdAndIsActive(
                        classId, currentSession.id!!, currentTerm.id!!, true, org.springframework.data.domain.PageRequest.of(0, 24)
                    )
                } else {
                    studentClassRepository.findBySchoolClassIdAndIsActive(
                        classId, true, org.springframework.data.domain.PageRequest.of(0, 24)
                    )
                }
                
                val studentsList = studentsPage.content.map { it.student }
                model.addAttribute("students", studentsList)
                model.addAttribute("studentsPage", studentsPage) // To access currentPage, totalPages etc.
                model.addAttribute("studentCurrentPage", studentsPage.number)
                model.addAttribute("studentTotalPages", studentsPage.totalPages)
                model.addAttribute("studentTotalItems", studentsPage.totalElements)
                
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
    
    @GetMapping("/classes/{classId}/students")
    fun getClassStudents(
        @org.springframework.web.bind.annotation.PathVariable classId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "24") size: Int,
        model: Model,
        authentication: Authentication,
        session: HttpSession
    ): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
        if (selectedSchoolId != null) {
            val schoolClass = schoolClassRepository.findById(classId).orElse(null)
            if (schoolClass != null && schoolClass.schoolId == selectedSchoolId) {
                
                val academicContext = getEffectiveSessionAndTerm(session, selectedSchoolId)
                val currentSession = academicContext.first
                val currentTerm = academicContext.second

                val studentsPage = if (currentSession != null && currentTerm != null) {
                    studentClassRepository.findBySchoolClassIdAndAcademicSessionIdAndTermIdAndIsActive(
                        classId, currentSession.id!!, currentTerm.id!!, true, org.springframework.data.domain.PageRequest.of(page, size)
                    )
                } else {
                    studentClassRepository.findBySchoolClassIdAndIsActive(
                        classId, true, org.springframework.data.domain.PageRequest.of(page, size)
                    )
                }
                
                model.addAttribute("students", studentsPage.content.map { it.student })
                model.addAttribute("studentsPage", studentsPage)
                model.addAttribute("studentCurrentPage", studentsPage.number)
                model.addAttribute("studentTotalPages", studentsPage.totalPages)
                model.addAttribute("studentTotalItems", studentsPage.totalElements)
                model.addAttribute("schoolClass", schoolClass)
            }
        }
        return "staff/class-details :: students-list-content"
    }

    @GetMapping("/classes/{classId}/assessments")
    fun getClassAssessments(
        @org.springframework.web.bind.annotation.PathVariable classId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "24") size: Int,
        @RequestParam(required = false) examType: String?,
        @RequestParam(required = false) isOnline: Boolean?,
        @RequestParam(required = false) search: String?,
        @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) startDate: java.time.LocalDate?,
        @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) endDate: java.time.LocalDate?,
        model: Model,
        authentication: Authentication,
        session: HttpSession
    ): String {
        // Sanitize inputs
        val sanitizedExamType = if (examType.isNullOrBlank()) null else examType
        val sanitizedSearch = if (search.isNullOrBlank()) null else search
        
        
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
                
                // Parse dates to LocalDateTime if provided
                val startDateTime = startDate?.atStartOfDay()
                val endDateTime = endDate?.atTime(23, 59, 59)

                val pageable = org.springframework.data.domain.PageRequest.of(page, size)
                val subjectIds = if (isClassTeacher) {
                    null
                } else {
                    val subjects = subjectTeacherRepository
                        .findByStaffIdAndAcademicSessionIdAndTermIdAndIsActive(
                            staff.id!!, currentSession.id!!, currentTerm.id!!, true
                        )
                        .filter { it.schoolClass.id == classId }
                        .map { it.subject.id!! }
                    
                    if (subjects.isEmpty()) {
                        // Not a class teacher and no subjects taught -> no access (return empty page)
                        val emptyPage = org.springframework.data.domain.Page.empty<com.haneef._school.entity.Examination>(pageable)
                        model.addAttribute("examinations", emptyPage.content)
                        model.addAttribute("currentPage", 0)
                        model.addAttribute("totalPages", 0)
                        model.addAttribute("totalItems", 0)
                        return "staff/class-assessments :: assessments-content"
                    }
                    subjects
                }

                logger.info("getClassAssessments: classId={}, page={}, size={}", classId, page, size)
                logger.info("getClassAssessments: isClassTeacher={}, subjectIds={}, session={}, term={}", isClassTeacher, subjectIds, currentSession.id, currentTerm.id)

                val examinationsPage = examinationRepository.findBySchoolIdAndAdvancedFilters(
                    selectedSchoolId, true, classId, subjectIds, sanitizedExamType, currentTerm.id, currentSession.id,
                    isOnline, sanitizedSearch, startDateTime, endDateTime, pageable
                )
                logger.info("getClassAssessments: Found {} results", examinationsPage.content.size)
                
                model.addAttribute("examinations", examinationsPage.content)
                model.addAttribute("currentPage", examinationsPage.number)
                model.addAttribute("totalPages", examinationsPage.totalPages)
                model.addAttribute("totalItems", examinationsPage.totalElements)
                
                // Pass filter current values back to view
                model.addAttribute("paramExamType", examType)
                model.addAttribute("paramIsOnline", isOnline)
                model.addAttribute("paramSearch", search)
                model.addAttribute("paramStartDate", startDate)
                model.addAttribute("paramEndDate", endDate)
                
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
        session: HttpSession,
        request: jakarta.servlet.http.HttpServletRequest
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
                // Resolve session and term directly from examination
                val sessionEntity = examination.academicSession
                val termEntity = examination.term
                
                if (sessionEntity == null || termEntity == null) {
                    return "fragments/error :: error-message"
                }
                val isClassTeacher = classTeacherRepository.existsByStaffIdAndSchoolClassIdAndAcademicSessionIdAndTermIdAndSchoolIdAndIsActive(
                    staff.id!!, classId, sessionEntity.id!!, termEntity.id!!, selectedSchoolId, true
                )
                
                val canManageExamination = if (isClassTeacher) {
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
                
                if (canManageExamination) {
                    model.addAttribute("school", school)
                    model.addAttribute("schoolClass", schoolClass)
                    model.addAttribute("examination", examination)
                    model.addAttribute("isClassTeacher", isClassTeacher)
                    model.addAttribute("classId", classId)
                    model.addAttribute("questions", examination.questions)
                    
                    val isHtmx = request.getHeader("HX-Request") != null
                    return if (isHtmx) {
                        "staff/examination-questions :: questions-management-content"
                    } else {
                        "staff/examination-questions-full" 
                    }
                }
            }
        }
        
        val isHtmx = request.getHeader("HX-Request") != null
        return if (isHtmx) {
            "staff/examination-questions :: error-content"
        } else {
             "redirect:/staff/classes"
        }
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
            // Resolve session and term directly from examination
            val sessionEntity = examination.academicSession
            val termEntity = examination.term
            
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
                        explanation = htmlSanitizerService.sanitize(qData.explanation),
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
        @org.springframework.web.bind.annotation.RequestParam(required = false) explanation: String?,
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
            // Resolve session and term directly from examination
            val sessionEntity = examination.academicSession
            val termEntity = examination.term
            
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
                this.explanation = htmlSanitizerService.sanitize(explanation)
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
        val schoolClass = authorizationService.validateAndGetSchoolClass(classId, selectedSchoolId)

        val (effectiveSession, effectiveTerm) = getEffectiveSessionAndTerm(session, selectedSchoolId)
        
        // Get students in this class for the current context
        val studentEnrollments = if (effectiveSession != null && effectiveTerm != null) {
            studentClassRepository.findBySchoolClassIdAndAcademicSessionIdAndTermIdAndIsActive(
                classId, effectiveSession.id!!, effectiveTerm.id!!, true
            )
        } else {
            studentClassRepository.findBySchoolClassIdAndIsActive(classId, true)
        }
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
        val schoolClass = authorizationService.validateAndGetSchoolClass(classId, selectedSchoolId)

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
        val schoolClass = authorizationService.validateAndGetSchoolClass(classId, selectedSchoolId)

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
        val sessionEntity = examination.academicSession
        val termEntity = examination.term
        
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
        
        // 1. Resolve Session Year
        // a. Try parameter
        // b. Try session attribute
        // c. Default to current session
        var effectiveSessionYear = sessionYear
        if (effectiveSessionYear == null) {
            effectiveSessionYear = session.getAttribute("lastSelectedSessionYear") as? String
        }
        
        // Get current session for fallback and default
        val currentSession = academicSessionRepository.findBySchoolIdAndIsCurrentSessionAndIsActive(selectedSchoolId, true, true)
        
        if (effectiveSessionYear == null) {
            effectiveSessionYear = currentSession?.sessionYear
        }
        
        // Save resolved session to http session
        if (effectiveSessionYear != null) {
            session.setAttribute("lastSelectedSessionYear", effectiveSessionYear)
        }

        // Determine session and term entities to use for assignments lookup
        val targetSession = if (effectiveSessionYear != null) {
            academicSessionRepository.findBySchoolIdAndSessionYearAndIsActive(selectedSchoolId, effectiveSessionYear, true)
        } else {
            currentSession
        }
        
        // 2. Resolve Term
        var effectiveTermName = term
        if (effectiveTermName == null) {
            effectiveTermName = session.getAttribute("lastSelectedTermName") as? String
        }

        val availableTerms = if (targetSession != null) {
            termRepository.findByAcademicSessionIdAndIsActiveOrderByStartDate(targetSession.id!!, true)
        } else emptyList()

        val targetTerm = if (!effectiveTermName.isNullOrBlank()) {
            availableTerms.find { it.termName.equals(effectiveTermName, ignoreCase = true) }
        } else if (targetSession?.id == currentSession?.id) {
             termRepository.findByAcademicSessionIdAndIsCurrentTermAndIsActive(targetSession!!.id!!, true, true).orElse(null)
        } else {
            availableTerms.firstOrNull()
        }

        if (targetTerm != null) {
            effectiveTermName = targetTerm.termName
            session.setAttribute("lastSelectedTermName", effectiveTermName)
        }

        val assignedClassIds = if (targetSession != null && targetTerm != null) {
            // Get assignments for the specific session and term
            val classTeacherAssignments = classTeacherRepository.findByStaffIdAndAcademicSessionIdAndTermIdAndIsActive(
                staff.id!!, targetSession.id!!, targetTerm.id!!, true
            )
            val subjectTeacherAssignments = subjectTeacherRepository.findByStaffIdAndAcademicSessionIdAndTermIdAndIsActive(
                staff.id!!, targetSession.id!!, targetTerm.id!!, true
            )
            (classTeacherAssignments.map { it.schoolClass.id } + subjectTeacherAssignments.map { it.schoolClass.id }).filterNotNull().toSet()
        } else {
            emptySet()
        }
        
        val academicSessions = academicSessionRepository.findBySchoolIdAndIsActiveOrderByYearDesc(selectedSchoolId, true)
        val educationTracks = educationTrackRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
        val departments = departmentRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
        val classes = schoolClassRepository.findAllById(assignedClassIds).filter { it.isActive }.sortedBy { it.className }
        val terms = availableTerms.map { it.termName }.ifEmpty { listOf("First Term", "Second Term", "Third Term") }

        // 2. Resolve Class ID
        // a. Try parameter
        // b. Try session attribute
        // c. Default to first available class
        var effectiveClassId = classId
        if (effectiveClassId == null) {
            effectiveClassId = session.getAttribute("lastSelectedClassId") as? UUID
        }
        
        // Validation: Ensure effectiveClassId is actually accessible to the user
        // (User might have switched terms/schools or lost access)
        if (effectiveClassId != null && !classes.any { it.id == effectiveClassId }) {
            effectiveClassId = null
        }
        
        // Fallback: Default to first class if still null
        if (effectiveClassId == null && classes.isNotEmpty()) {
            effectiveClassId = classes[0].id
        }
        
        // Save resolved class to http session
        if (effectiveClassId != null) {
            session.setAttribute("lastSelectedClassId", effectiveClassId)
        }

        model.addAttribute("school", school)
        model.addAttribute("academicSessions", academicSessions)
        model.addAttribute("educationTracks", educationTracks)
        model.addAttribute("departments", departments)
        model.addAttribute("classes", classes)
        model.addAttribute("terms", terms)
        
        model.addAttribute("selectedTrackId", trackId)
        model.addAttribute("selectedDepartmentId", departmentId)
        // Use effective values for view rendering
        model.addAttribute("selectedClassId", effectiveClassId) 
        model.addAttribute("selectedTerm", effectiveTermName)
        model.addAttribute("selectedSession", effectiveSessionYear)
        
        model.addAttribute("showFilters", true)
        model.addAttribute("hideSubjectFilter", true)
        model.addAttribute("hideExamTypeFilter", true)

        // Load students if we have a valid context
        if (effectiveClassId != null && effectiveSessionYear != null) {
             val sessionEntity = if (targetSession != null && targetSession.sessionYear == effectiveSessionYear) {
                 targetSession
             } else {
                 academicSessionRepository.findBySchoolIdAndSessionYearAndIsActive(selectedSchoolId, effectiveSessionYear, true)
             }
             
            if (sessionEntity != null) {
                val enrollments = studentClassRepository.findBySchoolClassIdAndAcademicSessionIdAndIsActive(
                    effectiveClassId, sessionEntity.id!!, true
                )
                model.addAttribute("students", enrollments.map { it.student }.sortedBy { it.user.fullName })
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
            
            // Persist selections to session
            if (classId != null) {
                session.setAttribute("lastSelectedClassId", classId)
            }
            if (sessionYear != null) {
                session.setAttribute("lastSelectedSessionYear", sessionYear)
            }

            // Determine session and term entities to use for assignments lookup
            val targetSession = if (sessionYear != null) {
                academicSessionRepository.findBySchoolIdAndSessionYearAndIsActive(selectedSchoolId, sessionYear, true)
            } else {
                academicSessionRepository.findBySchoolIdAndIsCurrentSessionAndIsActive(selectedSchoolId, true, true)
            }
            
            val targetTerm = if (targetSession != null && !term.isNullOrBlank()) {
                termRepository.findByAcademicSessionIdAndTermNameAndIsActive(targetSession.id!!, term, true).orElse(null)
            } else null

            val assignedClassIds = if (targetSession != null && targetTerm != null) {
                val classTeacherAssignments = classTeacherRepository.findByStaffIdAndAcademicSessionIdAndTermIdAndIsActive(
                    staff.id!!, targetSession.id!!, targetTerm.id!!, true
                )
                val subjectTeacherAssignments = subjectTeacherRepository.findByStaffIdAndAcademicSessionIdAndTermIdAndIsActive(
                    staff.id!!, targetSession.id!!, targetTerm.id!!, true
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
                
                val sessionObj = academicSessionRepository.findBySchoolIdAndSessionYearAndIsActive(selectedSchoolId, sessionYear, true)
                
                if (sessionObj != null && targetTerm != null) {
                    val enrollments = studentClassRepository.findBySchoolClassIdAndAcademicSessionIdAndTermIdAndIsActive(
                        classId, sessionObj.id!!, targetTerm.id!!, true
                    )
                    logger.info("Found {} enrollments for class {} and year {} and term {}", enrollments.size, classId, sessionYear, term)
                    model.addAttribute("students", enrollments.map { it.student })
                } else {
                    logger.warn("Session or Term not found for year: $sessionYear, term: $term")
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

    @GetMapping("/reports/filter-classes")
    @ResponseBody
    fun filterClassesBySessionAndTerm(
        @RequestParam sessionId: String,
        @RequestParam term: String,
        authentication: Authentication,
        session_http: HttpSession
    ): List<Map<String, Any>> {
        return try {
            val userDetails = userDetailsService.loadUserByUsername(authentication.name)
            val customUser = userDetails as com.haneef._school.service.CustomUserDetails
            val selectedSchoolId = session_http.getAttribute("selectedSchoolId") as? UUID 
                ?: throw RuntimeException("School not selected")

            val staff = staffRepository.findByUserIdAndSchoolId(customUser.getUserId()!!, selectedSchoolId)
                ?: throw RuntimeException("Staff record not found")

            // Try to parse sessionId as UUID first, then as session year string
            val academicSession = try {
                val uuid = java.util.UUID.fromString(sessionId)
                academicSessionRepository.findById(uuid).orElse(null)
            } catch (e: IllegalArgumentException) {
                // If not a UUID, try to find by session year
                academicSessionRepository.findBySchoolIdAndSessionYearAndIsActive(selectedSchoolId, sessionId, true)
            } ?: throw RuntimeException("Academic session not found")

            // Find the term in the session
            val termEntity = termRepository.findByAcademicSessionIdAndTermNameAndIsActive(
                academicSession.id!!, term, true
            ).orElse(null) ?: throw RuntimeException("Term not found")

            // Get class teacher assignments for this session and term
            val classTeacherAssignments = classTeacherRepository.findByStaffIdAndAcademicSessionIdAndTermIdAndIsActive(
                staff.id!!, academicSession.id!!, termEntity.id!!, true
            )

            // Get subject teacher assignments for this session and term
            val subjectTeacherAssignments = subjectTeacherRepository.findByStaffIdAndAcademicSessionIdAndTermIdAndIsActive(
                staff.id!!, academicSession.id!!, termEntity.id!!, true
            )

            // Combine and deduplicate class IDs
            val classIds = (classTeacherAssignments.mapNotNull { it.schoolClass.id } + 
                           subjectTeacherAssignments.mapNotNull { it.schoolClass.id }).toSet()

            // Fetch the actual class objects and return as JSON
            schoolClassRepository.findAllById(classIds)
                .filter { it.isActive }
                .sortedBy { it.className }
                .map { mapOf("id" to it.id.toString(), "className" to it.className) }

        } catch (e: Exception) {
            logger.error("Error filtering classes", e)
            emptyList()
        }
    }

    @GetMapping("/reports/class/{classId}/students")
    @ResponseBody
    fun getClassStudentsForReports(
        @org.springframework.web.bind.annotation.PathVariable classId: UUID,
        @RequestParam(required = false) sessionId: String?,
        @RequestParam(required = false) termId: String?,
        authentication: Authentication,
        session_http: HttpSession
    ): List<Map<String, Any>> {
        return try {
            val userDetails = userDetailsService.loadUserByUsername(authentication.name)
            val customUser = userDetails as com.haneef._school.service.CustomUserDetails
            val selectedSchoolId = session_http.getAttribute("selectedSchoolId") as? UUID 
                ?: throw RuntimeException("School not selected")

            val schoolClass = schoolClassRepository.findById(classId).orElse(null)
                ?: throw RuntimeException("Class not found")

            // Parse session and term IDs from request
            var academicSession = if (!sessionId.isNullOrBlank()) {
                try {
                    academicSessionRepository.findById(java.util.UUID.fromString(sessionId)).orElse(null)
                } catch (e: IllegalArgumentException) {
                    null
                }
            } else null

            var term = if (!termId.isNullOrBlank() && academicSession != null) {
                try {
                    termRepository.findById(java.util.UUID.fromString(termId)).orElse(null)
                } catch (e: IllegalArgumentException) {
                    null
                }
            } else null

            // If no session/term provided, use current session/term from HTTP session
            if (academicSession == null || term == null) {
                val (currentSession, currentTerm) = getEffectiveSessionAndTerm(session_http, selectedSchoolId)
                if (academicSession == null) academicSession = currentSession
                if (term == null) term = currentTerm
            }

            // Fetch students enrolled in the class for THIS specific session and term ONLY (no duplicates)
            val students = if (academicSession != null && term != null) {
                studentClassRepository.findBySchoolClassIdAndAcademicSessionIdAndTermIdAndIsActive(
                    classId, academicSession.id!!, term.id!!, true
                ).map { it.student }.distinctBy { it.id } // Remove any duplicates just in case
            } else {
                // Fallback if session/term cannot be resolved
                emptyList()
            }

            // Return students as JSON
            students.map { student ->
                mapOf(
                    "id" to (student.id?.toString() ?: ""),
                    "name" to (student.user?.fullName ?: ""),
                    "admissionNumber" to (student.admissionNumber ?: ""),
                    "user" to mapOf("fullName" to (student.user?.fullName ?: ""))
                )
            }

        } catch (e: Exception) {
            logger.error("Error fetching students for class reports", e)
            emptyList()
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
        val isAdmin = customUser.authorities.any { it.authority in listOf("ROLE_SYSTEM_ADMIN", "ROLE_SCHOOL_ADMIN", "ROLE_ADMIN", "ROLE_PRINCIPAL") }

        val staff = staffRepository.findByUserIdAndSchoolId(customUser.getUserId()!!, selectedSchoolId)
        if (staff == null && !isAdmin) {
            throw RuntimeException("Staff record not found")
        }
        
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
        
        // Check authorization in both requested term AND current active term (header context)
    val (effectiveSession, effectiveTerm) = getEffectiveSessionAndTerm(session_http, selectedSchoolId)
    
    val isClassTeacherForRequested = if (staff != null) {
        classTeacherRepository.existsByStaffIdAndSchoolClassIdAndAcademicSessionIdAndTermIdAndSchoolIdAndIsActive(
            staff.id!!, classId, sessionId, termId, selectedSchoolId, true
        )
    } else false

    val isClassTeacherCurrently = if (staff != null && effectiveSession != null && effectiveTerm != null) {
        classTeacherRepository.existsByStaffIdAndSchoolClassIdAndAcademicSessionIdAndTermIdAndSchoolIdAndIsActive(
            staff.id!!, classId, effectiveSession.id!!, effectiveTerm.id!!, selectedSchoolId, true
        )
    } else false

    val subjectsTaughtInRequested = if (staff != null) {
        subjectTeacherRepository.findByStaffIdAndAcademicSessionIdAndTermIdAndIsActive(
            staff.id!!, sessionId, termId, true
        ).filter { it.schoolClass.id == classId }.map { it.subject.id }
    } else emptyList()

    val subjectsTaughtCurrently = if (staff != null && effectiveSession != null && effectiveTerm != null) {
        subjectTeacherRepository.findByStaffIdAndAcademicSessionIdAndTermIdAndIsActive(
            staff.id!!, effectiveSession.id!!, effectiveTerm.id!!, true
        ).filter { it.schoolClass.id == classId }.map { it.subject.id }
    } else emptyList()

        val isClassTeacher = isClassTeacherForRequested || isClassTeacherCurrently
        val subjectsTaught = (subjectsTaughtInRequested + subjectsTaughtCurrently).distinct()

        val student = authorizationService.validateAndGetStudent(request.studentId, selectedSchoolId)

        val assessment = assessmentRepository.findByStudentIdAndAcademicSessionIdAndTermIdAndSchoolIdAndIsActive(
            request.studentId, sessionId, termId, selectedSchoolId, true
        ).orElseGet {
            val a = Assessment(
                admissionNumber = student.admissionNumber ?: "",
                student = student,
                academicSession = sessionEntity,
                term = termEntity
            )
            a.schoolId = selectedSchoolId
            a
        }
        
        // Only admin or class teacher can update behavioral traits and comments
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
            // Verify staff can grade this subject
            if (!isAdmin && !isClassTeacher && !subjectsTaught.contains(scoreInput.subjectId)) {
                logger.warn("Skipping score for subject {} - User not authorized (isAdmin={}, isClassTeacher={}, subjectsTaught={})", scoreInput.subjectId, isAdmin, isClassTeacher, subjectsTaught)
                return@forEach
            }

            val subject = subjectRepository.findById(scoreInput.subjectId).orElseThrow { RuntimeException("Subject not found") }
            
            val classSubject = classSubjectRepository.findBySchoolClassIdAndSubjectIdAndIsActive(
                classId, scoreInput.subjectId, true
            ) ?: throw RuntimeException("ClassSubject not found for subject ${subject.subjectName}")

            // VALIDATION: Check Maximum Scores
            val schemeJson = classSubject.schoolClass.scoringScheme
            val maxMap = mutableMapOf<String, Int>()
            
            if (!schemeJson.isNullOrBlank()) {
                try {
                    val schemeList = objectMapper.readValue(schemeJson, object : com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Any>>>() {})
                    schemeList.forEach { item ->
                        val name = item["name"] as? String ?: ""
                        val alias = item["alias"] as? String ?: name
                        val max = (item["max"] as? Number)?.toInt() ?: 100
                        if (name.isNotBlank()) maxMap[name] = max
                        if (alias.isNotBlank()) maxMap[alias] = max
                    }
                } catch (e: Exception) {
                    // Fallback to defaults on parse error
                    maxMap["CA"] = 40
                    maxMap["Exam"] = 60
                }
            } else {
                // Default Scheme
                maxMap["CA"] = 40
                maxMap["Exam"] = 60
            }

            // Validate Dynamic Scores
            scoreInput.scores.forEach { (component, score) ->
                if (score != null) {
                    // Match component to maxMap (handling potential casing or partial matches if needed, but strict for now)
                    // The frontend sends "CA", "Exam" usually.
                    // If component not found, default to 100? Or strict error? 
                    // Let's assume 100 for unknown components to allow flexibility, or restrict?
                    // Given the goal is "prevent entering score higher than max", we should be strict if we know the component.
                    
                    val max = maxMap[component] ?: if (component.contains("CA", true)) 40 else if (component.contains("Exam", true)) 60 else 100
                    
                    if (score > max) {
                        throw RuntimeException("Score $score for '$component' in subject '${subject.subjectName}' exceeds maximum of $max")
                    }
                }
            }

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

            // Source of Truth: JSON Map (scoresJson only)
            if (scoreInput.scores.isNotEmpty()) {
                subjectScore.scoresJson = objectMapper.writeValueAsString(scoreInput.scores)
            } else {
                // Fallback for legacy inputs if JSON map is empty
                val legacyScores = mutableMapOf<String, Int?>()
                if (scoreInput.ca1 != null) legacyScores["1st CA"] = scoreInput.ca1
                if (scoreInput.ca2 != null) legacyScores["2nd CA"] = scoreInput.ca2
                if (scoreInput.exam != null) legacyScores["Exam"] = scoreInput.exam
                
                if (legacyScores.isNotEmpty()) {
                    subjectScore.scoresJson = objectMapper.writeValueAsString(legacyScores)
                } else {
                    subjectScore.scoresJson = null
                }
            }
            
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
        val hasAdministrativeAccess = customUser.hasRole("ADMIN") || 
                                     customUser.hasRole("SCHOOL_ADMIN") || 
                                     customUser.hasRole("SYSTEM_ADMIN") || 
                                     customUser.hasRole("PRINCIPAL")

        val staff = staffRepository.findByUserIdAndSchoolId(customUser.getUserId()!!, selectedSchoolId)
        if (staff == null && !hasAdministrativeAccess) {
            throw RuntimeException("Staff record not found")
        }
        
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
        
        logger.info("Checking access for Staff: ${staff?.id}, Class: $classId, Session: ${sessionEntity.id}, Term: ${termEntity.id}")

        // Administrative bypass check is now handled above via hasAdministrativeAccess
        
        var isClassTeacher = false
        var allowedSubjectIds: Set<UUID>? = null

        if (hasAdministrativeAccess) {
            logger.info("Granting administrative access for user: ${customUser.username}")
            isClassTeacher = true
        } else {
            // Verify staff has access to this class (Original requested term)
            val isClassTeacherForRequestedTerm = if (staff != null) {
                classTeacherRepository.existsByStaffIdAndSchoolClassIdAndAcademicSessionIdAndTermIdAndSchoolIdAndIsActive(
                    staff.id!!, classId, sessionEntity.id!!, termEntity.id!!, selectedSchoolId, true
                )
            } else false
            
            val subjectsTaughtInRequestedTerm = if (staff != null) {
                subjectTeacherRepository.findByStaffIdAndAcademicSessionIdAndTermIdAndIsActive(
                    staff.id!!, sessionEntity.id!!, termEntity.id!!, true
                ).filter { it.schoolClass.id == classId }.map { it.subject.id }
            } else emptyList()

            // Secondary check: Are they assigned to this class in the CURRENT active context?
            val (effectiveSession, effectiveTerm) = getEffectiveSessionAndTerm(session_http, selectedSchoolId)
            
            val isCurrentlyClassTeacher = if (staff != null && effectiveSession != null && effectiveTerm != null) {
                classTeacherRepository.existsByStaffIdAndSchoolClassIdAndAcademicSessionIdAndTermIdAndSchoolIdAndIsActive(
                    staff.id!!, classId, effectiveSession.id!!, effectiveTerm.id!!, selectedSchoolId, true
                )
            } else false
            
            val currentlyTaughtSubjects = if (staff != null && effectiveSession != null && effectiveTerm != null) {
                subjectTeacherRepository.findByStaffIdAndAcademicSessionIdAndTermIdAndIsActive(
                    staff.id!!, effectiveSession.id!!, effectiveTerm.id!!, true
                ).filter { it.schoolClass.id == classId }.map { it.subject.id }
            } else emptyList()

            isClassTeacher = isClassTeacherForRequestedTerm || isCurrentlyClassTeacher
            
            if (!isClassTeacher) {
                allowedSubjectIds = (subjectsTaughtInRequestedTerm + currentlyTaughtSubjects).filterNotNull().toSet()
            }

            val isAuthorized = isClassTeacher || (allowedSubjectIds != null && allowedSubjectIds.isNotEmpty())

            logger.info("Access Check Result - Manual Check (Requested Term): ${(isClassTeacherForRequestedTerm || subjectsTaughtInRequestedTerm.isNotEmpty())}, Current Context Check: ${(isCurrentlyClassTeacher || currentlyTaughtSubjects.isNotEmpty())}")

            if (!isAuthorized) {
                logger.error("ACCESS DENIED: Staff ${staff?.id} is neither a class teacher nor a subject teacher for Class $classId in requested Context (Session ${sessionEntity.id}, Term ${termEntity.id})")
                throw RuntimeException("Access denied to this class")
            }
        }

        val student = authorizationService.validateAndGetStudent(studentId, selectedSchoolId)
        
        // Get all subjects for this class
        val classSubjects = classSubjectRepository.findBySchoolClassIdAndIsActive(classId, true)
        
        // Filter subjects based on staff role/assignments
        val filteredClassSubjects = if (allowedSubjectIds != null) {
            classSubjects.filter { allowedSubjectIds.contains(it.subject.id) }
        } else {
            classSubjects
        }
        
        // Get existing assessment if any
        val assessmentOpt = assessmentRepository.findByStudentIdAndAcademicSessionIdAndTermIdAndSchoolIdAndIsActive(
            studentId, sessionEntity.id!!, termEntity.id!!, selectedSchoolId, true
        )

        val assessment = assessmentOpt.orElse(null)

        // Calculate class statistics and rankings for each subject
        val classStatistics = mutableMapOf<UUID, Map<String, Any?>>() // Map of subjectId -> {highest, lowest, average, rankings}
        val studentRankings = mutableMapOf<UUID, MutableMap<UUID, String>>() // Map of subjectId -> Map of studentId -> rankingString
        
        // Get ONLY students enrolled in THIS specific session and term to avoid duplicates
        val allClassStudents = studentClassRepository.findBySchoolClassIdAndAcademicSessionIdAndTermIdAndIsActive(
            classId, sessionEntity.id!!, termEntity.id!!, true
        )
        
        for (subject in filteredClassSubjects) {
            val subjectScores = mutableListOf<Int>()
            val scoreToStudentMap = mutableMapOf<Int, MutableList<UUID>>() // Score -> List of StudentIds with that score
            
            // For each student in class, find their score for this subject
            for (classStudent in allClassStudents) {
                val studentAssessment = assessmentRepository.findByStudentIdAndAcademicSessionIdAndTermIdAndSchoolIdAndIsActive(
                    classStudent.student.id!!, sessionEntity.id!!, termEntity.id!!, selectedSchoolId, true
                )
                if (studentAssessment.isPresent) {
                    val subjectScore = subjectScoreRepository.findByAssessmentIdAndSubjectIdAndSchoolIdAndIsActive(
                        studentAssessment.get().id!!, subject.subject.id!!, selectedSchoolId, true
                    )
                    if (subjectScore.isNotEmpty()) {
                        val total = subjectScore[0].getTotalScore()
                        if (total != null) {
                            subjectScores.add(total)
                            // Map scores to students for ranking
                            scoreToStudentMap.computeIfAbsent(total) { mutableListOf() }.add(classStudent.student.id!!)
                        }
                    }
                }
            }
            
            // Calculate statistics
            if (subjectScores.isNotEmpty()) {
                val highest = subjectScores.maxOrNull()
                val lowest = subjectScores.minOrNull()
                val average = subjectScores.average()
                
                classStatistics[subject.subject.id!!] = mapOf(
                    "highest" to highest,
                    "lowest" to lowest,
                    "average" to average
                )
                
                // Calculate rankings with tie handling
                val sortedScores = subjectScores.distinct().sortedDescending()
                var position = 1
                val subjectRankings = mutableMapOf<UUID, String>()
                
                for (score in sortedScores) {
                    val studentsWithThisScore = scoreToStudentMap[score] ?: emptyList()
                    val positionString = when {
                        position == 1 -> "1st"
                        position == 2 -> "2nd"
                        position == 3 -> "3rd"
                        else -> "${position}th"
                    }
                    
                    // Assign this position to all students with this score
                    for (studentId in studentsWithThisScore) {
                        subjectRankings[studentId] = positionString
                    }
                    
                    // Increment position by the number of students with this score (tie handling)
                    position += studentsWithThisScore.size
                }
                
                studentRankings[subject.subject.id!!] = subjectRankings
            }
        }

        val subjectDataList = filteredClassSubjects.map { cs ->
            var ca1: Int? = null
            var ca2: Int? = null
            var exam: Int? = null
            var total: Int? = null
            var grade: String? = null
            var remark: String? = null

            var scoresMap = mutableMapOf<String, Int?>()

            if (assessment != null) {
                val subjectScores = subjectScoreRepository.findByAssessmentIdAndSubjectIdAndSchoolIdAndIsActive(
                    assessment.id!!, cs.subject.id!!, selectedSchoolId, true
                )
                if (subjectScores.isNotEmpty()) {
                    val ss = subjectScores[0]
                    total = ss.getTotalScore()
                    grade = ss.grade
                    remark = ss.remark
                    
                    if (!ss.scoresJson.isNullOrBlank()) {
                        try {
                            scoresMap = objectMapper.readValue(ss.scoresJson, object : com.fasterxml.jackson.core.type.TypeReference<MutableMap<String, Int?>>() {})
                            // Extract legacy variables from map for DTO response
                            for ((key, value) in scoresMap) {
                                val keyLower = key.lowercase()
                                if (keyLower.contains("ca 1") || keyLower.contains("ca1") || keyLower.contains("1st ca")) ca1 = value ?: ca1
                                else if (keyLower.contains("ca 2") || keyLower.contains("ca2") || keyLower.contains("2nd ca")) ca2 = value ?: ca2
                                else if (keyLower.contains("exam")) exam = value ?: exam
                            }
                        } catch (e: Exception) {
                            println("Error parsing scoresJson for subject ${cs.subject.subjectName}: ${e.message}")
                        }
                    }
                }
            }
            
            // Get class statistics for this subject
            val stats = classStatistics[cs.subject.id]
            val highestScore = stats?.get("highest") as? Int
            val lowestScore = stats?.get("lowest") as? Int
            val averageScore = stats?.get("average") as? Double
            
            // Get student's class position for this subject
            val classPosition = studentRankings[cs.subject.id]?.get(studentId)

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
                scores = scoresMap,
                highestScore = highestScore,
                lowestScore = lowestScore,
                averageScore = averageScore,
                classPosition = classPosition
            )
        }

        // Get class details directly from the requested classId
        val schoolClass = schoolClassRepository.findById(classId).orElseThrow { RuntimeException("Class not found") }
        
        val className = schoolClass.className
        val trackName = schoolClass.department?.track?.name ?: "Unknown Track"
        
        // Get school info
        val school = schoolRepository.findById(selectedSchoolId).orElse(null)
        val schoolName = school?.name ?: "School"
        val schoolLogoUrl = school?.logoUrl
        val schoolAddress = buildString {
            school?.addressLine1?.let { append(it) }
            if (!school?.addressLine1.isNullOrBlank() && !school?.addressLine2.isNullOrBlank()) append(", ")
            school?.addressLine2?.let { append(it) }
        }

        // Calculate summary statistics - only from subjects with valid (non-zero) totals
        val subjectsWithValidTotals = subjectDataList.filter { subject -> subject.total != null && (subject.total ?: 0) > 0 }
        val totals = subjectsWithValidTotals.mapNotNull { it.total }.map { it.toDouble() }
        
        // Return null instead of 0 when no valid totals
        val totalScore = if (totals.isNotEmpty()) totals.sum() else null
        val totalAverage = if (totals.isNotEmpty()) totalScore!! / totals.size else null
        
        // For highest and lowest scores: average of highest/lowest per subject (include zeros in min/max)
        // Collect the highest and lowest score for each subject
        val highestScoresPerSubject = mutableListOf<Double>()
        val lowestScoresPerSubject = mutableListOf<Double>()
        
        for (subject in subjectsWithValidTotals) {
            val scores = listOfNotNull(
                subject.ca1?.toDouble(),
                subject.ca2?.toDouble(),
                subject.exam?.toDouble()
            )
            
            if (scores.isNotEmpty()) {
                highestScoresPerSubject.add(scores.maxOrNull() ?: 0.0)
                lowestScoresPerSubject.add(scores.minOrNull() ?: 0.0)
            }
        }
        
        // Calculate averages of the highest and lowest scores across all subjects
        val highestScoresAvg = if (highestScoresPerSubject.isNotEmpty()) 
            highestScoresPerSubject.average() else null
        val lowestScoresAvg = if (lowestScoresPerSubject.isNotEmpty()) 
            lowestScoresPerSubject.average() else null  
        
        // Determine performance grade based on TOTAL AVERAGE
        val performanceGrade = if (totalAverage != null) {
            when {
                totalAverage >= 90 -> "A"
                totalAverage >= 80 -> "B"
                totalAverage >= 70 -> "C"
                totalAverage >= 60 -> "D"
                totalAverage >= 50 -> "E"
                else -> "F"
            }
        } else {
            null // Return null when no valid totals
        }

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
            headTeacherComment = assessment?.headTeacherComment,
            schoolName = schoolName,
            schoolLogoUrl = schoolLogoUrl,
            schoolAddress = schoolAddress,
            studentPassportPhotoUrl = student.passportPhotoUrl,
            sessionName = session,
            termName = term,
            totalScore = totalScore,
            totalAverage = totalAverage,
            highestScoresAvg = highestScoresAvg,
            lowestScoresAvg = lowestScoresAvg,
            performanceGrade = performanceGrade
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
        val isAdmin = customUser.authorities.any { it.authority in listOf("ROLE_SYSTEM_ADMIN", "ROLE_SCHOOL_ADMIN", "ROLE_ADMIN", "ROLE_PRINCIPAL") }

        val staff = staffRepository.findByUserIdAndSchoolId(customUser.getUserId()!!, selectedSchoolId)
        if (staff == null && !isAdmin) {
            throw RuntimeException("Staff record not found")
        }
        val sessionEntity = academicSessionRepository.findBySchoolIdAndSessionYearAndIsActive(selectedSchoolId, request.session, true)
            ?: throw RuntimeException("Session not found")
        val termEntity = termRepository.findByAcademicSessionIdAndTermNameAndIsActive(sessionEntity.id!!, request.term, true)
            .orElseThrow { RuntimeException("Term not found") }

        // Check authorization in both requested term AND current active term (header context)
        val (effectiveSession, effectiveTerm) = getEffectiveSessionAndTerm(session_http, selectedSchoolId)
        
        val isClassTeacherForRequested = if (staff != null) {
            classTeacherRepository.existsByStaffIdAndSchoolClassIdAndAcademicSessionIdAndTermIdAndSchoolIdAndIsActive(
                staff.id!!, request.classId, sessionEntity.id!!, termEntity.id!!, selectedSchoolId, true
            )
        } else false
        
        val isClassTeacherCurrently = if (staff != null && effectiveSession != null && effectiveTerm != null) {
            classTeacherRepository.existsByStaffIdAndSchoolClassIdAndAcademicSessionIdAndTermIdAndSchoolIdAndIsActive(
                staff.id!!, request.classId, effectiveSession.id!!, effectiveTerm.id!!, selectedSchoolId, true
            )
        } else false

        val subjectsTaughtInRequested = if (staff != null) {
            subjectTeacherRepository.findByStaffIdAndAcademicSessionIdAndTermIdAndIsActive(
                staff.id!!, sessionEntity.id!!, termEntity.id!!, true
            ).filter { it.schoolClass.id == request.classId }.map { it.subject.id }
        } else emptyList()

        val subjectsTaughtCurrently = if (staff != null && effectiveSession != null && effectiveTerm != null) {
            subjectTeacherRepository.findByStaffIdAndAcademicSessionIdAndTermIdAndIsActive(
                staff.id!!, effectiveSession.id!!, effectiveTerm.id!!, true
            ).filter { it.schoolClass.id == request.classId }.map { it.subject.id }
        } else emptyList()

        val isClassTeacher = isClassTeacherForRequested || isClassTeacherCurrently
        val subjectsTaught = (subjectsTaughtInRequested + subjectsTaughtCurrently).distinct()
        if (!isAdmin && !isClassTeacher && subjectsTaught.isEmpty()) {
            throw RuntimeException("Access denied to this class")
        }

        val studentId = request.studentId
        val students = if (studentId != null) {
            listOf(studentRepository.findById(studentId).orElseThrow { RuntimeException("Student not found") })
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
            val assessment = assessmentRepository.findByStudentIdAndAcademicSessionIdAndTermIdAndSchoolIdAndIsActive(
                student.id!!, sessionEntity.id!!, termEntity.id!!, selectedSchoolId, true
            ).orElseGet {
                val a = Assessment(
                    admissionNumber = student.admissionNumber ?: "",
                    student = student,
                    academicSession = sessionEntity,
                    term = termEntity
                )
                a.schoolId = selectedSchoolId
                a
            }
            assessmentRepository.save(assessment)

            val classSubjects = classSubjectRepository.findBySchoolClassIdAndIsActive(request.classId, true)
            
            classSubjects.forEach { cs ->
                // Verify staff can import for this subject
                if (!isAdmin && !isClassTeacher && !subjectsTaught.contains(cs.subject.id)) {
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

                // Resolve IDs first
                // sessionEntity and termEntity are already resolved at the top of the method (lines 1562, 1564)
                
                request.sources.forEach { source ->
                    val exams = examinationRepository.findBySubjectIdAndSchoolClassIdAndTermIdAndAcademicSessionIdAndExamTypeAndIsActive(
                        cs.subject.id!!, request.classId, termEntity.id!!, sessionEntity.id!!, source.examType, true
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

    // Parent-accessible assessment data fetching (bypasses staff authorization)
    private fun getParentAccessibleAssessmentData(
        studentId: UUID,
        classId: UUID,
        session: String,
        term: String,
        schoolId: UUID
    ): AssessmentReportData {
        // Resolve session and term
        val sessionEntity = academicSessionRepository.findBySchoolIdAndSessionYearAndIsActive(schoolId, session, true)
            ?: throw RuntimeException("Session not found")
        
        val sessionTerms = termRepository.findByAcademicSessionIdAndIsActiveOrderByStartDate(sessionEntity.id!!, true)
        val termEntity = sessionTerms.find { it.termName.equals(term, ignoreCase = true) }
            ?: throw RuntimeException("Term '$term' not found in session '$session'")
        
        // Get student, class, and school info
        val student = studentRepository.findById(studentId)
            .orElseThrow { RuntimeException("Student not found") }
        val schoolClass = schoolClassRepository.findById(classId)
            .orElseThrow { RuntimeException("Class not found") }
        val school = schoolRepository.findById(schoolId).orElse(null)
        
        // Get all subjects for this class
        val classSubjects = classSubjectRepository.findBySchoolClassIdAndIsActive(classId, true)
        
        // Get assessment for this student/session/term
        val assessmentOpt = assessmentRepository.findByStudentIdAndAcademicSessionIdAndTermIdAndSchoolIdAndIsActive(
            studentId, sessionEntity.id!!, termEntity.id!!, schoolId, true
        )
        val assessment = assessmentOpt.orElse(null)
        
        // Build subject data
        val subjectDataList = classSubjects.map { classSubject ->
            var ca1: Int? = null
            var ca2: Int? = null
            var exam: Int? = null
            var total: Int? = null
            var grade: String? = null
            var remark: String? = null
            var scoresMap = mutableMapOf<String, Int?>()
            
            if (assessment != null) {
                val subjectScores = subjectScoreRepository.findByAssessmentIdAndSubjectIdAndSchoolIdAndIsActive(
                    assessment.id!!, classSubject.subject.id!!, schoolId, true
                )
                if (subjectScores.isNotEmpty()) {
                    val ss = subjectScores[0]
                    total = ss.getTotalScore()
                    grade = ss.grade
                    remark = ss.remark
                    
                    if (!ss.scoresJson.isNullOrBlank()) {
                        try {
                            scoresMap = objectMapper.readValue(ss.scoresJson, object : com.fasterxml.jackson.core.type.TypeReference<MutableMap<String, Int?>>() {})
                            for ((key, value) in scoresMap) {
                                val keyLower = key.lowercase()
                                if (keyLower.contains("ca 1") || keyLower.contains("ca1") || keyLower.contains("1st ca")) ca1 = value ?: ca1
                                else if (keyLower.contains("ca 2") || keyLower.contains("ca2") || keyLower.contains("2nd ca")) ca2 = value ?: ca2
                                else if (keyLower.contains("exam")) exam = value ?: exam
                            }
                        } catch (e: Exception) {
                            logger.warn("Error parsing scoresJson for subject ${classSubject.subject.subjectName}: ${e.message}")
                        }
                    }
                }
            }
            
            SubjectAssessmentData(
                subjectId = classSubject.subject.id!!,
                subjectName = classSubject.subject.subjectName,
                ca1 = ca1,
                ca2 = ca2,
                exam = exam,
                total = total,
                grade = grade,
                remark = remark,
                scoringScheme = schoolClass.scoringScheme,
                scores = scoresMap
            )
        }
        
        // Calculate summary statistics
        val subjectsWithValidTotals = subjectDataList.filter { it.total != null && (it.total ?: 0) > 0 }
        val totals = subjectsWithValidTotals.mapNotNull { it.total }.map { it.toDouble() }
        
        val totalScore = if (totals.isNotEmpty()) totals.sum() else null
        val totalAverage = if (totals.isNotEmpty()) totalScore!! / totals.size else null
        
        val performanceGrade = if (totalAverage != null) {
            when {
                totalAverage >= 90 -> "A"
                totalAverage >= 80 -> "B"
                totalAverage >= 70 -> "C"
                totalAverage >= 60 -> "D"
                totalAverage >= 50 -> "E"
                else -> "F"
            }
        } else {
            null
        }
        
        return AssessmentReportData(
            studentId = studentId,
            studentName = student.user.fullName ?: "User",
            admissionNumber = student.admissionNumber ?: "",
            className = schoolClass.className,
            trackName = schoolClass.department?.track?.name ?: "Unknown Track",
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
            headTeacherComment = assessment?.headTeacherComment,
            schoolName = school?.name ?: "School",
            schoolLogoUrl = school?.logoUrl,
            schoolAddress = buildString {
                school?.addressLine1?.let { append(it) }
                if (!school?.addressLine1.isNullOrBlank() && !school?.addressLine2.isNullOrBlank()) append(", ")
                school?.addressLine2?.let { append(it) }
            },
            studentPassportPhotoUrl = student.passportPhotoUrl,
            sessionName = sessionEntity.sessionYear,
            termName = termEntity.termName,
            totalScore = totalScore,
            totalAverage = totalAverage,
            performanceGrade = performanceGrade
        )
    }

    // Helper method for resolving session/term context
    private fun getEffectiveSessionAndTerm(session: HttpSession, schoolId: UUID): Pair<AcademicSession?, Term?> {
        val selectedSessionId = session.getAttribute("selectedSessionId") as? UUID
        val selectedTermId = session.getAttribute("selectedTermId") as? UUID
        
        // 1. Try to get from session attributes (User selection)
        var effectiveSession = if (selectedSessionId != null) {
            academicSessionRepository.findById(selectedSessionId).orElse(null)
        } else {
            null
        }
        
        var effectiveTerm = if (selectedTermId != null) {
            termRepository.findById(selectedTermId).orElse(null)
        } else {
            null
        }

        // 2. If not selected, fallback to current session/term
        if (effectiveSession == null) {
            val sessions = academicSessionRepository.findBySchoolIdAndIsActiveOrderByYearDesc(schoolId, true)
            effectiveSession = sessions.find { it.isCurrentSession } ?: sessions.firstOrNull()
        }
        
        if (effectiveTerm == null && effectiveSession != null) {
             val terms = termRepository.findByAcademicSessionIdAndIsActiveOrderByStartDate(effectiveSession.id!!, true)
             effectiveTerm = terms.find { it.isCurrentTerm } ?: terms.firstOrNull()
        }
        
        return Pair(effectiveSession, effectiveTerm)
    }

    @GetMapping("/classes/{classId}/assessments/new")
    fun getNewClassAssessmentModal(
        @PathVariable classId: UUID,
        model: Model,
        authentication: Authentication,
        session: HttpSession
    ): String {
        val userDetails = userDetailsService.loadUserByUsername(authentication.name)
        val customUser = userDetails as com.haneef._school.service.CustomUserDetails
        
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/select-school"
            
        // Security Check: Ensure class belongs to the selected school
        val schoolClass = schoolClassRepository.findById(classId).orElse(null)
        if (schoolClass == null || schoolClass.schoolId != selectedSchoolId) {
            return "fragments/error :: error-message"
        }

        // Get staff record
        val staff = staffRepository.findByUserIdAndSchoolId(customUser.getUserId()!!, selectedSchoolId)
        if (staff == null || !staff.isActive) {
             return "fragments/error :: error-message"
        }
        
        // Get effective session and term
        val (effectiveSession, effectiveTerm) = getEffectiveSessionAndTerm(session, selectedSchoolId)
        
        if (effectiveSession == null || effectiveTerm == null) {
            model.addAttribute("error", "No active session or term found.")
            return "fragments/error :: error-message"
        }

        // Determine permissions and locked subjects
        val isClassTeacher = classTeacherRepository
            .existsByStaffIdAndSchoolClassIdAndAcademicSessionIdAndTermIdAndSchoolIdAndIsActive(
                staff.id!!, classId, effectiveSession.id!!, effectiveTerm.id!!, selectedSchoolId, true
            )
            
        val assignedSubjects = subjectTeacherRepository
            .findByStaffIdAndAcademicSessionIdAndTermIdAndIsActive(
                staff.id!!, effectiveSession.id!!, effectiveTerm.id!!, true
            )
            .filter { it.schoolClass.id == classId }
            .map { it.subject }

        val subjects = if (assignedSubjects.isNotEmpty()) {
            assignedSubjects
        } else if (isClassTeacher) {
            classSubjectRepository.findBySchoolClassIdAndIsActive(classId, true).map { it.subject }
        } else {
            emptyList()
        }

        val lockedSubject = if (isClassTeacher) {
            null // Class teacher can select any subject
        } else {
            if (subjects.size == 1) subjects.first() else null
        }
        
        // Prepare model for the modal
        model.addAttribute("user", customUser.user)
        model.addAttribute("examination", Examination().apply { 
            this.schoolClass = schoolClass 
            if (lockedSubject != null) this.subject = lockedSubject
        })
        
        // Required lists for the modal
        val academicSessions = listOf(effectiveSession) // Lock to current/selected session
        val educationTracks = listOf(schoolClass.track) // Lock to class track
        
        model.addAttribute("academicSessions", academicSessions)
        model.addAttribute("educationTracks", educationTracks)
        model.addAttribute("examTypes", listOf("Assignment", "Continuous Assessment", "Mid-Term Test", "End-of-Term Examination"))
        val termNames = listOf("First Term", "Second Term", "Third Term")
        model.addAttribute("terms", if (termNames.contains(effectiveTerm.termName)) listOf(effectiveTerm.termName) else termNames)
        model.addAttribute("isEdit", false)
        
        // Staff-specific context variables
        model.addAttribute("isStaffMode", true)
        model.addAttribute("lockedClass", schoolClass)
        model.addAttribute("lockedSubject", lockedSubject)
        model.addAttribute("headerContextTerm", effectiveTerm) 
        model.addAttribute("isCurrentTermSelected", effectiveTerm.isCurrentTerm) 
        model.addAttribute("formAction", "/staff/classes/$classId/assessments/save-htmx") 
        model.addAttribute("subjects", subjects) 

        return "admin/assessments/examination-modal"
    }

    @GetMapping("/classes/{classId}/assessments/{examId}/edit")
    fun getEditClassAssessmentModal(
        @PathVariable classId: UUID,
        @PathVariable examId: UUID,
        model: Model,
        authentication: Authentication,
        session: HttpSession
    ): String {
        val userDetails = userDetailsService.loadUserByUsername(authentication.name)
        val customUser = userDetails as com.haneef._school.service.CustomUserDetails
        
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/select-school"
            
        val examination = examinationRepository.findById(examId).orElse(null)
            ?: return "fragments/error :: error-message"
            
        if (examination.schoolId != selectedSchoolId || examination.schoolClass.id != classId) {
             return "fragments/error :: error-message"
        }

        // Staff validation
        val staff = staffRepository.findByUserIdAndSchoolId(customUser.getUserId()!!, selectedSchoolId)
        if (staff == null || !staff.isActive) {
             return "fragments/error :: error-message"
        }
        
        // Check permissions
        val isClassTeacher = classTeacherRepository
            .existsByStaffIdAndSchoolClassIdAndAcademicSessionIdAndTermIdAndSchoolIdAndIsActive(
                staff.id!!, classId, examination.academicSession!!.id!!, examination.term!!.id!!, selectedSchoolId, true
            )
            
        val canEdit = if (isClassTeacher) {
            true
        } else {
             val subjectsTaught = subjectTeacherRepository
                .findByStaffIdAndAcademicSessionIdAndTermIdAndIsActive(
                    staff.id!!, examination.academicSession!!.id!!, examination.term!!.id!!, true
                )
                .filter { it.schoolClass.id == classId }
                .map { it.subject.id }
             subjectsTaught.contains(examination.subject.id)
        }
        
        if (!canEdit) {
            return "fragments/error :: error-message"
        }

        // Prepare locked context
        val assignedSubjects = subjectTeacherRepository
            .findByStaffIdAndAcademicSessionIdAndTermIdAndIsActive(
                staff.id!!, examination.academicSession!!.id!!, examination.term!!.id!!, true
            )
            .filter { it.schoolClass.id == classId }
            .map { it.subject }

        val subjects = if (assignedSubjects.isNotEmpty()) {
            assignedSubjects
        } else if (isClassTeacher) {
            classSubjectRepository.findBySchoolClassIdAndIsActive(classId, true).map { it.subject }
        } else {
            emptyList()
        }

        val lockedSubject = if (!isClassTeacher) examination.subject else null
        
        model.addAttribute("user", customUser.user)
        model.addAttribute("examination", examination)
        
        model.addAttribute("academicSessions", listOf(examination.academicSession))
        model.addAttribute("educationTracks", listOf(examination.schoolClass.track))
        model.addAttribute("examTypes", listOf("Assignment", "Continuous Assessment", "Mid-Term Test", "End-of-Term Examination"))
        model.addAttribute("terms", listOf("First Term", "Second Term", "Third Term")) 
        model.addAttribute("isEdit", true)
        
        model.addAttribute("isStaffMode", true)
        model.addAttribute("lockedClass", examination.schoolClass)
        model.addAttribute("lockedSubject", lockedSubject)
        
        val (_, effectiveTerm) = getEffectiveSessionAndTerm(session, selectedSchoolId)
        model.addAttribute("headerContextTerm", effectiveTerm) 
        model.addAttribute("isCurrentTermSelected", effectiveTerm?.isCurrentTerm == true) 
        model.addAttribute("formAction", "/staff/classes/$classId/assessments/save-htmx") 
        model.addAttribute("subjects", subjects) 

        return "admin/assessments/examination-modal"
    }

    @PostMapping("/classes/{classId}/assessments/save-htmx")
    fun saveStaffAssessmentHtmx(
        @PathVariable classId: UUID,
        @ModelAttribute examinationDto: ExaminationDto,
        authentication: Authentication,
        session: HttpSession,
        model: Model,
        response: jakarta.servlet.http.HttpServletResponse
    ): String {
        val customUser = authentication.principal as CustomUserDetails
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "fragments/error :: error-message"

        try {
            val subject = subjectRepository.findById(examinationDto.subjectId).orElseThrow()
            val schoolClass = schoolClassRepository.findById(classId).orElseThrow()

            if (schoolClass.schoolId != selectedSchoolId) {
                return "fragments/error :: error-message"
            }

            // Get staff and verify permissions
            val staff = staffRepository.findByUserIdAndSchoolId(customUser.getUserId()!!, selectedSchoolId)
                ?: return "fragments/error :: error-message"

            val (effectiveSession, effectiveTerm) = getEffectiveSessionAndTerm(session, selectedSchoolId)
            val currentTerm = effectiveTerm ?: throw RuntimeException("No active term found")
            val currentSession = effectiveSession ?: throw RuntimeException("No active session found")

            // Basic permission check
            val isClassTeacher = classTeacherRepository.existsByStaffIdAndSchoolClassIdAndAcademicSessionIdAndTermIdAndSchoolIdAndIsActive(
                staff.id!!, classId, currentSession.id!!, currentTerm.id!!, selectedSchoolId, true
            )
            
            // Allow subject teachers to proceed if they teach a subject in the class (simplified check, real app might be stricter)
            // Ideally we check if they teach THE subject of the exam if not class teacher.
            
            val examId = examinationDto.id
            if (examId != null) {
                // Update
                val existingExamination = examinationRepository.findById(examId).orElseThrow()
                if (existingExamination.schoolId != selectedSchoolId) return "fragments/error :: error-message"

                existingExamination.apply {
                    this.title = examinationDto.title
                    this.examType = examinationDto.examType
                    this.isOnline = examinationDto.isOnline
                    this.subject = subject
                    this.schoolClass = schoolClass
                    this.durationMinutes = examinationDto.durationMinutes
                    this.totalMarks = examinationDto.totalMarks
                    val formatter = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                    this.startTime = if (!examinationDto.startTime.isNullOrBlank()) {
                        try {
                            java.time.LocalDateTime.parse(examinationDto.startTime, formatter)
                        } catch (e: Exception) {
                             try { java.time.LocalDateTime.parse(examinationDto.startTime) } catch (e2: Exception) { null }
                        }
                    } else null
                    
                    this.endTime = if (!examinationDto.endTime.isNullOrBlank()) {
                         try {
                            java.time.LocalDateTime.parse(examinationDto.endTime, formatter)
                        } catch (e: Exception) {
                             try { java.time.LocalDateTime.parse(examinationDto.endTime) } catch (e2: Exception) { null }
                        }
                    } else null
                    this.isPublished = examinationDto.isPublished
                    // term and session usually stay consistent with creation time context or updated context? 
                    // Let's assume we keep them or update if logic dictates. 
                    this.term = currentTerm
                    this.academicSession = currentSession
                }
                examinationRepository.save(existingExamination)
                model.addAttribute("examination", existingExamination)
                model.addAttribute("classId", classId)
                model.addAttribute("isNew", false)
            } else {
                // Create
                val newExamination = Examination(
                    title = examinationDto.title,
                    examType = examinationDto.examType,
                    isOnline = examinationDto.isOnline,
                    subject = subject,
                    schoolClass = schoolClass,
                    term = currentTerm,
                    academicSession = currentSession,
                    createdBy = customUser.user.id!!
                ).apply {
                    this.schoolId = selectedSchoolId
                    this.durationMinutes = examinationDto.durationMinutes
                    this.totalMarks = examinationDto.totalMarks
                    this.isActive = true
                    
                    val formatter = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                    this.startTime = if (!examinationDto.startTime.isNullOrBlank()) {
                        try {
                            java.time.LocalDateTime.parse(examinationDto.startTime, formatter)
                        } catch (e: Exception) {
                             try { java.time.LocalDateTime.parse(examinationDto.startTime) } catch (e2: Exception) { null }
                        }
                    } else null
                    
                    this.endTime = if (!examinationDto.endTime.isNullOrBlank()) {
                         try {
                            java.time.LocalDateTime.parse(examinationDto.endTime, formatter)
                        } catch (e: Exception) {
                             try { java.time.LocalDateTime.parse(examinationDto.endTime) } catch (e2: Exception) { null }
                        }
                    } else null

                    this.isPublished = examinationDto.isPublished
                }
                val savedExamination = examinationRepository.save(newExamination)
                model.addAttribute("examination", savedExamination)
                model.addAttribute("classId", classId)
                model.addAttribute("isNew", true)
            }

            // Check if triggers are supported by the client lib, otherwise rely on the header
            // Trigger refresh only for new assessments
            val refreshTrigger = if (examinationDto.id == null) ", \"refreshAssessments\": true" else ""
            response.setHeader("HX-Trigger", "{\"closeModal\": \"examinationModal\", \"showNotification\": \"Assessment saved successfully!\"$refreshTrigger}")

            return "staff/class-assessments :: examination-save-response"
        } catch (e: Exception) {
            model.addAttribute("error", "Error saving examination: ${e.message}")
            return "fragments/error :: error-message"
        }
    }

    @PostMapping("/classes/{classId}/examinations/{examId}/toggle-publish")
    @ResponseBody
    fun toggleExaminationPublish(
        @PathVariable classId: UUID,
        @PathVariable examId: UUID,
        authentication: Authentication,
        session: HttpSession
    ): String {
        val userDetails = userDetailsService.loadUserByUsername(authentication.name)
        val customUser = userDetails as com.haneef._school.service.CustomUserDetails
        
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "<span class='badge bg-danger'>Error</span>"
            
        val examination = examinationRepository.findById(examId).orElse(null)
            ?: return "<span class='badge bg-danger'>Not Found</span>"
            
        if (examination.schoolId != selectedSchoolId || examination.schoolClass.id != classId) {
             return "<span class='badge bg-danger'>Unauthorized</span>"
        }

        // Check permissions
        val staff = staffRepository.findByUserIdAndSchoolId(customUser.getUserId()!!, selectedSchoolId)
        if (staff == null || !staff.isActive) {
             return "<span class='badge bg-danger'>Unauthorized</span>"
        }

        val isClassTeacher = classTeacherRepository
            .existsByStaffIdAndSchoolClassIdAndAcademicSessionIdAndTermIdAndSchoolIdAndIsActive(
                staff.id!!, classId, examination.academicSession!!.id!!, examination.term!!.id!!, selectedSchoolId, true
            )
            
        val canEdit = if (isClassTeacher) {
            true
        } else {
             val subjectsTaught = subjectTeacherRepository
                .findByStaffIdAndAcademicSessionIdAndTermIdAndIsActive(
                    staff.id!!, examination.academicSession!!.id!!, examination.term!!.id!!, true
                )
                .filter { it.schoolClass.id == classId }
                .map { it.subject.id }
             subjectsTaught.contains(examination.subject.id)
        }
        
        if (!canEdit) {
            return "<span class='badge bg-danger'>Unauthorized</span>"
        }
        
        // Toggle status
        examination.isPublished = !examination.isPublished
        examinationRepository.save(examination)
        
        val statusClass = if (examination.isPublished) "published" else "draft"
        val statusText = if (examination.isPublished) "Published" else "Draft"
        val bgStyle = if (examination.isPublished) "background: #dcfce7; color: #166534;" else "background: #fef9c3; color: #854d0e;"
        
        // Return updated badge HTML
        return """
            <span class="badge $statusClass" 
                  style="font-size: 0.7rem; padding: 0.25rem 0.75rem; border-radius: 999px; text-transform: uppercase; font-weight: 700; letter-spacing: 0.05em; $bgStyle; cursor: pointer;"
                  hx-post="/staff/classes/$classId/examinations/$examId/toggle-publish"
                  hx-swap="outerHTML"
                  title="Click to toggle status">
                $statusText
            </span>
        """.trimIndent()
    }
    @DeleteMapping("/classes/{classId}/examinations/{examId}")
    @ResponseBody
    fun deleteExamination(
        @PathVariable classId: UUID,
        @PathVariable examId: UUID,
        authentication: Authentication,
        session: HttpSession
    ): String {
        val userDetails = userDetailsService.loadUserByUsername(authentication.name)
        val customUser = userDetails as com.haneef._school.service.CustomUserDetails
        
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return ""
            
        val examination = examinationRepository.findById(examId).orElse(null)
            ?: return ""
            
        if (examination.schoolId != selectedSchoolId || examination.schoolClass.id != classId) {
             return ""
        }

        // Check permissions
        val staff = staffRepository.findByUserIdAndSchoolId(customUser.getUserId()!!, selectedSchoolId)
        if (staff == null || !staff.isActive) {
             return ""
        }

        val isClassTeacher = classTeacherRepository
            .existsByStaffIdAndSchoolClassIdAndAcademicSessionIdAndTermIdAndSchoolIdAndIsActive(
                staff.id!!, classId, examination.academicSession!!.id!!, examination.term!!.id!!, selectedSchoolId, true
            )
            
        val canDelete = if (isClassTeacher) {
            true
        } else {
             val subjectsTaught = subjectTeacherRepository
                .findByStaffIdAndAcademicSessionIdAndTermIdAndIsActive(
                    staff.id!!, examination.academicSession!!.id!!, examination.term!!.id!!, true
                )
                .filter { it.schoolClass.id == classId }
                .map { it.subject.id }
             subjectsTaught.contains(examination.subject.id)
        }
        
        if (!canDelete) {
            return ""
        }
        
        // Soft delete
        examination.isActive = false
        examinationRepository.save(examination)
        
        return "" // Return empty response to remove element from DOM
    }

    @PostMapping("/classes/{classId}/examinations/{examId}/ai-generate")
    @org.springframework.web.bind.annotation.ResponseBody
    fun generateAiQuestions(
        @org.springframework.web.bind.annotation.PathVariable classId: java.util.UUID,
        @org.springframework.web.bind.annotation.PathVariable examId: java.util.UUID,
        @org.springframework.web.bind.annotation.RequestBody request: com.haneef._school.dto.AiQuestionRequest,
        authentication: org.springframework.security.core.Authentication,
        session: jakarta.servlet.http.HttpSession
    ): Map<String, Any> {
        return try {
            val selectedSchoolId = session.getAttribute("selectedSchoolId") as? java.util.UUID
                ?: return mapOf("success" to false, "message" to "School not selected")

            val examination = examinationRepository.findById(examId).orElse(null)
                ?: return mapOf("success" to false, "message" to "Examination not found")

            // Security Check
            if (examination.schoolId != selectedSchoolId) {
                return mapOf("success" to false, "message" to "Unauthorized access")
            }

            // Add subject and class context to request for better prompt
            val enhancedRequest = request.copy(
                subjectName = examination.subject.subjectName,
                className = examination.schoolClass.className,
                gradeLevel = examination.schoolClass.gradeLevelDisplayName
            )

            val generatedQuestions = aiService.generateQuestions(enhancedRequest)
            mapOf("success" to true, "questions" to generatedQuestions)
        } catch (e: Exception) {
            logger.error("AI Generation failed", e)
            mapOf("success" to false, "message" to (e.message ?: "Failed to generate questions"))
        }
    }

    @GetMapping("/reports/class/download-report-card")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'TEACHER', 'PARENT')")
    fun downloadReportCard(
        @RequestParam studentId: UUID,
        @RequestParam classId: UUID,
        @RequestParam session: String,
        @RequestParam term: String,
        authentication: Authentication,
        session_http: HttpSession
    ): ResponseEntity<ByteArray> {
        return try {
            val selectedSchoolId = session_http.getAttribute("selectedSchoolId") as? UUID 
                ?: throw RuntimeException("School not selected")
            
            val userDetails = userDetailsService.loadUserByUsername(authentication.name)
            val customUser = userDetails as com.haneef._school.service.CustomUserDetails
            
            // Check if user is a parent - look for PARENT role in any authority
            val isParent = customUser.authorities.any { 
                it.authority?.equals("PARENT", ignoreCase = true) == true || 
                it.authority?.equals("ROLE_PARENT", ignoreCase = true) == true
            }
            
            logger.info("User ${customUser.user.id} attempting report card download - isParent: $isParent, roles: ${customUser.authorities.map { it.authority }}")
            
            // If user is a parent, validate parent-student access and use parent-accessible data fetching
            val reportData = if (isParent) {
                logger.info("Processing as parent user")
                val parents = parentRepository.findByUserIdWithWallet(customUser.user.id!!)
                val parent = parents.firstOrNull() ?: throw RuntimeException("Parent record not found")
                
                // Verify student belongs to parent
                if (!parent.activeStudentRelationships.any { it.student.id == studentId }) {
                    throw RuntimeException("Unauthorized access to student data")
                }
                
                logger.info("Parent access verified for student $studentId")
                // Use parent-specific assessment data fetching
                getParentAccessibleAssessmentData(studentId, classId, session, term, selectedSchoolId)
            } else {
                logger.info("Processing as staff user")
                // Use standard staff authorization-based assessment data fetching
                getStudentAssessmentData(studentId, classId, session, term, authentication, session_http)
            }
            
            // Generate PDF
            val pdfBytes = generateReportCardPDF(reportData)
            
            // Create response with PDF content type and attachment disposition
            val fileName = "${reportData.studentName.replace(" ", "_")}_ReportCard.pdf"
            
            ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "attachment; filename=\"$fileName\"")
                .body(pdfBytes)
        } catch (e: Exception) {
            logger.error("Error generating report card PDF", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ByteArray(0))
        }
    }

    @GetMapping("/reports/class/download-all-report-cards")
    fun downloadAllClassReportCards(
        @RequestParam classId: UUID,
        @RequestParam session: String,
        @RequestParam term: String,
        authentication: Authentication,
        session_http: HttpSession
    ): ResponseEntity<ByteArray> {
        return try {
            val selectedSchoolId = session_http.getAttribute("selectedSchoolId") as? UUID 
                ?: throw RuntimeException("School not selected")
            
            val userDetails = userDetailsService.loadUserByUsername(authentication.name)
            val customUser = userDetails as com.haneef._school.service.CustomUserDetails
            
            // Get class details
            val schoolClass = schoolClassRepository.findById(classId)
                .orElseThrow { RuntimeException("Class not found") }
            
            // Get all students in the class
            val classStudents = studentClassRepository.findBySchoolClassIdAndIsActive(classId, true)
            
            if (classStudents.isEmpty()) {
                throw RuntimeException("No students found in this class")
            }
            
            // Generate PDF with all student reports
            val pdfBytes = generateAllClassReportsPDF(
                classStudents.map { it.student.id!! },
                classId,
                session,
                term,
                authentication,
                session_http
            )
            
            // Create response with PDF content type and attachment disposition
            val fileName = "${schoolClass.className.replace(" ", "_")}_${session.replace(" ", "_")}_${term.replace(" ", "_")}_Reports.pdf"
            
            ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "attachment; filename=\"$fileName\"")
                .body(pdfBytes)
        } catch (e: Exception) {
            logger.error("Error generating all class report cards PDF", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ByteArray(0))
        }
    }

    private fun generateAllClassReportsPDF(
        studentIds: List<UUID>,
        classId: UUID,
        session: String,
        term: String,
        authentication: Authentication,
        session_http: HttpSession
    ): ByteArray {
        val outputStream = java.io.ByteArrayOutputStream()
        
        val writer = PdfWriter(outputStream)
        val pdf = PdfDocument(writer)
        val document = com.itextpdf.layout.Document(pdf)
        
        var isFirstPage = true
        
        // Generate report for each student
        for (studentId in studentIds) {
            try {
                // Add page break before each student (except first)
                if (!isFirstPage) {
                    document.add(com.itextpdf.layout.element.AreaBreak())
                }
                isFirstPage = false
                
                // Get assessment data for this student
                val reportData = getStudentAssessmentData(studentId, classId, session, term, authentication, session_http)
                
                // Add report content (reuse the report generation logic without wrapper)
                addReportContentToDocument(document, reportData)
                
            } catch (e: Exception) {
                logger.warn("Error generating report for student $studentId", e)
                // Continue with next student instead of failing entirely
                document.add(
                    com.itextpdf.layout.element.Paragraph("Error generating report for student")
                        .setFontSize(10f)
                )
            }
        }
        
        document.close()
        return outputStream.toByteArray()
    }

    private fun addReportContentToDocument(
        document: com.itextpdf.layout.Document,
        reportData: AssessmentReportData
    ) {
        // Set margins
        document.setMargins(20f, 20f, 20f, 20f)
        
        // Get Arabic-compatible font once
        val arabicFont = getArabicFont()
        
        // ==== SCHOOL HEADER ====
        val headerTable = com.itextpdf.layout.element.Table(3)
            .setWidth(com.itextpdf.layout.properties.UnitValue.createPercentValue(100f))
        
        // Left cell - School Logo
        val leftCell = com.itextpdf.layout.element.Cell(1, 1)
            .setVerticalAlignment(com.itextpdf.layout.properties.VerticalAlignment.MIDDLE)
            .setPadding(12f)
            .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
        if (!reportData.schoolLogoUrl.isNullOrBlank()) {
            try {
                val logoUrl = reportData.schoolLogoUrl!!
                val imageData = ImageDataFactory.create(logoUrl)
                val image = com.itextpdf.layout.element.Image(imageData)
                    .setMaxWidth(60f)
                    .setMaxHeight(60f)
                leftCell.add(image)
            } catch (e: Exception) {
                // Leave empty on error
            }
        }
        headerTable.addCell(leftCell)
        
        // Center cell - School Name and Address
        val centerCell = com.itextpdf.layout.element.Cell(1, 1)
            .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
            .setVerticalAlignment(com.itextpdf.layout.properties.VerticalAlignment.MIDDLE)
            .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
        val schoolNamePara = com.itextpdf.layout.element.Paragraph(reportData.schoolName ?: "School")
            .setFontSize(16f)
            .setBold()
        if (arabicFont != null) {
            schoolNamePara.setFont(arabicFont)
            schoolNamePara.setBaseDirection(com.itextpdf.layout.properties.BaseDirection.DEFAULT_BIDI)
        }
        centerCell.add(schoolNamePara)
        
        if (!reportData.schoolAddress.isNullOrBlank()) {
            val addressPara = com.itextpdf.layout.element.Paragraph(reportData.schoolAddress!!)
                .setFontSize(9f)
                .setMarginTop(2f)
            if (arabicFont != null) {
                addressPara.setFont(arabicFont)
                addressPara.setBaseDirection(com.itextpdf.layout.properties.BaseDirection.DEFAULT_BIDI)
            }
            centerCell.add(addressPara)
        }
        // Add Session/Term on single line under address
        if (!reportData.sessionName.isNullOrBlank() || !reportData.termName.isNullOrBlank()) {
            val sessionTermText = buildString {
                if (!reportData.termName.isNullOrBlank()) {
                    append(reportData.termName)
                }
                if (!reportData.sessionName.isNullOrBlank()) {
                    if (isNotEmpty()) append(", ")
                    append("${reportData.sessionName} ACADEMIC SESSION")
                }
            }
            val sessionTermPara = com.itextpdf.layout.element.Paragraph(sessionTermText)
                .setFontSize(7f)
                .setMarginTop(4f)
                .setFontColor(com.itextpdf.kernel.colors.DeviceRgb(102, 126, 234))
            if (arabicFont != null) {
                sessionTermPara.setFont(arabicFont)
                sessionTermPara.setBaseDirection(com.itextpdf.layout.properties.BaseDirection.DEFAULT_BIDI)
            }
            centerCell.add(sessionTermPara)
        }
        headerTable.addCell(centerCell)
        
        // Right cell - Student Photo
        val rightCell = com.itextpdf.layout.element.Cell(1, 1)
            .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.RIGHT)
            .setVerticalAlignment(com.itextpdf.layout.properties.VerticalAlignment.MIDDLE)
            .setPadding(12f)
            .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
        if (!reportData.studentPassportPhotoUrl.isNullOrBlank()) {
            try {
                val photoUrl = reportData.studentPassportPhotoUrl!!
                val imageData = ImageDataFactory.create(photoUrl)
                val image = com.itextpdf.layout.element.Image(imageData)
                    .setMaxWidth(55f)
                    .setMaxHeight(65f)
                rightCell.add(image)
            } catch (e: Exception) {
                // Leave empty on error
            }
        }
        headerTable.addCell(rightCell)
        
        // Style header table - no border, no padding on table level
        headerTable.setBorder(null)
            .setMarginBottom(16f)
        
        document.add(headerTable)
        
        // Separator line removed (session/term now in header)
        
        // ==== STUDENT INFORMATION ====
        document.add(
            createCalligraphyParagraph("STUDENT INFORMATION", size = 12f, isBold = true)
        )
        
        val studentInfoTable = com.itextpdf.layout.element.Table(2)
            .setWidth(com.itextpdf.layout.properties.UnitValue.createPercentValue(100f))
        
        addTableRow(studentInfoTable, "Student Name:", reportData.studentName)
        addTableRow(studentInfoTable, "Admission Number:", reportData.admissionNumber)
        addTableRow(studentInfoTable, "Track / Class:", "${reportData.trackName} / ${reportData.className}")
        addTableRow(studentInfoTable, "Attendance:", "${reportData.attendance}")
        
        document.add(studentInfoTable)
        document.add(com.itextpdf.layout.element.Paragraph(" ").setMarginBottom(8f))
        
        // ==== ACADEMIC PERFORMANCE ====
        document.add(
            createCalligraphyParagraph("ACADEMIC PERFORMANCE", size = 12f, isBold = true)
        )
        
        val scoresTable = com.itextpdf.layout.element.Table(9)
            .setWidth(com.itextpdf.layout.properties.UnitValue.createPercentValue(100f))
        
        // Table headers
        addTableHeader(scoresTable, arrayOf("Subject", "CA 1", "CA 2", "Exam", "Total", "Highest", "Lowest", "Average", "Position"))
        
        // Table rows with subject scores
        reportData.subjects.forEach { subject ->
            val ca1 = subject.ca1?.toString() ?: "-"
            val ca2 = subject.ca2?.toString() ?: "-"
            val exam = subject.exam?.toString() ?: "-"
            val total = subject.total?.toString() ?: "-"
            val highest = subject.highestScore?.toString() ?: "-"
            val lowest = subject.lowestScore?.toString() ?: "-"
            val average = if (subject.averageScore != null) String.format("%.1f", subject.averageScore) else "-"
            val position = subject.classPosition ?: "-"
            
            val row = com.itextpdf.layout.element.Cell(1, 1).add(
                com.itextpdf.layout.element.Paragraph(subject.subjectName).setFontSize(9f)
            )
            scoresTable.addCell(row)
            
            scoresTable.addCell(com.itextpdf.layout.element.Cell(1, 1).add(
                com.itextpdf.layout.element.Paragraph(ca1).setFontSize(9f)
                    .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
            ))
            scoresTable.addCell(com.itextpdf.layout.element.Cell(1, 1).add(
                com.itextpdf.layout.element.Paragraph(ca2).setFontSize(9f)
                    .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
            ))
            scoresTable.addCell(com.itextpdf.layout.element.Cell(1, 1).add(
                com.itextpdf.layout.element.Paragraph(exam).setFontSize(9f)
                    .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
            ))
            scoresTable.addCell(com.itextpdf.layout.element.Cell(1, 1).add(
                com.itextpdf.layout.element.Paragraph(total).setFontSize(9f)
                    .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
            ))
            scoresTable.addCell(com.itextpdf.layout.element.Cell(1, 1).add(
                com.itextpdf.layout.element.Paragraph(highest).setFontSize(9f)
                    .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
            ))
            scoresTable.addCell(com.itextpdf.layout.element.Cell(1, 1).add(
                com.itextpdf.layout.element.Paragraph(lowest).setFontSize(9f)
                    .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
            ))
            scoresTable.addCell(com.itextpdf.layout.element.Cell(1, 1).add(
                com.itextpdf.layout.element.Paragraph(average).setFontSize(9f)
                    .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
            ))
            scoresTable.addCell(com.itextpdf.layout.element.Cell(1, 1).add(
                com.itextpdf.layout.element.Paragraph(position).setFontSize(9f)
                    .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
            ))
        }
        
        document.add(scoresTable)
        document.add(com.itextpdf.layout.element.Paragraph(" ").setMarginBottom(8f))
        
        // ==== SUMMARY SECTION ====
        document.add(
            createCalligraphyParagraph("SUMMARY", size = 12f, isBold = true)
        )
        
        val summaryTable = com.itextpdf.layout.element.Table(2)
            .setWidth(com.itextpdf.layout.properties.UnitValue.createPercentValue(100f))
        
        // Summary rows with colored backgrounds
        // Row 1: TOTAL SCORE - Light Blue background
        val totalScoreLabel = com.itextpdf.layout.element.Cell(1, 1)
            .add(com.itextpdf.layout.element.Paragraph("TOTAL SCORE").setBold().setFontSize(10f))
            
            .setPadding(4f)
        val totalScoreValue = com.itextpdf.layout.element.Cell(1, 1)
            .add(com.itextpdf.layout.element.Paragraph(if (reportData.totalScore != null) String.format("%.2f", reportData.totalScore!!) else "-").setFontSize(10f))
            
            .setPadding(4f)
        summaryTable.addCell(totalScoreLabel)
        summaryTable.addCell(totalScoreValue)
        
        // Row 2: TOTAL AVERAGE - Light Yellow background
        val totalAvgLabel = com.itextpdf.layout.element.Cell(1, 1)
            .add(com.itextpdf.layout.element.Paragraph("TOTAL AVERAGE").setBold().setFontSize(10f))
            
            .setPadding(4f)
        val totalAvgValue = com.itextpdf.layout.element.Cell(1, 1)
            .add(com.itextpdf.layout.element.Paragraph(if (reportData.totalAverage != null) String.format("%.1f", reportData.totalAverage!!) else "-").setFontSize(10f))
            
            .setPadding(4f)
        summaryTable.addCell(totalAvgLabel)
        summaryTable.addCell(totalAvgValue)
        
        // Row 3: PERFORMANCE GRADE - Light Green background
        val gradeLabel = com.itextpdf.layout.element.Cell(1, 1)
            .add(com.itextpdf.layout.element.Paragraph("PERFORMANCE GRADE").setBold().setFontSize(10f))
            
            .setPadding(4f)
        val gradeValue = com.itextpdf.layout.element.Cell(1, 1)
            .add(com.itextpdf.layout.element.Paragraph(reportData.performanceGrade ?: "-").setFontSize(10f).setBold())
            
            .setPadding(4f)
        summaryTable.addCell(gradeLabel)
        summaryTable.addCell(gradeValue)
        
        document.add(summaryTable)
        document.add(com.itextpdf.layout.element.Paragraph(" ").setMarginBottom(8f))
        
        // ==== BEHAVIORAL TRAITS ====
        document.add(
            createCalligraphyParagraph("BEHAVIORAL TRAITS", size = 12f, isBold = true)
        )
        
        val behavioralTable = com.itextpdf.layout.element.Table(4)
            .setWidth(com.itextpdf.layout.properties.UnitValue.createPercentValue(100f))
        
        // Left column traits
        val leftTraits = listOf(
            Pair("Fluency:", reportData.fluency?.toString() ?: "N/A"),
            Pair("Handwriting:", reportData.handwriting?.toString() ?: "N/A"),
            Pair("Game Sense:", reportData.game?.toString() ?: "N/A"),
            Pair("Initiative:", reportData.initiative?.toString() ?: "N/A"),
            Pair("Critical Thinking:", reportData.criticalThinking?.toString() ?: "N/A")
        )
        
        // Right column traits
        val rightTraits = listOf(
            Pair("Punctuality:", reportData.punctuality?.toString() ?: "N/A"),
            Pair("Attentiveness:", reportData.attentiveness?.toString() ?: "N/A"),
            Pair("Neatness:", reportData.neatness?.toString() ?: "N/A"),
            Pair("Self-Discipline:", reportData.selfDiscipline?.toString() ?: "N/A"),
            Pair("Politeness:", reportData.politeness?.toString() ?: "N/A")
        )
        
        // Add rows with left and right trait pairs
        for (i in 0 until maxOf(leftTraits.size, rightTraits.size)) {
            // Left trait label
            val leftLabel = if (i < leftTraits.size) leftTraits[i].first else ""
            val leftLabelCell = com.itextpdf.layout.element.Cell(1, 1)
                .add(com.itextpdf.layout.element.Paragraph(leftLabel).setBold().setFontSize(9f))
            behavioralTable.addCell(leftLabelCell)
            
            // Left trait value
            val leftValue = if (i < leftTraits.size) leftTraits[i].second else ""
            val leftValueCell = com.itextpdf.layout.element.Cell(1, 1)
                .add(com.itextpdf.layout.element.Paragraph(leftValue).setFontSize(9f)
                    .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER))
            behavioralTable.addCell(leftValueCell)
            
            // Right trait label
            val rightLabel = if (i < rightTraits.size) rightTraits[i].first else ""
            val rightLabelCell = com.itextpdf.layout.element.Cell(1, 1)
                .add(com.itextpdf.layout.element.Paragraph(rightLabel).setBold().setFontSize(9f))
            behavioralTable.addCell(rightLabelCell)
            
            // Right trait value
            val rightValue = if (i < rightTraits.size) rightTraits[i].second else ""
            val rightValueCell = com.itextpdf.layout.element.Cell(1, 1)
                .add(com.itextpdf.layout.element.Paragraph(rightValue).setFontSize(9f)
                    .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER))
            behavioralTable.addCell(rightValueCell)
        }
        
        document.add(behavioralTable)
        document.add(com.itextpdf.layout.element.Paragraph(" ").setMarginBottom(8f))
        
        // ==== COMMENTS ====
        if (!reportData.classTeacherComment.isNullOrBlank() || !reportData.headTeacherComment.isNullOrBlank()) {
            document.add(
                createCalligraphyParagraph("COMMENTS", size = 12f, isBold = true)
            )
            
            if (!reportData.classTeacherComment.isNullOrBlank()) {
                document.add(
                    com.itextpdf.layout.element.Paragraph("Class Teacher Comment:")
                        .setBold()
                        .setFontSize(10f)
                )
                document.add(
                    com.itextpdf.layout.element.Paragraph(reportData.classTeacherComment!!)
                        .setFontSize(10f)
                        .setMarginBottom(8f)
                )
            }
            
            if (!reportData.headTeacherComment.isNullOrBlank()) {
                document.add(
                    com.itextpdf.layout.element.Paragraph("Head Teacher Comment:")
                        .setBold()
                        .setFontSize(10f)
                )
                document.add(
                    com.itextpdf.layout.element.Paragraph(reportData.headTeacherComment!!)
                        .setFontSize(10f)
                )
            }
        }
    }

    private fun generateReportCardPDF(reportData: AssessmentReportData): ByteArray {
        val outputStream = java.io.ByteArrayOutputStream()
        
        val writer = PdfWriter(outputStream)
        val pdf = PdfDocument(writer)
        val document = com.itextpdf.layout.Document(pdf)
        
        // Set margins
        document.setMargins(20f, 20f, 20f, 20f)
        
        // Get Arabic-compatible font once
        val arabicFont = getArabicFont()
        
        // ==== SCHOOL HEADER ====
        val headerTable = com.itextpdf.layout.element.Table(3)
            .setWidth(com.itextpdf.layout.properties.UnitValue.createPercentValue(100f))
        
        // Left cell - School Logo
        val leftCell = com.itextpdf.layout.element.Cell(1, 1)
            .setVerticalAlignment(com.itextpdf.layout.properties.VerticalAlignment.MIDDLE)
            .setPadding(12f)
            .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
        if (!reportData.schoolLogoUrl.isNullOrBlank()) {
            try {
                val logoUrl = reportData.schoolLogoUrl!!
                val imageData = ImageDataFactory.create(logoUrl)
                val image = com.itextpdf.layout.element.Image(imageData)
                    .setMaxWidth(60f)
                    .setMaxHeight(60f)
                leftCell.add(image)
            } catch (e: Exception) {
                // Leave empty on error
            }
        }
        headerTable.addCell(leftCell)
        
        // Center cell - School Name and Address with calligraphy styling
        val centerCell = com.itextpdf.layout.element.Cell(1, 1)
            .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
            .setVerticalAlignment(com.itextpdf.layout.properties.VerticalAlignment.MIDDLE)
            .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
        // School name with calligraphy styling
        centerCell.add(
            createCalligraphyParagraph(reportData.schoolName ?: "School", size = 16f, isBold = true, arabicFont = arabicFont)
        )
        if (!reportData.schoolAddress.isNullOrBlank()) {
            val addressPara = com.itextpdf.layout.element.Paragraph(reportData.schoolAddress!!)
                .setFontSize(9f)
                .setMarginTop(2f)
            if (arabicFont != null) addressPara.setFont(arabicFont)
            centerCell.add(addressPara)
        }
        // Add Session/Term on single line under address
        if (!reportData.sessionName.isNullOrBlank() || !reportData.termName.isNullOrBlank()) {
            val sessionTermText = buildString {
                if (!reportData.termName.isNullOrBlank()) {
                    append(reportData.termName)
                }
                if (!reportData.sessionName.isNullOrBlank()) {
                    if (isNotEmpty()) append(", ")
                    append("${reportData.sessionName} ACADEMIC SESSION")
                }
            }
            centerCell.add(
                com.itextpdf.layout.element.Paragraph(sessionTermText)
                    .setFontSize(7f)
                    .setMarginTop(4f)
                    .setFontColor(com.itextpdf.kernel.colors.DeviceRgb(102, 126, 234))
            )
        }
        headerTable.addCell(centerCell)
        
        // Right cell - Student Photo
        val rightCell = com.itextpdf.layout.element.Cell(1, 1)
            .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.RIGHT)
            .setVerticalAlignment(com.itextpdf.layout.properties.VerticalAlignment.MIDDLE)
            .setPadding(12f)
            .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
        if (!reportData.studentPassportPhotoUrl.isNullOrBlank()) {
            try {
                val photoUrl = reportData.studentPassportPhotoUrl!!
                val imageData = ImageDataFactory.create(photoUrl)
                val image = com.itextpdf.layout.element.Image(imageData)
                    .setMaxWidth(55f)
                    .setMaxHeight(65f)
                rightCell.add(image)
            } catch (e: Exception) {
                // Leave empty on error
            }
        }
        headerTable.addCell(rightCell)
        
        // Style header table - no border, no padding on table level
        headerTable.setBorder(null)
            .setMarginBottom(16f)
        
        document.add(headerTable)
        
        // Separator line removed (session/term now in header)
        
        // ==== STUDENT INFORMATION ====
        document.add(
            createCalligraphyParagraph("STUDENT INFORMATION", size = 12f, isBold = true)
        )
        
        val studentInfoTable = com.itextpdf.layout.element.Table(2)
            .setWidth(com.itextpdf.layout.properties.UnitValue.createPercentValue(100f))
        
        addTableRow(studentInfoTable, "Student Name:", reportData.studentName)
        addTableRow(studentInfoTable, "Admission Number:", reportData.admissionNumber)
        addTableRow(studentInfoTable, "Track / Class:", "${reportData.trackName} / ${reportData.className}")
        addTableRow(studentInfoTable, "Attendance:", "${reportData.attendance}")
        
        document.add(studentInfoTable)
        document.add(com.itextpdf.layout.element.Paragraph(" ").setMarginBottom(8f))
        
        // ==== ACADEMIC PERFORMANCE ====
        document.add(
            createCalligraphyParagraph("ACADEMIC PERFORMANCE", size = 12f, isBold = true)
        )
        
        val scoresTable = com.itextpdf.layout.element.Table(9)
            .setWidth(com.itextpdf.layout.properties.UnitValue.createPercentValue(100f))
        
        // Table headers
        addTableHeader(scoresTable, arrayOf("Subject", "CA 1", "CA 2", "Exam", "Total", "Highest", "Lowest", "Average", "Position"))
        
        // Table rows with subject scores
        reportData.subjects.forEach { subject ->
            val ca1 = subject.ca1?.toString() ?: "-"
            val ca2 = subject.ca2?.toString() ?: "-"
            val exam = subject.exam?.toString() ?: "-"
            val total = subject.total?.toString() ?: "-"
            val highest = subject.highestScore?.toString() ?: "-"
            val lowest = subject.lowestScore?.toString() ?: "-"
            val average = if (subject.averageScore != null) String.format("%.1f", subject.averageScore) else "-"
            val position = subject.classPosition ?: "-"
            
            val subjectPara = com.itextpdf.layout.element.Paragraph(subject.subjectName).setFontSize(9f)
            if (arabicFont != null) subjectPara.setFont(arabicFont)
            val row = com.itextpdf.layout.element.Cell(1, 1).add(subjectPara)
            scoresTable.addCell(row)
            
            scoresTable.addCell(com.itextpdf.layout.element.Cell(1, 1).add(
                com.itextpdf.layout.element.Paragraph(ca1).setFontSize(9f)
                    .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
            ))
            scoresTable.addCell(com.itextpdf.layout.element.Cell(1, 1).add(
                com.itextpdf.layout.element.Paragraph(ca2).setFontSize(9f)
                    .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
            ))
            scoresTable.addCell(com.itextpdf.layout.element.Cell(1, 1).add(
                com.itextpdf.layout.element.Paragraph(exam).setFontSize(9f)
                    .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
            ))
            scoresTable.addCell(com.itextpdf.layout.element.Cell(1, 1).add(
                com.itextpdf.layout.element.Paragraph(total).setFontSize(9f)
                    .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
            ))
            scoresTable.addCell(com.itextpdf.layout.element.Cell(1, 1).add(
                com.itextpdf.layout.element.Paragraph(highest).setFontSize(9f)
                    .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
            ))
            scoresTable.addCell(com.itextpdf.layout.element.Cell(1, 1).add(
                com.itextpdf.layout.element.Paragraph(lowest).setFontSize(9f)
                    .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
            ))
            scoresTable.addCell(com.itextpdf.layout.element.Cell(1, 1).add(
                com.itextpdf.layout.element.Paragraph(average).setFontSize(9f)
                    .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
            ))
            scoresTable.addCell(com.itextpdf.layout.element.Cell(1, 1).add(
                com.itextpdf.layout.element.Paragraph(position).setFontSize(9f)
                    .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
            ))
        }
        
        document.add(scoresTable)
        document.add(com.itextpdf.layout.element.Paragraph(" ").setMarginBottom(8f))
        
        // ==== SUMMARY SECTION ====
        document.add(
            com.itextpdf.layout.element.Paragraph("SUMMARY")
                .setFontSize(12f)
                .setBold()
                .setMarginBottom(8f)
        )
        
        val summaryTable = com.itextpdf.layout.element.Table(2)
            .setWidth(com.itextpdf.layout.properties.UnitValue.createPercentValue(100f))
        
        // Summary rows with colored backgrounds
        // Row 1: TOTAL SCORE - Light Blue background
        val totalScoreLabel = com.itextpdf.layout.element.Cell(1, 1)
            .add(com.itextpdf.layout.element.Paragraph("TOTAL SCORE").setBold().setFontSize(10f))
            
            .setPadding(4f)
        val totalScoreValue = com.itextpdf.layout.element.Cell(1, 1)
            .add(com.itextpdf.layout.element.Paragraph(if (reportData.totalScore != null) String.format("%.2f", reportData.totalScore!!) else "-").setFontSize(10f))
            
            .setPadding(4f)
        summaryTable.addCell(totalScoreLabel)
        summaryTable.addCell(totalScoreValue)
        
        // Row 2: TOTAL AVERAGE - Light Yellow background
        val totalAvgLabel = com.itextpdf.layout.element.Cell(1, 1)
            .add(com.itextpdf.layout.element.Paragraph("TOTAL AVERAGE").setBold().setFontSize(10f))
            
            .setPadding(4f)
        val totalAvgValue = com.itextpdf.layout.element.Cell(1, 1)
            .add(com.itextpdf.layout.element.Paragraph(if (reportData.totalAverage != null) String.format("%.1f", reportData.totalAverage!!) else "-").setFontSize(10f))
            
            .setPadding(4f)
        summaryTable.addCell(totalAvgLabel)
        summaryTable.addCell(totalAvgValue)
        
        // Row 3: PERFORMANCE GRADE - Light Green background
        val gradeLabel = com.itextpdf.layout.element.Cell(1, 1)
            .add(com.itextpdf.layout.element.Paragraph("PERFORMANCE GRADE").setBold().setFontSize(10f))
            
            .setPadding(4f)
        val gradeValue = com.itextpdf.layout.element.Cell(1, 1)
            .add(com.itextpdf.layout.element.Paragraph(reportData.performanceGrade ?: "-").setFontSize(10f).setBold())
            
            .setPadding(4f)
        summaryTable.addCell(gradeLabel)
        summaryTable.addCell(gradeValue)
        
        document.add(summaryTable)
        document.add(com.itextpdf.layout.element.Paragraph(" ").setMarginBottom(8f))
        
        // ==== BEHAVIORAL TRAITS ====
        document.add(
            com.itextpdf.layout.element.Paragraph("BEHAVIORAL TRAITS")
                .setFontSize(12f)
                .setBold()
                .setMarginBottom(8f)
        )
        
        val behavioralTable = com.itextpdf.layout.element.Table(4)
            .setWidth(com.itextpdf.layout.properties.UnitValue.createPercentValue(100f))
        
        // Left column traits
        val leftTraits = listOf(
            Pair("Fluency:", reportData.fluency?.toString() ?: "N/A"),
            Pair("Handwriting:", reportData.handwriting?.toString() ?: "N/A"),
            Pair("Game Sense:", reportData.game?.toString() ?: "N/A"),
            Pair("Initiative:", reportData.initiative?.toString() ?: "N/A"),
            Pair("Critical Thinking:", reportData.criticalThinking?.toString() ?: "N/A")
        )
        
        // Right column traits
        val rightTraits = listOf(
            Pair("Punctuality:", reportData.punctuality?.toString() ?: "N/A"),
            Pair("Attentiveness:", reportData.attentiveness?.toString() ?: "N/A"),
            Pair("Neatness:", reportData.neatness?.toString() ?: "N/A"),
            Pair("Self-Discipline:", reportData.selfDiscipline?.toString() ?: "N/A"),
            Pair("Politeness:", reportData.politeness?.toString() ?: "N/A")
        )
        
        // Add rows with left and right trait pairs
        for (i in 0 until maxOf(leftTraits.size, rightTraits.size)) {
            // Left trait label
            val leftLabel = if (i < leftTraits.size) leftTraits[i].first else ""
            val leftLabelCell = com.itextpdf.layout.element.Cell(1, 1)
                .add(com.itextpdf.layout.element.Paragraph(leftLabel).setBold().setFontSize(9f))
            behavioralTable.addCell(leftLabelCell)
            
            // Left trait value
            val leftValue = if (i < leftTraits.size) leftTraits[i].second else ""
            val leftValueCell = com.itextpdf.layout.element.Cell(1, 1)
                .add(com.itextpdf.layout.element.Paragraph(leftValue).setFontSize(9f)
                    .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER))
            behavioralTable.addCell(leftValueCell)
            
            // Right trait label
            val rightLabel = if (i < rightTraits.size) rightTraits[i].first else ""
            val rightLabelCell = com.itextpdf.layout.element.Cell(1, 1)
                .add(com.itextpdf.layout.element.Paragraph(rightLabel).setBold().setFontSize(9f))
            behavioralTable.addCell(rightLabelCell)
            
            // Right trait value
            val rightValue = if (i < rightTraits.size) rightTraits[i].second else ""
            val rightValueCell = com.itextpdf.layout.element.Cell(1, 1)
                .add(com.itextpdf.layout.element.Paragraph(rightValue).setFontSize(9f)
                    .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER))
            behavioralTable.addCell(rightValueCell)
        }
        
        document.add(behavioralTable)
        document.add(com.itextpdf.layout.element.Paragraph(" ").setMarginBottom(8f))
        
        // ==== COMMENTS ====
        if (!reportData.classTeacherComment.isNullOrBlank() || !reportData.headTeacherComment.isNullOrBlank()) {
            document.add(
                createCalligraphyParagraph("COMMENTS", size = 12f, isBold = true)
            )
            
            if (!reportData.classTeacherComment.isNullOrBlank()) {
                document.add(
                    com.itextpdf.layout.element.Paragraph("Class Teacher Comment:")
                        .setBold()
                        .setFontSize(10f)
                )
                document.add(
                    com.itextpdf.layout.element.Paragraph(reportData.classTeacherComment!!)
                        .setFontSize(10f)
                        .setMarginBottom(8f)
                )
            }
            
            if (!reportData.headTeacherComment.isNullOrBlank()) {
                document.add(
                    com.itextpdf.layout.element.Paragraph("Head Teacher Comment:")
                        .setBold()
                        .setFontSize(10f)
                )
                document.add(
                    com.itextpdf.layout.element.Paragraph(reportData.headTeacherComment!!)
                        .setFontSize(10f)
                )
            }
        }
        
        document.close()
        return outputStream.toByteArray()
    }

    private fun addTableRow(table: com.itextpdf.layout.element.Table, label: String, value: String, arabicFont: com.itextpdf.kernel.font.PdfFont? = null) {
        val labelPara = com.itextpdf.layout.element.Paragraph(label).setBold().setFontSize(10f)
        if (arabicFont != null) {
            labelPara.setFont(arabicFont)
            labelPara.setBaseDirection(com.itextpdf.layout.properties.BaseDirection.DEFAULT_BIDI)
        }
        val labelCell = com.itextpdf.layout.element.Cell(1, 1)
            .add(labelPara)
        
        val valuePara = com.itextpdf.layout.element.Paragraph(value).setFontSize(10f)
        if (arabicFont != null) {
            valuePara.setFont(arabicFont)
            valuePara.setBaseDirection(com.itextpdf.layout.properties.BaseDirection.DEFAULT_BIDI)
        }
        val valueCell = com.itextpdf.layout.element.Cell(1, 1)
            .add(valuePara)
        
        table.addCell(labelCell)
        table.addCell(valueCell)
    }

    private fun addTableHeader(table: com.itextpdf.layout.element.Table, headers: Array<String>, arabicFont: com.itextpdf.kernel.font.PdfFont? = null) {
        headers.forEach { header ->
            val headerPara = com.itextpdf.layout.element.Paragraph(header)
                .setBold()
                .setFontSize(10f)
                .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
            if (arabicFont != null) {
                headerPara.setFont(arabicFont)
                headerPara.setBaseDirection(com.itextpdf.layout.properties.BaseDirection.DEFAULT_BIDI)
            }
            val cell = com.itextpdf.layout.element.Cell(1, 1)
                .add(headerPara)
                .setBackgroundColor(com.itextpdf.kernel.colors.ColorConstants.LIGHT_GRAY)
            table.addCell(cell)
        }
    }

    /**
     * Create a paragraph with calligraphy-style aesthetics using bold formatting and colors
     * Supports Arabic RTL text with proper shaping
     */
    private fun createCalligraphyParagraph(text: String, size: Float = 12f, isBold: Boolean = false, marginBottom: Float = 8f, arabicFont: com.itextpdf.kernel.font.PdfFont? = null): com.itextpdf.layout.element.Paragraph {
        val para = com.itextpdf.layout.element.Paragraph(text)
            .setFontSize(size)
            .setMarginBottom(marginBottom)
            .setBold()  // Always bold for emphasis
        
        if (arabicFont != null) {
            para.setFont(arabicFont)
            // Enable RTL (right-to-left) for Arabic text
            para.setBaseDirection(com.itextpdf.layout.properties.BaseDirection.DEFAULT_BIDI)
        }
        
        // Add elegant color gradient based on size
        if (size > 14f) {
            // Larger text (like school name) - rich dark blue
            para.setFontColor(com.itextpdf.kernel.colors.DeviceRgb(25, 50, 120))
        } else if (size >= 12f) {
            // Section headers - elegant blue with slight transparency effect via color
            para.setFontColor(com.itextpdf.kernel.colors.DeviceRgb(50, 80, 150))
        }
        
        return para
    }


data class AttendanceSubmissionRequest(
    val date: String,
    val attendance: Map<String, String> // studentId -> status
)
}
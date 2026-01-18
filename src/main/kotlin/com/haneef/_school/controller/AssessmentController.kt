package com.haneef._school.controller

import com.haneef._school.dto.*
import com.haneef._school.entity.*
import com.haneef._school.repository.*
import com.haneef._school.service.CustomUserDetails
import jakarta.servlet.http.HttpSession
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime
import java.math.BigDecimal
import java.util.UUID



@Controller
@RequestMapping("/admin/assessments")
@PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'SCHOOL_ADMIN', 'STAFF', 'TEACHER')")
class AssessmentController(
    private val examinationRepository: ExaminationRepository,
    private val questionRepository: QuestionRepository,
    private val subjectRepository: SubjectRepository,
    private val schoolClassRepository: SchoolClassRepository,
    private val academicSessionRepository: AcademicSessionRepository,
    private val termRepository: TermRepository,
    private val educationTrackRepository: EducationTrackRepository,
    private val departmentRepository: DepartmentRepository,
    private val classSubjectRepository: ClassSubjectRepository,
    private val schoolRepository: SchoolRepository,
    private val studentClassRepository: StudentClassRepository,
    private val htmlSanitizerService: com.haneef._school.service.HtmlSanitizerService,
    private val examinationSubmissionRepository: ExaminationSubmissionRepository,
    private val staffRepository: StaffRepository,
    private val classTeacherRepository: ClassTeacherRepository,
    private val subjectTeacherRepository: SubjectTeacherRepository
) {

    @GetMapping
    fun assessmentHome(model: Model, authentication: Authentication, session: HttpSession): String {
        val customUser = authentication.principal as CustomUserDetails
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/select-school"

        // Get school information
        val school = schoolRepository.findById(selectedSchoolId).orElseThrow { 
            RuntimeException("School not found") 
        }

        // Get assessment statistics
        val totalExaminations = examinationRepository.countBySchoolIdAndIsActive(selectedSchoolId, true)
        val publishedExaminations = examinationRepository.countBySchoolIdAndIsActiveAndIsPublished(selectedSchoolId, true, true)
        val draftExaminations = examinationRepository.countBySchoolIdAndIsActiveAndIsPublished(selectedSchoolId, true, false)
        val totalQuestions = questionRepository.countBySchoolId(selectedSchoolId)

        model.addAttribute("user", customUser.user)
        model.addAttribute("userRole", "School Administrator")
        model.addAttribute("school", school)
        model.addAttribute("assessmentStats", mapOf(
            "totalExaminations" to totalExaminations,
            "publishedExaminations" to publishedExaminations,
            "draftExaminations" to draftExaminations,
            "totalQuestions" to totalQuestions
        ))
        model.addAttribute("showFilters", false)

        return "admin/assessments/home"
    }

    @GetMapping("/examinations")
    fun examinationsList(
        model: Model, 
        authentication: Authentication, 
        httpSession: HttpSession,
        @RequestParam(required = false) trackId: UUID?,
        @RequestParam(required = false) departmentId: UUID?,
        @RequestParam(required = false) subjectId: UUID?,
        @RequestParam(required = false) classId: UUID?,
        @RequestParam(required = false) examType: String?,
        @RequestParam(required = false) term: String?,
        @RequestParam(required = false) sessionYear: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "12") size: Int
    ): String {
        val customUser = authentication.principal as CustomUserDetails
        val selectedSchoolId = httpSession.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/select-school"

        // Get school information
        val school = schoolRepository.findById(selectedSchoolId).orElseThrow { 
            RuntimeException("School not found") 
        }

        val pageable = org.springframework.data.domain.PageRequest.of(page, size)
        val examinationsPage = if (trackId == null && departmentId == null && subjectId == null && classId == null && examType == null && term == null && sessionYear == null) {
            // No filters applied, show all examinations
            examinationRepository.findBySchoolIdAndIsActiveOrderByCreatedAtDesc(selectedSchoolId, true, pageable)
        } else {
            // Filters applied, use filtered query
            examinationRepository.findBySchoolIdAndFiltersWithQuestions(
                selectedSchoolId, true, subjectId, classId, departmentId, trackId, examType, term, sessionYear, pageable
            )
        }
        
        val examinations = examinationsPage.content
        
        println("DEBUG: Filtered examinations found: ${examinations.size}")
        examinations.forEach { exam ->
            println("DEBUG: Examination - ID: ${exam.id}, Title: ${exam.title}, Start: ${exam.startTime}, End: ${exam.endTime}, Questions: ${exam.questions.size}")
        }
        val subjects = subjectRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
        val classes = schoolClassRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
        val academicSessions = academicSessionRepository.findBySchoolIdAndIsActiveOrderByYearDesc(selectedSchoolId, true)
        val educationTracks = educationTrackRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)

        val departments = departmentRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
        
        model.addAttribute("user", customUser.user)
        model.addAttribute("userRole", "School Administrator")
        model.addAttribute("school", school)
        model.addAttribute("examinations", examinations)
        model.addAttribute("subjects", subjects)
        model.addAttribute("classes", classes)
        model.addAttribute("departments", departments)
        model.addAttribute("academicSessions", academicSessions)
        model.addAttribute("educationTracks", educationTracks)
        model.addAttribute("examTypes", listOf("Assignment", "Continuous Assessment", "Mid-Term Test", "End-of-Term Examination"))
        model.addAttribute("terms", listOf("First Term", "Second Term", "Third Term"))
        model.addAttribute("selectedTrackId", trackId)
        model.addAttribute("selectedDepartmentId", departmentId)
        model.addAttribute("selectedSubjectId", subjectId)
        model.addAttribute("selectedClassId", classId)
        model.addAttribute("selectedExamType", examType)
        model.addAttribute("selectedTerm", term)
        model.addAttribute("selectedSession", sessionYear)
        
        // Pagination metadata
        model.addAttribute("currentPage", page)
        model.addAttribute("totalPages", examinationsPage.totalPages)
        model.addAttribute("totalElements", examinationsPage.totalElements)
        model.addAttribute("pageSize", size)
        
        // Add selected names for display
        val selectedSubjectName = if (subjectId != null) {
            subjects.find { it.id == subjectId }?.subjectName ?: "Unknown Subject"
        } else null
        
        val selectedClassName = if (classId != null) {
            classes.find { it.id == classId }?.className ?: "Unknown Class"
        } else null

        val selectedTrackName = if (trackId != null) {
            educationTracks.find { it.id == trackId }?.name ?: "Unknown Track"
        } else null

        val selectedDepartmentName = if (departmentId != null) {
            departments.find { it.id == departmentId }?.name ?: "Unknown Department"
        } else null
        
        model.addAttribute("selectedSubjectName", selectedSubjectName)
        model.addAttribute("selectedClassName", selectedClassName)
        model.addAttribute("selectedTrackName", selectedTrackName)
        model.addAttribute("selectedDepartmentName", selectedDepartmentName)
        model.addAttribute("showFilters", true)
        model.addAttribute("hideSubjectFilter", false)
        model.addAttribute("hideExamTypeFilter", false)

        return "admin/assessments/examinations"
    }

    @GetMapping("/examinations/filter")
    fun filterExaminations(
        model: Model, 
        authentication: Authentication, 
        httpSession: HttpSession,
        @RequestParam(required = false) trackId: UUID?,
        @RequestParam(required = false) departmentId: UUID?,
        @RequestParam(required = false) subjectId: UUID?,
        @RequestParam(required = false) classId: UUID?,
        @RequestParam(required = false) examType: String?,
        @RequestParam(required = false) term: String?,
        @RequestParam(required = false) sessionYear: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "12") size: Int
    ): String {
        val customUser = authentication.principal as CustomUserDetails
        val selectedSchoolId = httpSession.getAttribute("selectedSchoolId") as? UUID
            ?: return "fragments/error :: error-message"

        // Get school information
        val school = schoolRepository.findById(selectedSchoolId).orElseThrow { 
            RuntimeException("School not found") 
        }

        // Apply filters
        val pageable = org.springframework.data.domain.PageRequest.of(page, size)
        val examinationsPage = if (trackId == null && departmentId == null && subjectId == null && classId == null && examType == null && term == null && sessionYear == null) {
            // No filters applied, show all examinations
            examinationRepository.findBySchoolIdAndIsActiveOrderByCreatedAtDesc(selectedSchoolId, true, pageable)
        } else {
            // Filters applied, use filtered query
            examinationRepository.findBySchoolIdAndFiltersWithQuestions(
                selectedSchoolId, true, subjectId, classId, departmentId, trackId, examType, term, sessionYear, pageable
            )
        }
        
        val examinations = examinationsPage.content
        
        println("DEBUG: Filtered examinations found: ${examinations.size}")
        examinations.forEach { exam ->
            println("DEBUG: Filtered Exam - ID: ${exam.id}, Title: ${exam.title}, Start: ${exam.startTime}, End: ${exam.endTime}, Questions: ${exam.questions.size}")
        }
        
        val subjects = subjectRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
        val classes = schoolClassRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
        val educationTracks = educationTrackRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)

        val departments = departmentRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
        model.addAttribute("user", customUser.user)
        model.addAttribute("school", school)
        model.addAttribute("examinations", examinations)
        model.addAttribute("subjects", subjects)
        model.addAttribute("classes", classes)
        model.addAttribute("departments", departments)
        model.addAttribute("educationTracks", educationTracks)
        model.addAttribute("selectedTrackId", trackId)
        model.addAttribute("selectedDepartmentId", departmentId)
        model.addAttribute("selectedSubjectId", subjectId)
        model.addAttribute("selectedClassId", classId)
        model.addAttribute("selectedExamType", examType)
        model.addAttribute("selectedTerm", term)
        model.addAttribute("selectedSession", sessionYear)
        
        // Pagination metadata
        model.addAttribute("currentPage", page)
        model.addAttribute("totalPages", examinationsPage.totalPages)
        model.addAttribute("totalElements", examinationsPage.totalElements)
        model.addAttribute("pageSize", size)
        
        // Add selected names for display
        val selectedSubjectName = if (subjectId != null) {
            subjects.find { it.id == subjectId }?.subjectName ?: "Unknown Subject"
        } else null
        
        val selectedClassName = if (classId != null) {
            classes.find { it.id == classId }?.className ?: "Unknown Class"
        } else null

        val selectedTrackName = if (trackId != null) {
            educationTracks.find { it.id == trackId }?.name ?: "Unknown Track"
        } else null

        val selectedDepartmentName = if (departmentId != null) {
            departments.find { it.id == departmentId }?.name ?: "Unknown Department"
        } else null
        
        model.addAttribute("selectedSubjectName", selectedSubjectName)
        model.addAttribute("selectedClassName", selectedClassName)
        model.addAttribute("selectedTrackName", selectedTrackName)
        model.addAttribute("selectedDepartmentName", selectedDepartmentName)
        model.addAttribute("hideSubjectFilter", false)
        model.addAttribute("hideExamTypeFilter", false)

        return "admin/assessments/examinations :: examinations-list"
    }

    @GetMapping("/examinations/new/modal")
    fun getNewExaminationModal(model: Model, authentication: Authentication, session: HttpSession): String {
        val customUser = authentication.principal as CustomUserDetails
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/select-school"
        
        val academicSessions = academicSessionRepository.findBySchoolIdAndIsActiveOrderByYearDesc(selectedSchoolId, true)
        val educationTracks = educationTrackRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
        
        model.addAttribute("user", customUser.user)
        model.addAttribute("examination", Examination())
        model.addAttribute("academicSessions", academicSessions)
        model.addAttribute("educationTracks", educationTracks)
        model.addAttribute("examTypes", listOf("Assignment", "Continuous Assessment", "Mid-Term Test", "End-of-Term Examination"))
        model.addAttribute("terms", listOf("First Term", "Second Term", "Third Term"))
        model.addAttribute("isEdit", false)
        
        return "admin/assessments/examination-modal"
    }

    @GetMapping("/examinations/{id}/modal")
    fun getEditExaminationModal(@PathVariable id: UUID, model: Model, authentication: Authentication, session: HttpSession): String {
        val customUser = authentication.principal as CustomUserDetails
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/select-school"
        
        val examination = examinationRepository.findById(id).orElseThrow { RuntimeException("Examination not found") }
        
        // Security Check: Ensure examination belongs to the selected school
        if (examination.schoolId != selectedSchoolId) {
            return "redirect:/admin/assessments/examinations?error=Unauthorized+access"
        }
        
        val academicSessions = academicSessionRepository.findBySchoolIdAndIsActiveOrderByYearDesc(selectedSchoolId, true)
        val educationTracks = educationTrackRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
        
        model.addAttribute("user", customUser.user)
        model.addAttribute("examination", examination)
        model.addAttribute("academicSessions", academicSessions)
        model.addAttribute("educationTracks", educationTracks)
        model.addAttribute("examTypes", listOf("Assignment", "Continuous Assessment", "Mid-Term Test", "End-of-Term Examination"))
        model.addAttribute("terms", listOf("First Term", "Second Term", "Third Term"))
        model.addAttribute("isEdit", true)
        
        return "admin/assessments/examination-modal"
    }

    @PostMapping("/examinations/save-htmx")
    fun saveExaminationHtmx(
        @ModelAttribute examinationDto: ExaminationDto,
        authentication: Authentication,
        session: HttpSession,
        model: Model
    ): String {
        val customUser = authentication.principal as CustomUserDetails
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "fragments/error :: error-message"

        try {
            val subject = subjectRepository.findById(examinationDto.subjectId).orElseThrow()
            val schoolClass = schoolClassRepository.findById(examinationDto.classId).orElseThrow()

            // Security Check: Ensure subject and class belong to the selected school
            if (subject.schoolId != selectedSchoolId || schoolClass.schoolId != selectedSchoolId) {
                return "fragments/error :: error-message"
            }

            if (examinationDto.id != null) {
                // Update existing examination
                val existingExamination = examinationRepository.findById(examinationDto.id).orElseThrow()
                
                // Security Check: Ensure examination belongs to the selected school
                if (existingExamination.schoolId != selectedSchoolId) {
                    return "fragments/error :: error-message"
                }
                
                existingExamination.apply {
                    this.title = examinationDto.title
                    this.examType = examinationDto.examType
                    this.subject = subject
                    this.schoolClass = schoolClass
                    this.term = examinationDto.term
                    this.session = examinationDto.sessionYear
                    this.durationMinutes = examinationDto.durationMinutes
                    this.totalMarks = examinationDto.totalMarks
                    this.startTime = examinationDto.startTime
                    this.endTime = examinationDto.endTime
                }
                examinationRepository.save(existingExamination)
                model.addAttribute("message", "Examination updated successfully!")
            } else {
                // Create new examination
                val newExamination = Examination(
                    title = examinationDto.title,
                    examType = examinationDto.examType,
                    subject = subject,
                    schoolClass = schoolClass,
                    term = examinationDto.term,
                    session = examinationDto.sessionYear,
                    createdBy = customUser.user.id!!
                ).apply {
                    this.schoolId = selectedSchoolId
                    this.durationMinutes = examinationDto.durationMinutes
                    this.totalMarks = examinationDto.totalMarks
                    this.isActive = true
                    this.startTime = examinationDto.startTime
                    this.endTime = examinationDto.endTime
                }
                examinationRepository.save(newExamination)
                model.addAttribute("message", "Examination created successfully!")
            }

            return "fragments/success :: success-message"
        } catch (e: Exception) {
            model.addAttribute("error", "Error saving examination: ${e.message}")
            return "fragments/error :: error-message"
        }
    }

    @PostMapping("/examinations/{id}/publish")
    fun publishExamination(@PathVariable id: UUID, model: Model, session: HttpSession): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "fragments/error :: error-message"
            
        try {
            val examination = examinationRepository.findById(id).orElseThrow {
                RuntimeException("Examination not found")
            }
            
            // Security Check: Ensure examination belongs to the selected school
            if (examination.schoolId != selectedSchoolId) {
                return "fragments/error :: error-message"
            }
            
            examination.isPublished = true
            examinationRepository.save(examination)
            
            model.addAttribute("examination", examination)
            return "admin/assessments/examinations :: publish-button"
        } catch (e: Exception) {
            e.printStackTrace()
            model.addAttribute("error", "Error publishing examination: ${e.message}")
            return "fragments/error :: error-message"
        }
    }

    @PostMapping("/examinations/{id}/unpublish")
    fun unpublishExamination(@PathVariable id: UUID, model: Model, session: HttpSession): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "fragments/error :: error-message"
            
        try {
            val examination = examinationRepository.findById(id).orElseThrow {
                RuntimeException("Examination not found")
            }
            
            // Security Check: Ensure examination belongs to the selected school
            if (examination.schoolId != selectedSchoolId) {
                return "fragments/error :: error-message"
            }
            
            examination.isPublished = false
            examinationRepository.save(examination)
            
            model.addAttribute("examination", examination)
            return "admin/assessments/examinations :: publish-button"
        } catch (e: Exception) {
            e.printStackTrace()
            model.addAttribute("error", "Error unpublishing examination: ${e.message}")
            return "fragments/error :: error-message"
        }
    }

    @PostMapping("/examinations/delete/{id}")
    fun deleteExamination(@PathVariable id: UUID, session: HttpSession, model: Model): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "fragments/error :: error-message"
            
        try {
            val examination = examinationRepository.findById(id).orElseThrow()
            
            // Security Check: Ensure examination belongs to the selected school
            if (examination.schoolId != selectedSchoolId) {
                return "fragments/error :: error-message"
            }
            
            examination.isActive = false
            examinationRepository.save(examination)
            model.addAttribute("message", "Examination deleted successfully!")
            return "fragments/success :: success-message"
        } catch (e: Exception) {
            model.addAttribute("error", "Error deleting examination: ${e.message}")
            return "fragments/error :: error-message"
        }
    }

    @GetMapping("/examinations/{id}/questions")
    fun manageQuestions(@PathVariable id: UUID, model: Model, session: HttpSession): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/select-school"
            
        val school = schoolRepository.findById(selectedSchoolId).orElseThrow { 
            RuntimeException("School not found") 
        }
        
        val examination = examinationRepository.findById(id).orElseThrow {
            RuntimeException("Examination not found")
        }
        
        // Security Check: Ensure examination belongs to the selected school
        if (examination.schoolId != selectedSchoolId) {
            return "redirect:/admin/assessments/examinations?error=Unauthorized+access"
        }
        
        // Sidebar data
        val academicSessions = academicSessionRepository.findBySchoolIdAndIsActiveOrderByYearDesc(selectedSchoolId, true)
        val educationTracks = educationTrackRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
        
        // Assessment statistics for sidebar
        val totalExaminations = examinationRepository.countBySchoolIdAndIsActive(selectedSchoolId, true)
        val publishedExaminations = examinationRepository.countBySchoolIdAndIsActiveAndIsPublished(selectedSchoolId, true, true)
        val draftExaminations = examinationRepository.countBySchoolIdAndIsActiveAndIsPublished(selectedSchoolId, true, false)
        val totalQuestions = questionRepository.countBySchoolId(selectedSchoolId)
        
        model.addAttribute("school", school)
        model.addAttribute("examination", examination)
        model.addAttribute("academicSessions", academicSessions)
        model.addAttribute("educationTracks", educationTracks)
        model.addAttribute("assessmentStats", mapOf(
            "totalExaminations" to totalExaminations,
            "publishedExaminations" to publishedExaminations,
            "draftExaminations" to draftExaminations,
            "totalQuestions" to totalQuestions
        ))
        model.addAttribute("examTypes", listOf("Assignment", "Continuous Assessment", "Mid-Term Test", "End-of-Term Examination"))
        model.addAttribute("terms", listOf("First Term", "Second Term", "Third Term"))
        model.addAttribute("showFilters", false)
        
        return "admin/assessments/questions"
    }

    @PostMapping("/examinations/{id}/questions/save")
    fun saveQuestions(
        @PathVariable id: UUID,
        @ModelAttribute form: QuestionListDto,
        model: Model,
        session: HttpSession
    ): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "fragments/error :: error-message"
            
        try {
            val examination = examinationRepository.findById(id).orElseThrow {
                RuntimeException("Examination not found")
            }
            
            // Security Check: Ensure examination belongs to the selected school
            if (examination.schoolId != selectedSchoolId) {
                return "fragments/error :: error-message"
            }
            
            // We no longer clear all questions. We only add new ones.
            // Existing questions are managed individually via modals.
            
            form.questions.forEach { qData ->
                val sanitizedQuestionText = htmlSanitizerService.sanitize(qData.questionText)
                if (sanitizedQuestionText.isNotBlank()) {
                    val sanitizedInstruction = htmlSanitizerService.sanitize(qData.instruction)
                    val sanitizedOptionA = htmlSanitizerService.sanitize(qData.optionA)
                    val sanitizedOptionB = htmlSanitizerService.sanitize(qData.optionB)
                    val sanitizedOptionC = htmlSanitizerService.sanitize(qData.optionC)
                    val sanitizedOptionD = htmlSanitizerService.sanitize(qData.optionD)
                    val sanitizedOptionE = htmlSanitizerService.sanitize(qData.optionE)
                    
                    // Total size validation (roughly 1MB per question)
                    val totalSize = (sanitizedInstruction?.length ?: 0) + 
                                   sanitizedQuestionText.length + 
                                   sanitizedOptionA.length + 
                                   sanitizedOptionB.length + 
                                   (sanitizedOptionC?.length ?: 0) + 
                                   (sanitizedOptionD?.length ?: 0) + 
                                   (sanitizedOptionE?.length ?: 0)
                    
                    if (totalSize > 1024 * 1024) {
                        throw RuntimeException("Question content too large")
                    }

                    val question = Question(
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
            model.addAttribute("message", "New questions added successfully!")
            // Return a redirect or a script to refresh the page to show new questions in the preview list
            return "fragments/success :: success-message"
        } catch (e: Exception) {
            model.addAttribute("error", "Error saving questions. Please check your input.")
            return "fragments/error :: error-message"
        }
    }

    @GetMapping("/examinations/{id}/questions/{questionId}")
    @ResponseBody
    fun getQuestion(@PathVariable id: UUID, @PathVariable questionId: UUID, session: HttpSession): QuestionDto {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: throw RuntimeException("School not selected")
            
        val question = questionRepository.findById(questionId).orElseThrow {
            RuntimeException("Question not found")
        }
        
        // Security Check: Ensure question belongs to the selected school and examination
        if (question.schoolId != selectedSchoolId || question.examination.id != id) {
            throw RuntimeException("Unauthorized access")
        }
        
        return QuestionDto(
            id = question.id,
            instruction = question.instruction,
            questionText = question.questionText,
            optionA = question.optionA,
            optionB = question.optionB,
            optionC = question.optionC,
            optionD = question.optionD,
            optionE = question.optionE,
            correctAnswer = question.correctAnswer,
            marks = question.marks ?: 1.0
        )
    }

    @PostMapping("/examinations/{id}/questions/{questionId}/update")
    fun updateSingleQuestion(
        @PathVariable id: UUID,
        @PathVariable questionId: UUID,
        @ModelAttribute qData: QuestionDto,
        session: HttpSession,
        model: Model
    ): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "fragments/error :: error-message"
            
        try {
            val question = questionRepository.findById(questionId).orElseThrow {
                RuntimeException("Question not found")
            }
            
            // Security Check: Ensure question belongs to the selected school and examination
            if (question.schoolId != selectedSchoolId || question.examination.id != id) {
                return "fragments/error :: error-message"
            }

            val sanitizedQuestionText = htmlSanitizerService.sanitize(qData.questionText)
            if (sanitizedQuestionText.isBlank()) {
                throw RuntimeException("Question text cannot be empty")
            }

            val sanitizedInstruction = htmlSanitizerService.sanitize(qData.instruction)
            val sanitizedOptionA = htmlSanitizerService.sanitize(qData.optionA)
            val sanitizedOptionB = htmlSanitizerService.sanitize(qData.optionB)
            val sanitizedOptionC = htmlSanitizerService.sanitize(qData.optionC)
            val sanitizedOptionD = htmlSanitizerService.sanitize(qData.optionD)
            val sanitizedOptionE = htmlSanitizerService.sanitize(qData.optionE)

            // Total size validation
            val totalSize = (sanitizedInstruction?.length ?: 0) + 
                           sanitizedQuestionText.length + 
                           sanitizedOptionA.length + 
                           sanitizedOptionB.length + 
                           (sanitizedOptionC?.length ?: 0) + 
                           (sanitizedOptionD?.length ?: 0) + 
                           (sanitizedOptionE?.length ?: 0)
            
            if (totalSize > 1024 * 1024) {
                throw RuntimeException("Question content too large")
            }

            question.apply {
                instruction = sanitizedInstruction
                questionText = sanitizedQuestionText
                optionA = sanitizedOptionA
                optionB = sanitizedOptionB
                optionC = sanitizedOptionC
                optionD = sanitizedOptionD
                optionE = sanitizedOptionE
                correctAnswer = qData.correctAnswer
                marks = qData.marks
            }

            questionRepository.save(question)
            model.addAttribute("message", "Question updated successfully!")
            return "fragments/success :: success-message"
        } catch (e: Exception) {
            model.addAttribute("error", "Error updating question. Please check your input.")
            return "fragments/error :: error-message"
        }
    }

    @PostMapping("/examinations/{id}/questions/{questionId}/delete")
    fun deleteSingleQuestion(
        @PathVariable id: UUID,
        @PathVariable questionId: UUID,
        session: HttpSession,
        model: Model
    ): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "fragments/error :: error-message"
            
        try {
            val question = questionRepository.findById(questionId).orElseThrow {
                RuntimeException("Question not found")
            }
            
            // Security Check: Ensure question belongs to the selected school and examination
            if (question.schoolId != selectedSchoolId || question.examination.id != id) {
                return "fragments/error :: error-message"
            }

            questionRepository.delete(question)
            model.addAttribute("message", "Question deleted successfully!")
            return "fragments/success :: success-message"
        } catch (e: Exception) {
            model.addAttribute("error", "Error deleting question.")
            return "fragments/error :: error-message"
        }
    }

    @GetMapping("/examinations/{id}/submissions")
    fun viewSubmissions(
        @PathVariable id: UUID,
        model: Model,
        session: HttpSession
    ): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "fragments/error :: error-message"

        val examination = examinationRepository.findById(id).orElseThrow {
            RuntimeException("Examination not found")
        }

        if (examination.schoolId != selectedSchoolId) {
            return "fragments/error :: error-message"
        }

        val submissions = examinationSubmissionRepository.findByExaminationIdWithStudent(id)
        
        model.addAttribute("examination", examination)
        model.addAttribute("submissions", submissions)
        model.addAttribute("isAdmin", true)

        return "admin/assessments/submissions-modal :: submissions-content"
    }

    @PostMapping("/examinations/submissions/{submissionId}/delete")
    fun deleteSubmission(
        @PathVariable submissionId: UUID,
        session: HttpSession,
        model: Model
    ): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "fragments/error :: error-message"

        try {
            val submission = examinationSubmissionRepository.findById(submissionId).orElseThrow {
                RuntimeException("Submission not found")
            }

            if (submission.examination.schoolId != selectedSchoolId) {
                return "fragments/error :: error-message"
            }

            val examId = submission.examination.id!!
            examinationSubmissionRepository.delete(submission)
            
            // Refresh the list
            val submissions = examinationSubmissionRepository.findByExaminationIdWithStudent(examId)
            val examination = examinationRepository.findById(examId).get()
            
            model.addAttribute("examination", examination)
            model.addAttribute("submissions", submissions)
            model.addAttribute("isAdmin", true)
            model.addAttribute("successMessage", "Submission deleted successfully")

            return "admin/assessments/submissions-modal :: submissions-list-container"
        } catch (e: Exception) {
            model.addAttribute("errorMessage", "Error deleting submission: ${e.message}")
            return "fragments/error :: error-message"
        }
    }



    // Hierarchical Selection API Endpoints
    @GetMapping("/api/tracks")
    @ResponseBody
    fun getEducationTracks(authentication: Authentication, session: HttpSession): List<Map<String, Any>> {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: throw RuntimeException("School not selected")
        
        return educationTrackRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
            .map { track ->
                mapOf(
                    "id" to track.id!!,
                    "name" to track.name,
                    "description" to (track.description ?: "")
                )
            }
    }

    @GetMapping("/api/tracks/{trackId}/departments")
    @ResponseBody
    fun getDepartmentsByTrack(@PathVariable trackId: UUID, authentication: Authentication, session: HttpSession): List<Map<String, Any>> {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: throw RuntimeException("School not selected")
        
        return departmentRepository.findByTrackIdAndIsActive(trackId, true)
            .filter { it.track?.schoolId == selectedSchoolId }
            .map { department ->
                mapOf(
                    "id" to department.id!!,
                    "name" to department.name,
                    "description" to (department.description ?: "")
                )
            }
    }

    @GetMapping("/api/departments/{departmentId}/classes")
    @ResponseBody
    fun getClassesByDepartment(@PathVariable departmentId: UUID, authentication: Authentication, session: HttpSession): List<Map<String, Any>> {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: throw RuntimeException("School not selected")
        
        val customUser = authentication.principal as CustomUserDetails
        val isStaff = authentication.authorities.any { it.authority == "ROLE_STAFF" || it.authority == "ROLE_TEACHER" }
        val isAdmin = authentication.authorities.any { it.authority == "ROLE_ADMIN" || it.authority == "ROLE_SCHOOL_ADMIN" || it.authority == "ROLE_SYSTEM_ADMIN" || it.authority == "ROLE_PRINCIPAL" }
        
        var classes = schoolClassRepository.findByDepartmentIdAndIsActive(departmentId, true)
            .filter { it.department?.track?.schoolId == selectedSchoolId }
            
        if (isStaff && !isAdmin) {
             val staff = staffRepository.findByUserIdAndSchoolId(customUser.getUserId()!!, selectedSchoolId)
             if (staff != null) {
                 val currentSession = academicSessionRepository.findBySchoolIdAndIsCurrentSessionAndIsActive(selectedSchoolId, true, true)
                 val currentTerm = currentSession?.let { sess ->
                    termRepository.findByAcademicSessionIdAndIsCurrentTermAndIsActive(sess.id!!, true, true).orElse(null)
                 }
                 
                 if (currentSession != null && currentTerm != null) {
                     val classTeacherAssignments = classTeacherRepository.findByStaffIdAndAcademicSessionIdAndTermIdAndIsActive(
                        staff.id!!, currentSession.id!!, currentTerm.id!!, true
                     )
                     val subjectTeacherAssignments = subjectTeacherRepository.findByStaffIdAndAcademicSessionIdAndTermIdAndIsActive(
                        staff.id!!, currentSession.id!!, currentTerm.id!!, true
                     )
                     
                     val allowedClassIds = mutableSetOf<UUID>()
                     classTeacherAssignments.forEach { it.schoolClass.id?.let { id -> allowedClassIds.add(id) } }
                     subjectTeacherAssignments.forEach { it.schoolClass.id?.let { id -> allowedClassIds.add(id) } }
                     
                     classes = classes.filter { allowedClassIds.contains(it.id) }
                 } else {
                     classes = emptyList()
                 }
             }
        }
        
        return classes.map { schoolClass ->
                mapOf(
                    "id" to schoolClass.id!!,
                    "name" to schoolClass.className,
                    "level" to (schoolClass.gradeLevel ?: ""),
                    "capacity" to schoolClass.maxCapacity
                )
            }
    }

    @GetMapping("/api/classes/{classId}/subjects")
    @ResponseBody
    fun getSubjectsByClass(@PathVariable classId: UUID, authentication: Authentication, session: HttpSession): List<Map<String, Any>> {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: throw RuntimeException("School not selected")
        
        val customUser = authentication.principal as CustomUserDetails
        val isStaff = authentication.authorities.any { it.authority == "ROLE_STAFF" || it.authority == "ROLE_TEACHER" }
        val isAdmin = authentication.authorities.any { it.authority == "ROLE_ADMIN" || it.authority == "ROLE_SCHOOL_ADMIN" || it.authority == "ROLE_SYSTEM_ADMIN" || it.authority == "ROLE_PRINCIPAL" }
        
        var classSubjects = classSubjectRepository.findBySchoolClassIdAndIsActive(classId, true)
            .filter { it.schoolClass?.department?.track?.schoolId == selectedSchoolId }
            
        if (isStaff && !isAdmin) {
             val staff = staffRepository.findByUserIdAndSchoolId(customUser.getUserId()!!, selectedSchoolId)
             if (staff != null) {
                 val currentSession = academicSessionRepository.findBySchoolIdAndIsCurrentSessionAndIsActive(selectedSchoolId, true, true)
                 val currentTerm = currentSession?.let { sess ->
                    termRepository.findByAcademicSessionIdAndIsCurrentTermAndIsActive(sess.id!!, true, true).orElse(null)
                 }
                 
                 if (currentSession != null && currentTerm != null) {
                     val subjectTeacherAssignments = subjectTeacherRepository.findByStaffIdAndAcademicSessionIdAndTermIdAndIsActive(
                        staff.id!!, currentSession.id!!, currentTerm.id!!, true
                     )
                     
                     val allowedSubjectIds = subjectTeacherAssignments
                        .filter { it.schoolClass.id == classId }
                        .map { it.subject.id }
                        .toSet()
                        
                     classSubjects = classSubjects.filter { allowedSubjectIds.contains(it.subject.id) }
                 } else {
                     classSubjects = emptyList()
                 }
             }
        }
        
        return classSubjects.map { classSubject ->
                mapOf(
                    "id" to classSubject.subject?.id!!,
                    "name" to classSubject.subject?.subjectName!!,
                    "code" to (classSubject.subject?.subjectCode ?: ""),
                    "description" to (classSubject.subject?.description ?: "")
                )
            }
    }

    @GetMapping("/api/classes/{classId}/students")
    @ResponseBody
    fun getStudentsByClass(
        @PathVariable classId: UUID, 
        @RequestParam(required = false) sessionYear: String?,
        @RequestParam(required = false) sessionId: UUID?,
        @RequestParam(required = false) term: String?,
        authentication: Authentication, 
        session: HttpSession
    ): List<Map<String, Any?>> {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return emptyList()

        return try {
            println("DEBUG: getStudentsByClass called with classId=$classId, sessionYear=$sessionYear, sessionId=$sessionId, term=$term")

            // Resolve session ID
            val resolvedSessionId = if (sessionId != null) {
                sessionId
            } else if (sessionYear != null) {
                val session = academicSessionRepository.findBySchoolIdAndSessionYearAndIsActive(selectedSchoolId, sessionYear, true)
                session?.id
            } else {
                null
            }
            
            println("DEBUG: Final resolvedSessionId: $resolvedSessionId")

            // Get students enrolled in the class for the specified session/term
            val studentClasses = if (resolvedSessionId != null) {
                // Get students for specific session
                val results = studentClassRepository.findBySchoolClassIdAndAcademicSessionIdAndIsActive(
                    classId, resolvedSessionId, true
                )
                println("DEBUG: Found ${results.size} students for session ID $resolvedSessionId")
                results
            } else {
                // Get all active students in the class
                val results = studentClassRepository.findBySchoolClassIdAndIsActive(classId, true)
                    .filter { it.schoolId == selectedSchoolId }
                println("DEBUG: Found ${results.size} students (all active)")
                results
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

    @GetMapping("/api/sessions")
    @ResponseBody
    fun getAcademicSessions(authentication: Authentication, session: HttpSession): List<Map<String, Any>> {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: throw RuntimeException("School not selected")
        
        return academicSessionRepository.findBySchoolIdAndIsActiveOrderByYearDesc(selectedSchoolId, true)
            .map { academicSession ->
                mapOf(
                    "id" to academicSession.id!!,
                    "sessionYear" to academicSession.sessionYear,
                    "sessionName" to academicSession.sessionName,
                    "startDate" to academicSession.startDate.toString(),
                    "endDate" to (academicSession.endDate?.toString() ?: ""),
                    "isCurrent" to academicSession.isCurrentSession
                )
            }
    }

    @GetMapping("/api/sessions/{sessionId}/terms")
    @ResponseBody
    fun getTermsBySession(@PathVariable sessionId: UUID, authentication: Authentication, session: HttpSession): List<Map<String, Any>> {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: throw RuntimeException("School not selected")
        
        return termRepository.findByAcademicSessionIdAndIsActiveOrderByStartDate(sessionId, true)
            .filter { it.academicSession.schoolId == selectedSchoolId }
            .map { term ->
                mapOf(
                    "id" to term.id!!,
                    "termName" to term.termName,
                    "startDate" to term.startDate.toString(),
                    "endDate" to term.endDate.toString(),
                    "isCurrent" to term.isCurrentTerm,
                    "status" to term.status
                )
            }
    }

    @GetMapping("/api/classes/{classId}/hierarchy")
    @ResponseBody
    fun getClassHierarchy(@PathVariable classId: UUID, authentication: Authentication, session: HttpSession): Map<String, Any> {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: throw RuntimeException("School not selected")
        
        val schoolClass = schoolClassRepository.findById(classId).orElseThrow { 
            RuntimeException("Class not found") 
        }
        
        // Verify the class belongs to the selected school
        if (schoolClass.department?.track?.schoolId != selectedSchoolId) {
            throw RuntimeException("Class does not belong to selected school")
        }
        
        return mapOf(
            "classId" to schoolClass.id!!,
            "className" to schoolClass.className,
            "departmentId" to (schoolClass.department?.id ?: 0),
            "departmentName" to (schoolClass.department?.name ?: ""),
            "trackId" to (schoolClass.department?.track?.id ?: 0),
            "trackName" to (schoolClass.department?.track?.name ?: "")
        )
    }

    @GetMapping("/api/subjects/by-scope")
    @ResponseBody
    fun getSubjectsByScope(
        @RequestParam scopeType: String,
        @RequestParam(required = false) trackId: UUID?,
        @RequestParam(required = false) departmentId: UUID?,
        @RequestParam(required = false) classId: UUID?,
        authentication: Authentication,
        session: HttpSession
    ): List<SubjectWithClass> {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: throw RuntimeException("School not selected")

        return when (scopeType) {
            "SCHOOL" -> {
                val classes = schoolClassRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
                classes.flatMap { cls ->
                    val classSubjects = classSubjectRepository.findBySchoolClassIdAndIsActive(cls.id!!, true)
                    classSubjects.map { cs ->
                        SubjectWithClass(
                            id = cs.subject.id!!,
                            name = cs.subject.subjectName,
                            classId = cls.id!!,
                            className = cls.className
                        )
                    }
                }
            }
            "TRACK" -> {
                val track = educationTrackRepository.findById(trackId!!).orElseThrow()
                if (track.schoolId != selectedSchoolId) throw RuntimeException("Unauthorized access")

                val departments = departmentRepository.findByTrackIdAndIsActive(track.id!!, true)
                val classes = departments.flatMap { dept ->
                    schoolClassRepository.findByDepartmentIdAndIsActive(dept.id!!, true)
                }
                classes.flatMap { cls ->
                    val classSubjects = classSubjectRepository.findBySchoolClassIdAndIsActive(cls.id!!, true)
                    classSubjects.map { cs ->
                        SubjectWithClass(
                            id = cs.subject.id!!,
                            name = cs.subject.subjectName,
                            classId = cls.id!!,
                            className = cls.className
                        )
                    }
                }
            }
            "DEPARTMENT" -> {
                val department = departmentRepository.findById(departmentId!!).orElseThrow()
                if (department.schoolId != selectedSchoolId) throw RuntimeException("Unauthorized access")

                val classes = schoolClassRepository.findByDepartmentIdAndIsActive(department.id!!, true)
                classes.flatMap { cls ->
                    val classSubjects = classSubjectRepository.findBySchoolClassIdAndIsActive(cls.id!!, true)
                    classSubjects.map { cs ->
                        SubjectWithClass(
                            id = cs.subject.id!!,
                            name = cs.subject.subjectName,
                            classId = cls.id!!,
                            className = cls.className
                        )
                    }
                }
            }
            "CLASS" -> {
                val cls = schoolClassRepository.findById(classId!!).orElseThrow()
                if (cls.schoolId != selectedSchoolId) throw RuntimeException("Unauthorized access")
                
                val classSubjects = classSubjectRepository.findBySchoolClassIdAndIsActive(cls.id!!, true)
                classSubjects.map { cs ->
                    SubjectWithClass(
                        id = cs.subject.id!!,
                        name = cs.subject.subjectName,
                        classId = cls.id!!,
                        className = cls.className
                    )
                }
            }
            else -> emptyList<SubjectWithClass>()
        }
    }

    @PostMapping("/examinations/bulk-create")
    @ResponseBody
    fun bulkCreateExaminations(
        @RequestBody request: BulkCreateRequest,
        authentication: Authentication,
        session: HttpSession
    ): BulkCreateResponse {
        val customUser = authentication.principal as CustomUserDetails
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: throw RuntimeException("School not selected")

        val subjects: List<Triple<Subject, SchoolClass, ClassSubject>> = when (request.scopeType) {
            "SCHOOL" -> {
                val classes = schoolClassRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
                classes.flatMap { cls ->
                    classSubjectRepository.findBySchoolClassIdAndIsActive(cls.id!!, true).map { cs ->
                        Triple(cs.subject, cls, cs)
                    }
                }
            }
            "TRACK" -> {
                val track = educationTrackRepository.findById(request.scopeId!!).orElseThrow()
                if (track.schoolId != selectedSchoolId) throw RuntimeException("Unauthorized access")
                
                val departments = departmentRepository.findByTrackIdAndIsActive(track.id!!, true)
                val classes = departments.flatMap { dept ->
                    schoolClassRepository.findByDepartmentIdAndIsActive(dept.id!!, true)
                }
                classes.flatMap { cls ->
                    classSubjectRepository.findBySchoolClassIdAndIsActive(cls.id!!, true).map { cs ->
                        Triple(cs.subject, cls, cs)
                    }
                }
            }
            "DEPARTMENT" -> {
                val department = departmentRepository.findById(request.scopeId!!).orElseThrow()
                if (department.schoolId != selectedSchoolId) throw RuntimeException("Unauthorized access")
                
                val classes = schoolClassRepository.findByDepartmentIdAndIsActive(department.id!!, true)
                classes.flatMap { cls ->
                    classSubjectRepository.findBySchoolClassIdAndIsActive(cls.id!!, true).map { cs ->
                        Triple(cs.subject, cls, cs)
                    }
                }
            }
            "CLASS" -> {
                val cls = schoolClassRepository.findById(request.scopeId!!).orElseThrow()
                if (cls.schoolId != selectedSchoolId) throw RuntimeException("Unauthorized access")
                
                classSubjectRepository.findBySchoolClassIdAndIsActive(cls.id!!, true).map { cs ->
                    Triple(cs.subject, cls, cs)
                }
            }
            else -> emptyList<Triple<Subject, SchoolClass, ClassSubject>>()
        }

        var created = 0
        var skipped = 0

        subjects.forEach { (subject, schoolClass, _) ->
            val existing = examinationRepository.findBySubjectIdAndSchoolClassIdAndTermAndSessionAndIsActive(
                subject.id!!,
                schoolClass.id!!,
                request.term,
                request.session,
                true
            )

            if (existing.any { it.examType == request.examType }) {
                skipped++
            } else {
                val examination = Examination().apply {
                    this.schoolId = selectedSchoolId
                    this.subject = subject
                    this.schoolClass = schoolClass
                    this.examType = request.examType
                    this.term = request.term
                    this.session = request.session
                    this.title = "${request.term} ${request.examType} - ${subject.subjectName} (${schoolClass.className})"
                    this.durationMinutes = request.durationMinutes
                    this.totalMarks = request.totalMarks
                    this.isActive = true
                    this.isPublished = false
                    this.createdAt = LocalDateTime.now()
                    this.createdBy = customUser.getUserId()!!
                }
                examinationRepository.save(examination)
                created++
            }
        }

        return BulkCreateResponse(
            created = created,
            skipped = skipped,
            message = "Successfully created $created examinations. $skipped duplicates skipped."
        )
    }

}

package com.haneef._school.controller

import com.haneef._school.repository.ExaminationRepository
import com.haneef._school.repository.StudentClassRepository
import com.haneef._school.repository.StudentRepository
import com.haneef._school.service.CustomUserDetails
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.ResponseBody
import com.fasterxml.jackson.databind.ObjectMapper
import java.util.UUID
import java.time.LocalDateTime

@Controller
@RequestMapping("/student/examination")
@PreAuthorize("hasRole('STUDENT')")
class StudentExaminationController(
    private val examinationRepository: ExaminationRepository,
    private val studentRepository: StudentRepository,
    private val studentClassRepository: StudentClassRepository,
    private val examinationSubmissionRepository: com.haneef._school.repository.ExaminationSubmissionRepository,
    private val objectMapper: ObjectMapper
) {

    @GetMapping("/{id}")
    fun takeExamination(@PathVariable id: UUID, model: Model, authentication: Authentication): String {
        val customUser = authentication.principal as CustomUserDetails
        val user = customUser.user
        
        // 1. Fetch the examination with questions
        val examination = examinationRepository.findByIdWithRelationships(id).orElseThrow {
            RuntimeException("Examination not found")
        }

        // 2. SECURITY CHECK: Ensure it is published and active
        if (!examination.isPublished || !examination.isActive) {
            return "redirect:/student/dashboard?error=This examination is not yet available."
        }

        // 3. SECURITY CHECK: Ensure student belongs to this examination's class
        val student = user.studentProfiles.firstOrNull { it.schoolId == examination.schoolId && it.isActive }
            ?: return "redirect:/student/dashboard?error=Profile not found"

        val isEnrolled = studentClassRepository.existsByStudentIdAndSchoolClassIdAndIsActive(
            student.id!!, examination.schoolClass.id!!, true
        )

        if (!isEnrolled) {
            return "redirect:/student/dashboard?error=You are not enrolled in this class."
        }

        // 4. Create or Retrieve Submission
        var submission = examinationSubmissionRepository.findFirstByExaminationIdAndStudentIdOrderByStartedAtDesc(examination.id!!, student.id!!)
        
        if (submission == null || submission.status == "submitted") {
            // New attempt
            submission = com.haneef._school.entity.ExaminationSubmission(
                examination = examination,
                student = student,
                status = "ONGOING",
                startedAt = LocalDateTime.now(),
                attemptCount = (submission?.attemptCount ?: 0) + 1
            ).apply {
                this.schoolId = examination.schoolId
                this.isActive = true
            }
            examinationSubmissionRepository.save(submission)
        }

        model.addAttribute("examination", examination)
        model.addAttribute("submission", submission)
        model.addAttribute("student", student)
        model.addAttribute("user", user)
        model.addAttribute("viewOnly", false)
        model.addAttribute("title", "Taking: ${examination.title}")

        return "student/take-examination"
    }

    @PostMapping("/{id}/submit")
    fun handleSubmission(
        @PathVariable id: UUID,
        @org.springframework.web.bind.annotation.RequestParam allParams: Map<String, String>,
        authentication: Authentication
    ): String {
        val customUser = authentication.principal as CustomUserDetails
        val user = customUser.user
        val examination = examinationRepository.findByIdWithRelationships(id).orElseThrow()
        
        val student = user.studentProfiles.firstOrNull { it.schoolId == examination.schoolId && it.isActive }
            ?: return "redirect:/student/dashboard?error=Profile not found"

        // Find existing ONGOING submission
        val submission = examinationSubmissionRepository.findFirstByExaminationIdAndStudentIdOrderByStartedAtDesc(id, student.id!!)
            ?: return "redirect:/student/dashboard?error=No active submission found"

        // Calculate Score and preserve answers
        var totalScore = 0.0
        val answersList = mutableListOf<Map<String, String>>()
        
        examination.questions.forEach { question ->
            val studentAnswer = allParams["q_${question.id}"] ?: ""
            if (studentAnswer.isNotBlank()) {
                answersList.add(mapOf("question_id" to question.id.toString(), "answer" to studentAnswer))
                if (studentAnswer.trim().equals(question.correctAnswer.trim(), ignoreCase = true)) {
                    totalScore += (question.marks ?: 1.0)
                }
            }
        }

        val finalAnswersJson = objectMapper.writeValueAsString(answersList)

        submission.apply {
            this.status = "submitted"
            this.score = totalScore
            this.submittedAt = LocalDateTime.now()
            this.answersJson = finalAnswersJson
        }

        examinationSubmissionRepository.save(submission)

        return "redirect:/student/dashboard?success=Examination submitted successfully! You scored $totalScore marks."
    }

    @PostMapping("/{id}/auto-save")
    @ResponseBody
    fun autoSave(
        @PathVariable id: UUID,
        @org.springframework.web.bind.annotation.RequestParam answersJson: String,
        authentication: Authentication
    ): Map<String, Any> {
        return try {
            val customUser = authentication.principal as CustomUserDetails
            val student = customUser.user.studentProfiles.first { it.isActive }
            
            val submission = examinationSubmissionRepository.findFirstByExaminationIdAndStudentIdOrderByStartedAtDesc(id, student.id!!)
            
            if (submission != null && submission.status == "ONGOING") {
                submission.answersJson = answersJson
                examinationSubmissionRepository.save(submission)
                mapOf("success" to true)
            } else {
                mapOf("success" to false, "error" to "No ongoing submission")
            }
        } catch (e: Exception) {
            mapOf("success" to false, "error" to (e.message ?: "Unknown error"))
        }
    }

    @GetMapping("/{id}/view")
    fun viewResults(@PathVariable id: UUID, model: Model, authentication: Authentication): String {
        val customUser = authentication.principal as CustomUserDetails
        val user = customUser.user
        
        val examination = examinationRepository.findByIdWithRelationships(id).orElseThrow {
            RuntimeException("Examination not found")
        }

        val student = user.studentProfiles.firstOrNull { it.isActive && it.schoolId == examination.schoolId }
            ?: return "redirect:/student/dashboard?error=Profile not found"

        // Fetch the submission
        val submission = examinationSubmissionRepository.findFirstByExaminationIdAndStudentIdOrderByStartedAtDesc(id, student.id!!)
            ?: return "redirect:/student/dashboard?error=Submission not found"

        model.addAttribute("examination", examination)
        model.addAttribute("submission", submission)
        model.addAttribute("student", student)
        model.addAttribute("user", user)
        model.addAttribute("viewOnly", true)
        model.addAttribute("title", "Results: ${examination.title}")

        return "student/take-examination"
    }
}

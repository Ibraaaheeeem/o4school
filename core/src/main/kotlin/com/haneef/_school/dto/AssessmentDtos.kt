package com.haneef._school.dto

import com.haneef._school.config.NativeDto
import java.util.UUID
import java.time.LocalDateTime

@NativeDto
data class StudentReportInfo(
    val id: UUID,
    val admissionNumber: String,
    val fullName: String
)

@NativeDto
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
    val scores: Map<String, Int?> = HashMap(),
    val highestScore: Int? = null,
    val lowestScore: Int? = null,
    val averageScore: Double? = null,
    val classPosition: String? = null
)

@NativeDto
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
    val headTeacherComment: String? = null,
    val schoolName: String? = null,
    val schoolLogoUrl: String? = null,
    val schoolAddress: String? = null,
    val studentPassportPhotoUrl: String? = null
)

@NativeDto
data class SaveAssessmentRequest(
    val studentId: UUID,
    val sessionId: UUID? = null,
    val termId: UUID? = null,
    val session: String? = null,
    val term: String? = null,
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

@NativeDto
data class SubjectScoreInput(
    val subjectId: UUID,
    val ca1: Int? = null,
    val ca2: Int? = null,
    val exam: Int? = null,
    val scores: Map<String, Int?> = HashMap()
)

@NativeDto
data class ImportAssessmentRequest(
    val classId: UUID,
    val session: String,
    val term: String,
    val componentName: String,
    val sources: List<ImportSourceConfig>,
    val divisor: Double = 1.0,
    val studentId: UUID? = null
)

@NativeDto
data class ImportSourceConfig(
    val examType: String,
    val factor: Double = 1.0
)

@NativeDto
data class ExaminationDto(
    val id: UUID? = null,
    val title: String,
    val examType: String,
    val isOnline: Boolean = false,
    val subjectId: UUID,
    val classId: UUID,
    val termId: UUID? = null,
    val sessionId: UUID? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val durationMinutes: Int = 60,
    val totalMarks: Int? = null,
    val isPublished: Boolean = false
)

@NativeDto
data class QuestionDto(
    val id: UUID? = null,
    val instruction: String? = null,
    val questionText: String,
    val explanation: String? = null,
    val questionType: String = "multiple_choice",
    val optionA: String? = null,
    val optionB: String? = null,
    val optionC: String? = null,
    val optionD: String? = null,
    val optionE: String? = null,
    val correctAnswer: String,
    val marks: Double = 1.0
)

@NativeDto
data class QuestionListDto(
    val questions: List<QuestionDto> = emptyList()
)

@NativeDto
data class BulkCreateRequest(
    val examType: String,
    val term: String? = null,
    val session: String? = null,
    val scopeType: String,
    val scopeId: UUID?,
    val durationMinutes: Int,
    val totalMarks: Int,
    val startTime: LocalDateTime? = null,
    val endTime: LocalDateTime? = null,
    val isOnline: Boolean = false
)

@NativeDto
data class BulkCreateResponse(
    val created: Int,
    val skipped: Int,
    val message: String
)

@NativeDto
data class SubjectWithClass(
    val id: UUID,
    val name: String,
    val classId: UUID,
    val className: String
)

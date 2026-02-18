package com.haneef._school.dto

import java.util.UUID
import java.time.LocalDateTime

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

data class QuestionListDto(
    val questions: List<QuestionDto> = emptyList()
)

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

data class BulkCreateResponse(
    val created: Int,
    val skipped: Int,
    val message: String
)

data class SubjectWithClass(
    val id: UUID,
    val name: String,
    val classId: UUID,
    val className: String
)

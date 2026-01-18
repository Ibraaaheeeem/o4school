package com.haneef._school.dto

import java.util.UUID
import java.time.LocalDateTime

data class ExaminationDto(
    val id: UUID? = null,
    val title: String,
    val examType: String,
    val subjectId: UUID,
    val classId: UUID,
    val term: String,
    val sessionYear: String,
    val startTime: LocalDateTime? = null,
    val endTime: LocalDateTime? = null,
    val durationMinutes: Int = 60,
    val totalMarks: Int? = null
)

data class QuestionDto(
    val id: UUID? = null,
    val instruction: String? = null,
    val questionText: String,
    val optionA: String,
    val optionB: String,
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
    val term: String,
    val session: String,
    val scopeType: String,
    val scopeId: UUID?,
    val durationMinutes: Int,
    val totalMarks: Int
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

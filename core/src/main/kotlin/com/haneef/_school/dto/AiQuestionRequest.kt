package com.haneef._school.dto

import com.haneef._school.config.NativeDto

import java.util.UUID

@NativeDto
data class TopicRequest(
    val topic: String,
    val questionCount: Int
)

@NativeDto
data class AiQuestionRequest(
    val topics: List<TopicRequest>,
    val optionsCount: Int,
    val subjectName: String? = null,
    val className: String? = null,
    val gradeLevel: String? = null
)

@NativeDto
data class AiQuestionResponse(
    val questions: List<GeneratedQuestionDto>
)

@NativeDto
data class GeneratedQuestionDto(
    val instruction: String? = null,
    val questionText: String,
    val explanation: String? = null,
    val optionA: String,
    val optionB: String,
    val optionC: String? = null,
    val optionD: String? = null,
    val optionE: String? = null,
    val correctAnswer: String, // A, B, C, D, E
    val marks: Double = 1.0
)

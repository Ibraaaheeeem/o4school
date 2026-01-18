package com.haneef._school.dto

import com.haneef._school.entity.Examination
import com.haneef._school.entity.Question

data class StaffExaminationWithQuestions(
    val examination: Examination,
    val questions: List<Question>
)

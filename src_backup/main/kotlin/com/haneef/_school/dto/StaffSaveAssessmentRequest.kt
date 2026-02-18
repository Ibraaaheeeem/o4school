package com.haneef._school.dto

import java.util.UUID
import com.haneef._school.controller.SubjectScoreInput

data class StaffSaveAssessmentRequest(
    val studentId: UUID,
    val classId: UUID,
    val session: String,
    val term: String,
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

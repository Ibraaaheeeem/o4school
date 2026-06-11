package com.haneef.school.data.models

import com.google.gson.annotations.SerializedName

data class AcademicSession(
    @SerializedName("id") val id: String,
    @SerializedName("school_id") val schoolId: String,
    @SerializedName("name") val name: String,
    @SerializedName("start_date") val startDate: String,
    @SerializedName("end_date") val endDate: String?,
    @SerializedName("is_current") val isCurrent: Boolean,
    @SerializedName("is_active") val isActive: Boolean = true
)

data class Term(
    @SerializedName("id") val id: String,
    @SerializedName("school_id") val schoolId: String,
    @SerializedName("session_id") val sessionId: String,
    @SerializedName("name") val name: String,
    @SerializedName("term_number") val termNumber: Int,
    @SerializedName("start_date") val startDate: String,
    @SerializedName("end_date") val endDate: String?,
    @SerializedName("is_current") val isCurrent: Boolean,
    @SerializedName("is_active") val isActive: Boolean = true
)

data class CreateAcademicSessionRequest(
    @SerializedName("school_id") val schoolId: String,
    @SerializedName("name") val name: String,
    @SerializedName("start_date") val startDate: String,
    @SerializedName("end_date") val endDate: String?,
    @SerializedName("is_current") val isCurrent: Boolean
)

data class CreateTermStudentTransitionRequest(
    @SerializedName("student_id") val studentId: String,
    @SerializedName("source_class_id") val sourceClassId: String,
    @SerializedName("action") val action: String,
    @SerializedName("next_applied_class_id") val nextAppliedClassId: String?
)

data class CreateTermRequest(
    @SerializedName("school_id") val schoolId: String,
    @SerializedName("session_id") val sessionId: String,
    @SerializedName("name") val name: String,
    @SerializedName("term_number") val termNumber: Int,
    @SerializedName("start_date") val startDate: String,
    @SerializedName("end_date") val endDate: String?,
    @SerializedName("is_current") val isCurrent: Boolean,
    @SerializedName("source_term_id") val sourceTermId: String? = null,
    @SerializedName("student_transitions") val studentTransitions: List<CreateTermStudentTransitionRequest> = emptyList()
)

data class SchoolCalendar(
    @SerializedName("id") val id: String,
    @SerializedName("school_id") val schoolId: String,
    @SerializedName("session_id") val sessionId: String,
    @SerializedName("term_id") val termId: String?,
    @SerializedName("event_name") val eventName: String,
    @SerializedName("event_type") val eventType: String,
    @SerializedName("start_date") val startDate: String,
    @SerializedName("end_date") val endDate: String?,
    @SerializedName("color") val color: String?,
    @SerializedName("description") val description: String?,
    @SerializedName("is_exam_period") val isExamPeriod: Boolean?,
    @SerializedName("is_holiday") val isHoliday: Boolean?
)

data class CreateCalendarEventRequest(
    @SerializedName("school_id") val schoolId: String,
    @SerializedName("session_id") val sessionId: String,
    @SerializedName("term_id") val termId: String?,
    @SerializedName("event_name") val eventName: String,
    @SerializedName("event_type") val eventType: String,
    @SerializedName("start_date") val startDate: String,
    @SerializedName("end_date") val endDate: String?,
    @SerializedName("color") val color: String?,
    @SerializedName("description") val description: String?,
    @SerializedName("is_exam_period") val isExamPeriod: Boolean,
    @SerializedName("is_holiday") val isHoliday: Boolean
)

data class UpdateCalendarEventRequest(
    @SerializedName("event_name") val eventName: String,
    @SerializedName("event_type") val eventType: String,
    @SerializedName("start_date") val startDate: String,
    @SerializedName("end_date") val endDate: String?,
    @SerializedName("color") val color: String?,
    @SerializedName("description") val description: String?,
    @SerializedName("is_exam_period") val isExamPeriod: Boolean,
    @SerializedName("is_holiday") val isHoliday: Boolean,
    @SerializedName("session_id") val sessionId: String,
    @SerializedName("term_id") val termId: String?
)
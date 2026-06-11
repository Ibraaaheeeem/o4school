package com.haneef.school.data.models

import com.google.gson.annotations.SerializedName

data class SchoolTimetable(
    @SerializedName("id") val id: String,
    @SerializedName("school_id") val schoolId: String,
    @SerializedName("class_id") val classId: String?,
    @SerializedName("day_of_week") val dayOfWeek: String,
    @SerializedName("activity_type") val activityType: String,
    @SerializedName("start_time") val startTime: String,
    @SerializedName("end_time") val endTime: String,
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String?,
    @SerializedName("is_active") val isActive: Boolean,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String
)

data class CreateSchoolTimetableRequest(
    @SerializedName("school_id") val schoolId: String,
    @SerializedName("class_id") val classId: String? = null,
    @SerializedName("day_of_week") val dayOfWeek: String? = null,
    @SerializedName("days_of_week") val daysOfWeek: List<String>? = null,
    @SerializedName("activity_type") val activityType: String,
    @SerializedName("start_time") val startTime: String,
    @SerializedName("end_time") val endTime: String,
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String? = null
)

data class UpdateSchoolTimetableRequest(
    @SerializedName("school_id") val schoolId: String,
    @SerializedName("class_id") val classId: String? = null,
    @SerializedName("day_of_week") val dayOfWeek: String,
    @SerializedName("activity_type") val activityType: String,
    @SerializedName("start_time") val startTime: String,
    @SerializedName("end_time") val endTime: String,
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String? = null
)

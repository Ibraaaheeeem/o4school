package com.haneef.school.data.models

import com.google.gson.annotations.SerializedName

data class AcademicStructureResponse(
    @SerializedName("school_id")
    val schoolId: String,
    @SerializedName("tracks")
    val tracks: List<AcademicTrackNode>
)

data class AcademicTrackNode(
    @SerializedName("id")
    val id: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("description")
    val description: String? = null,
    @SerializedName("departments")
    val departments: List<DepartmentNode>
)

data class DepartmentNode(
    @SerializedName("id")
    val id: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("description")
    val description: String? = null,
    @SerializedName("classes")
    val classes: List<ClassNode>
)

data class ClassNode(
    @SerializedName("id")
    val id: String,
    @SerializedName("class_name")
    val className: String,
    @SerializedName("class_code")
    val classCode: String? = null,
    @SerializedName("grade_level")
    val gradeLevel: Int? = null
)

data class CreateEducationTrackRequest(
    @SerializedName("school_id")
    val schoolId: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("description")
    val description: String? = null
)

data class CreateDepartmentRequest(
    @SerializedName("school_id")
    val schoolId: String,
    @SerializedName("track_id")
    val trackId: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("description")
    val description: String? = null
)

data class CreateClassRequest(
    @SerializedName("school_id")
    val schoolId: String,
    @SerializedName("department_id")
    val departmentId: String,
    @SerializedName("class_name")
    val className: String,
    @SerializedName("class_code")
    val classCode: String? = null,
    @SerializedName("classroom_location")
    val classroomLocation: String? = null,
    @SerializedName("current_enrollment")
    val currentEnrollment: Int? = null,
    @SerializedName("grade_level")
    val gradeLevel: Int? = null,
    @SerializedName("max_capacity")
    val maxCapacity: Int? = null,
    @SerializedName("scoring_scheme")
    val scoringScheme: String? = null,
    @SerializedName("class_staff_id")
    val classStaffId: String? = null,
    @SerializedName("term")
    val term: String? = null
)

data class InitializeDefaultStructureRequest(
    @SerializedName("school_id")
    val schoolId: String
)

data class InitializeDefaultStructureResponse(
    @SerializedName("school_id")
    val schoolId: String,
    @SerializedName("created")
    val created: Boolean,
    @SerializedName("message")
    val message: String,
    @SerializedName("track_id")
    val trackId: String? = null,
    @SerializedName("department_id")
    val departmentId: String? = null,
    @SerializedName("class_id")
    val classId: String? = null,
    @SerializedName("class_subjects_created")
    val classSubjectsCreated: Long
)

data class SubjectNode(
    @SerializedName("id")
    val id: String,
    @SerializedName("school_id")
    val schoolId: String? = null,
    @SerializedName("subject_name")
    val name: String? = null,
    @SerializedName("subject_code")
    val code: String? = null,
    @SerializedName("description")
    val description: String? = null,
    @SerializedName("credit_hours")
    val creditHours: Int? = null
)

data class SchoolSubjectResponse(
    @SerializedName("id")
    val id: String,
    @SerializedName("subject_id")
    val subjectId: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("code")
    val code: String? = null,
    @SerializedName("description")
    val description: String? = null,
    @SerializedName("linked_classes")
    val linkedClasses: List<LinkedClassResponse>? = null
)

data class LinkedClassResponse(
    @SerializedName("id")
    val id: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("department_name")
    val departmentName: String? = null,
    @SerializedName("track_name")
    val trackName: String? = null
)

data class SaveSchoolSubjectsRequest(
    @SerializedName("school_id")
    val schoolId: String,
    @SerializedName("subject_ids")
    val subjectIds: List<String>
)

data class LinkClassToSubjectRequest(
    @SerializedName("school_id")
    val schoolId: String,
    @SerializedName("class_ids")
    val classIds: List<String>
)

data class UnlinkClassFromSubjectRequest(
    @SerializedName("school_id")
    val schoolId: String,
    @SerializedName("class_id")
    val classId: String
)

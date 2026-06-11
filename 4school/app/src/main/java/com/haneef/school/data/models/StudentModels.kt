package com.haneef.school.data.models

import com.google.gson.annotations.SerializedName

/**
 * Request body for creating a student with a user account
 */
data class CreateStudentRequest(
    @SerializedName("user") val user: CreateStudentUserInfo,
    @SerializedName("student") val student: CreateStudentDetails,
    @SerializedName("student_classes") val studentClasses: List<CreateStudentClassInfo>? = null
)

data class CreateStudentUserInfo(
    @SerializedName("email") val email: String?,
    @SerializedName("phone_number") val phoneNumber: String?,
    @SerializedName("first_name") val firstName: String,
    @SerializedName("middle_name") val middleName: String?,
    @SerializedName("last_name") val lastName: String,
    @SerializedName("gender") val gender: String?,
    @SerializedName("date_of_birth") val dateOfBirth: String?, // YYYY-MM-DD
    @SerializedName("school_slug") val schoolSlug: String
)

data class CreateStudentDetails(
    @SerializedName("student_id") val studentId: String?,
    @SerializedName("school_id") val schoolId: String,
    @SerializedName("admission_number") val admissionNumber: String?,
    @SerializedName("admission_date") val admissionDate: String?, // YYYY-MM-DD
    @SerializedName("graduation_date") val graduationDate: String?, // YYYY-MM-DD
    @SerializedName("academic_status") val academicStatus: String?,
    @SerializedName("current_grade_level") val currentGradeLevel: String?,
    @SerializedName("date_of_birth") val dateOfBirth: String?, // YYYY-MM-DD
    @SerializedName("gender") val gender: String?,
    @SerializedName("previous_school") val previousSchool: String?,
    @SerializedName("special_needs_description") val specialNeedsDescription: String?,
    @SerializedName("transportation_method") val transportationMethod: String?,
    @SerializedName("passport_photo_url") val passportPhotoUrl: String?,
    @SerializedName("has_special_needs") val hasSpecialNeeds: Boolean = false
)

data class CreateStudentClassInfo(
    @SerializedName("class_id") val classId: String,
    @SerializedName("session_id") val sessionId: String,
    @SerializedName("term_id") val termId: String,
    @SerializedName("enrollment_date") val enrollmentDate: String? = null // YYYY-MM-DD
)

/**
 * Detailed Student data DTO for GET/PUT of single student resource
 */
data class StudentDetailDto(
    @SerializedName("id") val id: String,
    @SerializedName("school_id") val schoolId: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("student_id") val studentId: String,
    @SerializedName("admission_number") val admissionNumber: String? = null,
    @SerializedName("admission_date") val admissionDate: String, // YYYY-MM-DD
    @SerializedName("graduation_date") val graduationDate: String? = null, // YYYY-MM-DD
    @SerializedName("academic_status") val academicStatus: String,
    @SerializedName("current_grade_level") val currentGradeLevel: String? = null,
    @SerializedName("date_of_birth") val dateOfBirth: String? = null, // YYYY-MM-DD
    @SerializedName("gender") val gender: String? = null,
    @SerializedName("previous_school") val previousSchool: String? = null,
    @SerializedName("special_needs_description") val specialNeedsDescription: String? = null,
    @SerializedName("transportation_method") val transportationMethod: String? = null,
    @SerializedName("passport_photo_url") val passportPhotoUrl: String? = null,
    @SerializedName("is_new") val isNew: Boolean = true,
    @SerializedName("has_special_needs") val hasSpecialNeeds: Boolean = false,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("is_active") val isActive: Boolean = true,

    // User profile fields (optional, used when updating details)
    @SerializedName("first_name") val firstName: String? = null,
    @SerializedName("last_name") val lastName: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("phone_number") val phoneNumber: String? = null,
    @SerializedName("guardian_name") val guardianName: String? = null,
    @SerializedName("guardian_phone") val guardianPhone: String? = null,
    @SerializedName("guardian_email") val guardianEmail: String? = null,
    @SerializedName("guardian_relationship") val guardianRelationship: String? = null
)

/**
 * Request body for assigning classes to a student
 */
data class AssignStudentClassesRequest(
    @SerializedName("school_id") val schoolId: String,
    @SerializedName("classes") val classes: List<CreateStudentClassInfo>
)

/**
 * Response type for student class assignments
 */
data class StudentClassAssignmentResponse(
    @SerializedName("id") val id: String,
    @SerializedName("school_id") val schoolId: String,
    @SerializedName("student_id") val studentId: String,
    @SerializedName("class_id") val classId: String,
    @SerializedName("class_name") val className: String?,
    @SerializedName("session_id") val sessionId: String,
    @SerializedName("term_id") val termId: String,
    @SerializedName("enrollment_date") val enrollmentDate: String?,
    @SerializedName("is_active") val isActive: Boolean
)

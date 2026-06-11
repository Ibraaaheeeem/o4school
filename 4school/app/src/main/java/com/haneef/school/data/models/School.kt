package com.haneef.school.data.models

import com.google.gson.annotations.SerializedName

/**
 * School data model
 */
data class School(
    @SerializedName("id")
    val id: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("address")
    val address: String,
    @SerializedName("phone")
    val phone: String,
    @SerializedName("email")
    val email: String,
    @SerializedName("website")
    val website: String? = null,
    @SerializedName("logo_url")
    val logoUrl: String? = null,
    @SerializedName("established_year")
    val establishedYear: Int? = null,
    @SerializedName("school_type")
    val schoolType: String,
    @SerializedName("is_active")
    val isActive: Boolean = true
)

/**
 * Student data model
 */
data class Student(
    @SerializedName("id")
    val id: String,
    @SerializedName("student_id")
    val studentId: String,
    @SerializedName("full_name")
    val fullName: String,
    @SerializedName("email")
    val email: String? = null,
    @SerializedName("phone_number")
    val phoneNumber: String? = null,
    @SerializedName("date_of_birth")
    val dateOfBirth: String? = null,
    @SerializedName("gender")
    val gender: String? = null,
    @SerializedName("class_id")
    val classId: String? = null,
    @SerializedName("class_name")
    val className: String? = null,
    @SerializedName("admission_date")
    val admissionDate: String,
    @SerializedName("profile_image_url")
    val profileImageUrl: String? = null,
    @SerializedName("is_active")
    val isActive: Boolean = true
)

data class StaffClassAssignment(
    @SerializedName("id") val id: String,
    @SerializedName("class_id") val classId: String,
    @SerializedName("class_name") val className: String
)

data class StaffSubjectAssignment(
    @SerializedName("id") val id: String,
    @SerializedName("class_id") val classId: String,
    @SerializedName("class_name") val className: String,
    @SerializedName("subject_id") val subjectId: String,
    @SerializedName("subject_name") val subjectName: String
)

/**
 * Staff data model
 */
data class Staff(
    @SerializedName("id")
    val id: String,
    @SerializedName("staff_id")
    val staffId: String,
    @SerializedName("full_name")
    val fullName: String,
    @SerializedName("email")
    val email: String,
    @SerializedName("phone_number")
    val phoneNumber: String,
    @SerializedName("department")
    val department: String,
    @SerializedName("position")
    val position: String,
    @SerializedName("hire_date")
    val hireDate: String,
    @SerializedName("salary")
    val salary: Double? = null,
    @SerializedName("profile_image_url")
    val profileImageUrl: String? = null,
    @SerializedName("is_active")
    val isActive: Boolean = true,
    @SerializedName("is_class_teacher")
    val isClassTeacher: Boolean = false,
    @SerializedName("is_subject_teacher")
    val isSubjectTeacher: Boolean = false,
    @SerializedName("class_teacher_class_name")
    val classTeacherClassName: String? = null,
    @SerializedName("subject_teacher_subjects")
    val subjectTeacherSubjects: String? = null,
    @SerializedName("class_assignments")
    val classAssignments: List<StaffClassAssignment> = emptyList(),
    @SerializedName("subject_assignments")
    val subjectAssignments: List<StaffSubjectAssignment> = emptyList()
)

/**
 * Subject data model
 */
data class Subject(
    @SerializedName("id")
    val id: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("code")
    val code: String,
    @SerializedName("description")
    val description: String? = null,
    @SerializedName("credit_hours")
    val creditHours: Int? = null,
    @SerializedName("department")
    val department: String? = null,
    @SerializedName("is_active")
    val isActive: Boolean = true
)

/**
 * Class data model
 */
data class SchoolClass(
    @SerializedName("id")
    val id: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("level")
    val level: String,
    @SerializedName("capacity")
    val capacity: Int,
    @SerializedName("current_enrollment")
    val currentEnrollment: Int,
    @SerializedName("class_teacher_id")
    val classTeacherId: String? = null,
    @SerializedName("class_teacher_name")
    val classTeacherName: String? = null,
    @SerializedName("academic_year")
    val academicYear: String,
    @SerializedName("is_active")
    val isActive: Boolean = true
)

data class CreateStaffWithUserRequest(
    @SerializedName("school_id") val schoolId: String,
    @SerializedName("email") val email: String,
    @SerializedName("phone_number") val phoneNumber: String?,
    @SerializedName("first_name") val firstName: String,
    @SerializedName("middle_name") val middleName: String?,
    @SerializedName("last_name") val lastName: String,
    @SerializedName("gender") val gender: String?,
    @SerializedName("date_of_birth") val dateOfBirth: String?,
    @SerializedName("staff") val staff: CreateStaffInfo,
    @SerializedName("staff_class_assignments") val staffClassAssignments: List<CreateClassTeacherInfo>?,
    @SerializedName("staff_subject_assignments") val staffSubjectAssignments: List<CreateSubjectTeacherInfo>?
)

data class CreateStaffInfo(
    @SerializedName("staff_id") val staffId: String? = null,
    @SerializedName("employee_number") val employeeNumber: String? = null,
    @SerializedName("designation") val designation: String? = null,
    @SerializedName("hire_date") val hireDate: String? = null,
    @SerializedName("employment_status") val employmentStatus: String? = null,
    @SerializedName("employment_type") val employmentType: String? = null,
    @SerializedName("highest_degree") val highestDegree: String? = null,
    @SerializedName("department") val department: String? = null,
    @SerializedName("is_class_teacher") val isClassTeacher: Boolean? = null,
    @SerializedName("is_subject_teacher") val isSubjectTeacher: Boolean? = null,
    @SerializedName("bank_name") val bankName: String? = null,
    @SerializedName("account_name") val accountName: String? = null,
    @SerializedName("account_number") val accountNumber: String? = null,
    @SerializedName("monthly_deduction") val monthlyDeduction: Double? = null,
    @SerializedName("class_teacher_for") val classTeacherFor: String? = null,
    @SerializedName("years_of_experience") val yearsOfExperience: Int? = null
)

data class CreateClassTeacherInfo(
    @SerializedName("class_id") val classId: String,
    @SerializedName("session_id") val sessionId: String,
    @SerializedName("term_id") val termId: String,
    @SerializedName("assigned_date") val assignedDate: String? = null
)

data class CreateSubjectTeacherInfo(
    @SerializedName("subject_id") val subjectId: String,
    @SerializedName("class_id") val classId: String,
    @SerializedName("session_id") val sessionId: String,
    @SerializedName("term_id") val termId: String,
    @SerializedName("assigned_date") val assignedDate: String? = null
)

data class AssignStaffClassesRequest(
    @SerializedName("school_id") val schoolId: String,
    @SerializedName("staff_class_assignments") val staffClassAssignments: List<CreateClassTeacherInfo>
)

data class AssignStaffSubjectsRequest(
    @SerializedName("school_id") val schoolId: String,
    @SerializedName("staff_subject_assignments") val staffSubjectAssignments: List<CreateSubjectTeacherInfo>
)

data class CurrentScheduleResponse(
    @SerializedName("session_id") val sessionId: String,
    @SerializedName("term_id") val termId: String
)
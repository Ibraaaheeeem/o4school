package com.haneef.school.data.models

import com.google.gson.annotations.SerializedName

/**
 * Parent display model in directory list
 */
data class ParentListResponse(
    @SerializedName("id")
    val id: String,
    @SerializedName("parent_id")
    val parentId: String,
    @SerializedName("full_name")
    val fullName: String,
    @SerializedName("email")
    val email: String,
    @SerializedName("phone_number")
    val phoneNumber: String,
    @SerializedName("is_verified")
    val isVerified: Boolean = false,
    @SerializedName("address")
    val address: String? = null,
    @SerializedName("profile_image_url")
    val profileImageUrl: String? = null,
    @SerializedName("linked_students")
    val linkedStudents: List<ParentLinkedStudent> = emptyList(),
    @SerializedName("message")
    val message: String? = null
)

/**
 * Linked child/student details under a parent
 */
data class ParentLinkedStudent(
    @SerializedName("id")
    val id: String,
    @SerializedName("student_id")
    val studentId: String,
    @SerializedName("full_name")
    val fullName: String,
    @SerializedName("class_name")
    val className: String? = null,
    @SerializedName("profile_image_url")
    val profileImageUrl: String? = null
)

data class CreateParentInfo(
    @SerializedName("occupation") val occupation: String? = null,
    @SerializedName("employer_name") val employerName: String? = null,
    @SerializedName("business_address") val businessAddress: String? = null
)

/**
 * Request body for creating a new parent with user account
 */
data class CreateParentWithUserRequest(
    @SerializedName("school_id") val schoolId: String,
    @SerializedName("email") val email: String,
    @SerializedName("first_name") val firstName: String,
    @SerializedName("last_name") val lastName: String,
    @SerializedName("phone_number") val phoneNumber: String?,
    @SerializedName("gender") val gender: String? = null,
    @SerializedName("date_of_birth") val dateOfBirth: String? = null,
    @SerializedName("parent") val parent: CreateParentInfo
)

/**
 * Request body for updating parent info
 */
data class UpdateParentRequest(
    @SerializedName("full_name") val fullName: String,
    @SerializedName("email") val email: String,
    @SerializedName("phone_number") val phoneNumber: String?,
    @SerializedName("address") val address: String?
)

/**
 * Request body for assigning/linking students to a parent
 */
data class AssignParentStudentsRequest(
    @SerializedName("school_id") val schoolId: String,
    @SerializedName("student_ids") val studentIds: List<String>
)

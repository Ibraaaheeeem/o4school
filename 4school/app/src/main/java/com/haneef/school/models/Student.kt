package com.haneef.school.models

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class Student(
    val id: UUID,
    val schoolId: UUID,
    val userId: UUID,
    val studentId: String,
    val admissionNumber: String?,
    val admissionDate: LocalDate,
    val graduationDate: LocalDate?,
    val academicStatus: String,
    val currentGradeLevel: String?,
    val dateOfBirth: LocalDate?,
    val gender: String?,
    val previousSchool: String?,
    val specialNeedsDescription: String?,
    val transportationMethod: String?,
    val passportPhotoUrl: String?,
    val isNew: Boolean,
    val hasSpecialNeeds: Boolean,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val isActive: Boolean
)

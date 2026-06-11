package com.haneef.school.models

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class Staff(
    val id: UUID,
    val schoolId: UUID,
    val userId: UUID,
    val staffId: String,
    val employeeNumber: String?,
    val designation: String,
    val hireDate: LocalDate,
    val terminationDate: LocalDate?,
    val employmentStatus: String,
    val employmentType: String,
    val highestDegree: String?,
    val department: String?,
    val isClassTeacher: Boolean,
    val isSubjectTeacher: Boolean,
    val bankName: String?,
    val accountName: String?,
    val accountNumber: String?,
    val monthlyDeduction: Double,
    val classTeacherFor: UUID?,
    val yearsOfExperience: Int,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val isActive: Boolean
)

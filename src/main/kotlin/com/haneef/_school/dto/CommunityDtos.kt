package com.haneef._school.dto

import java.time.LocalDate
import java.util.UUID

data class UserDto(
    var firstName: String? = null,
    var lastName: String? = null,
    var middleName: String? = null,
    var email: String? = null,
    var dateOfBirth: LocalDate? = null,
    var gender: String? = null,
    var addressLine1: String? = null,
    var addressLine2: String? = null,
    var city: String? = null,
    var state: String? = null,
    var postalCode: String? = null,
    var phoneNumber: String? = null
)

data class StaffDto(
    var designation: String = "Teacher",
    var employmentType: String = "full_time",
    var highestDegree: String? = null,
    var yearsOfExperience: Int = 0,
    var bankName: String? = null,
    var accountName: String? = null,
    var accountNumber: String? = null,
    var departmentId: UUID? = null
)

data class StudentDto(
    var admissionNumber: String? = null,
    var currentGradeLevel: String? = null,
    var dateOfBirth: LocalDate? = null,
    var gender: String? = null,
    var isNew: Boolean = true,
    var previousSchool: String? = null,
    var transportationMethod: String? = null,
    var hasSpecialNeeds: Boolean = false,
    var specialNeedsDescription: String? = null,
    var passportPhotoUrl: String? = null,
    var classId: UUID? = null
)

data class ParentDto(
    var profession: String? = null,
    var relationship: String? = null,
    var emergencyContact: String? = null,
    var isPrimaryContact: Boolean = false,
    var isEmergencyContact: Boolean = true,
    var isFinanciallyResponsible: Boolean = true,
    var receiveAcademicUpdates: Boolean = true,
    var receiveFinancialUpdates: Boolean = true,
    var receiveDisciplinaryUpdates: Boolean = true
)

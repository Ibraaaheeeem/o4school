package com.haneef._school.dto

import java.util.UUID

data class SchoolDto(
    var name: String? = null,
    var email: String? = null,
    var phone: String? = null,
    var website: String? = null,
    var addressLine1: String? = null,
    var addressLine2: String? = null,
    var city: String? = null,
    var state: String? = null,
    var postalCode: String? = null,
    var country: String? = null,
    var adminName: String? = null,
    var adminEmail: String? = null,
    var adminPhone: String? = null,
    var slug: String? = null,
    var admissionPrefix: String? = null
)

data class EducationTrackDto(
    var name: String? = null,
    var description: String? = null
)

data class DepartmentDto(
    var name: String? = null,
    var description: String? = null,
    var trackId: UUID? = null
)

data class SchoolClassDto(
    var className: String? = null,
    var maxCapacity: Int? = null,
    var departmentId: UUID? = null
)

data class SubjectDto(
    var subjectName: String? = null,
    var subjectCode: String? = null,
    var isCoreSubject: Boolean = false,
    var description: String? = null,
    var assignedClassIds: List<UUID?> = emptyList()
)

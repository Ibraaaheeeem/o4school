package com.haneef._school.dto

import com.haneef._school.config.NativeDto
import com.haneef._school.entity.SchoolClass
import com.haneef._school.entity.Subject
import java.util.UUID

@NativeDto
data class StaffClassInfo(
    val schoolClass: SchoolClass,
    val isClassTeacher: Boolean,
    val subjects: MutableList<Subject>
)

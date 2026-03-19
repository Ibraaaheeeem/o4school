package com.haneef._school.dto

import com.haneef._school.config.NativeDto

@NativeDto
data class CommunityStats(
    val staffCount: Long,
    val studentCount: Long,
    val parentCount: Long
)

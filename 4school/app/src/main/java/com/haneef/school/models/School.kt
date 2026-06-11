package com.haneef.school.models

import java.time.LocalDateTime
import java.util.UUID

data class School(
    val id: UUID,
    val name: String,
    val slug: String,
    val addressLine1: String,
    val addressLine2: String?,
    val city: String,
    val state: String,
    val postalCode: String?,
    val country: String,
    val status: String?,
    val timezone: String?,
    val currency: String?,
    val language: String?,
    val website: String?,
    val adminName: String,
    val adminEmail: String,
    val adminPhone: String,
    val bannerUrl: String?,
    val logoUrl: String?,
    val primaryColor: String?,
    val secondaryColor: String?,
    val schoolMotto: String?,
    val admissionPrefix: String?,
    val staffIdPrefix: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val isActive: Boolean
)

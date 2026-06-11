package com.haneef.school.models

import java.time.LocalDateTime
import java.util.UUID

data class Parent(
    val id: UUID,
    val userId: UUID,
    val schoolId: UUID,
    val isPrimaryContact: Boolean?,
    val isEmergencyContact: Boolean?,
    val isFinanciallyResponsible: Boolean?,
    val receiveAcademicUpdates: Boolean?,
    val receiveDisciplinaryUpdates: Boolean?,
    val receiveFinancialUpdates: Boolean?,
    val paymentDistributionType: String?,
    val paymentPriorityOrder: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val isActive: Boolean
)

package com.haneef._school.dto

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import org.springframework.format.annotation.DateTimeFormat

data class RecordReimbursementDto(
    val schoolId: UUID,
    val amount: BigDecimal,
    val reference: String,
    val academicSessionId: UUID? = null,
    val termId: UUID? = null,
    val notes: String? = null,
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    val date: LocalDate? = null
)

data class InitiatePayoutDto(
    val schoolId: UUID,
    val amount: BigDecimal,
    val academicSessionId: UUID? = null,
    val termId: UUID? = null
)

package com.haneef._school.dto

import java.math.BigDecimal
import java.time.LocalDate

data class PaymentAnalyticsDto(
    val totalSettlements: BigDecimal,
    val totalReimbursements: BigDecimal,
    val totalManualPayments: BigDecimal,
    val netRevenue: BigDecimal,
    val settlementTrend: List<TrendPoint>,
    val reimbursementTrend: List<TrendPoint>
)

data class TrendPoint(
    val date: LocalDate,
    val amount: BigDecimal
)

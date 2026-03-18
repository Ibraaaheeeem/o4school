package com.haneef._school.dto

import java.util.UUID

data class BroadcastRecipientFilter(
    val recipientType: String? = "ALL", // ALL, STAFF, PARENTS
    val trackIds: List<UUID> = emptyList(),
    val departmentNames: List<String> = emptyList(),
    val classIds: List<UUID> = emptyList(),
    val feeStatus: String? = "ANY", // ANY, OWING, COMPLETED
    val minFeePercentage: Double? = 0.0,
    val maxFeePercentage: Double? = 100.0,
    val minAmountOwed: java.math.BigDecimal? = null,
    val maxAmountOwed: java.math.BigDecimal? = null,
    val studentGender: String? = "ANY", // ANY, MALE, FEMALE
    val studentStatus: String? = "ANY", // ANY, NEW, RETURNING
    val manualUserIds: Set<UUID> = emptySet(),
    val manualPhoneNumbers: Set<String> = emptySet(),
    val excludedUserIds: Set<UUID> = emptySet(),
    val excludedPhoneNumbers: Set<String> = emptySet(),
    val addAll: Boolean = false // If true, adds ALL recipients matching the filters. If false, only manual additions apply.
)

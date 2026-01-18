
package com.haneef._school.dto

import com.haneef._school.entity.DiscountType
import com.haneef._school.entity.FeeCategory
import com.haneef._school.entity.RecurrenceType
import com.haneef._school.entity.GenderEligibility
import com.haneef._school.entity.StudentStatusEligibility
import java.math.BigDecimal
import java.util.UUID

data class FeeItemDto(
    @field:jakarta.validation.constraints.NotBlank(message = "Name is required")
    var name: String? = null,

    @field:jakarta.validation.constraints.NotNull(message = "Amount is required")
    @field:jakarta.validation.constraints.Min(value = 0, message = "Amount must be positive")
    var amount: BigDecimal? = null,

    @field:jakarta.validation.constraints.NotNull(message = "Category is required")
    var feeCategory: FeeCategory? = null,

    var description: String? = null,
    var isMandatory: Boolean = false,
    var isRecurring: Boolean = false,
    var recurrenceType: RecurrenceType? = null,
    var academicSessionId: String? = null,
    var termId: String? = null,
    var genderEligibility: GenderEligibility? = null,
    var studentStatusEligibility: StudentStatusEligibility? = null,
    var staffDiscountType: DiscountType? = null,
    var staffDiscountAmount: BigDecimal? = null
)

data class ClassFeeAssignmentDto(
    var classIds: List<String>? = null,
    var customAmount: BigDecimal? = null
)

data class ManualSettlementDto(
    var parentId: String? = null,
    var amount: BigDecimal? = null,
    var academicSessionId: String? = null,
    var termId: String? = null,
    var notes: String? = null
)

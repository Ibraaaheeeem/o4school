package com.haneef._school.service

import com.haneef._school.entity.SchoolSubscription
import com.haneef._school.entity.ServiceFeature
import com.haneef._school.entity.ServiceUsageLog
import com.haneef._school.entity.SubscriptionStatus
import com.haneef._school.repository.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Service
class SubscriptionService(
    private val subscriptionRepository: SchoolSubscriptionRepository,
    private val usageLogRepository: ServiceUsageLogRepository,
    private val schoolRepository: SchoolRepository,
    private val userRepository: UserRepository,
    private val studentRepository: StudentRepository,
    @Value("\${fourschool.subscription.rate:1000}") private val subscriptionRate: Long
) {

    fun getSubscription(schoolId: UUID): SchoolSubscription {
        return subscriptionRepository.findBySchoolId(schoolId) ?: createDefaultSubscription(schoolId)
    }

    private fun createDefaultSubscription(schoolId: UUID): SchoolSubscription {
        val school = schoolRepository.findById(schoolId)
            .orElseThrow { IllegalArgumentException("School not found") }
        
        val sub = SchoolSubscription(
            school = school,
            feeCollectionActive = false,
            whatsappBalance = 0,
            smsBalance = 0,
            aiTokenBalance = 0
        )
        // 4 months free trial initially
        sub.validUntil = LocalDateTime.now().plusMonths(4)
        sub.subscriptionStatus = SubscriptionStatus.ACTIVE
        return subscriptionRepository.save(sub)
    }

    @Transactional
    fun deductTokens(schoolId: UUID, userId: UUID, feature: ServiceFeature, amount: Int, description: String, allowNegative: Boolean = false) {
        if (amount < 0) throw IllegalArgumentException("Deduction amount must be positive")
        
        val sub = getSubscription(schoolId)
        
        // Ensure there are enough funds (unless allowNegative is true)
        when (feature) {
            ServiceFeature.AI_TOKENS -> {
                if (!allowNegative && sub.aiTokenBalance < amount) {
                    throw IllegalStateException("Insufficient AI Token balance. Please top up your subscription.")
                }
                sub.aiTokenBalance -= amount
            }
            ServiceFeature.SMS_MESSAGING -> {
                if (!allowNegative && sub.smsBalance < amount) {
                    throw IllegalStateException("Insufficient SMS balance. Please top up your subscription.")
                }
                sub.smsBalance -= amount
            }
            ServiceFeature.WHATSAPP_MESSAGING -> {
                if (!allowNegative && sub.whatsappBalance < amount) {
                    throw IllegalStateException("Insufficient WhatsApp balance. Please top up your subscription.")
                }
                sub.whatsappBalance -= amount
            }
            ServiceFeature.FEE_COLLECTION -> {
                // Fee collection shouldn't be deducted, it's boolean
                throw IllegalArgumentException("Fee collection is a boolean subscription, not a consumable resource.")
            }
        }

        sub.lastUpdated = LocalDateTime.now()
        subscriptionRepository.save(sub)

        // Create the usage log
        val school = schoolRepository.findById(schoolId).orElseThrow()
        val user = userRepository.findById(userId).orElseThrow()
        
        val log = ServiceUsageLog(
            school = school,
            user = user,
            serviceType = feature,
            amount = amount,
            description = description,
            timestamp = LocalDateTime.now()
        )
        usageLogRepository.save(log)
    }

    @Transactional
    fun topUpTokens(schoolId: UUID, feature: ServiceFeature, amount: Int) {
        if (amount < 0) throw IllegalArgumentException("Top up amount must be positive")
        val sub = getSubscription(schoolId)
        
        when (feature) {
            ServiceFeature.AI_TOKENS -> sub.aiTokenBalance += amount
            ServiceFeature.SMS_MESSAGING -> sub.smsBalance += amount
            ServiceFeature.WHATSAPP_MESSAGING -> sub.whatsappBalance += amount
            ServiceFeature.FEE_COLLECTION -> sub.feeCollectionActive = true
        }
        
        sub.lastUpdated = LocalDateTime.now()
        subscriptionRepository.save(sub)
    }

    @Transactional
    fun updateFeeCollectionSettings(schoolId: UUID, accountNumber: String?, bankName: String?, termsAccepted: Boolean, isActive: Boolean) {
        val sub = getSubscription(schoolId)
        
        sub.feeCollectionActive = isActive
        if (isActive) {
            sub.accountNumber = accountNumber
            sub.bankName = bankName
            sub.termsAccepted = termsAccepted
        } else {
            // Optional: decide if opting out clears the data or just deactivates the flag
            // For now, let's keep the data in case they opt back in but just change the flag
        }
        
        sub.lastUpdated = LocalDateTime.now()
        subscriptionRepository.save(sub)
    }

    /**
     * Get the active student count for a school.
     */
    fun getActiveStudentCount(schoolId: UUID): Long {
        return studentRepository.countBySchoolIdAndIsActive(schoolId, true)
    }

    /**
     * Calculate the expected yearly renewal fee based on active students.
     */
    fun calculateRenewalFee(schoolId: UUID): Long {
        val studentCount = getActiveStudentCount(schoolId)
        return studentCount * subscriptionRate
    }

    /**
     * Renew a school's subscription (adds specified years) and update status to ACTIVE.
     */
    @Transactional
    fun renewSubscription(schoolId: UUID, years: Int = 1) {
        val sub = getSubscription(schoolId)
        
        // If renewing early, add a year to current validity. If already expired, add a year from today.
        val baseDate = if (sub.validUntil != null && sub.validUntil!!.isAfter(LocalDateTime.now())) {
            sub.validUntil!!
        } else {
            LocalDateTime.now()
        }
        
        sub.validUntil = baseDate.plusYears(years.toLong())
        sub.subscriptionStatus = SubscriptionStatus.ACTIVE
        sub.lastUpdated = LocalDateTime.now()
        
        subscriptionRepository.save(sub)
        
        // In a real application, you might record this payment/revenue event, but we simulate success here.
    }
}

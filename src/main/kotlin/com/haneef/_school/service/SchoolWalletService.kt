package com.haneef._school.service

import com.haneef._school.entity.School
import com.haneef._school.entity.SchoolWallet
import com.haneef._school.repository.SchoolWalletRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

@Service
@Transactional
class SchoolWalletService(
    private val schoolWalletRepository: SchoolWalletRepository,
    private val paystackService: PaystackService
) {
    private val logger = LoggerFactory.getLogger(SchoolWalletService::class.java)

    /**
     * Get wallet for a school
     */
    fun getWalletBySchoolId(schoolId: UUID): SchoolWallet? {
        return schoolWalletRepository.findBySchoolId(schoolId)
    }

    /**
     * Check if school has a wallet
     */
    fun hasWallet(schoolId: UUID): Boolean {
        return schoolWalletRepository.existsBySchoolId(schoolId)
    }

    /**
     * Create wallet for a school using Paystack
     */
    fun createWalletForSchool(school: School, preferredBank: String = "wema-bank"): Result<SchoolWallet> {
        try {
            // Check if wallet already exists
            if (hasWallet(school.id!!)) {
                return Result.failure(Exception("Wallet already exists for this school"))
            }

            // Validate school data
            if (school.email.isNullOrBlank()) {
                return Result.failure(Exception("School email is required"))
            }
            if (school.phone.isNullOrBlank()) {
                return Result.failure(Exception("School phone number is required"))
            }

            logger.info("Creating wallet for school: ${school.id}")

            // Step 1: Create Paystack customer (using school details)
            val customerResponse = paystackService.createCustomer(
                email = school.email!!,
                lastName = school.name?.trim()?.substringBefore(" ") ?: "School",
                firstName = school.name?.trim()?.substringAfter(" ") ?: "Account",
                phone = school.phone!!
            )

            if (customerResponse == null || !customerResponse.status || customerResponse.data == null) {
                logger.error("Failed to create Paystack customer for school")
                return Result.failure(Exception("Failed to create customer account: ${customerResponse?.message ?: "Unknown error"}"))
            }

            val customerCode = customerResponse.data.customerCode
            logger.info("Created Paystack customer for school: $customerCode")

            // Step 2: Create dedicated NUBAN account
            val accountResponse = paystackService.createDedicatedAccount(
                customerCode = customerCode,
                preferredBank = preferredBank
            )

            if (accountResponse == null || !accountResponse.status || accountResponse.data == null) {
                logger.error("Failed to create dedicated account for school")
                return Result.failure(Exception("Failed to create dedicated account: ${accountResponse?.message ?: "Unknown error"}"))
            }

            val accountData = accountResponse.data
            logger.info("Created dedicated account for school: ${accountData.accountNumber}")

            // Step 3: Save wallet to database
            val wallet = SchoolWallet(
                school = school,
                customerCode = customerCode,
                accountNumber = accountData.accountNumber,
                accountName = accountData.accountName,
                bankName = accountData.bank.name,
                bankSlug = accountData.bank.slug,
                bankId = accountData.bank.id,
                currency = accountData.currency,
                paystackAccountId = accountData.id,
                assignedAt = parsePaystackDateTime(accountData.assignment?.assignedAt)
            ).apply {
                isActive = accountData.active
            }

            val savedWallet = schoolWalletRepository.save(wallet)
            logger.info("Wallet created successfully for school: ${school.id}")

            return Result.success(savedWallet)

        } catch (e: Exception) {
            logger.error("Error creating wallet for school ${school.id}: ${e.message}", e)
            return Result.failure(e)
        }
    }

    /**
     * Parse Paystack datetime string to LocalDateTime
     */
    private fun parsePaystackDateTime(dateTimeString: String?): LocalDateTime? {
        if (dateTimeString.isNullOrBlank()) return null
        return try {
            LocalDateTime.parse(dateTimeString, DateTimeFormatter.ISO_DATE_TIME)
        } catch (e: Exception) {
            logger.warn("Failed to parse datetime: $dateTimeString", e)
            null
        }
    }
}

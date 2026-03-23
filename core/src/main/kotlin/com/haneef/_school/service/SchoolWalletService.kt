package com.haneef._school.service

import com.haneef._school.entity.School
import com.haneef._school.entity.SchoolWallet
import com.haneef._school.repository.SchoolWalletRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
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
            // Require persisted school id
            val schoolId = school.id
            if (schoolId == null) {
                return Result.failure(IllegalArgumentException("School must have an id before creating a wallet"))
            }

            // Check if wallet already exists
            if (hasWallet(schoolId)) {
                return Result.failure(IllegalStateException("Wallet already exists for this school"))
            }

            // Validate school data
            if (school.email.isNullOrBlank()) {
                return Result.failure(IllegalArgumentException("School email is required"))
            }
            if (school.phone.isNullOrBlank()) {
                return Result.failure(IllegalArgumentException("School phone number is required"))
            }

            logger.info("Creating wallet for school: ${schoolId}")

            // Step 1: Create Paystack customer (using school details)
            val name = school.name?.trim()
            val firstName = name?.substringBefore(" ") ?: "Account"
            val lastName = name?.substringAfter(" ") ?: "School"

            val customerResponse = paystackService.createCustomer(
                email = school.email!!,
                firstName = firstName,
                lastName = lastName,
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

            val savedWallet = try {
                schoolWalletRepository.save(wallet)
            } catch (e: DataIntegrityViolationException) {
                // Possible race: another process created the wallet concurrently. Try to return the existing wallet.
                logger.warn("DataIntegrityViolation when saving wallet for school $schoolId, attempting to fetch existing wallet", e)
                val existing = schoolWalletRepository.findBySchoolId(schoolId)
                if (existing != null) {
                    existing
                } else {
                    throw e
                }
            }
            logger.info("Wallet created successfully for school: ${schoolId}")

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
            // Try ISO_OFFSET_DATE_TIME first (e.g. 2024-01-01T12:00:00+01:00), then fallback to ISO_DATE_TIME
            try {
                java.time.OffsetDateTime.parse(dateTimeString, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toLocalDateTime()
            } catch (_: Exception) {
                LocalDateTime.parse(dateTimeString, DateTimeFormatter.ISO_DATE_TIME)
            }
        } catch (e: Exception) {
            logger.warn("Failed to parse datetime: $dateTimeString", e)
            null
        }
    }
}

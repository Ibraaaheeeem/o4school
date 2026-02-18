package com.haneef._school.service

import com.haneef._school.entity.Parent
import com.haneef._school.entity.PaystackParentWallet
import com.haneef._school.repository.PaystackParentWalletRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

@Service
@Transactional
class PaystackParentWalletService(
    private val paystackParentWalletRepository: PaystackParentWalletRepository,
    private val paystackService: PaystackService,
    private val walletAsyncService: WalletAsyncService
) {

    private val logger = LoggerFactory.getLogger(PaystackParentWalletService::class.java)

    /**
     * Get wallet for a parent
     */
    fun getWalletByParentId(parentId: UUID): PaystackParentWallet? {
        return paystackParentWalletRepository.findByParentId(parentId)
    }

    /**
     * Check if parent has a wallet
     */
    fun hasWallet(parentId: UUID): Boolean {
        return paystackParentWalletRepository.existsByParentId(parentId)
    }

    /**
     * Create wallet for a parent using Paystack
     */
    fun createWalletForParent(parent: Parent, preferredBank: String = "wema-bank"): Result<PaystackParentWallet> {
        try {
            // Check if wallet already exists
            if (hasWallet(parent.id!!)) {
                return Result.failure(Exception("Wallet already exists for this parent"))
            }

            // Validate parent data
            if (parent.user.email.isNullOrBlank()) {
                return Result.failure(Exception("Parent email is required"))
            }
            if (parent.user.phoneNumber.isNullOrBlank()) {
                return Result.failure(Exception("Parent phone number is required"))
            }

            logger.info("Creating initial wallet record for parent: ${parent.id}")

            // Step 1: Create Paystack customer
            val customerResponse = paystackService.createCustomer(
                email = parent.user.email!!,
                firstName = parent.user.firstName ?: "",
                lastName = parent.user.lastName ?: "",
                phone = parent.user.phoneNumber!!
            )

            if (customerResponse == null || !customerResponse.status || customerResponse.data == null) {
                logger.error("Failed to create Paystack customer")
                logger.error(customerResponse.toString())
                return Result.failure(Exception("Failed to create customer account: ${customerResponse?.message ?: "Unknown error"}"))
            }

            val customerCode = customerResponse.data.customerCode
            logger.info("Created Paystack customer: $customerCode")

            // Step 2: Save initial wallet to database (without account details)
            val wallet = PaystackParentWallet(
                parent = parent,
                customerCode = customerCode,
                accountNumber = null,
                accountName = null,
                bankName = "Generating..."
            ).apply {
                schoolId = parent.schoolId
            }

            val savedWallet = paystackParentWalletRepository.save(wallet)
            logger.info("Initial wallet record created for parent: ${parent.id}")

            // Step 3: Trigger asynchronous account generation AFTER transaction commits
            val walletId = savedWallet.id!!
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() {
                    walletAsyncService.generatePaystackAccount(walletId, preferredBank)
                }
            })

            return Result.success(savedWallet)

        } catch (e: Exception) {
            logger.error("Error creating wallet for parent ${parent.id}: ${e.message}", e)
            return Result.failure(e)
        }
    }

    /**
     * Update wallet balance (this would typically be called by a webhook handler)
     */
    fun updateWalletBalance(walletId: UUID, newBalance: java.math.BigDecimal): PaystackParentWallet? {
        val wallet = paystackParentWalletRepository.findById(walletId).orElse(null) ?: return null
        wallet.balance = newBalance
        wallet.updatedAt = LocalDateTime.now()
        return paystackParentWalletRepository.save(wallet)
    }

    /**
     * Deactivate wallet
     */
    fun deactivateWallet(walletId: UUID): Boolean {
        val wallet = paystackParentWalletRepository.findById(walletId).orElse(null) ?: return false
        wallet.isActive = false
        wallet.updatedAt = LocalDateTime.now()
        paystackParentWalletRepository.save(wallet)
        return true
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

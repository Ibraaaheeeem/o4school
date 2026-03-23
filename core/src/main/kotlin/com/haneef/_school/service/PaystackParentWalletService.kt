package com.haneef._school.service

import com.haneef._school.entity.Parent
import com.haneef._school.entity.PaystackParentWallet
import com.haneef._school.repository.PaystackParentWalletRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

@Service
@Transactional
class PaystackParentWalletService(
    private val paystackParentWalletRepository: PaystackParentWalletRepository,
    private val paystackService: PaystackService,
    private val walletAsyncService: WalletAsyncService,
    private val clock: Clock = Clock.systemDefaultZone()
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
            val parentId = parent.id
                ?: return Result.failure(IllegalArgumentException("Parent id is required"))
            val user = parent.user
                ?: return Result.failure(IllegalArgumentException("Parent user is required"))

            // Check if wallet already exists
            if (hasWallet(parentId)) {
                return Result.failure(IllegalStateException("Wallet already exists for this parent"))
            }

            // Validate parent data
            val email = user.email?.takeIf { it.isNotBlank() }
                ?: return Result.failure(IllegalArgumentException("Parent email is required"))
            val phoneNumber = user.phoneNumber?.takeIf { it.isNotBlank() }
                ?: return Result.failure(IllegalArgumentException("Parent phone number is required"))

            logger.info("Creating initial wallet record for parent: {}", parentId)

            // Step 1: Create Paystack customer
            val customerResponse = paystackService.createCustomer(
                email = email,
                firstName = user.firstName ?: "",
                lastName = user.lastName ?: "",
                phone = phoneNumber
            )

            if (customerResponse == null || !customerResponse.status || customerResponse.data == null) {
                logger.error("Failed to create Paystack customer")
                logger.error("Paystack customer creation failed: {}", customerResponse?.message ?: "Unknown error")
                return Result.failure(IllegalStateException("Failed to create customer account: ${customerResponse?.message ?: "Unknown error"}"))
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

            val savedWallet = try {
                paystackParentWalletRepository.save(wallet)
            } catch (e: DataIntegrityViolationException) {
                logger.warn("Wallet creation race detected for parent {}, attempting to fetch existing wallet", parentId, e)
                paystackParentWalletRepository.findByParentId(parentId) ?: throw e
            }
            logger.info("Initial wallet record created for parent: {}", parentId)

            // Step 3: Trigger asynchronous account generation AFTER transaction commits
            val walletId = savedWallet.id!!
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                    override fun afterCommit() {
                        walletAsyncService.generatePaystackAccount(walletId, preferredBank)
                    }
                })
            } else {
                logger.warn("Transaction synchronization is not active; triggering Paystack account generation immediately for wallet {}", walletId)
                walletAsyncService.generatePaystackAccount(walletId, preferredBank)
            }

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
        wallet.updatedAt = LocalDateTime.now(clock)
        return paystackParentWalletRepository.save(wallet)
    }

    /**
     * Deactivate wallet
     */
    fun deactivateWallet(walletId: UUID): Boolean {
        val wallet = paystackParentWalletRepository.findById(walletId).orElse(null) ?: return false
        wallet.isActive = false
        wallet.updatedAt = LocalDateTime.now(clock)
        paystackParentWalletRepository.save(wallet)
        return true
    }
}

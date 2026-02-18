package com.haneef._school.service

import com.haneef._school.entity.Parent
import com.haneef._school.entity.SquadParentWallet
import com.haneef._school.repository.SquadParentWalletRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

@Service
@Transactional
class SquadParentWalletService(
    private val squadParentWalletRepository: SquadParentWalletRepository,
    private val squadService: SquadService,
    private val walletAsyncService: WalletAsyncService
) {

    private val logger = LoggerFactory.getLogger(SquadParentWalletService::class.java)

    /**
     * Get wallet for a parent
     */
    fun getWalletByParentId(parentId: UUID): SquadParentWallet? {
        return squadParentWalletRepository.findByParentId(parentId)
    }

    /**
     * Check if parent has a wallet
     */
    fun hasWallet(parentId: UUID): Boolean {
        return squadParentWalletRepository.existsByParentId(parentId)
    }

    /**
     * Create wallet for a parent using Squad
     */
    fun createWalletForParent(
        parent: Parent,
        bvn: String,
        dob: String,
        gender: String,
        address: String
    ): Result<SquadParentWallet> {
        try {
            // Check if wallet already exists
            if (hasWallet(parent.id!!)) {
                return Result.failure(Exception("Squad wallet already exists for this parent"))
            }

            // Validate parent data
            if (parent.user.email.isNullOrBlank()) {
                return Result.failure(Exception("Parent email is required"))
            }
            if (parent.user.phoneNumber.isNullOrBlank()) {
                return Result.failure(Exception("Parent phone number is required"))
            }

            logger.info("Creating initial Squad wallet record for parent: ${parent.id}")

            // Create initial wallet record
            // Note: Squad uses email as customer identifier usually, or we can generate one.
            val customerIdentifier = parent.user.email!!

            // Call Squad API synchronously to ensure success
            // Sanitize phone number: remove leading '+' if present
            var phone = parent.user.phoneNumber!!
            if (phone.startsWith("+")) {
                phone = phone.substring(1)
            }

            val accountResponse = squadService.createVirtualAccount(
                firstName = parent.user.firstName ?: "",
                lastName = parent.user.lastName ?: "",
                email = parent.user.email!!,
                phone = phone,
                middleName = null,
                bvn = bvn,
                dob = dob,
                gender = gender,
                address = address
            )

            if (accountResponse == null || !accountResponse.success || accountResponse.data == null) {
                val errorMsg = accountResponse?.message ?: "Unknown error from Squad API"
                logger.error("Failed to generate Squad virtual account for parent: ${parent.id}. Response: $errorMsg")
                return Result.failure(Exception(errorMsg))
            }

            val accountData = accountResponse.data!!
            if (accountData.accountNumber == null) {
                logger.error("Squad account number is missing in successful response for parent: ${parent.id}")
                return Result.failure(Exception("Squad account number missing in response"))
            }

            val wallet = SquadParentWallet(
                parent = parent,
                customerIdentifier = accountData.customerIdentifier ?: customerIdentifier,
                accountNumber = accountData.accountNumber,
                accountName = "${accountData.firstName ?: ""} ${accountData.lastName ?: ""}".trim(),
                bankName = accountData.bankName ?: "Squad"
            ).apply {
                schoolId = parent.schoolId
                currency = accountData.currency ?: "NGN"
                assignedAt = LocalDateTime.now()
                isActive = true
            }

            val savedWallet = squadParentWalletRepository.save(wallet)
            logger.info("Squad wallet created successfully for parent: ${parent.id} with account: ${wallet.accountNumber}")

            return Result.success(savedWallet)

        } catch (e: Exception) {
            logger.error("Error creating Squad wallet for parent ${parent.id}: ${e.message}", e)
            return Result.failure(e)
        }
    }

    /**
     * Update wallet balance
     */
    fun updateWalletBalance(walletId: UUID, newBalance: java.math.BigDecimal): SquadParentWallet? {
        val wallet = squadParentWalletRepository.findById(walletId).orElse(null) ?: return null
        wallet.balance = newBalance
        wallet.updatedAt = LocalDateTime.now()
        return squadParentWalletRepository.save(wallet)
    }
}

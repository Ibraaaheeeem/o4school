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
        // Validate parent and user
        val parentId = parent.id ?: return Result.failure(IllegalArgumentException("Parent id is required"))
        if (hasWallet(parentId)) {
            return Result.failure(IllegalStateException("Squad wallet already exists for this parent"))
        }

        val user = parent.user
        if (user == null) {
            return Result.failure(IllegalArgumentException("Parent.user is required"))
        }

        val email = user.email?.takeIf { it.isNotBlank() }
            ?: return Result.failure(IllegalArgumentException("Parent email is required"))
        val rawPhone = user.phoneNumber?.takeIf { it.isNotBlank() }
            ?: return Result.failure(IllegalArgumentException("Parent phone number is required"))

        logger.info("Creating initial Squad wallet record for parent: $parentId")

        // Sanitize phone number: keep digits only
        val phone = rawPhone.replace(Regex("\\D"), "").let { if (it.startsWith("0")) it.dropWhile { ch -> ch == '0' } else it }

        val customerIdentifier = email

        return try {
            val accountResponse = squadService.createVirtualAccount(
                firstName = user.firstName ?: "",
                lastName = user.lastName ?: "",
                email = email,
                phone = phone,
                middleName = null,
                bvn = bvn,
                dob = dob,
                gender = gender,
                address = address
            )

            if (accountResponse == null || !accountResponse.success || accountResponse.data == null) {
                val errorMsg = accountResponse?.message ?: "Unknown error from Squad API"
                logger.error("Failed to generate Squad virtual account for parent: $parentId. Response: $errorMsg")
                return Result.failure(IllegalStateException(errorMsg))
            }

            val accountData = accountResponse.data
            if (accountData?.accountNumber.isNullOrBlank()) {
                logger.error("Squad account number is missing in response for parent: $parentId")
                return Result.failure(IllegalStateException("Squad account number missing in response"))
            }

            val wallet = SquadParentWallet(
                parent = parent,
                customerIdentifier = accountData.customerIdentifier ?: customerIdentifier,
                accountNumber = accountData.accountNumber!!,
                accountName = "${accountData.firstName ?: ""} ${accountData.lastName ?: ""}".trim(),
                bankName = accountData.bankName ?: "Squad"
            ).apply {
                schoolId = parent.schoolId
                currency = accountData.currency ?: "NGN"
                assignedAt = LocalDateTime.now()
                isActive = true
            }

            val savedWallet = squadParentWalletRepository.save(wallet)
            logger.info("Squad wallet created successfully for parent: $parentId with account: ${savedWallet.accountNumber}")
            Result.success(savedWallet)

        } catch (e: Exception) {
            logger.error("Error creating Squad wallet for parent $${parent.id}: ${e.message}", e)
            Result.failure(e)
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
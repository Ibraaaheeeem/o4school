package com.haneef._school.service

import com.haneef._school.repository.PaystackParentWalletRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

@Service

class WalletAsyncService(
    private val paystackParentWalletRepository: PaystackParentWalletRepository,
    private val paystackService: PaystackService,
    private val squadParentWalletRepository: com.haneef._school.repository.SquadParentWalletRepository,
    private val squadService: SquadService
) {
    private val logger = LoggerFactory.getLogger(WalletAsyncService::class.java)

    @Async
    @Transactional
    fun generatePaystackAccount(walletId: UUID, preferredBank: String) {
        try {
            val wallet = paystackParentWalletRepository.findById(walletId).orElse(null) ?: return
            
            logger.info("Asynchronously generating Paystack dedicated account for wallet: $walletId")
            
            val accountResponse = paystackService.createDedicatedAccount(
                customerCode = wallet.customerCode,
                preferredBank = preferredBank
            )

            if (accountResponse == null || !accountResponse.status || accountResponse.data == null) {
                logger.error("Failed to generate Paystack dedicated account for wallet: $walletId")
                return
            }

            val accountData = accountResponse.data
            wallet.accountNumber = accountData.accountNumber
            wallet.accountName = accountData.accountName
            wallet.bankName = accountData.bank.name
            wallet.bankSlug = accountData.bank.slug
            wallet.bankId = accountData.bank.id
            wallet.currency = accountData.currency
            wallet.paystackAccountId = accountData.id
            wallet.assignedAt = parsePaystackDateTime(accountData.assignment?.assignedAt)
            wallet.isActive = accountData.active
            wallet.updatedAt = LocalDateTime.now()

            paystackParentWalletRepository.save(wallet)
            logger.info("Successfully updated Paystack wallet $walletId with account number: ${accountData.accountNumber}")
            
        } catch (e: Exception) {
            logger.error("Error in asynchronous Paystack account generation for wallet $walletId: ${e.message}", e)
        }
    }

    @Async
    @Transactional
    fun generateSquadAccount(
        walletId: UUID,
        bvn: String,
        dob: String,
        gender: String,
        address: String
    ) {
        try {
            val wallet = squadParentWalletRepository.findById(walletId).orElse(null) ?: return
            
            logger.info("Asynchronously generating Squad virtual account for wallet: $walletId")
            
            val parent = wallet.parent
            
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
                logger.error("Failed to generate Squad virtual account for wallet: $walletId. Response: ${accountResponse?.message}")
                // Delete the wallet so the user can try again
                squadParentWalletRepository.delete(wallet)
                return
            }

            val accountData = accountResponse.data!!
            
            if (accountData.accountNumber == null) {
                logger.error("Squad account number is missing in successful response for wallet: $walletId")
                squadParentWalletRepository.delete(wallet)
                return
            }

            wallet.accountNumber = accountData.accountNumber
            wallet.accountName = "${accountData.firstName ?: ""} ${accountData.lastName ?: ""}".trim()
            wallet.bankName = accountData.bankName ?: "Squad"
            wallet.customerIdentifier = accountData.customerIdentifier ?: parent.user.email!!
            wallet.currency = accountData.currency ?: "NGN"
            wallet.assignedAt = LocalDateTime.now()
            wallet.isActive = true
            wallet.updatedAt = LocalDateTime.now()

            squadParentWalletRepository.save(wallet)
            logger.info("Successfully updated Squad wallet $walletId with account number: ${accountData.accountNumber}")
            
        } catch (e: Exception) {
            logger.error("Error in asynchronous Squad account generation for wallet $walletId: ${e.message}", e)
            // Cleanup on error
            try {
                val wallet = squadParentWalletRepository.findById(walletId).orElse(null)
                if (wallet != null) {
                    squadParentWalletRepository.delete(wallet)
                }
            } catch (ex: Exception) {
                logger.error("Failed to cleanup wallet after error", ex)
            }
        }
    }

    private fun parsePaystackDateTime(dateTimeString: String?): LocalDateTime? {
        if (dateTimeString.isNullOrBlank()) return null
        return try {
            LocalDateTime.parse(dateTimeString, DateTimeFormatter.ISO_DATE_TIME)
        } catch (e: Exception) {
            null
        }
    }
}

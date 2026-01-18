package com.haneef._school.service

import com.haneef._school.repository.ParentWalletRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

@Service
class WalletAsyncService(
    private val parentWalletRepository: ParentWalletRepository,
    private val paystackService: PaystackService
) {
    private val logger = LoggerFactory.getLogger(WalletAsyncService::class.java)

    @Async
    @Transactional
    fun generateDedicatedAccount(walletId: UUID, preferredBank: String) {
        try {
            val wallet = parentWalletRepository.findById(walletId).orElse(null) ?: return
            
            logger.info("Asynchronously generating dedicated account for wallet: $walletId")
            
            val accountResponse = paystackService.createDedicatedAccount(
                customerCode = wallet.customerCode,
                preferredBank = preferredBank
            )

            if (accountResponse == null || !accountResponse.status || accountResponse.data == null) {
                logger.error("Failed to generate dedicated account for wallet: $walletId")
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

            parentWalletRepository.save(wallet)
            logger.info("Successfully updated wallet $walletId with account number: ${accountData.accountNumber}")
            
        } catch (e: Exception) {
            logger.error("Error in asynchronous account generation for wallet $walletId: ${e.message}", e)
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

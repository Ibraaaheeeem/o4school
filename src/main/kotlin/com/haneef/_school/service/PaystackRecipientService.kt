package com.haneef._school.service

import com.haneef._school.entity.SchoolBankAccount
import com.haneef._school.repository.SchoolBankAccountRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.util.*

@Service
class PaystackRecipientService(
    private val schoolBankAccountRepository: SchoolBankAccountRepository,
    @Value("\${paystack.secret.key}") private val paystackSecretKey: String
) {

    private val restTemplate = RestTemplate()
    private val paystackBaseUrl = "https://api.paystack.co"

    /**
     * Create a transfer recipient on Paystack
     * Returns the recipient code if successful, null otherwise
     */
    fun createTransferRecipient(bankAccountId: UUID): Result<String> {
        return try {
            val bankAccount = schoolBankAccountRepository.findById(bankAccountId)
                .orElseThrow { IllegalArgumentException("Bank account not found") }

            // Check if recipient code already exists
            if (!bankAccount.recipientCode.isNullOrBlank()) {
                return Result.success(bankAccount.recipientCode!!)
            }

            // Prepare request
            val headers = HttpHeaders().apply {
                set("Authorization", "Bearer $paystackSecretKey")
                contentType = MediaType.APPLICATION_JSON
            }

            val requestBody = mapOf(
                "type" to "nuban",
                "name" to bankAccount.accountName,
                "account_number" to bankAccount.accountNumber,
                "bank_code" to (bankAccount.bankCode ?: ""),
                "currency" to "NGN"
            )

            val request = HttpEntity(requestBody, headers)
            val url = "$paystackBaseUrl/transferrecipient"

            val response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                request,
                Map::class.java
            )

            val responseBody = response.body as? Map<*, *>
            if (responseBody?.get("status") == true) {
                val data = responseBody["data"] as? Map<*, *>
                val recipientCode = data?.get("recipient_code") as? String

                if (recipientCode != null) {
                    // Save recipient code to database
                    bankAccount.recipientCode = recipientCode
                    schoolBankAccountRepository.save(bankAccount)
                    Result.success(recipientCode)
                } else {
                    Result.failure(Exception("Recipient code not found in response"))
                }
            } else {
                val message = responseBody?.get("message") as? String ?: "Unknown error"
                Result.failure(Exception(message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Verify bank account details with Paystack
     */
    fun verifyBankAccount(accountNumber: String, bankCode: String): Result<String> {
        return try {
            val headers = HttpHeaders().apply {
                set("Authorization", "Bearer $paystackSecretKey")
            }

            val request = HttpEntity<Any>(headers)
            val url = "$paystackBaseUrl/bank/resolve?account_number=$accountNumber&bank_code=$bankCode"

            val response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                request,
                Map::class.java
            )

            val responseBody = response.body as? Map<*, *>
            if (responseBody?.get("status") == true) {
                val data = responseBody["data"] as? Map<*, *>
                val accountName = data?.get("account_name") as? String
                if (accountName != null) {
                    Result.success(accountName)
                } else {
                    Result.failure(Exception("Account name not found"))
                }
            } else {
                val message = responseBody?.get("message") as? String ?: "Unable to verify account"
                Result.failure(Exception(message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

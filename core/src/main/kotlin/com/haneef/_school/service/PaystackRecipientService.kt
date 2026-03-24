package com.haneef._school.service

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.haneef._school.config.NativeDto
import com.haneef._school.entity.SchoolBankAccount
import com.haneef._school.repository.SchoolBankAccountRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.util.*

@Service
class PaystackRecipientService(
    private val schoolBankAccountRepository: SchoolBankAccountRepository,
    @Value("\${paystack.secret.key:}") private val paystackSecretKey: String,
    @Value("\${paystack.api.url:https://api.paystack.co}") private val paystackBaseUrl: String,
    private val objectMapper: ObjectMapper
) {

    private val logger = LoggerFactory.getLogger(PaystackRecipientService::class.java)

    private val restTemplate = RestTemplate().apply {
        val requestFactory = org.springframework.http.client.SimpleClientHttpRequestFactory()
        requestFactory.setConnectTimeout(15000)
        requestFactory.setReadTimeout(15000)
        this.requestFactory = requestFactory
    }

    init {
        if (paystackSecretKey.isBlank()) {
            logger.error("Paystack secret key is NOT configured for PaystackRecipientService")
        }
    }

    /**
     * Create a transfer recipient on Paystack
     * Returns the recipient code if successful, null otherwise
     */
    fun createTransferRecipient(bankAccountId: UUID): Result<String> {
        if (paystackSecretKey.isBlank()) {
            return Result.failure(IllegalStateException("Paystack secret key is not configured"))
        }

        return try {
            val bankAccount = schoolBankAccountRepository.findById(bankAccountId)
                .orElseThrow { IllegalArgumentException("Bank account not found") }

            // Check if recipient code already exists
            if (!bankAccount.recipientCode.isNullOrBlank()) {
                return Result.success(bankAccount.recipientCode!!)
            }

            if (bankAccount.bankCode.isNullOrBlank()) {
                return Result.failure(IllegalArgumentException("Bank code is required"))
            }

            if (bankAccount.accountNumber.isNullOrBlank()) {
                return Result.failure(IllegalArgumentException("Account number is required"))
            }

            if (bankAccount.accountName.isNullOrBlank()) {
                return Result.failure(IllegalArgumentException("Account name is required"))
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
                "bank_code" to bankAccount.bankCode,
                "currency" to "NGN"
            )

            val request = HttpEntity(requestBody, headers)
            val url = "$paystackBaseUrl/transferrecipient"

            val response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                request,
                String::class.java
            )

            if (!response.statusCode.is2xxSuccessful) {
                logger.error("Paystack recipient creation failed with status {}", response.statusCode)
                return Result.failure(IllegalStateException("Failed with status ${response.statusCode}"))
            }

            val bodyText = response.body ?: ""
            if (bodyText.isBlank()) {
                return Result.failure(IllegalStateException("Empty response body from Paystack"))
            }

            val responseBody = objectMapper.readValue(bodyText, PaystackRecipientResponse::class.java)
            if (responseBody.status == true) {
                val recipientCode = responseBody.data?.recipientCode

                if (recipientCode != null) {
                    // Save recipient code to database
                    bankAccount.recipientCode = recipientCode
                    schoolBankAccountRepository.save(bankAccount)
                    Result.success(recipientCode)
                } else {
                    Result.failure(Exception("Recipient code not found in response"))
                }
            } else {
                val message = responseBody.message ?: "Unknown error"
                Result.failure(Exception(message))
            }
        } catch (e: HttpClientErrorException) {
            logger.error("Client error creating transfer recipient: {}", e.responseBodyAsString, e)
            Result.failure(e)
        } catch (e: HttpServerErrorException) {
            logger.error("Server error creating transfer recipient", e)
            Result.failure(e)
        } catch (e: Exception) {
            logger.error("Unexpected error creating transfer recipient", e)
            Result.failure(e)
        }
    }

    /**
     * Verify bank account details with Paystack
     */
    fun verifyBankAccount(accountNumber: String, bankCode: String): Result<String> {
        if (paystackSecretKey.isBlank()) {
            return Result.failure(IllegalStateException("Paystack secret key is not configured"))
        }

        if (accountNumber.isBlank()) {
            return Result.failure(IllegalArgumentException("Account number is required"))
        }

        if (bankCode.isBlank()) {
            return Result.failure(IllegalArgumentException("Bank code is required"))
        }

        return try {
            val headers = HttpHeaders().apply {
                set("Authorization", "Bearer $paystackSecretKey")
            }

            val request = HttpEntity<Any>(headers)
            val url = UriComponentsBuilder
                .fromUriString("$paystackBaseUrl/bank/resolve")
                .queryParam("account_number", accountNumber)
                .queryParam("bank_code", bankCode)
                .toUriString()

            val response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                request,
                String::class.java
            )

            if (!response.statusCode.is2xxSuccessful) {
                logger.error("Paystack account verification failed with status {}", response.statusCode)
                return Result.failure(IllegalStateException("Failed with status ${response.statusCode}"))
            }

            val bodyText = response.body ?: ""
            if (bodyText.isBlank()) {
                return Result.failure(IllegalStateException("Empty response body from Paystack"))
            }

            val responseBody = objectMapper.readValue(bodyText, PaystackResolveResponse::class.java)
            if (responseBody.status == true) {
                val accountName = responseBody.data?.accountName
                if (accountName != null) {
                    Result.success(accountName)
                } else {
                    Result.failure(Exception("Account name not found"))
                }
            } else {
                val message = responseBody.message ?: "Unable to verify account"
                Result.failure(Exception(message))
            }
        } catch (e: HttpClientErrorException) {
            logger.error("Client error verifying bank account: {}", e.responseBodyAsString, e)
            Result.failure(e)
        } catch (e: HttpServerErrorException) {
            logger.error("Server error verifying bank account", e)
            Result.failure(e)
        } catch (e: Exception) {
            logger.error("Unexpected error verifying bank account", e)
            Result.failure(e)
        }
    }
}

@NativeDto
data class PaystackRecipientResponse(
    val status: Boolean?,
    val message: String?,
    val data: PaystackRecipientData?
)

@NativeDto
data class PaystackRecipientData(
    @JsonProperty("recipient_code") val recipientCode: String?
)

@NativeDto
data class PaystackResolveResponse(
    val status: Boolean?,
    val message: String?,
    val data: PaystackResolveData?
)

@NativeDto
data class PaystackResolveData(
    @JsonProperty("account_name") val accountName: String?
)

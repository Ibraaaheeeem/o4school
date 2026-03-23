package com.haneef._school.service

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.haneef._school.config.NativeDto
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.*
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException

@Service
@org.springframework.aot.hint.annotation.RegisterReflectionForBinding(
    PaystackProvidersResponse::class,
    PaystackCustomerResponse::class,
    PaystackAccountResponse::class,
    CustomerData::class,
    AccountData::class,
    BankData::class,
    AssignmentData::class,
    ProviderData::class
)
class PaystackService(
    @Value("\${paystack.secret.key:}") private val secretKey: String,
    @Value("\${paystack.api.url:https://api.paystack.co}") private val apiUrl: String,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(PaystackService::class.java)
    
    init {
        if (secretKey.isBlank()) {
            logger.error("Paystack secret key is NOT configured!")
        } else {
            val maskedKey = if (secretKey.length > 10) secretKey.substring(0, 7) + "..." else "***"
            logger.info("PaystackService initialized with key starting with: $maskedKey")
        }
    }

    private val restTemplate = RestTemplate().apply {
        val requestFactory = org.springframework.http.client.SimpleClientHttpRequestFactory()
        requestFactory.setConnectTimeout(15000) // 15 seconds
        requestFactory.setReadTimeout(15000) // 15 seconds
        this.requestFactory = requestFactory
    }

    /**
     * Create a Paystack customer
     */
    fun createCustomer(
        email: String,
        firstName: String,
        lastName: String,
        phone: String
    ): PaystackCustomerResponse? {
        if (secretKey.isBlank()) {
            logger.error("Paystack secret key not configured; cannot create customer")
            return null
        }
        try {
            val url = "$apiUrl/customer"
            val headers = createHeaders()
            
            val requestBody = mapOf(
                "email" to email,
                "first_name" to firstName,
                "last_name" to lastName,
                "phone" to phone
            )
            
            val request = HttpEntity(requestBody, headers)
            
            logger.info("Creating Paystack customer")
            val response = restTemplate.postForEntity(url, request, String::class.java)
            
            if (response.statusCode.is2xxSuccessful) {
                val bodyText = response.body ?: ""
                if (bodyText.isBlank()) {
                    logger.error("Empty response body from Paystack customer creation")
                    return null
                }
                val responseBody = objectMapper.readValue(bodyText, PaystackCustomerResponse::class.java)
                logger.info("Successfully created customer: ${responseBody.data?.customerCode}")
                return responseBody
            }
            
            logger.error("Failed to create customer. Status: ${response.statusCode}")
            return null
            
        } catch (e: HttpClientErrorException) {
            logger.error("Client error creating customer: ${e.message}", e)
            logger.error("Response body: ${e.responseBodyAsString}")
            return null
        } catch (e: HttpServerErrorException) {
            logger.error("Server error creating customer: ${e.message}", e)
            return null
        } catch (e: Exception) {
            logger.error("Error creating customer: ${e.message}", e)
            return null
        }
    }

    /**
     * Create a dedicated NUBAN account for a customer
     */
    fun createDedicatedAccount(
        customerCode: String,
        preferredBank: String = "wema-bank"
    ): PaystackAccountResponse? {
        if (secretKey.isBlank()) {
            logger.error("Paystack secret key not configured; cannot create dedicated account")
            return null
        }
        try {
            val url = "$apiUrl/dedicated_account"
            val headers = createHeaders()
            
            val requestBody = mapOf(
                "customer" to customerCode,
                "preferred_bank" to preferredBank
            )
            
            val request = HttpEntity(requestBody, headers)
            
            logger.info("Creating dedicated account for customer: $customerCode")
            val response = restTemplate.postForEntity(url, request, String::class.java)
            
            if (response.statusCode.is2xxSuccessful) {
                val bodyText = response.body ?: ""
                if (bodyText.isBlank()) {
                    logger.error("Empty response body from Paystack account creation")
                    return null
                }
                val responseBody = objectMapper.readValue(bodyText, PaystackAccountResponse::class.java)
                logger.info("Successfully created account: ${responseBody.data?.accountNumber}")
                return responseBody
            }
            
            logger.error("Failed to create dedicated account. Status: ${response.statusCode}")
            return null
            
        } catch (e: HttpClientErrorException) {
            logger.error("Client error creating dedicated account: ${e.message}", e)
            logger.error("Response body: ${e.responseBodyAsString}")
            return null
        } catch (e: HttpServerErrorException) {
            logger.error("Server error creating dedicated account: ${e.message}", e)
            return null
        } catch (e: Exception) {
            logger.error("Error creating dedicated account: ${e.message}", e)
            return null
        }
    }

    /**
     * Get customer details
     */
    fun getCustomer(customerCode: String): PaystackCustomerResponse? {
        if (secretKey.isBlank()) {
            logger.error("Paystack secret key not configured; cannot get customer")
            return null
        }
        try {
            val url = "$apiUrl/customer/$customerCode"
            val headers = createHeaders()
            val request = HttpEntity<String>(headers)
            
            val response = restTemplate.exchange(url, HttpMethod.GET, request, String::class.java)
            
            if (response.statusCode.is2xxSuccessful) {
                val bodyText = response.body ?: ""
                if (bodyText.isBlank()) return null
                return objectMapper.readValue(bodyText, PaystackCustomerResponse::class.java)
            }
            
            return null
        } catch (e: Exception) {
            logger.error("Error getting customer: ${e.message}", e)
            return null
        }
    }

    /**
     * Get available dedicated account providers
     */
    fun getAvailableProviders(): List<ProviderData> {
        if (secretKey.isBlank()) {
            logger.warn("Paystack secret key not configured; cannot fetch providers")
            return emptyList()
        }
        try {
            val url = "$apiUrl/dedicated_account/available_providers"
            val headers = createHeaders()
            val request = HttpEntity<String>(headers)
            
            logger.info("Fetching available dedicated account providers")
            val response = restTemplate.exchange(url, HttpMethod.GET, request, String::class.java)
            
            if (response.statusCode.is2xxSuccessful) {
                val bodyText = response.body ?: ""
                if (bodyText.isBlank()) return emptyList()
                val responseBody = objectMapper.readValue(bodyText, PaystackProvidersResponse::class.java)
                return responseBody.data ?: emptyList()
            }
            
            logger.warn("Failed to fetch providers. Status: ${response.statusCode}")
            return emptyList()
        } catch (e: Exception) {
            logger.warn("Error fetching providers (using defaults): ${e.message}")
            return emptyList()
        }
    }

    /**
     * Initiate a transfer (payout)
     */
    fun initiateTransfer(
        amount: java.math.BigDecimal,
        recipientCode: String,
        reason: String,
        reference: String
    ): PaystackTransferResponse? {
        if (secretKey.isBlank()) {
            logger.error("Paystack secret key not configured; cannot initiate transfer")
            return null
        }
        try {
            val url = "$apiUrl/transfer"
            val headers = createHeaders()
            
            // Paystack amount is in kobo - fail fast on precision loss
            val amountInKobo: Long = try {
                amount.movePointRight(2).longValueExact()
            } catch (ex: ArithmeticException) {
                logger.error("Invalid transfer amount (fractional kobo): $amount")
                return null
            }
            
            val requestBody = mapOf(
                "source" to "balance",
                "amount" to amountInKobo,
                "recipient" to recipientCode,
                "reason" to reason,
                "reference" to reference
            )
            
            val request = HttpEntity(requestBody, headers)
            
            logger.info("Initiating Paystack transfer of $amount to recipient: $recipientCode")
            val response = restTemplate.postForEntity(url, request, String::class.java)
            
            if (response.statusCode.is2xxSuccessful) {
                val bodyText = response.body ?: ""
                if (bodyText.isBlank()) {
                    logger.error("Empty response body from Paystack transfer initiation")
                    return null
                }
                val responseBody = objectMapper.readValue(bodyText, PaystackTransferResponse::class.java)
                logger.info("Successfully initiated transfer: ${responseBody.data}")
                return responseBody
            }
            
            logger.error("Failed to initiate transfer. Status: ${response.statusCode}")
            return null
            
        } catch (e: HttpClientErrorException) {
            logger.error("Client error initiating transfer: ${e.message}", e)
            logger.error("Response body: ${e.responseBodyAsString}")
            return null
        } catch (e: Exception) {
            logger.error("Error initiating transfer: ${e.message}", e)
            return null
        }
    }

    /**
     * Get Paystack account balance
     */
    fun getAccountBalance(): List<Map<String, Any>> {
        if (secretKey.isBlank()) {
            logger.warn("Paystack secret key not configured; cannot fetch balance")
            return emptyList()
        }
        try {
            val url = "$apiUrl/balance"
            val headers = createHeaders()
            val request = HttpEntity<String>(headers)
            
            val response = restTemplate.exchange(url, HttpMethod.GET, request, String::class.java)
            
            if (response.statusCode.is2xxSuccessful) {
                val bodyText = response.body ?: ""
                if (bodyText.isBlank()) return emptyList()
                val responseBody = objectMapper.readValue(bodyText, PaystackBalanceResponse::class.java)
                return responseBody.data ?: emptyList()
            }
            return emptyList()
        } catch (e: Exception) {
            logger.error("Error fetching Paystack balance: ${e.message}")
            return emptyList()
        }
    }

    private fun createHeaders(): HttpHeaders {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers.set("Authorization", "Bearer $secretKey")
        return headers
    }
}

// Response DTOs
@NativeDto
data class PaystackCustomerResponse(
    val status: Boolean,
    val message: String,
    val data: CustomerData?
)

@NativeDto
data class CustomerData(
    val id: Long,
    @JsonProperty("first_name") val firstName: String?,
    @JsonProperty("last_name") val lastName: String?,
    val email: String,
    @JsonProperty("customer_code") val customerCode: String,
    val phone: String?,
    val metadata: Map<String, Any>?,
    @JsonProperty("risk_action") val riskAction: String?
)

@NativeDto
data class PaystackAccountResponse(
    val status: Boolean,
    val message: String,
    val data: AccountData?
)

@NativeDto
data class AccountData(
    val bank: BankData,
    @JsonProperty("account_name") val accountName: String,
    @JsonProperty("account_number") val accountNumber: String,
    val assigned: Boolean,
    val currency: String,
    val active: Boolean,
    val id: Long,
    @JsonProperty("created_at") val createdAt: String,
    @JsonProperty("updated_at") val updatedAt: String,
    val assignment: AssignmentData?,
    val customer: CustomerData?
)

@NativeDto
data class BankData(
    val name: String,
    val id: Int,
    val slug: String
)

@NativeDto
data class AssignmentData(
    val integration: Long,
    @JsonProperty("assignee_id") val assigneeId: Long,
    @JsonProperty("assignee_type") val assigneeType: String,
    val expired: Boolean,
    @JsonProperty("account_type") val accountType: String,
    @JsonProperty("assigned_at") val assignedAt: String
)

@NativeDto
data class PaystackProvidersResponse(
    val status: Boolean,
    val message: String,
    val data: List<ProviderData>?
)

@NativeDto
data class ProviderData(
    @JsonProperty("provider_slug") val providerSlug: String,
    @JsonProperty("bank_id") val bankId: Int,
    @JsonProperty("bank_name") val bankName: String,
    val id: Int
)

@NativeDto
data class PaystackTransferResponse(
    val status: Boolean,
    val message: String,
    val data: Map<String, Any>?
)

@NativeDto
data class PaystackBalanceResponse(
    val status: Boolean,
    val message: String,
    val data: List<Map<String, Any>>?
)

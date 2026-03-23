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
class SquadService(
    @Value("\${squad.secret.key:}") private val secretKey: String,
    @Value("\${squad.api.url:https://sandbox-api-d.squadco.com}") private val apiUrl: String,
    @Value("\${squad.beneficiary.account:}") private val beneficiaryAccount: String,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(SquadService::class.java)
    private val restTemplate = RestTemplate().apply {
        val requestFactory = org.springframework.http.client.SimpleClientHttpRequestFactory()
        requestFactory.setConnectTimeout(30000)
        requestFactory.setReadTimeout(30000)
        this.requestFactory = requestFactory
    }

    /**
     * Create a Squad Virtual Account for a customer
     */
    fun createVirtualAccount(
        firstName: String,
        lastName: String,
        email: String,
        phone: String,
        middleName: String? = null,
        bvn: String,
        dob: String, // dd/mm/yyyy
        gender: String, // "1" for Male, "2" for Female
        address: String
    ): SquadAccountResponse? {
        if (secretKey.isBlank()) {
            logger.error("Squad secret key is not configured; aborting virtual account creation")
            return SquadAccountResponse(false, "Missing Squad API secret key", null)
        }

        try {
            val url = "$apiUrl/virtual-account"
            val headers = createHeaders()
            
            val requestBody = mutableMapOf(
                "first_name" to firstName,
                "last_name" to lastName,
                "email" to email,
                "mobile_num" to phone,
                "customer_identifier" to email,
                "bvn" to bvn,
                "dob" to dob,
                "gender" to gender,
                "address" to address,
                "beneficiary_account" to beneficiaryAccount
            )
            
            if (middleName != null) requestBody["middle_name"] = middleName
            
            
            val request = HttpEntity(requestBody, headers)
            
            logger.info("Creating Squad virtual account for email: $email")
            val response = restTemplate.postForEntity(url, request, String::class.java)

            if (response.statusCode.is2xxSuccessful) {
                logger.info("Squad API Response: ${response.body}")
                val bodyText = response.body ?: ""
                try {
                    val responseBody = if (bodyText.isNotBlank()) objectMapper.readValue(bodyText, SquadAccountResponse::class.java) else null
                    if (responseBody != null && responseBody.success) {
                        logger.info("Successfully created Squad account: ${responseBody.data?.accountNumber}")
                        return responseBody
                    } else {
                        logger.error("Squad API returned success=false or empty body: ${responseBody?.message}")
                        return responseBody ?: SquadAccountResponse(false, "Empty response body from Squad API", null)
                    }
                } catch (ex: Exception) {
                    logger.error("Failed to parse Squad response body", ex)
                    return SquadAccountResponse(false, "Invalid response from Squad API", null)
                }
            }

            logger.error("Failed to create Squad account. Status: ${response.statusCode}")
            return SquadAccountResponse(false, "Unexpected status ${response.statusCode}", null)
            
        } catch (e: HttpClientErrorException) {
            logger.error("Client error creating Squad account: ${e.message}", e)
            val body = e.responseBodyAsString
            logger.error("Response body: $body")
            return try {
                if (body.isNullOrBlank()) SquadAccountResponse(false, e.message ?: "Unknown client error", null)
                else objectMapper.readValue(body, SquadAccountResponse::class.java)
            } catch (ex: Exception) {
                SquadAccountResponse(false, e.message ?: "Unknown client error", null)
            }
        } catch (e: HttpServerErrorException) {
            logger.error("Server error creating Squad account: ${e.message}", e)
            val body = e.responseBodyAsString
            return SquadAccountResponse(false, body ?: e.message ?: "Server error", null)
        } catch (e: Exception) {
            logger.error("Error creating Squad account: ${e.message}", e)
            return SquadAccountResponse(false, e.message ?: "Unknown error", null)
        }
    }

    /**
     * Create a Dynamic Virtual Account for a transaction
     */
    fun createDynamicVirtualAccount(
        transactionRef: String,
        amount: java.math.BigDecimal,
        email: String
    ): SquadAccountResponse? {
        if (secretKey.isBlank()) {
            logger.error("Squad secret key is not configured; aborting dynamic virtual account creation")
            return SquadAccountResponse(false, "Missing Squad API secret key", null)
        }

        try {
            val url = "$apiUrl/virtual-account/business"
            val headers = createHeaders()

            // Amount in kobo - exact conversion, fail if fractional kobo
            val amountInKobo: Long = try {
                amount.movePointRight(2).longValueExact()
            } catch (ex: ArithmeticException) {
                logger.error("Invalid amount for kobo conversion: $amount", ex)
                return SquadAccountResponse(false, "Invalid amount", null)
            }

            val requestBody = mapOf(
                "transaction_ref" to transactionRef,
                "amount" to amountInKobo,
                "email" to email,
                "currency" to "NGN"
            )

            val request = HttpEntity(requestBody, headers)

            logger.info("Creating Squad dynamic virtual account for ref: $transactionRef")
            val response = restTemplate.postForEntity(url, request, String::class.java)

            if (response.statusCode.is2xxSuccessful) {
                val bodyText = response.body ?: ""
                try {
                    val responseBody = if (bodyText.isNotBlank()) objectMapper.readValue(bodyText, SquadAccountResponse::class.java) else null
                    if (responseBody != null && responseBody.success) {
                        logger.info("Successfully created dynamic account: ${responseBody.data?.accountNumber}")
                        return responseBody
                    } else {
                        logger.error("Squad API returned success=false or empty body: ${responseBody?.message}")
                        return responseBody ?: SquadAccountResponse(false, "Empty response body from Squad API", null)
                    }
                } catch (ex: Exception) {
                    logger.error("Failed to parse Squad response body", ex)
                    return SquadAccountResponse(false, "Invalid response from Squad API", null)
                }
            }

            logger.error("Failed to create dynamic account. Status: ${response.statusCode}")
            return SquadAccountResponse(false, "Unexpected status ${response.statusCode}", null)

        } catch (e: HttpClientErrorException) {
            logger.error("Client error creating dynamic account: ${e.message}", e)
            val body = e.responseBodyAsString
            logger.error("Response body: $body")
            return try {
                if (body.isNullOrBlank()) SquadAccountResponse(false, e.message ?: "Unknown client error", null)
                else objectMapper.readValue(body, SquadAccountResponse::class.java)
            } catch (ex: Exception) {
                SquadAccountResponse(false, e.message ?: "Unknown client error", null)
            }
        } catch (e: HttpServerErrorException) {
            logger.error("Server error creating dynamic account: ${e.message}", e)
            val body = e.responseBodyAsString
            return SquadAccountResponse(false, body ?: e.message ?: "Server error", null)
        } catch (e: Exception) {
            logger.error("Error creating dynamic account: ${e.message}", e)
            return SquadAccountResponse(false, e.message ?: "Unknown error", null)
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
data class SquadAccountResponse(
    val success: Boolean,
    val message: String,
    val data: SquadAccountData?
)

@NativeDto
data class SquadAccountData(
    @JsonProperty("first_name") val firstName: String?,
    @JsonProperty("last_name") val lastName: String?,
    @JsonProperty("virtual_account_number") val accountNumber: String?,
    @JsonProperty("bank_name") val bankName: String?,
    @JsonProperty("bank_code") val bankCode: String?,
    @JsonProperty("customer_identifier") val customerIdentifier: String?,
    val currency: String? = "NGN"
)

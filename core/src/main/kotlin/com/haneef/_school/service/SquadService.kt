package com.haneef._school.service

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
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
            
            if (response.statusCode == HttpStatus.OK || response.statusCode == HttpStatus.CREATED) {
                logger.info("Squad API Response: ${response.body}")
                val responseBody = objectMapper.readValue(response.body, SquadAccountResponse::class.java)
                if (responseBody.success) {
                    logger.info("Successfully created Squad account: ${responseBody.data?.accountNumber}")
                    return responseBody
                } else {
                    logger.error("Squad API returned success=false: ${responseBody.message}")
                    return null
                }
            }
            
            logger.error("Failed to create Squad account. Status: ${response.statusCode}")
            return null
            
        } catch (e: HttpClientErrorException) {
            logger.error("Client error creating Squad account: ${e.message}", e)
            logger.error("Response body: ${e.responseBodyAsString}")
            try {
                return objectMapper.readValue(e.responseBodyAsString, SquadAccountResponse::class.java)
            } catch (ex: Exception) {
                return SquadAccountResponse(false, e.message ?: "Unknown client error", null)
            }
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
        try {
            val url = "$apiUrl/virtual-account/business"
            val headers = createHeaders()
            
            // Amount in kobo
            val amountInKobo = amount.multiply(java.math.BigDecimal(100)).toLong()
            
            val requestBody = mapOf(
                "transaction_ref" to transactionRef,
                "amount" to amountInKobo,
                "email" to email,
                "currency" to "NGN"
            )
            
            val request = HttpEntity(requestBody, headers)
            
            logger.info("Creating Squad dynamic virtual account for ref: $transactionRef")
            val response = restTemplate.postForEntity(url, request, String::class.java)
            
            if (response.statusCode == HttpStatus.OK || response.statusCode == HttpStatus.CREATED) {
                val responseBody = objectMapper.readValue(response.body, SquadAccountResponse::class.java)
                if (responseBody.success) {
                    logger.info("Successfully created dynamic account: ${responseBody.data?.accountNumber}")
                    return responseBody
                } else {
                    logger.error("Squad API returned success=false: ${responseBody.message}")
                    return null
                }
            }
            
            logger.error("Failed to create dynamic account. Status: ${response.statusCode}")
            return null
            
        } catch (e: Exception) {
            logger.error("Error creating dynamic account: ${e.message}", e)
            return null
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
data class SquadAccountResponse(
    val success: Boolean,
    val message: String,
    val data: SquadAccountData?
)

data class SquadAccountData(
    @JsonProperty("first_name") val firstName: String?,
    @JsonProperty("last_name") val lastName: String?,
    @JsonProperty("virtual_account_number") val accountNumber: String?,
    @JsonProperty("bank_name") val bankName: String?,
    @JsonProperty("bank_code") val bankCode: String?,
    @JsonProperty("customer_identifier") val customerIdentifier: String?,
    val currency: String? = "NGN"
)

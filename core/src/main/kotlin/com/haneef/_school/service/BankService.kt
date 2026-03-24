package com.haneef._school.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ResourceLoader
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Service
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import jakarta.annotation.PostConstruct

@Service
class BankService(
    private val resourceLoader: ResourceLoader,
    private val objectMapper: ObjectMapper,
    @Value("\${paystack.secret.key}") private val paystackSecretKey: String
) {
    companion object {
        private val logger = LoggerFactory.getLogger(BankService::class.java)
        private const val PAYSTACK_BANKS_URL = "https://api.paystack.co/bank"
        private const val PAYSTACK_RESOLVE_URL = "https://api.paystack.co/bank/resolve"
    }

    private var banks: List<BankInfo> = emptyList()
    private val restTemplate = RestTemplate(SimpleClientHttpRequestFactory().apply {
        setConnectTimeout(15_000)
        setReadTimeout(15_000)
    })

    @PostConstruct
    fun loadBanks() {
        if (paystackSecretKey.isBlank()) {
            logger.warn("Paystack secret key is not configured; loading banks from local fallback")
            loadBanksFromLocal()
            return
        }

        try {
            val headers = HttpHeaders().apply {
                set("Authorization", "Bearer $paystackSecretKey")
            }
            val entity = HttpEntity<Any>(headers)
            val response = restTemplate.exchange(
                PAYSTACK_BANKS_URL,
                HttpMethod.GET,
                entity,
                Map::class.java
            )

            val body = response.body as? Map<*, *>
            if (body?.get("status") == true) {
                @Suppress("UNCHECKED_CAST")
                val data = body["data"] as? List<Map<*, *>>
                banks = data?.map {
                    BankInfo(
                        name = it["name"] as String,
                        code = it["code"] as String,
                        slug = it["slug"] as? String
                    )
                } ?: emptyList()
            } else {
                // Fallback to local file if API fails
                logger.warn("Paystack bank list response was unsuccessful; falling back to local bank list")
                loadBanksFromLocal()
            }
        } catch (e: Exception) {
            logger.warn("Error fetching banks from Paystack, falling back to local list", e)
            loadBanksFromLocal()
        }
    }

    private fun loadBanksFromLocal() {
        try {
            val resource = resourceLoader.getResource("classpath:banks.json")
            if (resource.exists()) {
                resource.inputStream.use { inputStream ->
                    banks = objectMapper.readValue(inputStream, object : TypeReference<List<BankInfo>>() {})
                }
            } else {
                logger.warn("banks.json local fallback file was not found")
            }
        } catch (e: Exception) {
            logger.error("Error loading banks.json fallback file", e)
        }
    }

    fun getAllBanks(): List<BankInfo> = banks.toList()

    fun getBankByCode(code: String): BankInfo? = banks.find { it.code.equals(code.trim(), ignoreCase = true) }
 
    fun resolveAccountNumber(accountNumber: String, bankCode: String): String? {
        if (paystackSecretKey.isBlank() || accountNumber.isBlank() || bankCode.isBlank()) {
            return null
        }

        return try {
            val headers = HttpHeaders().apply {
                set("Authorization", "Bearer $paystackSecretKey")
            }
            val entity = HttpEntity<Any>(headers)
            val url = UriComponentsBuilder.fromUriString(PAYSTACK_RESOLVE_URL)
                .queryParam("account_number", accountNumber.trim())
                .queryParam("bank_code", bankCode.trim())
                .toUriString()
            val response = restTemplate.exchange(url, HttpMethod.GET, entity, Map::class.java)
 
            val body = response.body as? Map<*, *>
            if (body?.get("status") == true) {
                val data = body["data"] as? Map<*, *>
                data?.get("account_name") as? String
            } else null
        } catch (e: Exception) {
            logger.warn("Error resolving account number for bank code {}", bankCode, e)
            null
        }
    }

    data class BankInfo(
        val name: String,
        val code: String,
        val slug: String? = null
    )
}

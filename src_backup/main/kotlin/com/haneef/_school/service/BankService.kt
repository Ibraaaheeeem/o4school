package com.haneef._school.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ResourceLoader
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import jakarta.annotation.PostConstruct

@Service
class BankService(
    private val resourceLoader: ResourceLoader,
    private val objectMapper: ObjectMapper,
    @Value("\${paystack.secret.key}") private val paystackSecretKey: String
) {
    private var banks: List<BankInfo> = emptyList()
    private val restTemplate = RestTemplate()

    @PostConstruct
    fun loadBanks() {
        try {
            val headers = HttpHeaders().apply {
                set("Authorization", "Bearer $paystackSecretKey")
            }
            val entity = HttpEntity<Any>(headers)
            val response = restTemplate.exchange(
                "https://api.paystack.co/bank",
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
                loadBanksFromLocal()
            }
        } catch (e: Exception) {
            println("Error fetching banks from Paystack: ${e.message}")
            loadBanksFromLocal()
        }
    }

    private fun loadBanksFromLocal() {
        try {
            val resource = resourceLoader.getResource("classpath:banks.json")
            if (resource.exists()) {
                val inputStream = resource.inputStream
                banks = objectMapper.readValue(inputStream, object : TypeReference<List<BankInfo>>() {})
            }
        } catch (e: Exception) {
            println("Error loading banks.json: ${e.message}")
        }
    }

    fun getAllBanks(): List<BankInfo> = banks

    fun getBankByCode(code: String): BankInfo? = banks.find { it.code == code }

    data class BankInfo(
        val name: String,
        val code: String,
        val slug: String? = null
    )
}

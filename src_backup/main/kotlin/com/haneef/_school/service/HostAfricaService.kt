package com.haneef._school.service

import com.haneef._school.entity.School
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64

@Service
class HostAfricaService(
    @Value("\${hostafrica.api.url:https://portal.hostraha.com/modules/addons/DomainsReseller/api/index.php}")
    private val apiUrl: String,
    
    @Value("\${hostafrica.api.username:}")
    private val apiUsername: String,
    
    @Value("\${hostafrica.api.token:}")
    private val apiToken: String
) {
    private val logger = LoggerFactory.getLogger(HostAfricaService::class.java)
    private val restTemplate = RestTemplate()

    fun checkDomainAvailability(domain: String): Boolean {
        logger.info("Checking availability for domain: $domain")
        
        if (apiUsername.isBlank() || apiToken.isBlank()) {
            logger.warn("HostAfrica API credentials not set. Using mock response.")
            return !domain.contains("taken")
        }

        try {
            // Action identified from CheckAvailability.php: domains/lookup
            // Blesta module sends specific fields:
            val tld = if (domain.contains(".")) ".${domain.substringAfterLast(".")}" else ""
            
            val params = mapOf(
                "searchTerm" to domain,
                "punyCodeSearchTerm" to "",
                "tldsToInclude" to listOf(tld),
                "isIdnDomain" to "",
                "premiumEnabled" to ""
            )
            
            val response = callApi("domains/lookup", params)
            
            // Response analysis: The module returns $result[0]["isAvailable"]
            // This implies response is a Map with numeric keys (JSON array treated as map/list)
            // OR checks generic response structure.
            // Let's assume response might be a List or Map.
            
            if (response.containsKey("0")) {
                @Suppress("UNCHECKED_CAST")
                val firstResult = response["0"] as? Map<String, Any>
                return firstResult?.get("isAvailable") == true || firstResult?.get("isAvailable") == "true"
            }
            
            // Fallback check if response itself has keys
            return response["isAvailable"] == true || response["isAvailable"] == "true" || response["status"] == "available"
        } catch (e: Exception) {
            logger.error("Error checking domain availability", e)
            return false
        }
    }

    fun registerDomain(domain: String, school: School): Boolean {
        logger.info("Registering domain: $domain for school: ${school.name}")
        
        if (!isValidDomain(domain)) {
            throw IllegalArgumentException("Invalid domain format")
        }

        if (apiUsername.isBlank() || apiToken.isBlank()) {
            logger.warn("HostAfrica API credentials not set. Simulating registration.")
            return true
        }

        try {
            // Action identified from RegisterDomain.php: order/domains/register
            
            // Construct contact details
            val contact = mapOf(
                "firstname" to (school.adminName?.split(" ")?.firstOrNull() ?: "Admin"),
                "lastname" to (school.adminName?.split(" ")?.lastOrNull() ?: "User"),
                "companyname" to (school.name ?: "School"),
                "email" to (school.adminEmail ?: ""),
                "address1" to (school.addressLine1 ?: "Address"),
                "city" to (school.city ?: "City"),
                "state" to (school.state ?: "State"),
                "postcode" to (school.postalCode ?: "100001"), 
                "country" to "NG",
                "phonenumber" to (school.adminPhone ?: "")
            )
            
            // Construct nameservers map
            val nameservers = mapOf(
                "ns1" to "ns1.hostafrica.com",
                "ns2" to "ns2.hostafrica.com"
            )
            
            // Construct full payload
            val params = mapOf(
                "domain" to domain,
                "regperiod" to 1, // Defaulting to 1 year
                "domainfields" to getEmptyPhpArrayBase64(),
                "addons" to emptyList<Any>(),
                "nameservers" to nameservers,
                "contacts" to mapOf(
                    "tech" to contact,
                    "registrant" to contact,
                    "admin" to contact,
                    "billing" to contact
                )
            )
            
            val response = callApi("order/domains/register", params)
            
            val result = response["result"] as? String
            if (result == "success") {
                logger.info("Domain registration successful for $domain")
                return true
            } else {
                val errorMsg = response["error"] ?: response["message"] ?: "Unknown error"
                logger.error("Domain registration failed: $errorMsg")
                throw RuntimeException("Registration failed: $errorMsg")
            }
        } catch (e: Exception) {
            logger.error("Error registering domain", e)
            throw e
        }
    }
    
    private fun callApi(action: String, params: Map<String, Any>): Map<String, Any> {
        val timestamp = java.time.format.DateTimeFormatter.ofPattern("yy-MM-dd HH")
            .withZone(java.time.ZoneId.of("UTC"))
            .format(java.time.Instant.now())
            
        val authHeader = generateAuthHeaders(timestamp)
        
        val headers = HttpHeaders()
        // Switch to Form URL Encoded as Blesta/Guzzle default behavior favors this for arrays
        headers.contentType = MediaType.APPLICATION_FORM_URLENCODED
        headers.setAll(authHeader)
        
        // Construct full URL
        val fullUrl = if (apiUrl.endsWith("/")) "$apiUrl$action" else "$apiUrl/$action"
        
        logger.info("Calling HostAfrica API: $fullUrl")
        
        // Create MultiValueMap for Form URL Encoded
        val map = org.springframework.util.LinkedMultiValueMap<String, Any>()
        flattenParams(params, map)
        
        val request = HttpEntity(map, headers)
        
        try {
            // Request as String first to handle HTML errors gracefully
            val rawResponse = restTemplate.postForObject(fullUrl, request, String::class.java)
            
            logger.info("HostAfrica API Response: $rawResponse")
            
            if (rawResponse.isNullOrBlank()) {
                 return mapOf("result" to "error", "message" to "Empty response")
            }
            
            // Try parsing as JSON
            val objectMapper = com.fasterxml.jackson.databind.ObjectMapper()
            try {
                // Try List first
                if (rawResponse.trim().startsWith("[")) {
                     val list = objectMapper.readValue(rawResponse, List::class.java)
                     val responseMap = mutableMapOf<String, Any>()
                     list.forEachIndexed { index, item ->
                         if (item != null) responseMap[index.toString()] = item
                     }
                     return responseMap
                } else {
                     // Try Map
                     @Suppress("UNCHECKED_CAST")
                     return objectMapper.readValue(rawResponse, Map::class.java) as Map<String, Any>
                }
            } catch (jsonEx: Exception) {
                // If parsing fails, it's likely HTML error page
                logger.error("Failed to parse response as JSON. content: $rawResponse")
                return mapOf("result" to "error", "message" to "Invalid API response: $rawResponse")
            }
        } catch (e: Exception) {
            logger.error("API call failed", e)
            throw RuntimeException("HostAfrica API call failed: ${e.message}")
        }
    }
    
    // Recursive helper to flatten nested maps into bracket notation contact[firstname]
    private fun flattenParams(params: Map<String, Any>, map: org.springframework.util.MultiValueMap<String, Any>, prefix: String = "") {
        for ((key, value) in params) {
            val newKey = if (prefix.isEmpty()) key else "$prefix[$key]"
            if (value is Map<*, *>) {
                @Suppress("UNCHECKED_CAST")
                flattenParams(value as Map<String, Any>, map, newKey)
            } else if (value is List<*>) {
                 value.forEachIndexed { index, item ->
                     val listKey = "$newKey[$index]"
                     if (item is Map<*, *>) {
                         @Suppress("UNCHECKED_CAST")
                         flattenParams(item as Map<String, Any>, map, listKey)
                     } else {
                         map.add(listKey, item.toString())
                     }
                 }
            } else {
                map.add(newKey, value.toString())
            }
        }
    }

    private fun generateAuthHeaders(timestamp: String): Map<String, String> {
        return mapOf(
            "username" to apiUsername,
            "token" to generateToken(timestamp),
            "User-Agent" to "ModulesGarden - Domains Reseller"
        )
    }

    private fun generateToken(timestamp: String): String {
        // HostAfrica API (Blesta Module context) Logic:
        // PHP: hash_hmac("sha256", $this->ApiKey, "{$this->Username}:$time")
        // hash_hmac(algo, data, key)
        // Data = ApiKey (Token)
        // Key  = Username:Timestamp
        
        try {
            val keyString = "$apiUsername:$timestamp"
            val messageString = apiToken // The data to be hashed
            
            val sha256Hmac = javax.crypto.Mac.getInstance("HmacSHA256")
            val secretKey = javax.crypto.spec.SecretKeySpec(keyString.toByteArray(), "HmacSHA256")
            sha256Hmac.init(secretKey)
            
            val hashBytes = sha256Hmac.doFinal(messageString.toByteArray())
            
            // PHP hash_hmac returns HEX string by default
            val hexString = hashBytes.joinToString("") { "%02x".format(it) }
            
            return Base64.getEncoder().encodeToString(hexString.toByteArray())
        } catch (e: Exception) {
            logger.error("Error generating token", e)
            return ""
        }
    }
    
    // Helper for PHP serialization of empty array a:0:{} -> base64
    private fun getEmptyPhpArrayBase64(): String {
        return "YTowOnt9" 
    }

    private fun isValidDomain(domain: String): Boolean {
        val regex = "^((?!-)[A-Za-z0-9-]{1,63}(?<!-)\\.)+[A-Za-z]{2,6}$".toRegex()
        return regex.matches(domain)
    }
}


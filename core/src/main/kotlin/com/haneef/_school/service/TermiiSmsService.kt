package com.haneef._school.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.*
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.util.*

@Service
class TermiiSmsService(
    @Value("\${termii.api.key:}") private val apiKey: String,
    @Value("\${termii.api.url:https://v3.api.termii.com}") private val apiUrl: String,
    @Value("\${termii.sender.id:o4School}") private val senderId: String,
    @Value("\${termii.channel:generic}") private val channel: String,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(TermiiSmsService::class.java)
    private val restTemplate = RestTemplate().apply {
        val requestFactory = org.springframework.http.client.SimpleClientHttpRequestFactory()
        requestFactory.setConnectTimeout(30000)
        requestFactory.setReadTimeout(30000)
        this.requestFactory = requestFactory
    }

    /**
     * Send a single SMS via Termii
     * @param to Recipient phone number in international format (e.g. 2348012345678)
     * @param message The SMS content
     * @return Termii response message ID if successful, null otherwise
     */
    fun sendSms(to: String, message: String): String? {
        if (apiKey.isBlank()) {
            logger.warn("Termii API key is not configured. SMS not sent.")
            return null
        }

        try {
            val url = "$apiUrl/api/sms/send"
            val headers = HttpHeaders().apply {
                contentType = MediaType.APPLICATION_JSON
            }

            val requestBody = mapOf(
                "to" to to,
                "from" to senderId,
                "sms" to message,
                "type" to "plain",
                "channel" to channel,
                "api_key" to apiKey
            )

            val request = HttpEntity(requestBody, headers)
            logger.info("Sending Termii SMS to $to")
            
            val response = restTemplate.postForEntity(url, request, Map::class.java)
            
            if (response.statusCode == HttpStatus.OK) {
                val body = response.body
                val messageId = body?.get("message_id") as? String
                if (messageId != null) {
                    logger.info("Termii SMS sent successfully. Message ID: $messageId")
                    return messageId
                } else {
                    logger.error("Termii API returned success but no message_id: $body")
                }
            } else {
                logger.error("Termii API returned error status: ${response.statusCode}, body: ${response.body}")
            }
        } catch (e: Exception) {
            logger.error("Error sending Termii SMS: ${e.message}", e)
        }
        return null
    }

    /**
     * Send bulk SMS via Termii
     * @param to List of recipient phone numbers
     * @param message The SMS content
     */
    fun sendBulkSms(to: List<String>, message: String): String? {
        if (apiKey.isBlank()) {
            logger.warn("Termii API key is not configured. Bulk SMS not sent.")
            return null
        }

        try {
            val url = "$apiUrl/api/sms/send/bulk"
            val headers = HttpHeaders().apply {
                contentType = MediaType.APPLICATION_JSON
            }

            val requestBody = mapOf(
                "to" to to,
                "from" to senderId,
                "sms" to message,
                "type" to "plain",
                "channel" to channel,
                "api_key" to apiKey
            )

            val request = HttpEntity(requestBody, headers)
            val response = restTemplate.postForEntity(url, request, Map::class.java)
            
            if (response.statusCode == HttpStatus.OK) {
                return response.body?.get("message_id") as? String
            }
        } catch (e: Exception) {
            logger.error("Error sending Termii Bulk SMS: ${e.message}", e)
        }
        return null
    }
}

package com.haneef._school.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.*
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.util.*
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpStatusCode

@Service
open class TermiiSmsService(
    private val objectMapper: ObjectMapper,
    private val restTemplate: RestTemplate = RestTemplate(),
    @Value("\${termii.api.url:}") private val apiUrl: String,
    @Value("\${termii.api.key:}") private val apiKey: String,
    @Value("\${termii.sender.id:Termii}") private val senderId: String,
    @Value("\${termii.channel:generic}") private val channel: String,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    data class SmsResult(val ok: Boolean, val messageId: String?, val status: HttpStatusCode?, val body: Map<String, Any>?)

    /**
     * Compatibility wrapper used by existing callers: returns message_id string or null.
     */
    fun sendSms(to: String, message: String): String? {
        return sendSmsResult(to, message)?.messageId
    }

    /**
     * Send a single SMS via Termii and return a richer result.
     */
    fun sendSmsResult(to: String, message: String): SmsResult? {
        if (apiKey.isBlank()) {
            logger.warn("Termii API key is not configured. SMS not sent.")
            return null
        }

        val normalized = normalizePhone(to) ?: run {
            logger.warn("Invalid phone number for Termii SMS: {}", to)
            return null
        }

        val url = "$apiUrl/api/sms/send"
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val requestBody = mapOf(
            "to" to normalized,
            "from" to senderId,
            "sms" to message,
            "type" to "plain",
            "channel" to channel,
            "api_key" to apiKey
        )

        val request = HttpEntity(requestBody, headers)

        return try {
            logger.info("Sending Termii SMS to {}", normalized)
            val response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                request,
                object : ParameterizedTypeReference<Map<String, Any>>() {}
            )

            val body = response.body
            val messageId = body?.get("message_id") as? String
            if (response.statusCode == HttpStatus.OK) {
                if (messageId != null) logger.info("Termii SMS sent, id={}", messageId)
                SmsResult(true, messageId, response.statusCode, body)
            } else {
                logger.error("Termii API returned error status: {}, body: {}", response.statusCode, body)
                SmsResult(false, messageId, response.statusCode, body)
            }
        } catch (e: Exception) {
            logger.error("Error sending Termii SMS: {}", e.message, e)
            SmsResult(false, null, null, mapOf("error" to (e.message ?: "unknown")))
        }
    }

    /**
     * Send bulk SMS via Termii
     */
    /**
     * Compatibility wrapper for bulk callers that expect a message id string.
     */
    fun sendBulkSms(to: List<String>, message: String): String? {
        return sendBulkSmsResult(to, message)?.messageId
    }

    /**
     * Send bulk SMS via Termii and return a richer result.
     */
    fun sendBulkSmsResult(to: List<String>, message: String): SmsResult? {
        if (apiKey.isBlank()) {
            logger.warn("Termii API key is not configured. Bulk SMS not sent.")
            return null
        }

        val normalized = to.mapNotNull { normalizePhone(it) }
        if (normalized.isEmpty()) {
            logger.warn("No valid phone numbers supplied for Termii bulk SMS")
            return null
        }

        val url = "$apiUrl/api/sms/send/bulk"
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }

        val requestBody = mapOf(
            "to" to normalized,
            "from" to senderId,
            "sms" to message,
            "type" to "plain",
            "channel" to channel,
            "api_key" to apiKey
        )

        val request = HttpEntity(requestBody, headers)

        return try {
            val response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                request,
                object : ParameterizedTypeReference<Map<String, Any>>() {}
            )

            val body = response.body
            val messageId = body?.get("message_id") as? String
            if (response.statusCode == HttpStatus.OK) {
                SmsResult(true, messageId, response.statusCode, body)
            } else {
                logger.error("Termii API returned error status for bulk: {}, body: {}", response.statusCode, body)
                SmsResult(false, messageId, response.statusCode, body)
            }
        } catch (e: Exception) {
            logger.error("Error sending Termii Bulk SMS: {}", e.message, e)
            SmsResult(false, null, null, mapOf("error" to (e.message ?: "unknown")))
        }
    }

    private fun normalizePhone(input: String): String? {
        val digits = input.filter { it.isDigit() }
        if (digits.length < 9) return null
        return if (digits.startsWith("0")) digits.drop(1) else digits
    }
}

package com.haneef._school.service

import com.haneef._school.config.WhatsAppProperties
import com.haneef._school.entity.MessageDirection
import com.haneef._school.entity.User
import com.haneef._school.entity.WhatsAppMessage
import com.haneef._school.repository.SchoolRepository
import com.haneef._school.repository.WhatsAppMessageRepository
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.util.*

@Service
class WhatsAppService(
    private val properties: WhatsAppProperties,
    private val messageRepository: WhatsAppMessageRepository,
    private val phoneNumberService: PhoneNumberService,
    private val schoolRepository: SchoolRepository
) {
    private val restTemplate = RestTemplate()

    fun sendTextMessage(to: String, text: String, user: User? = null): Boolean {
        val formattedNumber = phoneNumberService.cleanPhoneNumber(to).removePrefix("+")
        
        val url = "https://graph.facebook.com/${properties.apiVersion}/${properties.phoneNumberId}/messages"
        
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers.setBearerAuth(properties.accessToken ?: return false)

        val body = mapOf(
            "messaging_product" to "whatsapp",
            "recipient_type" to "individual",
            "to" to formattedNumber,
            "type" to "text",
            "text" to mapOf("body" to text)
        )

        val entity = HttpEntity(body, headers)

        return try {
            val response = restTemplate.postForEntity(url, entity, Map::class.java)
            val responseBody = response.body as? Map<*, *>
            val messages = responseBody?.get("messages") as? List<*>
            val metaId = (messages?.firstOrNull() as? Map<*, *>)?.get("id") as? String

            logMessage(to, text, MessageDirection.OUTGOING, "SENT", metaId, user)
            true
        } catch (e: Exception) {
            println("Failed to send WhatsApp message to $to: ${e.message}")
            logMessage(to, text, MessageDirection.OUTGOING, "FAILED", null, user)
            false
        }
    }

    fun sendTemplateMessage(to: String, templateName: String, languageCode: String = "en_US", components: List<Map<String, Any>>, user: User? = null): Boolean {
        val formattedNumber = phoneNumberService.cleanPhoneNumber(to).removePrefix("+")
        
        val url = "https://graph.facebook.com/${properties.apiVersion}/${properties.phoneNumberId}/messages"
        
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers.setBearerAuth(properties.accessToken ?: return false)

        val body = mapOf(
            "messaging_product" to "whatsapp",
            "to" to formattedNumber,
            "type" to "template",
            "template" to mapOf(
                "name" to templateName,
                "language" to mapOf("code" to languageCode),
                "components" to components
            )
        )

        val entity = HttpEntity(body, headers)

        return try {
            val response = restTemplate.postForEntity(url, entity, Map::class.java)
            val responseBody = response.body as? Map<*, *>
            val messages = responseBody?.get("messages") as? List<*>
            val metaId = (messages?.firstOrNull() as? Map<*, *>)?.get("id") as? String

            logMessage(to, "[Template: $templateName]", MessageDirection.OUTGOING, "SENT", metaId, user)
            true
        } catch (e: Exception) {
            println("Failed to send WhatsApp template $templateName to $to: ${e.message}")
            logMessage(to, "[Template: $templateName] Error: ${e.message}", MessageDirection.OUTGOING, "FAILED", null, user)
            false
        }
    }

    private fun logMessage(to: String, content: String, direction: MessageDirection, status: String, metaId: String?, user: User?) {
        val schoolId = user?.getSchools()?.firstOrNull()
        val school = schoolId?.let { schoolRepository.findById(it).orElse(null) }

        val message = WhatsAppMessage(
            recipientPhone = to,
            content = content,
            direction = direction,
            status = status,
            metaMessageId = metaId,
            user = user,
            school = school
        )
        messageRepository.save(message)
    }
}

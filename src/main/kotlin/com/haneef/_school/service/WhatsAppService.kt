package com.haneef._school.service

import com.haneef._school.config.WhatsAppProperties
import com.haneef._school.entity.MessageDirection
import com.haneef._school.entity.User
import com.haneef._school.entity.WhatsAppMessage
import com.haneef._school.repository.SchoolRepository
import com.haneef._school.repository.UserRepository
import com.haneef._school.repository.WhatsAppMessageRepository
import com.haneef._school.repository.WhatsAppTemplateRepository
import com.haneef._school.entity.UserSchoolRole
import com.haneef._school.repository.UserSchoolRoleRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.ai.chat.client.ChatClient
import java.util.*

@Service
open class WhatsAppService(
    private val properties: WhatsAppProperties,
    private val messageRepository: WhatsAppMessageRepository,
    private val phoneNumberService: PhoneNumberService,
    private val schoolRepository: SchoolRepository,
    private val userRepository: UserRepository,
    private val templateRepository: WhatsAppTemplateRepository,
    private val objectMapper: ObjectMapper,
    private val chatClient: ChatClient,
    private val schoolDataTools: SchoolDataTools,
    private val userSchoolRoleRepository: UserSchoolRoleRepository
) {
    private val restTemplate = RestTemplate()

    open fun sendTextMessage(to: String, text: String, user: User? = null, schoolId: UUID? = null): Boolean {
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

            logMessage(to, text, MessageDirection.OUTGOING, "SENT", metaId, user, schoolId)
            true
        } catch (e: Exception) {
            println("Failed to send WhatsApp message to $to: ${e.message}")
            logMessage(to, text, MessageDirection.OUTGOING, "FAILED", null, user, schoolId)
            false
        }
    }

    open fun getTemplates(): List<Map<String, Any>> {
        val url = "https://graph.facebook.com/${properties.apiVersion}/${properties.businessAccountId}/message_templates"
        
        val headers = HttpHeaders()
        headers.setBearerAuth(properties.accessToken ?: return emptyList())

        return try {
            val response = restTemplate.exchange(
                url,
                org.springframework.http.HttpMethod.GET,
                HttpEntity<Any>(headers),
                Map::class.java
            )
            val responseBody = response.body as? Map<*, *>
            val data = responseBody?.get("data") as? List<Map<String, Any>>
            data ?: emptyList()
        } catch (e: Exception) {
            println("Failed to fetch WhatsApp templates: ${e.message}")
            emptyList()
        }
    }

    open fun sendTemplateMessage(to: String, templateName: String, languageCode: String = "en_US", components: List<Map<String, Any>>, user: User? = null, schoolId: UUID? = null): Boolean {
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

            val displayedContent = reconstructTemplateMessage(templateName, schoolId, components)
            logMessage(to, displayedContent, MessageDirection.OUTGOING, "SENT", metaId, user, schoolId)
            true
        } catch (e: Exception) {
            println("Failed to send WhatsApp template $templateName to $to: ${e.message}")
            logMessage(to, "[Template: $templateName] Error: ${e.message}", MessageDirection.OUTGOING, "FAILED", null, user, schoolId)
            false
        }
    }

    open fun processWebhook(payload: Map<String, Any>) {
        val entries = payload["entry"] as? List<*> ?: return
        
        for (entry in entries) {
            val entryMap = entry as? Map<*, *> ?: continue
            val changes = entryMap["changes"] as? List<*> ?: continue
            
            for (change in changes) {
                val changeMap = change as? Map<*, *> ?: continue
                val value = changeMap["value"] as? Map<*, *> ?: continue
                
                // 1. Handle Messages
                val messages = value["messages"] as? List<*>
                messages?.forEach { message ->
                    val messageMap = message as? Map<*, *> ?: return@forEach
                    val from = messageMap["from"] as? String ?: return@forEach
                    val text = (messageMap["text"] as? Map<*, *>)?.get("body") as? String ?: ""
                    val metaId = messageMap["id"] as? String
                    
                    // Link to user/school
                    val user = findUserByPhone(from)
                    val incoming = WhatsAppMessage(
                        recipientPhone = from,
                        content = text,
                        direction = MessageDirection.INCOMING,
                        status = "RECEIVED",
                        metaMessageId = metaId,
                        user = user,
                        school = user?.getSchools()?.firstOrNull()?.let { schoolRepository.findById(it).orElse(null) }
                    )
                    messageRepository.save(incoming)

                    // 1.5 Handle AI Query if it's a text message
                    if (text.isNotBlank()) {
                        handleAiQuery(from, text)
                    }
                }

                // 2. Handle Status Updates
                val statuses = value["statuses"] as? List<*>
                statuses?.forEach { status ->
                    val statusMap = status as? Map<*, *> ?: return@forEach
                    val metaId = statusMap["id"] as? String ?: return@forEach
                    val newStatus = statusMap["status"] as? String ?: return@forEach
                    
                    val existing = messageRepository.findByMetaMessageId(metaId)
                    if (existing != null) {
                        existing.status = newStatus.uppercase()
                        messageRepository.save(existing)
                    }
                }
            }
        }
    }

    private fun findUserByPhone(phone: String): User? {
        val cleaned = phoneNumberService.cleanPhoneNumber(phone)
        return userRepository.findByPhoneNumber(cleaned).orElse(null) 
            ?: userRepository.findByPhoneNumber(cleaned.removePrefix("+")).orElse(null)
    }

    private fun reconstructTemplateMessage(templateName: String, schoolId: UUID?, components: List<Map<String, Any>>): String {
        if (schoolId == null) return "[Template: $templateName]"
        
        return try {
            val template = templateRepository.findByTemplateNameAndSchoolId(templateName, schoolId).orElse(null)
                ?: return "[Template: $templateName]"
            
            val componentsJson = template.componentsJson ?: return "[Template: $templateName]"
            val metaComponents = objectMapper.readValue(componentsJson, List::class.java) as? List<Map<String, Any>> ?: return "[Template: $templateName]"
            
            val bodyComponent = metaComponents.find { it["type"] == "BODY" } ?: return "[Template: $templateName]"
            var text = bodyComponent["text"] as? String ?: return "[Template: $templateName]"
            
            val bodyParams = components.find { it["type"] == "body" }?.get("parameters") as? List<Map<String, Any>>
            
            if (bodyParams != null) {
                bodyParams.forEachIndexed { index, param ->
                    val placeholder = "{{${index + 1}}}"
                    val value = param["text"] as? String ?: ""
                    text = text.replace(placeholder, value)
                }
            }
            
            text
        } catch (e: Exception) {
            println("Failed to reconstruct template message: ${e.message}")
            "[Template: $templateName]"
        }
    }

    private fun logMessage(to: String, content: String, direction: MessageDirection, status: String, metaId: String?, user: User?, schoolId: UUID? = null) {
        val effectiveSchoolId = schoolId ?: user?.getSchools()?.firstOrNull()
        val school = effectiveSchoolId?.let { schoolRepository.findById(it).orElse(null) }

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

    open fun handleAiQuery(from: String, text: String) {
        val user = findUserByPhone(from) ?: run {
            sendTextMessage(from, "Sorry, your phone number is not linked to any account in our system. Please contact your school administrator.")
            return
        }
        
        val roles = userSchoolRoleRepository.findByUserAndIsActive(user, true)
        if (roles.isEmpty()) {
            sendTextMessage(from, "Sorry, you don't have permission to query school data. Please contact your school administrator.")
            return
        }

        val schoolId = roles.firstOrNull()?.schoolId ?: run {
            sendTextMessage(from, "Sorry, we couldn't determine your school affiliation.")
            return
        }
        val schoolName = schoolRepository.findById(schoolId).orElse(null)?.name ?: "the school"

        try {
            val response = chatClient.prompt()
                .system("""
                    You are a helpful school assistant for $schoolName. 
                    You are chatting with ${user.fullName} (phone: $from).
                    Always provide the schoolId ($schoolId) to tools.
                    
                    AVAILABLE TOOLS:
                    - Use 'getChildAcademicDetails' if the user asks about their children's progress, grades, subjects, or attendance. Pass parentUserId = ${user.id}.
                    - Use 'getParentFinancialSummary' if the user asks about their fees, payments, or balance. Pass parentUserId = ${user.id}.
                    - Use 'getSchoolInfo' if the user asks for the class timetable, school calendar, or upcoming events.
                    - Use 'getStudentInfo' if searching for a specific student's basic details.
                    
                    SECURITY: You can ONLY provide academic and financial details for the user's OWN children. The tools handle this check internally if you pass the correct parentUserId.
                    
                    TONE: Be professional, supportive, and clear.
                """.trimIndent())
                .user(text)
                .tools(schoolDataTools)
                .call()
                .content()

            sendTextMessage(from, response ?: "I'm sorry, I couldn't find any information for that query.")
        } catch (e: Exception) {
            println("AI Query Error: ${e.message}")
            sendTextMessage(from, "I'm sorry, I'm having trouble processing your request right now. ${e.message}")
        }
    }
}

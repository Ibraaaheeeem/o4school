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
import org.springframework.stereotype.Service
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.ObjectProvider
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.HttpStatusCodeException
import com.haneef._school.entity.ServiceFeature
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.context.ApplicationEventPublisher
import com.haneef._school.event.MessageFailureEvent
import com.haneef._school.service.MultimodalChannel
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
    private val schoolDataToolsProvider: ObjectProvider<SchoolDataTools>,
    private val userSchoolRoleRepository: UserSchoolRoleRepository,
    private val subscriptionService: SubscriptionService,
    private val eventPublisher: ApplicationEventPublisher
) {
    private val restTemplate = RestTemplate()

    open fun sendTextMessage(to: String, text: String, user: User? = null, schoolId: UUID? = null, triggerFallback: Boolean = false, isFallback: Boolean = false, templateName: String? = null, paramsJson: String? = null, fallbackChannel: String? = null, broadcastId: UUID? = null): Boolean {
        val formattedNumber = phoneNumberService.cleanPhoneNumber(to).removePrefix("+")
        
        val url = "https://graph.facebook.com/${properties.apiVersion.trim()}/${(properties.phoneNumberId ?: "").trim()}/messages"
        
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers.setBearerAuth(properties.accessToken?.trim() ?: return false)

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

            logMessage(to, text, MessageDirection.OUTGOING, "SENT", metaId, user, schoolId, triggerFallback, isFallback, templateName, paramsJson, fallbackChannel, broadcastId)
            true
        } catch (e: HttpStatusCodeException) {
            val errorMsg = extractErrorMessage(e.responseBodyAsString)
            println("Failed to send WhatsApp message to $to: ${e.statusCode} - $errorMsg")
            logMessage(to, text + " (Error: $errorMsg)", MessageDirection.OUTGOING, "FAILED", null, user, schoolId, triggerFallback, isFallback, templateName, paramsJson, fallbackChannel, broadcastId)
            false
        } catch (e: Exception) {
            println("Failed to send WhatsApp message to $to: ${e.message}")
            logMessage(to, text, MessageDirection.OUTGOING, "FAILED", null, user, schoolId, triggerFallback, isFallback, templateName, paramsJson, fallbackChannel, broadcastId)
            false
        }
    }

    open fun getTemplates(): List<Map<String, Any>> {
        val url = "https://graph.facebook.com/${properties.apiVersion.trim()}/${(properties.businessAccountId ?: "").trim()}/message_templates"
        println("Fetching WhatsApp templates from Meta: $url")
        
        val headers = HttpHeaders()
        val token = properties.accessToken?.trim()
        headers.setBearerAuth(token ?: run {
            println("WhatsApp access token is missing")
            return emptyList()
        })

        return try {
            val response = restTemplate.exchange(
                url,
                org.springframework.http.HttpMethod.GET,
                HttpEntity<Any>(headers),
                Map::class.java
            )
            val responseBody = response.body as? Map<*, *>
            val data = responseBody?.get("data") as? List<Map<String, Any>>
            
            if (data == null) {
                println("Meta response body missing 'data' field: $responseBody")
            } else if (data.isEmpty()) {
                println("Meta returned 0 templates for Business Account ID: ${properties.businessAccountId}")
            } else {
                println("Successfully fetched ${data.size} templates from Meta")
            }
            
            data ?: emptyList()
        } catch (e: org.springframework.web.client.HttpStatusCodeException) {
            println("Meta API Error (${e.statusCode}): ${e.responseBodyAsString}")
            emptyList()
        } catch (e: Exception) {
            println("Unexpected error fetching WhatsApp templates: ${e.message}")
            emptyList()
        }
    }

    open fun sendTemplateMessage(to: String, templateName: String, languageCode: String = "en_US", components: List<Map<String, Any>>, user: User? = null, schoolId: UUID? = null, triggerFallback: Boolean = false, isFallback: Boolean = false, paramsJson: String? = null, fallbackChannel: String? = null, broadcastId: UUID? = null): Boolean {
        val formattedNumber = phoneNumberService.cleanPhoneNumber(to).removePrefix("+")
        
        val url = "https://graph.facebook.com/${properties.apiVersion.trim()}/${(properties.phoneNumberId ?: "").trim()}/messages"
        
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        val token = properties.accessToken?.trim() ?: return false
        val tokenPrefix = if (token.length > 10) token.substring(0, 10) else "***"
        println("Sending WhatsApp template message using token: $tokenPrefix... (length: ${token.length})")
        headers.setBearerAuth(token)

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
            logMessage(to, displayedContent, MessageDirection.OUTGOING, "SENT", metaId, user, schoolId, triggerFallback, isFallback, templateName, paramsJson, fallbackChannel, broadcastId)
            true
        } catch (e: HttpStatusCodeException) {
            val errorBody = e.responseBodyAsString
            val errorMsg = extractErrorMessage(errorBody)
            println("Failed to send WhatsApp template $templateName to $to: ${e.statusCode} - $errorBody")
            println("Request Body was: ${objectMapper.writeValueAsString(body)}")
            logMessage(to, "[Template: $templateName] $errorMsg", MessageDirection.OUTGOING, "FAILED", null, user, schoolId, triggerFallback, isFallback, templateName, paramsJson, fallbackChannel, broadcastId)
            false
        } catch (e: Exception) {
            println("Unexpected error sending WhatsApp template $templateName to $to: ${e.message}")
            logMessage(to, "[Template: $templateName] Error: ${e.message}", MessageDirection.OUTGOING, "FAILED", null, user, schoolId, triggerFallback, isFallback, templateName, paramsJson, fallbackChannel, broadcastId)
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

                    if (metaId != null) {
                        val existingMessage = messageRepository.findByMetaMessageId(metaId)
                        if (existingMessage != null) {
                            println("WhatsApp webhook duplicate message received (ID: $metaId). Skipping to ensure idempotency.")
                            return@forEach // Skip processing this duplicate message
                        }
                    }
                    
                    // Link to user/school
                    val user = findUserByPhone(from)
                    val schoolId = user?.getSchools()?.firstOrNull() ?: user?.schoolRoles?.firstOrNull()?.schoolId
                    val incoming = WhatsAppMessage(
                        recipientPhone = from,
                        content = text,
                        direction = MessageDirection.INCOMING,
                        status = "RECEIVED",
                        metaMessageId = metaId,
                        user = user,
                        school = schoolId?.let { schoolRepository.findById(it).orElse(null) }
                    )
                    messageRepository.save(incoming)

                    // 1.5 Handle AI Query if it's a text message (Async to prevent webhook timeout)
                    if (text.isNotBlank()) {
                        java.util.concurrent.CompletableFuture.runAsync {
                            handleAiQuery(from, text)
                        }
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
                        val currentStatus = existing.status
                        existing.status = newStatus.uppercase()
                        messageRepository.save(existing)

                        // Trigger Fallback if FAILED and enabled
                        if (existing.status == "FAILED" && existing.triggerFallback) {
                            eventPublisher.publishEvent(
                                MessageFailureEvent(
                                    channel = MultimodalChannel.WHATSAPP,
                                    messageId = existing.id!!,
                                    recipientPhone = existing.recipientPhone,
                                    schoolId = existing.school?.id,
                                    senderUserId = existing.user?.id,
                                    templateName = existing.templateName,
                                    paramsJson = existing.paramsJson,
                                    fallbackChannel = existing.fallbackChannel?.let { MultimodalChannel.valueOf(it) },
                                    broadcastId = existing.broadcastId,
                                    content = existing.content
                                )
                            )
                        }

                        // Deduct usage once when message is delivered
                        if (existing.status == "DELIVERED" && currentStatus != "DELIVERED") {
                            existing.school?.let { school ->
                                try {
                                    // Use the system admin user or a relevant user for the deduction log
                                    // EXEMPTION: Free-form messages sent within 24h window are not deducted
                                    val isFreeForm = existing.templateName == null
                                    val lastIncoming = messageRepository.findTopByRecipientPhoneAndDirectionOrderByCreatedAtDesc(existing.recipientPhone, MessageDirection.INCOMING)
                                    val isWithin24hSession = lastIncoming != null && 
                                        java.time.Duration.between(lastIncoming.createdAt, existing.createdAt).toHours() < 24

                                    if (isFreeForm && isWithin24hSession) {
                                        println("WhatsApp Session Exemption (Webhook): Skipping delivery deduction for free-form message to ${existing.recipientPhone}")
                                    } else {
                                        val deductUser = existing.user ?: userRepository.findAll().firstOrNull()
                                        if(deductUser != null) {
                                          subscriptionService.deductTokens(
                                              schoolId = school.id!!,
                                              userId = deductUser.id!!,
                                              feature = com.haneef._school.entity.ServiceFeature.WHATSAPP_MESSAGING,
                                              amount = 1,
                                              description = if (isFreeForm) "WhatsApp Message Delivered to ${existing.recipientPhone}" else "WhatsApp Template (${existing.templateName}) Delivered to ${existing.recipientPhone}",
                                              allowNegative = existing.isFallback
                                          )
                                        }
                                    }
                                } catch (e: Exception) {
                                    println("Warning: Failed to deduct WhatsApp balance for school ${school.id}: ${e.message}")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun findUserByPhone(phone: String): User? {
        val cleaned = phoneNumberService.cleanPhoneNumber(phone)
        val directMatch = userRepository.findByPhoneNumber(cleaned).orElse(null) 
            ?: userRepository.findByPhoneNumber(cleaned.removePrefix("+")).orElse(null)
            ?: userRepository.findByPhoneNumber("+$cleaned").orElse(null)
            
        if (directMatch != null) return directMatch
        
        // Next, try to find an exact match where the database simply stores the cleaned numeric format
        val allUsers = userRepository.findAll()
        val exactDbMatch = allUsers.firstOrNull { 
            val dbCleaned = it.phoneNumber?.let { p -> phoneNumberService.cleanPhoneNumber(p) } ?: ""
            dbCleaned == cleaned || dbCleaned == cleaned.removePrefix("+")
        }
        if (exactDbMatch != null) return exactDbMatch
        
        // If not found, try matching the last 10 digits against all users (local format 080... vs +23480...)
        // This is a naive fallback, but effective for matching international to local
        val last10 = if (cleaned.length >= 10) cleaned.substring(cleaned.length - 10) else cleaned
        if (last10.length >= 10) {
            val fuzzyMatch = allUsers.firstOrNull { user ->
                val userPhone = user.phoneNumber?.let { phoneNumberService.cleanPhoneNumber(it) } ?: ""
                userPhone.endsWith(last10)
            }
            if (fuzzyMatch != null) return fuzzyMatch
        }
        
        return null
    }

    private fun reconstructTemplateMessage(templateName: String, schoolId: UUID?, components: List<Map<String, Any>>): String {
        if (schoolId == null) return "[Template: $templateName]"
        
        return try {
            val template = templateRepository.findByTemplateName(templateName).orElse(null)
                ?: return "[Template: $templateName]"
            
            val componentsJson = template.componentsJson ?: return "[Template: $templateName]"
            val metaComponents = objectMapper.readValue(componentsJson, List::class.java) as? List<Map<String, Any>> ?: return "[Template: $templateName]"
            
            val bodyComponent = metaComponents.find { it["type"] == "BODY" } ?: return "[Template: $templateName]"
            var text = bodyComponent["text"] as? String ?: return "[Template: $templateName]"
            
            val bodyParams = components.find { it["type"] == "body" }?.get("parameters") as? List<Map<String, Any>>
            
            if (bodyParams != null) {
                bodyParams.forEachIndexed { index, param ->
                    val numberedPlaceholder = "{{${index + 1}}}"
                    val value = param["text"] as? String ?: ""
                    
                    // Replace numbered placeholder
                    if (text.contains(numberedPlaceholder)) {
                        text = text.replace(numberedPlaceholder, value)
                    } else {
                        // If numbered not found, try to find a named placeholder that might correspond to this index
                        // This assumes Meta maps named params in order for the request components
                        val regex = Regex("\\{\\{([a-zA-Z0-9_]+)}}")
                        val match = regex.findAll(text).elementAtOrNull(index)
                        if (match != null) {
                            text = text.replace(match.value, value)
                        }
                    }
                }
            }
            
            text
        } catch (e: Exception) {
            println("Failed to reconstruct template message: ${e.message}")
            "[Template: $templateName]"
        }
    }

    private fun logMessage(to: String, content: String, direction: MessageDirection, status: String, metaId: String?, user: User?, schoolId: UUID? = null, triggerFallback: Boolean = false, isFallback: Boolean = false, templateName: String? = null, paramsJson: String? = null, fallbackChannel: String? = null, broadcastId: UUID? = null) {
        val effectiveSchoolId = schoolId ?: user?.getSchools()?.firstOrNull() ?: user?.schoolRoles?.firstOrNull()?.schoolId
        val school = effectiveSchoolId?.let { schoolRepository.findById(it).orElse(null) }

        val message = WhatsAppMessage(
            recipientPhone = to,
            content = content,
            direction = direction,
            status = status,
            metaMessageId = metaId,
            user = user,
            school = school,
            triggerFallback = triggerFallback,
            isFallback = isFallback,
            templateName = templateName,
            paramsJson = paramsJson,
            fallbackChannel = fallbackChannel,
            broadcastId = broadcastId
        )
        messageRepository.save(message)

        if (direction == MessageDirection.OUTGOING && metaId != null && effectiveSchoolId != null) {
            try {
                // EXEMPTION: Free-form messages sent within the 24h customer service window (since last INCOMING) are NOT deducted.
                val isFreeForm = templateName == null
                val lastIncoming = messageRepository.findTopByRecipientPhoneAndDirectionOrderByCreatedAtDesc(to, MessageDirection.INCOMING)
                val isWithin24hSession = lastIncoming != null && 
                    java.time.Duration.between(lastIncoming.createdAt, java.time.LocalDateTime.now()).toHours() < 24

                if (isFreeForm && isWithin24hSession) {
                    println("WhatsApp Session Exemption: Skipping deduction for free-form message to $to (last incoming was at ${lastIncoming?.createdAt})")
                } else {
                    subscriptionService.deductTokens(
                        schoolId = effectiveSchoolId,
                        userId = user?.id ?: java.util.UUID.fromString("00000000-0000-0000-0000-000000000000"), // System user fallback
                        feature = ServiceFeature.WHATSAPP_MESSAGING,
                        amount = 1,
                        description = if (isFreeForm) "WhatsApp sent to $to" else "WhatsApp Template ($templateName) sent to $to",
                        allowNegative = isFallback
                    )
                }
            } catch (e: Exception) {
                println("Failed to deduct WhatsApp token for school $effectiveSchoolId: ${e.message}")
            }
        }
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

        // Get all unique active schools for this user
        val distinctSchools = roles.mapNotNull { role -> 
            role.schoolId?.let { id -> schoolRepository.findById(id).orElse(null) } 
        }.distinctBy { it.id }

        if (distinctSchools.isEmpty()) {
            sendTextMessage(from, "Sorry, we couldn't determine your school affiliation.")
            return
        }

        val primarySchoolId = distinctSchools.first().id
        val primarySchoolName = distinctSchools.first().name

        // Build the multi-school context
        val schoolContextStr = if (distinctSchools.size == 1) {
            """
            You are a helpful school assistant for $primarySchoolName. 
            Always provide the schoolId ($primarySchoolId) to tools.
            """.trimIndent()
        } else {
            val schoolsList = distinctSchools.joinToString("\n") { "- ${it.name} (ID: ${it.id})" }
            """
            You are a helpful school assistant for multiple schools. The user is associated with these schools:
            $schoolsList
            
            CRITICAL INSTRUCTION: Since the user belongs to multiple schools, you MUST know which school they are asking about before calling any tools. 
            If their message does not clearly specify the school, politely ask them to clarify (e.g., "Which school are you asking about?"). 
            Once known, pass the correct schoolId from the list above to the data tools. DO NOT guess the schoolId.
            """.trimIndent()
        }

        // Fetch Conversation History for stateless memory tracking
        val cleanedPhone = from.removePrefix("+")
        // We include messages sent to/from "+<phone>"
        val recentMessages = messageRepository.findByRecipientPhoneOrderByCreatedAtDesc("+$cleanedPhone")
            .take(8)
            .reversed()
            
        val historyStr = if (recentMessages.isNotEmpty()) {
            "\n--- RECENT CONVERSATION HISTORY ---\n" + recentMessages.joinToString("\n") { msg ->
                val sender = if (msg.direction == com.haneef._school.entity.MessageDirection.INCOMING) "User" else "Assistant"
                "$sender: ${msg.content}"
            } + "\n--- END OF HISTORY ---\n(Ensure you use this context to understand the user's latest query.)\n"
        } else ""

        try {
            val response = chatClient.prompt()
                .system("""
                    $schoolContextStr
                    You are chatting with ${user.fullName} (phone: $from).
                    
                    AVAILABLE TOOLS:
                    - Use 'getChildAcademicDetails' if the user asks about their children's progress, grades, subjects, or attendance. Pass parentUserId = ${user.id} and the correct schoolId.
                    - Use 'getParentFinancialSummary' if the user asks about their fees, payments, or balance. Pass parentUserId = ${user.id} and the correct schoolId.
                    - Use 'getSchoolInfo' if the user asks for the class timetable, school calendar, or upcoming events. Pass the correct schoolId.
                    - Use 'getStudentInfo' if searching for a specific student's basic details. Pass the correct schoolId.
                    
                    SECURITY: You can ONLY provide academic and financial details for the user's OWN children. The tools handle this check internally if you pass the correct parentUserId.
                    
                    TONE: Be professional, supportive, and clear.
                    $historyStr
                """.trimIndent())
                .user(text)
                .tools(schoolDataToolsProvider.getObject())
                .call()
                .content()

            sendTextMessage(from, response ?: "I'm sorry, I couldn't find any information for that query.")
        } catch (e: Exception) {
            println("AI Query Error for user $from: ${e.message}")
            // Silently drop the message instead of responding with an error per user request
        }
    }

    private fun extractErrorMessage(errorBody: String?): String {
        if (errorBody.isNullOrBlank()) return "Unknown Error"
        return try {
            val map = objectMapper.readValue(errorBody, Map::class.java)
            val error = map["error"] as? Map<*, *>
            val details = (error?.get("error_data") as? Map<*, *>)?.get("details") as? String
            details ?: error?.get("message") as? String ?: "Meta API Error"
        } catch (e: Exception) {
            "Meta API Error"
        }
    }
}

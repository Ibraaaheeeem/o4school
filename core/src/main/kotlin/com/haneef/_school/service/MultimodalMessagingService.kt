package com.haneef._school.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.haneef._school.entity.User
import com.haneef._school.entity.WhatsAppTemplate
import com.haneef._school.event.MessageFailureEvent
import com.haneef._school.repository.UserRepository
import com.haneef._school.repository.WhatsAppTemplateRepository
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

enum class MultimodalChannel {
    WHATSAPP, SMS
}

@Service
class MultimodalMessagingService(
    private val whatsappService: WhatsAppService,
    private val smsMessagingService: SmsMessagingService,
    private val templateParameterResolver: TemplateParameterResolver,
    private val userRepository: UserRepository,
    private val templateRepository: WhatsAppTemplateRepository
) {
    private val logger = LoggerFactory.getLogger(MultimodalMessagingService::class.java)

    /**
     * Sends a message via the specified channels either simultaneously or with fallback.
     */
    fun sendMultimodalMessage(
        recipient: User?,
        phoneNumber: String?,
        schoolId: UUID,
        senderId: UUID,
        senderUser: User,
        strategy: String,
        channelPriority: List<MultimodalChannel>,
        template: WhatsAppTemplate?,
        templateName: String?,
        namedManualParams: Map<String, String>,
        manualParams: List<Map<String, Any>>,
        broadcastId: UUID? = null
    ): Map<MultimodalChannel, Boolean> {
        val results = mutableMapOf<MultimodalChannel, Boolean>()

        if (strategy == "SIMULTANEOUS") {
            for (channel in channelPriority) {
                results[channel] = attemptSend(
                    channel = channel,
                    recipient = recipient,
                    phoneNumber = phoneNumber,
                    schoolId = schoolId,
                    senderId = senderId,
                    senderUser = senderUser,
                    template = template,
                    templateName = templateName,
                    namedManualParams = namedManualParams,
                    manualParams = manualParams,
                    triggerFallback = false,
                    isFallback = false,
                    broadcastId = broadcastId
                )
            }
        } else if (strategy == "FALLBACK") {
            // First leg: attempt first channel and set triggerFallback = true
            val firstChannel = channelPriority.getOrNull(0)
            val secondChannel = channelPriority.getOrNull(1)

            if (firstChannel != null) {
                val firstSuccess = attemptSend(
                    channel = firstChannel,
                    recipient = recipient,
                    phoneNumber = phoneNumber,
                    schoolId = schoolId,
                    senderId = senderId,
                    senderUser = senderUser,
                    template = template,
                    templateName = templateName,
                    namedManualParams = namedManualParams,
                    manualParams = manualParams,
                    triggerFallback = (secondChannel != null), // Trigger next if there's a second channel
                    isFallback = false,
                    fallbackChannel = secondChannel, // Pass the next channel
                    broadcastId = broadcastId
                )
                results[firstChannel] = firstSuccess
                
                // If the first leg SYNC fails (e.g. API error), we can immediately try the second leg
                if (!firstSuccess && secondChannel != null) {
                    val secondSuccess = attemptSend(
                        channel = secondChannel,
                        recipient = recipient,
                        phoneNumber = phoneNumber,
                        schoolId = schoolId,
                        senderId = senderId,
                        senderUser = senderUser,
                        template = template,
                        templateName = templateName,
                        namedManualParams = namedManualParams,
                        manualParams = manualParams,
                        triggerFallback = false,
                        isFallback = true, // Allow negative balance for second leg
                        broadcastId = broadcastId
                    )
                    results[secondChannel] = secondSuccess
                } else if (secondChannel != null) {
                    results[secondChannel] = false // Pending outcome of first leg (ASYNC via webhook)
                }
            }
        }

        return results
    }

    private fun attemptSend(
        channel: MultimodalChannel,
        recipient: User?,
        phoneNumber: String?,
        schoolId: UUID,
        senderId: UUID,
        senderUser: User,
        template: WhatsAppTemplate?,
        templateName: String?,
        namedManualParams: Map<String, String>,
        manualParams: List<Map<String, Any>>,
        triggerFallback: Boolean,
        isFallback: Boolean,
        fallbackChannel: MultimodalChannel? = null,
        broadcastId: UUID? = null
    ): Boolean {
        // Resolve the correct template name for the current channel
        val effectiveTemplateName = when {
            channel == MultimodalChannel.SMS && templateName != null && !templateName.startsWith("sms_") -> "sms_$templateName"
            channel == MultimodalChannel.WHATSAPP && templateName?.startsWith("sms_") == true -> templateName.substringAfter("sms_")
            else -> templateName
        }

        // Fetch the template if the name changed or if it was null
        val effectiveTemplate = if (effectiveTemplateName != templateName || template == null) {
            effectiveTemplateName?.let { tName ->
                templateRepository.findByTemplateNameAndSchoolId(tName, schoolId).orElse(null)
            } ?: template
        } else {
            template
        }

        val mapper = jacksonObjectMapper()
        val paramsJson = try {
            if (manualParams.isNotEmpty()) mapper.writeValueAsString(manualParams) else null
        } catch (e: Exception) { null }

        return try {
            when (channel) {
                MultimodalChannel.WHATSAPP -> {
                    if (phoneNumber == null || effectiveTemplateName.isNullOrBlank()) return false
                    
                    val components = if (effectiveTemplate != null && recipient != null) {
                        val resolved = templateParameterResolver.resolveAllParameters(recipient, schoolId, effectiveTemplate, namedManualParams)
                        if (resolved.isNotEmpty()) listOf(mapOf("type" to "body", "parameters" to resolved)) else emptyList()
                    } else if (manualParams.isNotEmpty()) {
                        listOf(mapOf("type" to "body", "parameters" to manualParams))
                    } else emptyList()

                    whatsappService.sendTemplateMessage(
                        to = phoneNumber,
                        templateName = effectiveTemplateName,
                        languageCode = effectiveTemplate?.language ?: "en_GB",
                        components = components,
                        user = recipient,
                        schoolId = schoolId,
                        triggerFallback = triggerFallback,
                        isFallback = isFallback,
                        paramsJson = paramsJson,
                        fallbackChannel = fallbackChannel?.name,
                        broadcastId = broadcastId
                    )
                }
                MultimodalChannel.SMS -> {
                    if (phoneNumber == null || effectiveTemplateName.isNullOrBlank() || effectiveTemplate == null) return false
                    
                    var smsText = ""
                    if (recipient != null) {
                        val resolvedParams = templateParameterResolver.resolveAllParameters(recipient, schoolId, effectiveTemplate, namedManualParams)
                        val bodyText = effectiveTemplate.componentsJson?.let { json ->
                            val parsedComponents = mapper.readValue<List<Map<String, Any>>>(json)
                            var text = parsedComponents.find { c -> c["type"] == "BODY" }?.get("text") as? String ?: ""
                            resolvedParams.forEach { param ->
                                val pName = param["parameter_name"] as? String ?: ""
                                if (pName.isNotBlank()) {
                                    text = text.replace("{{$pName}}", param["text"] as? String ?: "")
                                }
                            }
                            resolvedParams.forEachIndexed { index, param ->
                                text = text.replace("{{${index + 1}}}", param["text"] as? String ?: "")
                            }
                            text
                        } ?: ""
                        smsText = bodyText
                    }

                    if (smsText.isNotBlank()) {
                        smsMessagingService.sendSms(
                            to = phoneNumber,
                            content = smsText,
                            schoolId = schoolId,
                            userId = senderId,
                            user = senderUser,
                            triggerFallback = triggerFallback,
                            isFallback = isFallback,
                            templateName = effectiveTemplateName,
                            paramsJson = paramsJson,
                            fallbackChannel = fallbackChannel?.name,
                            broadcastId = broadcastId
                        )
                    } else false
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to push message via channel $channel: ${e.message}", e)
            false
        }
    }

    /**
     * Listen for message failure events and trigger fallback if a secondary channel is available.
     */
    @EventListener
    @Transactional
    fun handleMessageFailure(event: MessageFailureEvent) {
        val fallbackChannel = event.fallbackChannel ?: return
        
        logger.info("Triggering multimodal fallback for message ${event.messageId} from ${event.channel} to $fallbackChannel")

        val schoolId = event.schoolId ?: return
        val senderUserId = event.senderUserId ?: return

        val senderUser = userRepository.findById(senderUserId).orElse(null) ?: return
        
        // Try to find the recipient by phone to help with parameter resolution (e.g. {{name}})
        val recipient = userRepository.findByPhoneNumber(event.recipientPhone).orElse(null)

        // Determine the fallback template name
        val originalTemplateName = event.templateName
        val fallbackTemplateName = when {
            event.channel == MultimodalChannel.WHATSAPP && fallbackChannel == MultimodalChannel.SMS && originalTemplateName != null -> {
                "sms_$originalTemplateName"
            }
            event.channel == MultimodalChannel.SMS && fallbackChannel == MultimodalChannel.WHATSAPP && originalTemplateName?.startsWith("sms_") == true -> {
                originalTemplateName.substringAfter("sms_")
            }
            else -> originalTemplateName
        }

        val template = fallbackTemplateName?.let { tName ->
            templateRepository.findByTemplateNameAndSchoolId(tName, schoolId).orElse(null)
        }

        val manualParams = try {
            event.paramsJson?.let { jacksonObjectMapper().readValue<List<Map<String, Any>>>(it) } ?: emptyList()
        } catch (e: Exception) { emptyList() }

        attemptSend(
            channel = fallbackChannel,
            recipient = recipient,
            phoneNumber = event.recipientPhone,
            schoolId = schoolId,
            senderId = senderUserId,
            senderUser = senderUser,
            template = template,
            templateName = fallbackTemplateName,
            namedManualParams = emptyMap(), // Params are already in manualParams/paramsJson
            manualParams = manualParams,
            triggerFallback = false, // Stop after one fallback leg for now
            isFallback = true,
            broadcastId = event.broadcastId
        )
    }
}

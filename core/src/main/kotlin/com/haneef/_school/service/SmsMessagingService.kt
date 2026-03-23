package com.haneef._school.service

import com.haneef._school.entity.*
import com.haneef._school.event.MessageFailureEvent
import com.haneef._school.repository.SmsMessageRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*
import java.util.concurrent.CompletableFuture
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext

@Service
class SmsMessagingService(
    private val termiiSmsService: TermiiSmsService,
    private val smsMessageRepository: SmsMessageRepository,
    private val subscriptionService: SubscriptionService,
    private val phoneNumberService: PhoneNumberService,
    private val schoolRepository: com.haneef._school.repository.SchoolRepository,
    private val eventPublisher: ApplicationEventPublisher
) {
    private val logger = LoggerFactory.getLogger(SmsMessagingService::class.java)

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    /**
     * Send an SMS and track usage
     */
    @Transactional
    fun sendSms(
        to: String,
        content: String,
        schoolId: UUID,
        userId: UUID,
        user: User? = null,
        school: School? = null,
        triggerFallback: Boolean = false,
        isFallback: Boolean = false,
        templateName: String? = null,
        paramsJson: String? = null,
        fallbackChannel: String? = null,
        broadcastId: UUID? = null
    ): Boolean {
        // 1. Format phone number for Termii (234...)
        val formattedNumberWithPlus = phoneNumberService.parseAndFormatPhoneNumber(to, "NG")
        val formattedNumber = formattedNumberWithPlus?.removePrefix("+")
        if (formattedNumber == null) {
            logger.error("Invalid phone number: $to")
            return false
        }

        // 2. Call Termii API
        val messageId = termiiSmsService.sendSms(formattedNumber, content)
        val status = if (messageId != null) "SENT" else "FAILED"

        // 3. Resolve school if not provided
        val effectiveSchool = school ?: schoolRepository.findById(schoolId).orElse(null)

        // 4. Log the message
        val smsMessage = SmsMessage(
            recipientPhone = formattedNumber,
            content = content,
            direction = MessageDirection.OUTGOING,
            status = status,
            externalMessageId = messageId,
            user = user,
            school = effectiveSchool,
            triggerFallback = triggerFallback,
            isFallback = isFallback,
            templateName = templateName,
            paramsJson = paramsJson,
            fallbackChannel = fallbackChannel,
            broadcastId = broadcastId
        )
        smsMessageRepository.save(smsMessage)

        // 4. Deduct balance if successful
        if (messageId != null) {
            try {
                subscriptionService.deductTokens(
                    schoolId = schoolId,
                    userId = userId,
                    feature = ServiceFeature.SMS_MESSAGING,
                    amount = 1,
                    description = "SMS sent to $to",
                    allowNegative = isFallback
                )
            } catch (e: Exception) {
                logger.error("Failed to deduct SMS token for school $schoolId: ${e.message}")
                // We still returned true because the message was sent
            }
        }

        return messageId != null
    }

    /**
     * Send bulk SMS and track usage
     */
    @Transactional
    fun sendBulkSms(
        recipients: List<String>,
        content: String,
        schoolId: UUID,
        userId: UUID,
        user: User? = null,
        school: School? = null,
        broadcastId: UUID? = null
    ): Boolean {
        // Normalize and keep only valid formatted numbers (without leading '+')
        val validRecipients = recipients.mapNotNull {
            phoneNumberService.parseAndFormatPhoneNumber(it, "NG")?.removePrefix("+")
        }

        if (validRecipients.isEmpty()) {
            logger.error("No valid recipients for bulk SMS")
            return false
        }

        // Use the Termii bulk API for efficiency and then persist a per-recipient record
        val externalId = try {
            termiiSmsService.sendBulkSms(validRecipients, content)
        } catch (ex: Exception) {
            logger.error("Termii bulk send failed: ${ex.message}", ex)
            null
        }

        val status = if (externalId != null) "SENT" else "FAILED"

        val effectiveSchool = school ?: schoolRepository.findById(schoolId).orElse(null)

        var savedCount = 0
        for (phone in validRecipients) {
            val smsMessage = SmsMessage(
                recipientPhone = phone,
                content = content,
                direction = MessageDirection.OUTGOING,
                status = status,
                externalMessageId = externalId,
                user = user,
                school = effectiveSchool,
                triggerFallback = false,
                isFallback = false,
                templateName = null,
                paramsJson = null,
                fallbackChannel = null,
                broadcastId = broadcastId
            )
            smsMessageRepository.save(smsMessage)
            savedCount++

            if (externalId != null) {
                try {
                    subscriptionService.deductTokens(
                        schoolId = schoolId,
                        userId = userId,
                        feature = ServiceFeature.SMS_MESSAGING,
                        amount = 1,
                        description = "Bulk SMS sent to $phone"
                    )
                } catch (e: Exception) {
                    logger.error("Failed to deduct SMS token for school $schoolId: ${e.message}")
                }
            }
        }

        logger.info("Bulk SMS: attempted=${validRecipients.size}, succeeded=$savedCount")

        return externalId != null && savedCount > 0
    }

    @Transactional
    fun processWebhook(payload: Map<String, Any>) {
        val externalId = payload["message_id"] as? String
        val status = payload["status"] as? String
        if (externalId.isNullOrBlank() || status.isNullOrBlank()) {
            logger.warn("Webhook payload missing message_id or status: $payload")
            return
        }
        
        val messages = smsMessageRepository.findByExternalMessageId(externalId)
        if (messages.isEmpty()) {
            logger.warn("Received Termii webhook for unknown message ID: $externalId")
            return
        }

            val newStatus = status.uppercase()
        messages.forEach { message ->
            val oldStatus = message.status
            message.status = newStatus
            smsMessageRepository.save(message)

            // Trigger fallback if FAILED and enabled
                if (newStatus == "FAILED" || newStatus == "EXPIRED" || newStatus == "UNDELIVERED") {
                    if (message.triggerFallback && !message.fallbackChannel.isNullOrBlank()) {
                        val msgId = message.id
                        if (msgId == null) {
                            logger.warn("Skipping fallback publish: message id is null for externalId=$externalId")
                        } else {
                            try {
                                val fallbackEnum = try {
                                    MultimodalChannel.valueOf(message.fallbackChannel!!)
                                } catch (ex: Exception) {
                                    logger.error("Invalid fallback channel stored on message id=$msgId: ${message.fallbackChannel}")
                                    null
                                }

                                if (fallbackEnum != null) {
                                    eventPublisher.publishEvent(
                                        MessageFailureEvent(
                                            channel = MultimodalChannel.SMS,
                                            messageId = msgId,
                                            recipientPhone = message.recipientPhone,
                                            schoolId = message.school?.id,
                                            senderUserId = message.user?.id,
                                            templateName = message.templateName,
                                            paramsJson = message.paramsJson,
                                            fallbackChannel = fallbackEnum,
                                            broadcastId = message.broadcastId,
                                            content = message.content
                                        )
                                    )
                                }
                            } catch (ex: Exception) {
                                logger.error("Failed to publish fallback event for message id=${message.id}: ${ex.message}", ex)
                            }
                        }
                    }
                }
        }
    }
}

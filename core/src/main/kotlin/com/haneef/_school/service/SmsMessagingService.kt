package com.haneef._school.service

import com.haneef._school.entity.*
import com.haneef._school.event.MessageFailureEvent
import com.haneef._school.repository.SmsMessageRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

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
        val formattedNumber = phoneNumberService.parseAndFormatPhoneNumber(to, "NG")?.removePrefix("+")
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
            recipientPhone = to,
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
        val validRecipients = recipients.mapNotNull { 
            phoneNumberService.parseAndFormatPhoneNumber(it, "NG")?.removePrefix("+") 
        }

        if (validRecipients.isEmpty()) {
            logger.error("No valid recipients for bulk SMS")
            return false
        }

        // termiiSmsService.sendBulkSms(validRecipients, content) 
        // For simplicity and usage tracking per message, we might loop or use bulk
        // If we use bulk, we deduct validRecipients.size tokens
        
        val messageId = termiiSmsService.sendBulkSms(validRecipients, content)
        val status = if (messageId != null) "SENT" else "FAILED"

        // Resolve school if not provided
        val effectiveSchool = school ?: schoolRepository.findById(schoolId).orElse(null)

        // Log each message (or just one for the bulk)
        // Existing pattern seems to log each message for history
        recipients.forEach { phone ->
            val smsMessage = SmsMessage(
                recipientPhone = phone,
                content = content,
                direction = MessageDirection.OUTGOING,
                status = status,
                externalMessageId = messageId,
                user = user,
                school = effectiveSchool,
                broadcastId = broadcastId
            )
            smsMessageRepository.save(smsMessage)
        }

        if (messageId != null) {
            try {
                subscriptionService.deductTokens(
                    schoolId = schoolId,
                    userId = userId,
                    feature = ServiceFeature.SMS_MESSAGING,
                    amount = validRecipients.size,
                    description = "Bulk SMS sent to ${validRecipients.size} recipients"
                )
            } catch (e: Exception) {
                logger.error("Failed to deduct SMS tokens for bulk SMS: ${e.message}")
            }
        }
        return messageId != null
    }

    @Transactional
    fun processWebhook(payload: Map<String, Any>) {
        val externalId = payload["message_id"] as? String ?: return
        val status = payload["status"] as? String ?: return
        
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
                if (message.triggerFallback && message.fallbackChannel != null) {
                    eventPublisher.publishEvent(
                        MessageFailureEvent(
                            channel = MultimodalChannel.SMS,
                            messageId = message.id!!,
                            recipientPhone = message.recipientPhone,
                            schoolId = message.school?.id,
                            senderUserId = message.user?.id,
                            templateName = message.templateName,
                            paramsJson = message.paramsJson,
                            fallbackChannel = MultimodalChannel.valueOf(message.fallbackChannel!!),
                            broadcastId = message.broadcastId,
                            content = message.content
                        )
                    )
                }
            }
        }
    }
}

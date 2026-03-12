package com.haneef._school.controller

import com.haneef._school.service.SmsMessagingService
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/sms")
class SmsWebhookController(
    private val smsMessagingService: SmsMessagingService
) {
    private val logger = LoggerFactory.getLogger(SmsWebhookController::class.java)

    @PostMapping("/webhook")
    fun handleTermiiWebhook(@RequestBody payload: Map<String, Any>) {
        logger.info("Received Termii SMS webhook: $payload")
        try {
            smsMessagingService.processWebhook(payload)
        } catch (e: Exception) {
            logger.error("Error processing Termii SMS webhook: ${e.message}", e)
        }
    }
}

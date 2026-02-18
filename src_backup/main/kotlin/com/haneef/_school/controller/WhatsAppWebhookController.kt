package com.haneef._school.controller

import com.haneef._school.config.WhatsAppProperties
import com.haneef._school.service.WhatsAppService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/webhook/whatsapp")
class WhatsAppWebhookController(
    private val properties: WhatsAppProperties,
    private val whatsappService: WhatsAppService
) {

    @GetMapping
    fun verifyWebhook(
        @RequestParam("hub.mode") mode: String,
        @RequestParam("hub.verify_token") token: String,
        @RequestParam("hub.challenge") challenge: String
    ): ResponseEntity<String> {
        return if (mode == "subscribe" && token == properties.verifyToken) {
            ResponseEntity.ok(challenge)
        } else {
            ResponseEntity.status(403).build()
        }
    }

    @PostMapping
    fun handleIncomingMessage(@RequestBody payload: Map<String, Any>): ResponseEntity<Void> {
        whatsappService.processWebhook(payload)
        return ResponseEntity.ok().build()
    }
}

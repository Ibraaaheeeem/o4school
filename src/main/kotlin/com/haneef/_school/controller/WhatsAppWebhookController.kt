package com.haneef._school.controller

import com.haneef._school.config.WhatsAppProperties
import com.haneef._school.entity.MessageDirection
import com.haneef._school.repository.WhatsAppMessageRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/webhooks/whatsapp")
class WhatsAppWebhookController(
    private val properties: WhatsAppProperties,
    private val messageRepository: WhatsAppMessageRepository
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
        val entries = payload["entry"] as? List<*> ?: return ResponseEntity.ok().build()
        
        for (entry in entries) {
            val entryMap = entry as? Map<*, *> ?: continue
            val changes = entryMap["changes"] as? List<*> ?: continue
            
            for (change in changes) {
                val changeMap = change as? Map<*, *> ?: continue
                val value = changeMap["value"] as? Map<*, *> ?: continue
                val messages = value["messages"] as? List<*> ?: continue
                
                for (message in messages) {
                    val messageMap = message as? Map<*, *> ?: continue
                    val from = messageMap["from"] as? String ?: continue
                    val text = (messageMap["text"] as? Map<*, *>)?.get("body") as? String ?: ""
                    val metaId = messageMap["id"] as? String
                    
                    val incoming = com.haneef._school.entity.WhatsAppMessage(
                        recipientPhone = from,
                        content = text,
                        direction = MessageDirection.INCOMING,
                        status = "RECEIVED",
                        metaMessageId = metaId
                    )
                    messageRepository.save(incoming)
                }
            }
        }
        
        return ResponseEntity.ok().build()
    }
}

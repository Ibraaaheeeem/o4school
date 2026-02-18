package com.haneef._school.controller

import com.haneef._school.entity.SettlementType
import com.fasterxml.jackson.databind.ObjectMapper
import com.haneef._school.entity.Settlement
import com.haneef._school.repository.SettlementRepository
import com.haneef._school.repository.SquadParentWalletRepository
import com.haneef._school.service.FinancialService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@RestController
@RequestMapping("/squad/webhooks")
class SquadWebhookController(
    private val settlementRepository: SettlementRepository,
    private val squadParentWalletRepository: SquadParentWalletRepository,
    private val financialService: FinancialService,
    private val objectMapper: ObjectMapper,
    @Value("\${squad.secret.key:}") private val secretKey: String
) {

    private val logger = LoggerFactory.getLogger(SquadWebhookController::class.java)

    @PostMapping
    @Transactional
    fun handleWebhook(
        @RequestBody payload: String,
        @RequestHeader("x-squad-encrypted-body") signature: String
    ): ResponseEntity<String> {
        logger.info("Received Squad webhook: $payload")

        // Verify signature
        if (!verifySignature(payload, signature)) {
            logger.error("Invalid Squad webhook signature")
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid signature")
        }

        try {
            val event = objectMapper.readTree(payload)
            val eventType = event.path("Event").asText()

            if (eventType == "charge_successful") {
                val body = event.path("Body")
                val reference = body.path("transaction_ref").asText()
                val amountKobo = body.path("amount").asLong()
                val amount = BigDecimal.valueOf(amountKobo).divide(BigDecimal(100))
                val email = body.path("email").asText()
                val currency = body.path("currency").asText()
                
                // Check if settlement already exists
                if (settlementRepository.existsByReference(reference)) {
                    logger.info("Settlement already processed for reference: $reference")
                    return ResponseEntity.ok("Already processed")
                }

                // Find wallet by email (Squad usually links via email or we can pass metadata)
                // For now, we'll try to find by email or customer identifier
                val wallet = squadParentWalletRepository.findByCustomerIdentifier(email)
                    ?: squadParentWalletRepository.findAll().find { it.parent.user.email == email }

                if (wallet != null) {
                    val settlement = Settlement(
                        squadWallet = wallet,
                        amount = amount,
                        currency = currency,
                        reference = reference,
                        status = "success",
                        paymentChannel = "card", // Or extract from payload if available
                        payerEmail = email,
                        rawPayload = payload,
                        settlementType = SettlementType.SQUAD,
                    )
                    
                    // Set school ID from wallet
                    settlement.schoolId = wallet.schoolId

                    settlementRepository.save(settlement)
                    
                    // Process financial settlement (distribute to invoices)
                    financialService.processSettlement(settlement)
                    
                    logger.info("Successfully processed Squad settlement: $reference for amount: $amount")
                } else {
                    logger.warn("No Squad wallet found for email: $email. Settlement saved without wallet link.")
                    
                    val settlement = Settlement(
                        amount = amount,
                        currency = currency,
                        reference = reference,
                        status = "success",
                        paymentChannel = "card",
                        payerEmail = email,
                        rawPayload = payload,
                        settlementType = SettlementType.SQUAD,
                    )
                    settlementRepository.save(settlement)
                }
            }

            return ResponseEntity.ok("Webhook received")
        } catch (e: Exception) {
            logger.error("Error processing Squad webhook: ${e.message}", e)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing webhook")
        }
    }

    private fun verifySignature(payload: String, signature: String): Boolean {
        if (secretKey.isBlank()) return true // Skip verification in dev if no key
        
        try {
            val hmac = Mac.getInstance("HmacSHA512")
            val secretKeySpec = SecretKeySpec(secretKey.toByteArray(), "HmacSHA512")
            hmac.init(secretKeySpec)
            val hash = hmac.doFinal(payload.toByteArray())
            val calculatedSignature = hash.joinToString("") { "%02x".format(it) }.uppercase()
            
            return calculatedSignature.equals(signature, ignoreCase = true)
        } catch (e: Exception) {
            logger.error("Error verifying signature", e)
            return false
        }
    }
}

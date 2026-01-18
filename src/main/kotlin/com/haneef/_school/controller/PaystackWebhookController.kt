package com.haneef._school.controller

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.haneef._school.entity.Settlement
import com.haneef._school.repository.ParentWalletRepository
import com.haneef._school.repository.SettlementRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import jakarta.servlet.http.HttpServletRequest

@RestController
@RequestMapping("/paystack/webhooks")
class PaystackWebhookController(
    private val settlementRepository: SettlementRepository,
    private val parentWalletRepository: ParentWalletRepository,
    private val academicSessionRepository: com.haneef._school.repository.AcademicSessionRepository,
    private val termRepository: com.haneef._school.repository.TermRepository,
    private val schoolRepository: com.haneef._school.repository.SchoolRepository,
    private val invoiceRepository: com.haneef._school.repository.InvoiceRepository,
    private val invoiceService: com.haneef._school.service.InvoiceService,
    private val emailService: com.haneef._school.service.EmailService,
    private val financialService: com.haneef._school.service.FinancialService,
    private val objectMapper: ObjectMapper,
    @Value("\${paystack.secret.key}") private val paystackSecretKey: String
) {

    private val logger = LoggerFactory.getLogger(PaystackWebhookController::class.java)

    @PostMapping
    @Transactional
    fun handleWebhook(
        @RequestBody payload: String,
        @RequestHeader("x-paystack-signature") signature: String,
        request: HttpServletRequest
    ): ResponseEntity<String> {
        
        // 1. Verify Signature
        
        if (!verifySignature(payload, signature)) {
            logger.warn("Invalid Paystack signature received from IP: ${request.remoteAddr}")
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid signature")
        }

        try {
            val eventNode = objectMapper.readTree(payload)
            val event = eventNode.get("event").asText()

            if (event == "charge.success") {
                processChargeSuccess(eventNode, payload)
            } else {
                logger.info("Received unhandled Paystack event: $event")
            }

            return ResponseEntity.ok("Webhook received")
        } catch (e: Exception) {
            logger.error("Error processing webhook: ${e.message}", e)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing webhook")
        }
    }

    private fun processChargeSuccess(eventNode: JsonNode, rawPayload: String) {
        val data = eventNode.get("data")
        val reference = data.get("reference").asText()
        
        // Idempotency check
        if (settlementRepository.existsByReference(reference)) {
            logger.info("Settlement already processed for reference: $reference")
            return
        }

        val amountKobo = data.get("amount").asLong()
        val amount = BigDecimal(amountKobo).divide(BigDecimal(100)) // Convert to main currency unit
        val currency = data.get("currency").asText()
        val status = data.get("status").asText()
        
        val customer = data.get("customer")
        val customerEmail = customer.get("email")?.asText() ?: ""
        val customerCode = customer.get("customer_code")?.asText()
        
        val authorization = data.get("authorization")
        val channel = authorization?.get("channel")?.asText()
        
        // Try to find wallet by customer code first (more reliable for dedicated accounts)
        var wallet = if (customerCode != null) {
            parentWalletRepository.findByCustomerCode(customerCode)
        } else {
            null
        }
        
        // Fallback: Try to find by dedicated account number if available in authorization (for bank transfers)
        if (wallet == null && authorization != null && authorization.has("receiver_bank_account_number")) {
             val receiverAccount = authorization.get("receiver_bank_account_number").asText()
             wallet = parentWalletRepository.findByAccountNumber(receiverAccount)
        }
        
        // Fallback: Try to find by email (less reliable if parent changed email)
        // Note: This requires a custom query joining Parent -> User, which we might not have direct access to here easily without a service
        // For now, let's rely on customer code which is standard for Paystack dedicated accounts
        
        if (wallet != null) {
            logger.info("Processing settlement for wallet: ${wallet.accountNumber}, Amount: $amount")
            
            // Fetch current session and term
            val currentSession = academicSessionRepository.findBySchoolIdAndIsCurrentSessionAndIsActive(wallet.schoolId!!, true, true)
            val currentTerm = termRepository.findBySchoolIdAndIsCurrentTermAndIsActive(wallet.schoolId!!, true, true).orElse(null)

            // Create Settlement
            val settlement = Settlement(
                wallet = wallet,
                amount = amount,
                currency = currency,
                reference = reference,
                status = status,
                paymentChannel = channel,
                payerEmail = customerEmail,
                rawPayload = rawPayload,
                academicSession = currentSession,
                term = currentTerm
            ).apply {
                schoolId = wallet.schoolId // Inherit school context
            }
            
            settlementRepository.save(settlement)
            
            // Update Wallet Balance
            wallet.balance = wallet.balance.add(amount)
            parentWalletRepository.save(wallet)
            
            logger.info("Wallet balance updated. New Balance: ${wallet.balance}")
            
            // Generate Invoice and Send Email
            try {
                val school = schoolRepository.findById(wallet.schoolId!!).orElse(null)
                if (school != null) {
                    // Calculate Financials
                    val parent = wallet.parent
                    logger.info("Calculating financials for Parent: ${parent.user.email} using FinancialService")
                    
                    val financialData = financialService.getFeeBreakdown(parent, currentSession?.id, currentTerm?.id)
                    
                    val totalBill = financialData["totalFees"] as BigDecimal
                    val settledBill = financialData["totalSettled"] as BigDecimal
                    val outstandingBill = financialData["balance"] as BigDecimal
                    
                    logger.info("Financials Calculated - Total: $totalBill, Settled: $settledBill, Outstanding: $outstandingBill")

                    var invoiceImage: ByteArray? = null
                    // try {
                    //     invoiceImage = invoiceService.generateInvoiceImage(
                    //         settlement, 
                    //         school, 
                    //         totalBill,
                    //         settledBill,
                    //         outstandingBill
                    //     )
                    // } catch (e: Throwable) {
                    //     logger.error("Failed to generate invoice image (likely AWT missing): ${e.message}. Proceeding without attachment.", e)
                    // }

                    emailService.sendSettlementEmail(
                        to = customerEmail,
                        settlement = settlement,
                        schoolName = school.name ?: "School Name",
                        balance = wallet.balance,
                        totalBill = totalBill,
                        settledBill = settledBill,
                        outstandingBill = outstandingBill,
                        invoiceImage = invoiceImage
                    )
                    logger.info("Settlement email sent to $customerEmail")
                } else {
                    logger.warn("School not found for wallet ${wallet.id}, skipping email")
                }
            } catch (e: Exception) {
                logger.error("Error sending settlement email: ${e.message}", e)
            }
        } else {
            logger.warn("Could not find wallet for settlement. Reference: $reference, Customer Code: $customerCode, Email: $customerEmail")
            // Ideally, save to an 'UnclaimedSettlements' table or log for manual review
        }
    }

    private fun verifySignature(payload: String, signature: String): Boolean {
        try {
            val sha512HMAC = Mac.getInstance("HmacSHA512")
            val secretKey = SecretKeySpec(paystackSecretKey.trim().toByteArray(), "HmacSHA512")
            sha512HMAC.init(secretKey)
            val hashBytes = sha512HMAC.doFinal(payload.toByteArray())
            val expectedSignature = hashBytes.joinToString("") { "%02x".format(it) }
            
            return expectedSignature == signature
        } catch (e: Exception) {
            logger.error("Error verifying signature: ${e.message}", e)
            return false
        }
    }
}

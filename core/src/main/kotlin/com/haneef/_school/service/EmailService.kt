package com.haneef._school.service

import jakarta.mail.internet.InternetAddress
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Service

@Service
class EmailService(
    private val mailSender: JavaMailSender
) {
    companion object {
        private val logger = LoggerFactory.getLogger(EmailService::class.java)
    }

    @Value("\${spring.mail.from:}")
    private lateinit var fromEmail: String

    @Value("\${SENDER_NAME:4School Admin}")
    private lateinit var senderName: String

    fun validateConfiguration() {
        require(fromEmail.isNotBlank()) { "spring.mail.from must not be blank" }
    }

    private fun buildFromAddress(): String? {
        val trimmedFrom = fromEmail.trim()
        if (trimmedFrom.isBlank()) {
            logger.warn("SMTP 'from' email (spring.mail.from) is not configured or is blank — email will not be sent")
            return null
        }

        return try {
            InternetAddress(trimmedFrom, true).address
        } catch (ex: Exception) {
            logger.error("SMTP 'from' email is invalid: {}", trimmedFrom, ex)
            null
        }
    }

    private fun isValidRecipient(address: String): Boolean {
        return try {
            InternetAddress(address.trim(), true)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun sendApprovalEmail(to: String, name: String, role: String = "User", school: String = "School"): Pair<Boolean, Any> {
        val from = buildFromAddress() ?: return Pair(false, "spring.mail.from is invalid or not configured")
        if (!isValidRecipient(to)) {
            return Pair(false, "Recipient email is invalid")
        }

        val subject = "Your 4School Account has been Approved!"
        val roleDisplay = when(role) {
            "SCHOOL_ADMIN" -> "School Administrator"
            "TEACHER", "STAFF" -> "Staff Member"
            "PARENT" -> "Parent"
            else -> role
        }

        val actionText = if (role == "SCHOOL_ADMIN") {
            "You can now log in to your account and proceed to set up your school."
        } else {
            "You can now log in to your account and access your dashboard."
        }

        val text = """
            Dear $name,
            
            We are pleased to inform you that your registration as a $roleDisplay in $school on the 4School platform has been approved.
            
            $actionText
            
            Login URL: https://4school.app/login
            
            Best regards,
            The 4School Team
        """.trimIndent()

        logger.debug("Sending approval email: from='{}', to='{}', subject='{}'", from, to, subject)

        val message = SimpleMailMessage()
        message.from = from
        message.setTo(to)
        message.subject = subject
        message.text = text

        return try {
            mailSender.send(message)
            Pair(true, Unit)
        } catch (e: Exception) {
            logger.error("Failed to send approval email to {}", to, e)
            Pair(false, (e.message ?: "Failed to send email"))
        }
    }

    fun sendOtpEmail(to: String, otp: String): Pair<Boolean, Any> {
        val from = buildFromAddress() ?: return Pair(false, "spring.mail.from is invalid or not configured")
        if (!isValidRecipient(to)) {
            return Pair(false, "Recipient email is invalid")
        }

        val subject = "Your 4School Activation Code"
        val text = """
            Hello,
            
            Thank you for registering on 4School. Your activation code is:
            
            $otp
            
            Please enter this code on the activation page to verify your email address then provide your password.
            
            Best regards,
            The 4School Team
        """.trimIndent()

        logger.debug("Sending OTP email: from='{}', to='{}', subject='{}'", from, to, subject)
        logger.debug("OTP email text length: {} chars", text.length)

        val message = SimpleMailMessage()
        message.from = from
        message.setTo(to)
        message.subject = subject
        message.text = text

        return try {
            mailSender.send(message)
            Pair(true, Unit)
        } catch (e: Exception) {
            logger.error("Failed to send OTP email to {}", to, e)
            Pair(false, (e.message ?: "Failed to send OTP email"))
        }
    }

    fun sendSettlementEmail(
        to: String, 
        settlement: com.haneef._school.entity.Settlement, 
        schoolName: String, 
        balance: java.math.BigDecimal,
        totalBill: java.math.BigDecimal,
        settledBill: java.math.BigDecimal,
        outstandingBill: java.math.BigDecimal,
        invoiceImage: ByteArray?
    ): Pair<Boolean, Any> {
        try {
            val mimeMessage = mailSender.createMimeMessage()
            val helper = org.springframework.mail.javamail.MimeMessageHelper(mimeMessage, true)
            
            val formattedSchoolName = "4School/$schoolName"
            val from = buildFromAddress() ?: return Pair(false, "spring.mail.from is invalid or not configured")
            if (!isValidRecipient(to)) {
                return Pair(false, "Recipient email is invalid")
            }

            logger.debug("Sending settlement email: from='{}', to='{}', subject='Payment Receipt - {}'", from, to, formattedSchoolName)

            helper.setFrom(from)
            helper.setTo(to)
            helper.setSubject("Payment Receipt - $formattedSchoolName")
            
            val text = """
                Dear Parent,
                
                We have received your payment of ${settlement.currency} ${settlement.amount}.
                
                Details:
                School: $formattedSchoolName
                Date: ${settlement.createdAt.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))}
                Reference: ${settlement.reference}
                Session: ${settlement.academicSession?.sessionName ?: "N/A"}
                Term: ${settlement.term?.termName ?: "N/A"}
                
                Financial Summary:
                Total Bill: ${settlement.currency} $totalBill
                Settled Bill: ${settlement.currency} $settledBill
                Outstanding Bill: ${settlement.currency} $outstandingBill
                Current Balance: ${settlement.currency} $balance
                
                
                For more details, please visit your profile on 4School.
                
                Best regards,
                $formattedSchoolName Administration
            """.trimIndent()
            
            helper.setText(text)
            
            if (invoiceImage != null) {
                helper.addAttachment("Invoice_${settlement.reference}.png", org.springframework.core.io.ByteArrayResource(invoiceImage))
            }
            
            mailSender.send(mimeMessage)
            return Pair(true, Unit)
        } catch (e: Exception) {
            logger.error("Failed to send settlement email to {}", to, e)
            return Pair(false, (e.message ?: "Failed to send settlement email"))
        }
    }
}

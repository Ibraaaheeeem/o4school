package com.haneef._school.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Service

@Service
class EmailService(
    private val mailSender: JavaMailSender
) {
    @Value("\${spring.mail.from}")
    private lateinit var fromEmail: String

    @Value("\${SENDER_NAME:4School Admin}")
    private lateinit var senderName: String

    fun sendApprovalEmail(to: String, name: String, role: String = "User") {
        val message = SimpleMailMessage()
        message.from = "$senderName <$fromEmail>"
        message.setTo(to)
        message.subject = "Your 4School Account has been Approved!"
        
        val roleDisplay = when(role) {
            "SCHOOL_ADMIN" -> "School Administrator"
            "TEACHER", "STAFF" -> "Staff Member"
            "PARENT" -> "Parent"
            else -> role
        }

        val actionText = if (role == "SCHOOL_ADMIN") {
            "You can now log in to your account and proceed to set up your school."
        } else {
            "You can now log in to your account and access your school's dashboard."
        }

        message.text = """
            Dear $name,
            
            We are pleased to inform you that your registration as a $roleDisplay on the 4School platform has been approved.
            
            $actionText
            
            Login URL: https://4school.app/login
            
            Best regards,
            The 4School Team
        """.trimIndent()
        
        try {
            mailSender.send(message)
        } catch (e: Exception) {
            println("Failed to send email to $to: ${e.message}")
        }
    }

    fun sendOtpEmail(to: String, otp: String) {
        val message = SimpleMailMessage()
        message.from = "$senderName <$fromEmail>"
        message.setTo(to)
        message.subject = "Your 4School Activation Code"
        message.text = """
            Hello,
            
            Thank you for registering on 4School. Your activation code is:
            
            $otp
            
            Please enter this code on the activation page to verify your email address then provide your password.
            
            Best regards,
            The 4School Team
        """.trimIndent()
        
        try {
            mailSender.send(message)
        } catch (e: Exception) {
            println("Failed to send OTP email to $to: ${e.message}")
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
    ) {
        try {
            val mimeMessage = mailSender.createMimeMessage()
            val helper = org.springframework.mail.javamail.MimeMessageHelper(mimeMessage, true)
            
            val formattedSchoolName = "4School/$schoolName"
            
            helper.setFrom("$senderName <$fromEmail>")
            helper.setTo(to)
            helper.setSubject("Payment Receipt - $formattedSchoolName")
            
            val attachmentText = if (invoiceImage != null) "Please find the receipt attached." else "Receipt generation is currently unavailable."
            
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
                
                
                For more details, please visit your profile on 4School.
                
                Best regards,
                $formattedSchoolName Administration
            """.trimIndent()
            
            helper.setText(text)
            
            if (invoiceImage != null) {
                helper.addAttachment("Invoice_${settlement.reference}.png", org.springframework.core.io.ByteArrayResource(invoiceImage))
            }
            
            mailSender.send(mimeMessage)
        } catch (e: Exception) {
            println("Failed to send settlement email to $to: ${e.message}")
            e.printStackTrace()
        }
    }
}

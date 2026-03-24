package com.haneef._school.service

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender

class EmailServiceTest {

    private lateinit var mailSender: JavaMailSender
    private lateinit var emailService: EmailService

    @BeforeEach
    fun setUp() {
        mailSender = mockk(relaxed = true)
        emailService = EmailService(mailSender)
        setField(emailService, "fromEmail", "noreply@4school.app")
        setField(emailService, "senderName", "4School Admin")
    }

    @Test
    fun `validateConfiguration throws for blank from email`() {
        setField(emailService, "fromEmail", "   ")

        assertThrows(IllegalArgumentException::class.java) {
            emailService.validateConfiguration()
        }
    }

    @Test
    fun `sendApprovalEmail succeeds for valid mail sender`() {
        every { mailSender.send(any<SimpleMailMessage>()) } just runs

        val result = emailService.sendApprovalEmail("parent@example.com", "Parent Name", "PARENT", "Alpha School")

        assertTrue(result.first)
        verify(exactly = 1) { mailSender.send(any<SimpleMailMessage>()) }
    }

    @Test
    fun `sendOtpEmail returns failure when mail sender throws`() {
        every { mailSender.send(any<SimpleMailMessage>()) } throws RuntimeException("SMTP unavailable")

        val result = emailService.sendOtpEmail("parent@example.com", "123456")

        assertFalse(result.first)
    }

    private fun setField(target: Any, fieldName: String, value: Any) {
        val field = target.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(target, value)
    }
}

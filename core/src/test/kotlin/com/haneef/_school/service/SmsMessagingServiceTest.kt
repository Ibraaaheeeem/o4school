package com.haneef._school.service

import com.haneef._school.entity.*
import com.haneef._school.event.MessageFailureEvent
import com.haneef._school.repository.SmsMessageRepository
import io.mockk.*
import io.mockk.impl.annotations.MockK
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.util.*

class SmsMessagingServiceTest {
    private val termiiSmsService = mockk<TermiiSmsService>(relaxed = true)
    private val smsMessageRepository = mockk<SmsMessageRepository>(relaxed = true)
    private val subscriptionService = mockk<SubscriptionService>(relaxed = true)
    private val phoneNumberService = mockk<PhoneNumberService>(relaxed = true)
    private val schoolRepository = mockk<com.haneef._school.repository.SchoolRepository>(relaxed = true)
    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)

    private val service = SmsMessagingService(
        termiiSmsService,
        smsMessageRepository,
        subscriptionService,
        phoneNumberService,
        schoolRepository,
        eventPublisher
    )

    private val schoolId = UUID.randomUUID()
    private val userId = UUID.randomUUID()

    @BeforeEach
    fun setup() {
        clearAllMocks()
    }

    @Test
    fun `sendSms success saves formatted phone and deducts`() {
        val to = "08012345678"
        every { phoneNumberService.parseAndFormatPhoneNumber(to, "NG") } returns "+2348012345678"
        every { termiiSmsService.sendSms("2348012345678", "hello") } returns "mid-1"
        every { schoolRepository.findById(schoolId) } returns Optional.of(School())
        every { smsMessageRepository.save(any()) } answers { firstArg() }
        every { subscriptionService.deductTokens(any(), any(), any(), any(), any(), any()) } just Runs

        val result = service.sendSms(to, "hello", schoolId, userId)

        assertTrue(result)
        verify(exactly = 1) { smsMessageRepository.save(match { it.recipientPhone == "2348012345678" }) }
        verify(exactly = 1) { subscriptionService.deductTokens(schoolId, userId, ServiceFeature.SMS_MESSAGING, 1, any(), allowNegative = false) }
    }

    @Test
    fun `sendSms invalid phone returns false and does not save`() {
        val to = "invalid"
        every { phoneNumberService.parseAndFormatPhoneNumber(to, "NG") } returns null

        val result = service.sendSms(to, "hello", schoolId, userId)

        assertFalse(result)
        verify { smsMessageRepository wasNot Called }
    }

    @Test
    fun `sendBulkSms saves only valid recipients and deducts tokens`() {
        val recipients = listOf("valid", "bad")
        every { phoneNumberService.parseAndFormatPhoneNumber("valid", "NG") } returns "+2348012345678"
        every { phoneNumberService.parseAndFormatPhoneNumber("bad", "NG") } returns null
        every { termiiSmsService.sendBulkSms(listOf("2348012345678"), "hi") } returns "bulk-1"
        every { smsMessageRepository.save(any()) } answers { firstArg() }
        every { subscriptionService.deductTokens(any(), any(), any(), any(), any()) } just Runs
        every { schoolRepository.findById(schoolId) } returns Optional.of(School())

        val result = service.sendBulkSms(recipients, "hi", schoolId, userId)

        assertTrue(result)
        verify(exactly = 1) { smsMessageRepository.save(match { it.recipientPhone == "2348012345678" }) }
        verify(exactly = 1) { subscriptionService.deductTokens(schoolId, userId, ServiceFeature.SMS_MESSAGING, 1, any()) }
    }

    @Test
    fun `processWebhook publishes fallback event when failed and triggerFallback true`() {
        val externalId = "ext-1"
        val msgId = UUID.randomUUID()
        val message = SmsMessage(
            recipientPhone = "2348012345678",
            content = "failed message"
        ).apply {
            id = msgId
            externalMessageId = externalId
            triggerFallback = true
            fallbackChannel = "WHATSAPP"
        }
        every { smsMessageRepository.findByExternalMessageId(externalId) } returns listOf(message)
        every { smsMessageRepository.save(any()) } answers { firstArg() }
        every { eventPublisher.publishEvent(any()) } just Runs

        service.processWebhook(mapOf("message_id" to externalId, "status" to "FAILED"))

        verify { smsMessageRepository.save(message) }
        val slot = slot<Any>()
        verify { eventPublisher.publishEvent(capture(slot)) }
        assertTrue(slot.captured is MessageFailureEvent)
        assertEquals("2348012345678", (slot.captured as MessageFailureEvent).recipientPhone)
    }

    @Test
    fun `processWebhook missing fields logs and returns`() {
        service.processWebhook(emptyMap())
        // nothing to verify other than not throwing — ensure repository not called
        verify { smsMessageRepository wasNot Called }
    }
}

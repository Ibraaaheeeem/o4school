package com.haneef._school.service

import com.haneef._school.entity.*
import com.haneef._school.repository.*
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*

class SubscriptionServiceTest {
    private val subscriptionRepo = mockk<SchoolSubscriptionRepository>(relaxed = true)
    private val usageLogRepo = mockk<ServiceUsageLogRepository>(relaxed = true)
    private val schoolRepo = mockk<SchoolRepository>(relaxed = true)
    private val userRepo = mockk<UserRepository>(relaxed = true)
    private val studentRepo = mockk<StudentRepository>(relaxed = true)

    private val fixedInstant = Instant.parse("2026-03-21T12:00:00Z")
    private val clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)

    private val service = SubscriptionService(
        subscriptionRepo,
        usageLogRepo,
        schoolRepo,
        userRepo,
        studentRepo,
        1000L,
        clock
    )

    private val schoolId = UUID.randomUUID()
    private val userId = UUID.randomUUID()

    @BeforeEach
    fun setup() {
        io.mockk.clearAllMocks()
    }

    @Test
    fun `createDefaultSubscription when missing`() {
        every { subscriptionRepo.findBySchoolId(schoolId) } returns null
        val school = School()
        every { schoolRepo.findById(schoolId) } returns Optional.of(school)
        every { subscriptionRepo.save(any()) } answers { firstArg() }

        val sub = service.getSubscription(schoolId)

        assertNotNull(sub.validUntil)
        assertEquals(SubscriptionStatus.ACTIVE, sub.subscriptionStatus)
        verify(exactly = 1) { subscriptionRepo.save(any()) }
    }

    @Test
    fun `deductTokens reduces balance and logs usage`() {
        val sub = SchoolSubscription(school = School())
        sub.aiTokenBalance = 100
        every { subscriptionRepo.findBySchoolId(schoolId) } returns sub
        every { subscriptionRepo.save(any()) } answers { firstArg() }
        val user = com.haneef._school.entity.User()
        every { userRepo.findById(userId) } returns Optional.of(user)
        every { usageLogRepo.save(any()) } answers { firstArg() }

        service.deductTokens(schoolId, userId, ServiceFeature.AI_TOKENS, 10, "test", allowNegative = false)

        assertEquals(90, sub.aiTokenBalance)
        verify { subscriptionRepo.save(sub) }
        verify { usageLogRepo.save(any()) }
    }

    @Test
    fun `deductTokens throws when insufficient`() {
        val sub = SchoolSubscription(school = School())
        sub.smsBalance = 1
        every { subscriptionRepo.findBySchoolId(schoolId) } returns sub

        val ex = assertThrows(IllegalStateException::class.java) {
            service.deductTokens(schoolId, userId, ServiceFeature.SMS_MESSAGING, 5, "x", allowNegative = false)
        }
        assertTrue(ex.message!!.contains("Insufficient"))
    }

    @Test
    fun `topUpTokens increases balance`() {
        val sub = SchoolSubscription(school = School())
        sub.whatsappBalance = 5
        every { subscriptionRepo.findBySchoolId(schoolId) } returns sub
        every { subscriptionRepo.save(any()) } answers { firstArg() }

        service.topUpTokens(schoolId, ServiceFeature.WHATSAPP_MESSAGING, 10)

        assertEquals(15, sub.whatsappBalance)
        verify { subscriptionRepo.save(sub) }
    }

    @Test
    fun `updateFeeCollectionSettings activates and stores details`() {
        val sub = SchoolSubscription(school = School())
        every { subscriptionRepo.findBySchoolId(schoolId) } returns sub
        every { subscriptionRepo.save(any()) } answers { firstArg() }

        service.updateFeeCollectionSettings(schoolId, "12345", "MyBank", true, true)

        assertTrue(sub.feeCollectionActive)
        assertEquals("12345", sub.accountNumber)
        assertEquals("MyBank", sub.bankName)
        verify { subscriptionRepo.save(sub) }
    }

    @Test
    fun `calculateRenewalFee multiplies student count by rate`() {
        every { studentRepo.countBySchoolIdAndIsActive(schoolId, true) } returns 10L

        val fee = service.calculateRenewalFee(schoolId)
        assertEquals(10L * 1000L, fee)
    }

    @Test
    fun `renewSubscription extends validUntil appropriately`() {
        val sub = SchoolSubscription(school = School())
        // expired
        sub.validUntil = LocalDateTime.now(clock).minusDays(1)
        every { subscriptionRepo.findBySchoolId(schoolId) } returns sub
        every { subscriptionRepo.save(any()) } answers { firstArg() }

        service.renewSubscription(schoolId, years = 2)
        val expected = LocalDateTime.now(clock).plusYears(2)
        assertEquals(expected.withNano(0), sub.validUntil?.withNano(0))

        // when active, extend from current validUntil
        sub.validUntil = LocalDateTime.now(clock).plusDays(10)
        service.renewSubscription(schoolId, years = 1)
        val expected2 = LocalDateTime.now(clock).plusDays(10).plusYears(1)
        assertEquals(expected2.withNano(0), sub.validUntil?.withNano(0))
    }
}

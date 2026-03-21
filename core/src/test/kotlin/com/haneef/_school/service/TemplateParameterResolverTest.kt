package com.haneef._school.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.haneef._school.entity.WhatsAppTemplate
import io.mockk.mockk
import io.mockk.every
import java.util.Optional
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class TemplateParameterResolverTest {
    private val parentRepo = mockk<com.haneef._school.repository.ParentRepository>(relaxed = true)
    private val staffRepo = mockk<com.haneef._school.repository.StaffRepository>(relaxed = true)
    private val studentRepo = mockk<com.haneef._school.repository.StudentRepository>(relaxed = true)
    private val financialService = mockk<com.haneef._school.service.FinancialService>(relaxed = true)
    private val schoolRepo = mockk<com.haneef._school.repository.SchoolRepository>(relaxed = true)
    private val sessionRepo = mockk<com.haneef._school.repository.AcademicSessionRepository>(relaxed = true)
    private val termRepo = mockk<com.haneef._school.repository.TermRepository>(relaxed = true)
    private val invoiceRepo = mockk<com.haneef._school.repository.InvoiceRepository>(relaxed = true)
    private val settlementRepo = mockk<com.haneef._school.repository.SettlementRepository>(relaxed = true)

    private val resolver = TemplateParameterResolver(
        parentRepo, staffRepo, studentRepo, financialService,
        schoolRepo, sessionRepo, termRepo, invoiceRepo, settlementRepo
    )

    init {
        // Prevent relaxed mocks of repositories from returning mocked Optionals
        // which can cause ClassCastException when calling `.orElse(null)`.
        every { schoolRepo.findById(any()) } returns Optional.empty()
        every { invoiceRepo.findById(any()) } returns Optional.empty()
        every { settlementRepo.findById(any()) } returns Optional.empty()
    }
    @Test
    fun `extract placeholders from array root and resolve via extraParams`() {
        val json = """[
            {"type":"BODY","text":"Hello {{name}} and {{amount}}"}
        ]"""
        val tpl = WhatsAppTemplate(
            templateId = "tpl-1",
            templateName = "tpl",
            language = "en_US",
            category = "GENERAL",
            parameterCount = 2,
            parameterMapping = null,
            componentsJson = json,
            status = "ACTIVE",
            lastSyncedAt = java.time.LocalDateTime.now(),
            isForBroadcast = false,
            targetRole = "GENERAL"
        )

        val user = com.haneef._school.entity.User().apply {
            id = null
            firstName = "Test"
            lastName = "User"
        }

        val extra = mapOf("name" to "Alice", "amount" to "500")

        val params = resolver.resolveAllParameters(user, java.util.UUID.randomUUID(), tpl, extra)
        assertEquals(2, params.size)
        assertEquals("Alice", params[0]["text"])
        assertEquals("500", params[1]["text"])
        assertTrue(params[0].containsKey("parameter_name"))
    }

    @Test
    fun `extract placeholders from object root and handle mapping and manual overrides`() {
        val json = """{
            "body": { "text": "Dear {{student_name}}, your fee is {{amount}}" }
        }"""
        val tpl = WhatsAppTemplate(
            templateId = "tpl-2",
            templateName = "tpl2",
            language = "en_US",
            category = "GENERAL",
            parameterCount = 2,
            parameterMapping = "1=student_name,2=amount",
            componentsJson = json,
            status = "ACTIVE",
            lastSyncedAt = java.time.LocalDateTime.now(),
            isForBroadcast = false,
            targetRole = "GENERAL"
        )

        val user = com.haneef._school.entity.User().apply {
            id = null
            firstName = "Test"
            lastName = "User"
        }

        val extra = mapOf("1" to "Overridden Name", "amount" to "1500")

        val params = resolver.resolveAllParameters(user, java.util.UUID.randomUUID(), tpl, extra)
        // ensure manual override by mapped id works and object-root extraction
        // placeholders extraction order may vary; find entries by parameter_name
        val byName = params.associateBy { it["parameter_name"]?.toString() ?: it["text"].toString() }
        assertEquals("Overridden Name", (byName["student_name"]?.get("text") as String?))
        assertEquals("1500", (byName["amount"]?.get("text") as String?))
    }

    @Test
    fun `resolveParameters uses mapping and manual overrides`() {
        val mapping = "1=student_name,2=amount"
        val user = com.haneef._school.entity.User().apply {
            id = null
            firstName = "Test"
            lastName = "User"
        }
        val extra = mapOf("1" to "Manual Student", "amount" to "2000")

        val params = resolver.resolveParameters(user, java.util.UUID.randomUUID(), mapping, extra)
        assertEquals(2, params.size)
        assertEquals("Manual Student", params[0]["text"])
        assertEquals("2000", params[1]["text"])
    }

    @Test
    fun `toBigDecimal handles strings and numbers`() {
        assertEquals(BigDecimal("123.45"), resolver.toBigDecimal("123.45"))
        assertEquals(BigDecimal.valueOf(42), resolver.toBigDecimal(42))
        assertEquals(BigDecimal.valueOf(3.14), resolver.toBigDecimal(3.14))
    }

    @Test
    fun `formatCurrency returns non-empty string`() {
        val out = resolver.formatCurrency(BigDecimal("1234.5"))
        assertTrue(out.isNotBlank())
    }
}

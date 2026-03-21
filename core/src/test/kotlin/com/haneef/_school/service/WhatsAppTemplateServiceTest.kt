package com.haneef._school.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.haneef._school.config.WhatsAppProperties
import com.haneef._school.entity.WhatsAppTemplate
import com.haneef._school.repository.WhatsAppTemplateRepository
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.client.RestTemplate
import java.util.*

class WhatsAppTemplateServiceTest {
    private val templateRepository = mockk<WhatsAppTemplateRepository>(relaxed = true)
    private val properties = WhatsAppProperties()
    private val restTemplate = mockk<RestTemplate>(relaxed = true)
    private val objectMapper = ObjectMapper()

    private lateinit var service: WhatsAppTemplateService

    @BeforeEach
    fun setup() {
        properties.businessAccountId = "acct123"
        properties.accessToken = "token"
        service = WhatsAppTemplateService(templateRepository, properties, restTemplate, objectMapper)
        clearAllMocks()
    }

    @Test
    fun `markSelectedTemplates saves only changed templates`() {
        val t1 = WhatsAppTemplate(templateId = "id1", templateName = "one", language = "en", category = "c")
        val t2 = WhatsAppTemplate(templateId = "id2", templateName = "two", language = "en", category = "c")
        t2.isForBroadcast = true

        every { templateRepository.findAll() } returns listOf(t1, t2)
        every { templateRepository.save(any()) } returnsArgument 0

        service.markSelectedTemplates(listOf("one"))

        verify(exactly = 1) { templateRepository.save(match { it.templateName == "one" && it.isForBroadcast }) }
        verify(exactly = 1) { templateRepository.save(match { it.templateName == "two" && !it.isForBroadcast }) }
    }

    @Test
    fun `createMetaTemplate persists new template on success`() {
        val components = listOf(mapOf("type" to "BODY", "text" to "Hello {{1}}"))
        val response = mockk<org.springframework.http.ResponseEntity<Map<*, *>>>(relaxed = true)
        every { response.statusCode } returns HttpStatus.OK
        every { response.body } returns mapOf("id" to "meta123")
        every { restTemplate.postForEntity(any<String>(), any(), eq(Map::class.java)) } returns response
        every { templateRepository.findByTemplateId("meta123") } returns Optional.empty()
        every { templateRepository.save(any()) } returnsArgument 0

        val result = service.createMetaTemplate("My Name", "UTILITY", "en_US", components, null)

        assert(result)
        verify { templateRepository.save(match { it.templateId == "meta123" && it.templateName == "my_name" }) }
    }

    @Test
    fun `createMetaTemplate returns false when properties missing`() {
        val svc = WhatsAppTemplateService(templateRepository, WhatsAppProperties(), restTemplate, objectMapper)
        val ok = svc.createMetaTemplate("n","c","en", emptyList())
        assert(!ok)
    }

    @Test
    fun `syncTemplates and deleteMetaTemplate are disabled`() {
        val sync = service.syncTemplates()
        assert(!sync)

        val deleted = service.deleteMetaTemplate(UUID.randomUUID())
        assert(!deleted)
    }
}

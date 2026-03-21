package com.haneef._school.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.haneef._school.entity.MessageDirection
import com.haneef._school.entity.WhatsAppMessage
import com.haneef._school.entity.WhatsAppTemplate
import com.haneef._school.repository.SchoolRepository
import com.haneef._school.repository.UserRepository
import com.haneef._school.repository.WhatsAppMessageRepository
import com.haneef._school.repository.WhatsAppTemplateRepository
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import java.util.*

class WhatsAppServiceTest {
    private val properties = mockk<com.haneef._school.config.WhatsAppProperties>(relaxed = true)
    private val messageRepository = mockk<WhatsAppMessageRepository>(relaxed = true)
    private val phoneNumberService = mockk<PhoneNumberService>(relaxed = true)
    private val schoolRepository = mockk<SchoolRepository>(relaxed = true)
    private val userRepository = mockk<UserRepository>(relaxed = true)
    private val templateRepository = mockk<WhatsAppTemplateRepository>(relaxed = true)
    private val objectMapper = ObjectMapper()
    private val chatClient = mockk<org.springframework.ai.chat.client.ChatClient>(relaxed = true)
    private val schoolDataToolsProvider = mockk<org.springframework.beans.factory.ObjectProvider<SchoolDataTools>>(relaxed = true)
    private val userSchoolRoleRepository = mockk<com.haneef._school.repository.UserSchoolRoleRepository>(relaxed = true)
    private val subscriptionService = mockk<SubscriptionService>(relaxed = true)
    private val eventPublisher = mockk<org.springframework.context.ApplicationEventPublisher>(relaxed = true)
    private val restTemplate = mockk<RestTemplate>(relaxed = true)

    private lateinit var service: WhatsAppService

    @BeforeEach
    fun setup() {
        every { properties.accessToken } returns "token"
        every { phoneNumberService.cleanPhoneNumber(any()) } answers { firstArg<String>() }
        every { schoolRepository.findById(any()) } returns Optional.empty()

        service = WhatsAppService(
            properties,
            messageRepository,
            phoneNumberService,
            schoolRepository,
            userRepository,
            templateRepository,
            objectMapper,
            chatClient,
            schoolDataToolsProvider,
            userSchoolRoleRepository,
            subscriptionService,
            eventPublisher,
            "url",
            "turl",
            restTemplate
        )
        // keep mocks/stubs set up for tests
    }

    @Test
    fun `sendTextMessage success saves message`() {
        val response = mockk<org.springframework.http.ResponseEntity<Map<*, *>>>(relaxed = true)
        every { response.statusCode } returns org.springframework.http.HttpStatus.OK
        every { response.body } returns mapOf("messages" to listOf(mapOf("id" to "m1")))
        every { restTemplate.postForEntity(any<String>(), any(), eq(Map::class.java)) } returns response
        every { messageRepository.save(any()) } returnsArgument 0

        val ok = service.sendTextMessage("+1234", "hello")

        assert(ok)
        verify { messageRepository.save(match { it.direction == MessageDirection.OUTGOING && it.status == "SENT" }) }
    }

    @Test
    fun `sendTextMessage failure logs and saves failed message`() {
        every { restTemplate.postForEntity(any<String>(), any(), eq(Map::class.java)) } throws RuntimeException("boom")
        every { messageRepository.save(any()) } returnsArgument 0

        val ok = service.sendTextMessage("+1234", "hello")

        assert(!ok)
        verify { messageRepository.save(match { it.status == "FAILED" }) }
    }

    @Test
    fun `sendTemplateMessage reconstructs message and saves`() {
        val componentsJson = "[{\"type\":\"BODY\",\"text\":\"Hi {{1}}\"}]"
        val template = WhatsAppTemplate(templateId = "t1", templateName = "tpl", language = "en", category = "c", componentsJson = componentsJson)
        every { templateRepository.findByTemplateName("tpl") } returns Optional.of(template)

        val response2 = mockk<org.springframework.http.ResponseEntity<Map<*, *>>>(relaxed = true)
        every { response2.statusCode } returns org.springframework.http.HttpStatus.OK
        every { response2.body } returns mapOf("messages" to listOf(mapOf("id" to "m1")))
        every { restTemplate.postForEntity(any<String>(), any(), eq(Map::class.java)) } returns response2
        every { messageRepository.save(any()) } returnsArgument 0

        val ok = service.sendTemplateMessage("+1234", "tpl", components = listOf(mapOf("type" to "body", "parameters" to listOf(mapOf("text" to "Alice")))), schoolId = UUID.randomUUID())

        assert(ok)
        verify { messageRepository.save(match { it.content.contains("Alice") && it.status == "SENT" }) }
    }
}

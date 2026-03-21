package com.haneef._school.service

import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import java.util.*

class TermiiSmsServiceTest {

    private val restTemplate: RestTemplate = mockk()
    private val svc = TermiiSmsService(
        apiKey = "key",
        apiUrl = "https://v3.api.termii.com",
        senderId = "o4School",
        channel = "generic",
        objectMapper = com.fasterxml.jackson.databind.ObjectMapper(),
        restTemplate = restTemplate
    )

    @Test
    fun `sendSms returns message id on success`() {
        val to = "2348012345678"
        val body = mapOf("message_id" to "mid-1")
        every {
            restTemplate.exchange(
                any<String>(),
                eq(HttpMethod.POST),
                any<HttpEntity<*>>(),
                ofType<ParameterizedTypeReference<Map<String, Any>>>()
            )
        } returns ResponseEntity(body as Map<String, Any>, HttpStatus.OK)

        val res = svc.sendSms(to, "hello")
        assertEquals("mid-1", res)
    }

    @Test
    fun `sendSms returns null for invalid phone`() {
        val res = svc.sendSms("123", "x")
        assertNull(res)
    }

    @Test
    fun `sendBulkSms returns message id on success`() {
        val to = listOf("2348012345678", "08012345679")
        val body = mapOf("message_id" to "bulk-1")
        every {
            restTemplate.exchange(
                any<String>(),
                eq(HttpMethod.POST),
                any<HttpEntity<*>>(),
                ofType<ParameterizedTypeReference<Map<String, Any>>>()
            )
        } returns ResponseEntity(body as Map<String, Any>, HttpStatus.OK)

        val res = svc.sendBulkSms(to, "bulk")
        assertEquals("bulk-1", res)
    }

    @Test
    fun `sendBulkSms returns null when all phones invalid`() {
        val res = svc.sendBulkSms(listOf("1", "2"), "m")
        assertNull(res)
    }
}

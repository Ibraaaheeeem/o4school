package com.haneef._school.service

import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import io.mockk.every
import io.mockk.mockk
import jakarta.servlet.http.HttpServletRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

class RateLimitingServiceTest {

    private val service = RateLimitingService()

    @Test
    fun `resolveClientKey uses first forwarded ip`() {
        val request = mockk<HttpServletRequest>()
        every { request.getHeader("X-Forwarded-For") } returns "203.0.113.10, 10.0.0.1"
        every { request.getHeader("X-Real-IP") } returns "198.51.100.9"

        val clientKey = service.resolveClientKey(request)

        assertEquals("203.0.113.10", clientKey)
    }

    @Test
    fun `resolveClientKey trims forwarded header values and still picks first ip`() {
        val request = mockk<HttpServletRequest>()
        every { request.getHeader("X-Forwarded-For") } returns "   203.0.113.10   ,   10.0.0.1   "
        every { request.getHeader("X-Real-IP") } returns "198.51.100.9"

        val clientKey = service.resolveClientKey(request)

        assertEquals("203.0.113.10", clientKey)
    }

    @Test
    fun `resolveClientKey falls back to real ip when forwarded is missing`() {
        val request = mockk<HttpServletRequest>()
        every { request.getHeader("X-Forwarded-For") } returns null
        every { request.getHeader("X-Real-IP") } returns "198.51.100.9"

        val clientKey = service.resolveClientKey(request)

        assertEquals("198.51.100.9", clientKey)
    }

    @Test
    fun `resolveClientKey falls back to remote address and normalizes case`() {
        val request = mockk<HttpServletRequest>()
        every { request.getHeader("X-Forwarded-For") } returns ""
        every { request.getHeader("X-Real-IP") } returns ""
        every { request.remoteAddr } returns "ABCD::EF01"

        val clientKey = service.resolveClientKey(request)

        assertEquals("abcd::ef01", clientKey)
    }

    @Test
    fun `resolveClientKey falls back to unknown when remote address is null`() {
        val request = mockk<HttpServletRequest>()
        every { request.getHeader("X-Forwarded-For") } returns ""
        every { request.getHeader("X-Real-IP") } returns ""
        every { request.remoteAddr } returns null

        val clientKey = service.resolveClientKey(request)

        assertEquals("unknown", clientKey)
    }

    @Test
    fun `getFormattedWaitTime returns zero when token is available`() {
        val bucket = Bucket.builder()
            .addLimit(
                Bandwidth.builder()
                    .capacity(1)
                    .refillGreedy(1, Duration.ofMinutes(1))
                    .build()
            )
            .build()

        val waitTime = service.getFormattedWaitTime(bucket)

        assertEquals("0 seconds", waitTime)
    }

    @Test
    fun `getFormattedWaitTime returns positive wait when token is exhausted`() {
        val bucket = Bucket.builder()
            .addLimit(
                Bandwidth.builder()
                    .capacity(1)
                    .refillGreedy(1, Duration.ofSeconds(1))
                    .build()
            )
            .build()

        bucket.tryConsume(1)
        val waitTime = service.getFormattedWaitTime(bucket)

        assertNotEquals("0 seconds", waitTime)
        assertTrue(waitTime.matches(Regex("\\d+ (seconds|minutes)( and \\d+ seconds)?")))
    }
}

package com.haneef._school.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HtmlSanitizerServiceTest {

    private val service = HtmlSanitizerService()

    @Test
    fun `sanitize returns empty string for null or blank input`() {
        assertEquals("", service.sanitize(null))
        assertEquals("", service.sanitize("   "))
    }

    @Test
    fun `sanitize removes script tags and event handlers`() {
        val html = "<p onclick=\"evil()\">Hello</p><script>alert('x')</script><img src=\"x\" onerror=\"bad()\" />"

        val sanitized = service.sanitize(html)

        assertFalse(sanitized.contains("<script", ignoreCase = true))
        assertFalse(sanitized.contains("onclick", ignoreCase = true))
        assertFalse(sanitized.contains("onerror", ignoreCase = true))
        assertTrue(sanitized.contains("<p>Hello</p>"))
    }
}

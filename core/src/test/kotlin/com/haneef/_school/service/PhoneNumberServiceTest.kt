package com.haneef._school.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PhoneNumberServiceTest {

    private val service = PhoneNumberService()

    @Test
    fun `parseAndFormatPhoneNumber formats local nigeria number to E164`() {
        val result = service.parseAndFormatPhoneNumber("08012345678", "NG")

        assertEquals("+2348012345678", result)
    }

    @Test
    fun `parseAndFormatPhoneNumber normalizes number that already includes calling code`() {
        val result = service.parseAndFormatPhoneNumber("23408012345678", "NG")

        assertEquals("+2348012345678", result)
    }

    @Test
    fun `parseAndFormatPhoneNumber validates country code format`() {
        val result = service.parseAndFormatPhoneNumber("08012345678", "NGA")

        assertNull(result)
    }

    @Test
    fun `parseAndFormatPhoneNumber rejects E164 number with mismatched country`() {
        val result = service.parseAndFormatPhoneNumber("+12025550123", "NG")

        assertNull(result)
    }

    @Test
    fun `parseAndFormatPhoneNumber rejects invalid national length for configured country`() {
        val result = service.parseAndFormatPhoneNumber("0801234", "NG")

        assertNull(result)
    }

    @Test
    fun `cleanPhoneNumber trims whitespace and preserves leading plus`() {
        val cleaned = service.cleanPhoneNumber("  +234 (801) 234-5678  ")

        assertEquals("+2348012345678", cleaned)
    }

    @Test
    fun `formatForDisplay returns grouped E164 number`() {
        val display = service.formatForDisplay("+2348012345678")

        assertEquals("+234 801 234 5678", display)
    }

    @Test
    fun `formatForDisplay returns original when value is not E164`() {
        val display = service.formatForDisplay("invalid-number")

        assertEquals("invalid-number", display)
    }

    @Test
    fun `parseAndFormatPhoneNumber uses configured country rules when provided`() {
        val configuredService = PhoneNumberService("FR:33:9:9")

        val valid = configuredService.parseAndFormatPhoneNumber("0612345678", "FR")
        val invalid = configuredService.parseAndFormatPhoneNumber("06123456", "FR")

        assertEquals("+33612345678", valid)
        assertNull(invalid)
    }

    @Test
    fun `isValidPhoneNumber returns true for valid and false for invalid values`() {
        assertTrue(service.isValidPhoneNumber("08012345678", "NG"))
        assertFalse(service.isValidPhoneNumber("123", "NG"))
    }
}

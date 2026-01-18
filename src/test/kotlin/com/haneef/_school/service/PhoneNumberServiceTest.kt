package com.haneef._school.service

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class PhoneNumberServiceTest {

    private val phoneNumberService = PhoneNumberService()

    @Test
    fun `should remove leading zeros from phone numbers`() {
        // Test cases for leading zero removal
        assertEquals("8012345678", phoneNumberService.cleanPhoneNumber("08012345678"))
        assertEquals("12345678", phoneNumberService.cleanPhoneNumber("0012345678"))
        assertEquals("8012345678", phoneNumberService.cleanPhoneNumber("8012345678")) // no change
        assertEquals("0", phoneNumberService.cleanPhoneNumber("0")) // preserve single zero
        assertEquals("123", phoneNumberService.cleanPhoneNumber("000123"))
        assertEquals("", phoneNumberService.cleanPhoneNumber("")) // empty string
    }

    @Test
    fun `should clean phone numbers with non-digit characters`() {
        // Test cases for cleaning non-digit characters
        assertEquals("8012345678", phoneNumberService.cleanPhoneNumber("0801-234-5678"))
        assertEquals("8012345678", phoneNumberService.cleanPhoneNumber("0801 234 5678"))
        assertEquals("8012345678", phoneNumberService.cleanPhoneNumber("(0801) 234-5678"))
        assertEquals("+2348012345678", phoneNumberService.cleanPhoneNumber("+234-0801-234-5678"))
    }

    @Test
    fun `should parse and format Nigerian phone numbers correctly`() {
        // Test Nigerian phone numbers with leading zeros removed
        val result1 = phoneNumberService.parseAndFormatPhoneNumber("08012345678", "NG")
        assertEquals("+2348012345678", result1)
        
        val result2 = phoneNumberService.parseAndFormatPhoneNumber("8012345678", "NG")
        assertEquals("+2348012345678", result2)
        
        // Test with already formatted number
        val result3 = phoneNumberService.parseAndFormatPhoneNumber("2348012345678", "NG")
        assertEquals("+2348012345678", result3)
    }

    @Test
    fun `should validate phone numbers correctly`() {
        // Valid Nigerian numbers
        assertTrue(phoneNumberService.isValidPhoneNumber("08012345678", "NG"))
        assertTrue(phoneNumberService.isValidPhoneNumber("8012345678", "NG"))
        assertTrue(phoneNumberService.isValidPhoneNumber("2348012345678", "NG"))
        
        // Invalid numbers
        assertFalse(phoneNumberService.isValidPhoneNumber("123", "NG"))
        assertFalse(phoneNumberService.isValidPhoneNumber("", "NG"))
        assertFalse(phoneNumberService.isValidPhoneNumber("abcd", "NG"))
    }
}
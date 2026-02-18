package com.haneef._school.service

import org.springframework.stereotype.Service

@Service
class PhoneNumberService {
    
    // Map of supported country codes. Can be expanded as needed.
    private val countryCallingCodes = mapOf(
        "NG" to "234",
        "US" to "1",
        "GB" to "44",
        "GH" to "233",
        "CA" to "1"
    )
    
    fun parseAndFormatPhoneNumber(phoneNumber: String, countryCode: String): String? {
        return try {
            val cleaned = cleanPhoneNumber(phoneNumber)
            if (cleaned.isBlank()) return null
            
            // If it's already in E.164 format (starts with +), validate and return
            if (cleaned.startsWith("+")) {
                // Basic validation: + followed by 7-15 digits
                if (cleaned.matches(Regex("^\\+\\d{7,15}$"))) {
                    return cleaned
                }
                return null
            }

            val callingCode = countryCallingCodes[countryCode.uppercase()] ?: return null
            
            // Check if it starts with the calling code (e.g. 234...)
            if (cleaned.startsWith(callingCode)) {
                val formatted = "+$cleaned"
                if (formatted.matches(Regex("^\\+\\d{7,15}$"))) {
                    return formatted
                }
            }
            
            // Handle local format (e.g. 080...)
            // Remove leading zeros
            val numberWithoutLeadingZeros = removeLeadingZeros(cleaned)
            
            // Construct full number
            val fullNumber = "+$callingCode$numberWithoutLeadingZeros"
            
            // Validate length (E.164 allows up to 15 digits excluding +)
            if (fullNumber.matches(Regex("^\\+\\d{7,15}$"))) {
                return fullNumber
            }
            
            null
        } catch (e: Exception) {
            null
        }
    }
    
    fun isValidPhoneNumber(phoneNumber: String, countryCode: String): Boolean {
        return parseAndFormatPhoneNumber(phoneNumber, countryCode) != null
    }
    
    fun formatForDisplay(phoneNumber: String): String {
        // For display, we can just return the E.164 format or try to prettify it
        // For now, returning the cleaned version or original is safer without the library
        return phoneNumber
    }
    
    /**
     * Removes leading zeros from phone numbers while preserving the number structure
     * Examples:
     * - "08012345678" -> "8012345678"
     * - "0012345678" -> "12345678"
     * - "8012345678" -> "8012345678" (no change)
     * - "0" -> "0" (preserves single zero)
     */
    private fun removeLeadingZeros(phoneNumber: String): String {
        val trimmed = phoneNumber.trim()
        
        // Handle empty or single character cases
        if (trimmed.isEmpty() || trimmed.length == 1) {
            return trimmed
        }
        
        // Remove leading zeros but keep at least one digit
        val withoutLeadingZeros = trimmed.dropWhile { it == '0' }
        
        // If all digits were zeros, return a single zero
        return if (withoutLeadingZeros.isEmpty()) "0" else withoutLeadingZeros
    }
    
    /**
     * Utility function to clean and normalize phone number input
     * Removes non-digit characters and leading zeros
     */
    fun cleanPhoneNumber(phoneNumber: String): String {
        // Remove all non-digit characters except + at the beginning
        val digitsOnly = if (phoneNumber.startsWith("+")) {
            "+" + phoneNumber.substring(1).filter { it.isDigit() }
        } else {
            phoneNumber.filter { it.isDigit() }
        }
        
        return digitsOnly
    }
}
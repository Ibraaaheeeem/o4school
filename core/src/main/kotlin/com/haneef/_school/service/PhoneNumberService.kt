package com.haneef._school.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class PhoneNumberService(
    @Value("\${phone-number.country-rules:}") private val configuredCountryRules: String = ""
) {

    private val countryRules: Map<String, CountryPhoneRule> by lazy {
        parseConfiguredCountryRules(configuredCountryRules).ifEmpty { DEFAULT_COUNTRY_RULES }
    }

    fun parseAndFormatPhoneNumber(phoneNumber: String, countryCode: String): String? {
        val normalizedCountryCode = normalizeCountryCode(countryCode) ?: return null
        val rule = countryRules[normalizedCountryCode] ?: return null
        val cleaned = cleanPhoneNumber(phoneNumber)

        if (cleaned.isBlank()) {
            logger.debug("Phone number is blank after cleaning")
            null
        }

        if (cleaned.startsWith("+")) {
            if (!E164_PATTERN.matches(cleaned)) {
                logger.debug("Rejected invalid E.164 phone number format")
                return null
            }

            val digits = cleaned.removePrefix("+")
            if (!digits.startsWith(rule.callingCode)) {
                logger.debug("Rejected E.164 phone number with mismatched country calling code")
                return null
            }

            val nationalDigits = removeLeadingZeros(digits.removePrefix(rule.callingCode))
            return buildAndValidate(rule, nationalDigits)
        }

        val digitsOnly = cleaned
        val nationalDigits = if (digitsOnly.startsWith(rule.callingCode)) {
            removeLeadingZeros(digitsOnly.removePrefix(rule.callingCode))
        } else {
            removeLeadingZeros(digitsOnly)
        }

        return buildAndValidate(rule, nationalDigits)
    }

    fun isValidPhoneNumber(phoneNumber: String, countryCode: String): Boolean {
        return parseAndFormatPhoneNumber(phoneNumber, countryCode) != null
    }

    fun formatForDisplay(phoneNumber: String): String {
        val cleaned = cleanPhoneNumber(phoneNumber)
        if (!E164_PATTERN.matches(cleaned)) {
            return phoneNumber
        }

        val digits = cleaned.removePrefix("+")
        val rule = countryRules.values
            .sortedByDescending { it.callingCode.length }
            .firstOrNull { digits.startsWith(it.callingCode) }
            ?: return cleaned

        val national = digits.removePrefix(rule.callingCode)
        val formattedNational = formatNationalForDisplay(national)

        return "+${rule.callingCode} $formattedNational"
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
     * Removes non-digit characters while preserving a leading '+'
     */
    fun cleanPhoneNumber(phoneNumber: String): String {
        val trimmed = phoneNumber.trim()

        // Remove all non-digit characters except + at the beginning
        val digitsOnly = if (trimmed.startsWith("+")) {
            "+" + trimmed.substring(1).filter { it.isDigit() }
        } else {
            trimmed.filter { it.isDigit() }
        }

        return digitsOnly
    }

    private fun normalizeCountryCode(countryCode: String): String? {
        val trimmed = countryCode.trim()
        if (!COUNTRY_CODE_PATTERN.matches(trimmed)) {
            logger.debug("Rejected invalid country code format")
            return null
        }

        return trimmed.uppercase()
    }

    private fun buildAndValidate(rule: CountryPhoneRule, nationalDigits: String): String? {
        if (!nationalDigits.all { it.isDigit() }) {
            logger.debug("Rejected non-digit national number part")
            return null
        }

        if (nationalDigits.length !in rule.minNationalDigits..rule.maxNationalDigits) {
            logger.debug("Rejected national number length outside configured bounds")
            return null
        }

        val fullNumber = "+${rule.callingCode}$nationalDigits"
        if (!E164_PATTERN.matches(fullNumber)) {
            logger.debug("Rejected non E.164 phone number")
            return null
        }

        return fullNumber
    }

    private fun formatNationalForDisplay(value: String): String {
        return when (value.length) {
            10 -> "${value.substring(0, 3)} ${value.substring(3, 6)} ${value.substring(6)}"
            9 -> "${value.substring(0, 3)} ${value.substring(3, 6)} ${value.substring(6)}"
            else -> chunkFromRight(value, 3)
        }
    }

    private fun chunkFromRight(value: String, chunkSize: Int): String {
        if (value.isBlank()) return value

        val groups = mutableListOf<String>()
        var index = value.length
        while (index > 0) {
            val start = (index - chunkSize).coerceAtLeast(0)
            groups.add(value.substring(start, index))
            index = start
        }

        return groups.reversed().joinToString(" ")
    }

    private fun parseConfiguredCountryRules(configuration: String): Map<String, CountryPhoneRule> {
        if (configuration.isBlank()) {
            return emptyMap()
        }

        return configuration.split(",")
            .mapNotNull { token ->
                val parts = token.trim().split(":")
                if (parts.size != 4) {
                    logger.warn("Skipping malformed phone country rule token: {}", token.trim())
                    return@mapNotNull null
                }

                val code = parts[0].trim().uppercase()
                val callingCode = parts[1].trim()
                val minDigits = parts[2].trim().toIntOrNull()
                val maxDigits = parts[3].trim().toIntOrNull()

                if (!COUNTRY_CODE_PATTERN.matches(code) || !callingCode.all { it.isDigit() } || minDigits == null || maxDigits == null || minDigits <= 0 || maxDigits < minDigits) {
                    logger.warn("Skipping invalid phone country rule token: {}", token.trim())
                    return@mapNotNull null
                }

                code to CountryPhoneRule(callingCode, minDigits, maxDigits)
            }
            .toMap()
    }

    private data class CountryPhoneRule(
        val callingCode: String,
        val minNationalDigits: Int,
        val maxNationalDigits: Int
    )

    companion object {
        private val logger = LoggerFactory.getLogger(PhoneNumberService::class.java)
        private val E164_PATTERN = Regex("^\\+\\d{7,15}$")
        private val COUNTRY_CODE_PATTERN = Regex("^[A-Za-z]{2}$")

        private val DEFAULT_COUNTRY_RULES = mapOf(
            "NG" to CountryPhoneRule("234", 10, 10),
            "US" to CountryPhoneRule("1", 10, 10),
            "GB" to CountryPhoneRule("44", 10, 10),
            "GH" to CountryPhoneRule("233", 9, 9),
            "CA" to CountryPhoneRule("1", 10, 10)
        )
    }
}
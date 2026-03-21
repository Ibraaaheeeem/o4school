package com.haneef._school.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.haneef._school.entity.*
import com.haneef._school.repository.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import java.text.NumberFormat

@Service
class TemplateParameterResolver(
    private val parentRepository: ParentRepository,
    private val staffRepository: StaffRepository,
    private val studentRepository: StudentRepository,
    private val financialService: FinancialService,
    private val schoolRepository: SchoolRepository,
    private val academicSessionRepository: AcademicSessionRepository,
    private val termRepository: TermRepository,
    private val invoiceRepository: InvoiceRepository,
    private val settlementRepository: SettlementRepository,
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
) {
    private val logger = LoggerFactory.getLogger(TemplateParameterResolver::class.java)

    internal fun formatCurrency(amount: BigDecimal): String {
        val fmt = NumberFormat.getNumberInstance(Locale("en", "NG")).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
        return fmt.format(amount)
    }

    internal fun toBigDecimal(obj: Any?): BigDecimal = when (obj) {
        null -> BigDecimal.ZERO
        is BigDecimal -> obj
        is Long -> BigDecimal.valueOf(obj)
        is Int -> BigDecimal.valueOf(obj.toLong())
        is Double -> BigDecimal.valueOf(obj)
        is Float -> BigDecimal.valueOf(obj.toDouble())
        is Number -> BigDecimal.valueOf(obj.toDouble())
        is String -> try {
            BigDecimal(obj)
        } catch (e: Exception) {
            BigDecimal.ZERO
        }
        else -> BigDecimal.ZERO
    }

    fun resolveParameters(user: User, schoolId: UUID, mapping: String?, extraParams: Map<String, String> = emptyMap()): List<Map<String, Any>> {
        if (mapping.isNullOrBlank()) return emptyList()

        val data = getRecipientData(user, schoolId, extraParams)
        val parameters = mutableListOf<Map<String, Any>>()
        
        // Mapping format: "1=student_name,2=amount" or just "student_name,amount" (for named templates)
            val mappings = mapping.split(",")

            mappings.forEach { m ->
                val placeholderId: String? = if (m.contains("=")) m.substringBefore("=").trim() else null
                val mappedKey = if (m.contains("=")) m.substringAfter("=").trim() else m.trim()

                // Manual overrides can be supplied as either the numeric placeholder id or the mapped/system key.
                val manualValue = if (placeholderId != null) extraParams[placeholderId] ?: extraParams[mappedKey] else extraParams[mappedKey]

                val value = manualValue ?: data[mappedKey] ?: ""
                parameters.add(mapOf("type" to "text", "text" to value.toString()))
        }

        return parameters
    }

    /**
     * Resolves ALL parameters for a template by extracting placeholders from its components JSON.
     * This ensures we send exactly the number of parameters Meta expects.
     */
    fun resolveAllParameters(user: User, schoolId: UUID, template: WhatsAppTemplate, extraParams: Map<String, String> = emptyMap()): List<Map<String, Any>> {
        val placeholders = extractPlaceholders(template.componentsJson)
        logger.debug("Found placeholders in template {}: {}", template.templateName, placeholders)
        if (placeholders.isEmpty()) return emptyList()

        val data = getRecipientData(user, schoolId, extraParams)
        val mapping = parseMapping(template.parameterMapping)
        logger.debug("Mapping for {}: {}", template.templateName, mapping)
        
        return placeholders.map { placeholder ->
            // 1. Check if we have a manual override for this specific placeholder ID (e.g. "1" or "student_name")
                var value: String? = null

                // 1. Direct manual override (by placeholder name)
                value = extraParams[placeholder]

                // 2. Manual override via reverse-mapped id (e.g. mapping contains "1=student_name" and extraParams has "1")
                if (value == null) {
                    val mappedId = mapping.entries.firstOrNull { it.value == placeholder }?.key
                    if (mappedId != null) {
                        value = extraParams[mappedId]
                        if (value != null) logger.debug("Resolved placeholder '{}' via manual mapped-id '{}'", placeholder, mappedId)
                    }
                }

                // 3. Forward mapping (placeholder -> system key)
                if (value == null) {
                    val mappedKey = mapping[placeholder]
                    if (mappedKey != null) {
                        value = extraParams[mappedKey] ?: data[mappedKey]?.toString()
                        if (value != null) logger.debug("Resolved placeholder '{}' via mapping to '{}'", placeholder, mappedKey)
                    }
                }

                // 4. Identity mapping from system data
                if (value == null) {
                    value = data[placeholder]?.toString()
                    if (value != null) logger.debug("Resolved placeholder '{}' via identity mapping", placeholder)
                }

                if (value == null) logger.warn("FAILED to resolve placeholder '{}' for user {}", placeholder, user.id ?: "unknown")

                val param = mutableMapOf<String, Any>(
                    "type" to "text",
                    "text" to (value ?: "")
                )

                if (placeholder.any { !it.isDigit() }) {
                    param["parameter_name"] = placeholder
                }

                param
        }
    }

    private fun extractPlaceholders(componentsJson: String?): List<String> {
        if (componentsJson.isNullOrBlank()) return emptyList()
        return try {
            val root: JsonNode = objectMapper.readTree(componentsJson)
            val placeholders = mutableListOf<String>()
            val regex = Regex("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*}}")

            fun scan(node: JsonNode) {
                when {
                    node.isObject -> {
                        val it = node.fields()
                        while (it.hasNext()) {
                            val entry = it.next()
                            scan(entry.value)
                        }
                    }
                    node.isArray -> node.forEach { scan(it) }
                    node.isTextual -> {
                        val text = node.asText()
                        regex.findAll(text).forEach { match ->
                            val placeholder = match.groupValues[1].trim()
                            if (!placeholders.contains(placeholder)) placeholders.add(placeholder)
                        }
                    }
                    else -> {
                        // ignore other node types
                    }
                }
            }

            scan(root)
            placeholders
        } catch (e: Exception) {
            logger.error("Error parsing componentsJson", e)
            emptyList()
        }
    }

    private fun parseMapping(mappingStr: String?): Map<String, String> {
        if (mappingStr.isNullOrBlank()) return emptyMap()
        return mappingStr.split(",").associate { m ->
            if (m.contains("=")) {
                m.substringBefore("=").trim() to m.substringAfter("=").trim()
            } else {
                m.trim() to m.trim()
            }
        }
    }

    private fun getRecipientData(user: User, schoolId: UUID, extraParams: Map<String, String> = emptyMap()): Map<String, Any?> {
        val data = mutableMapOf<String, Any?>()
        
        // Add manual/extra params first — manual entries must NOT be overwritten by system values
        data.putAll(extraParams)

        fun putIfAbsent(key: String, value: Any?) {
            if (!data.containsKey(key)) data[key] = value
        }

        // Basic User Info (only set if manual value not supplied)
        putIfAbsent("name", user.fullName)
        putIfAbsent("first_name", user.firstName)
        putIfAbsent("last_name", user.lastName)
        putIfAbsent("phone", user.phoneNumber)
        putIfAbsent("date", LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))

        val school = schoolRepository.findById(schoolId).orElse(null)
        putIfAbsent("school_name", school?.name ?: "")
        putIfAbsent("school_contact", school?.phone ?: "")

        // Role-based data
        val uid = user.id ?: run {
            logger.warn("User id is null while resolving recipient data for school {}", schoolId)
            return data
        }

        val parent = parentRepository.findByUserIdAndSchoolId(uid, schoolId)
        if (parent != null && parent.isActive) {
            putIfAbsent("parent_name", user.fullName)

            val activeChildren = parent.activeStudentRelationships?.map { it.student } ?: emptyList()
            
            // {{students}} = [Aisha Abdullahi - Primary 4, Nana Muhammad - JSS 2]
            val studentsList = activeChildren.map { student ->
                val className = student.classEnrollments.find { it.isActive }?.schoolClass?.className ?: "Not Assigned"
                "${student.user.fullName} - $className"
            }
            putIfAbsent("students", if (studentsList.isNotEmpty()) studentsList.joinToString(", ", "[", "]") else "None")

            putIfAbsent("student_name", if (activeChildren.isNotEmpty()) {
                activeChildren.joinToString(", ") { it.user.fullName ?: "Student" }
            } else {
                "your children"
            })
            
            val classes = activeChildren.mapNotNull { student ->
                student.classEnrollments.find { it.isActive }?.schoolClass?.className
            }.distinct()
            
            putIfAbsent("class_name", if (classes.isNotEmpty()) classes.joinToString(", ") else "")
            putIfAbsent("student_names", data["student_name"])
            
            // Financial Data
            val currentSession = academicSessionRepository.findBySchoolIdAndIsCurrentSessionAndIsActive(schoolId, true, true)
            val currentTerm = termRepository.findBySchoolIdAndIsCurrentTermAndIsActive(schoolId, true, true).orElse(null)
            
            data["current_session"] = currentSession?.sessionName ?: ""
            data["current_term"] = currentTerm?.termName ?: ""
            data["academic_year"] = currentSession?.sessionYear ?: ""
            data["term_number"] = currentTerm?.termNumber?.toString() ?: ""

            val breakdown = financialService.getFeeBreakdown(parent, currentSession?.id, currentTerm?.id)
            val currentBill = toBigDecimal(breakdown["totalFees"])
            val settledBill = toBigDecimal(breakdown["totalSettled"])
            val termBalance = currentBill.subtract(settledBill)
            
            // Truly all-time balance: Total Owed (All Time) - Total Paid (All Time)
            val financialStatus = financialService.calculateParentFinancialStatus(parent)
            val totalOwedAllTime = financialStatus.totalOwed
            val totalSettledAllTime = financialStatus.totalPaid
            val netBalance = financialStatus.balance
            
            // Refined Mappings as requested:
            // current_bill: Bill for just the current term
            // total_bill: current_bill + any outstanding bill (basically Total Owed - Total Paid in the past)
            // settled_bill: Amount settled in the current term
            // current_balance: Remaining balance to pay (net all-time balance)
            
            val totalBill = netBalance.add(settledBill)
            val outstanding = totalBill.subtract(currentBill).max(BigDecimal.ZERO)

            putIfAbsent("term_fees", formatCurrency(currentBill))
            putIfAbsent("settled_bill", formatCurrency(settledBill))
            putIfAbsent("outstanding", formatCurrency(outstanding))
            putIfAbsent("amount", formatCurrency(netBalance))
            putIfAbsent("balance", formatCurrency(netBalance))
            putIfAbsent("current_bill", formatCurrency(currentBill))
            putIfAbsent("total_bill", formatCurrency(totalBill.max(BigDecimal.ZERO)))
            putIfAbsent("current_balance", formatCurrency(netBalance.max(BigDecimal.ZERO)))
            putIfAbsent("net_balance", formatCurrency(netBalance)) // Can be negative if they have credit
            
            // Dedicated Account Details
            val paystackWallet = parent.paystackWallet
            val squadWallet = parent.squadWallet
            
            val accountDetails = when {
                paystackWallet?.accountNumber != null -> {
                    "Account: ${paystackWallet.accountNumber} (${paystackWallet.bankName ?: "Paystack Bank"}) - ${paystackWallet.accountName}"
                }
                squadWallet?.accountNumber != null -> {
                    "Account: ${squadWallet.accountNumber} (${squadWallet.bankName ?: "GTBank/Squad"}) - ${squadWallet.accountName}"
                }
                else -> "Not Assigned"
            }
            putIfAbsent("dedicated_account", accountDetails)
            putIfAbsent("account_number", paystackWallet?.accountNumber ?: squadWallet?.accountNumber ?: "Not Assigned")
        }
        val staff = staffRepository.findByUserIdAndSchoolId(uid, schoolId)
        if (staff != null && staff.isActive) {
            putIfAbsent("staff_name", user.fullName)
            putIfAbsent("cadre", staff.designation ?: "")
            putIfAbsent("department", staff.department ?: "")
        }

        // Student-specific (if the user is the student themselves)
        val student = studentRepository.findByUserIdAndSchoolId(uid, schoolId)
        if (student != null) {
            putIfAbsent("student_name", user.fullName)
            val className = student.classEnrollments.find { it.isActive }?.schoolClass?.className
            putIfAbsent("class_name", className ?: "")
        }

        return data
    }
}

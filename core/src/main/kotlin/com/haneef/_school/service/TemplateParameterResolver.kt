package com.haneef._school.service

import com.fasterxml.jackson.databind.ObjectMapper
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
    private val settlementRepository: SettlementRepository
) {
    private val logger = LoggerFactory.getLogger(TemplateParameterResolver::class.java)
    private val objectMapper = jacksonObjectMapper()

    private val currencyFormatter = java.text.NumberFormat.getNumberInstance(Locale("en", "NG")).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }

    fun resolveParameters(user: User, schoolId: UUID, mapping: String?, extraParams: Map<String, String> = emptyMap()): List<Map<String, Any>> {
        if (mapping.isNullOrBlank()) return emptyList()

        val data = getRecipientData(user, schoolId, extraParams)
        val parameters = mutableListOf<Map<String, Any>>()
        
        // Mapping format: "1=student_name,2=amount" or just "student_name,amount" (for named templates)
        val mappings = mapping.split(",")
        
        mappings.forEach { m ->
            val placeholderId = if (m.contains("=")) m.substringBefore("=").trim() else ""
            val mappedKey = if (m.contains("=")) m.substringAfter("=").trim() else m.trim()
            
            // Check if there is a manual override for either the placeholder number or the system key
            val manualValue = extraParams[placeholderId] ?: extraParams[mappedKey]
            
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
            var value = extraParams[placeholder]
            val fromManual = if (value != null) "manual override ($placeholder)" else null
            
            // 2. If no direct override, check the mapping
            if (value == null) {
                val mappedKey = mapping[placeholder]
                if (mappedKey != null) {
                    value = extraParams[mappedKey] ?: data[mappedKey]?.toString()
                    if (value != null) logger.debug("Resolved placeholder '{}' via mapping to '{}'", placeholder, mappedKey)
                }
            }
            
            // 3. If still no value, try to resolve placeholder name directly from system data (identity mapping)
            if (value == null) {
                value = data[placeholder]?.toString()
                if (value != null) logger.debug("Resolved placeholder '{}' via identity mapping", placeholder)
            }
            
            if (value == null) logger.warn("FAILED to resolve placeholder '{}' for user {}", placeholder, user.id)
            
            val param = mutableMapOf<String, Any>(
                "type" to "text",
                "text" to (value ?: "")
            )
            
            // For named templates, Meta requires the 'parameter_name' field
            if (placeholder.any { !it.isDigit() }) {
                param["parameter_name"] = placeholder
            }
            
            param
        }
    }

    private fun extractPlaceholders(componentsJson: String?): List<String> {
        if (componentsJson.isNullOrBlank()) return emptyList()
        return try {
            val components = objectMapper.readValue<List<Map<String, Any>>>(componentsJson)
            val placeholders = mutableListOf<String>()
            val regex = Regex("\\{\\{([a-zA-Z0-9_]+)}}")
            
            components.forEach { comp ->
                // Check multiple possible keys for text content (Meta uses 'text', 'caption', 'body', etc. in various contexts)
                val textKeys = listOf("text", "TEXT", "caption", "CAPTION")
                val text = textKeys.mapNotNull { comp[it] as? String }.joinToString(" ")
                
                regex.findAll(text).forEach { match ->
                    val placeholder = match.groupValues[1].trim()
                    if (!placeholders.contains(placeholder)) {
                        placeholders.add(placeholder)
                    }
                }
            }
            placeholders
        } catch (e: Exception) {
            logger.error("Error parsing componentsJson for {}: {}", componentsJson, e.message)
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
        
        // Add manual/extra params first so they can be overridden by system data if keys match
        data.putAll(extraParams)
        
        // Basic User Info
        data["name"] = user.fullName
        data["first_name"] = user.firstName
        data["last_name"] = user.lastName
        data["phone"] = user.phoneNumber
        data["date"] = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        
        val school = schoolRepository.findById(schoolId).orElse(null)
        data["school_name"] = school?.name ?: ""
        data["school_contact"] = school?.phone ?: ""

        // Role-based data
        val parent = parentRepository.findByUserIdAndSchoolId(user.id!!, schoolId)
        if (parent != null && parent.isActive) {
            data["parent_name"] = user.fullName
            
            val activeChildren = parent.activeStudentRelationships.map { it.student }
            
            // {{students}} = [Aisha Abdullahi - Primary 4, Nana Muhammad - JSS 2]
            val studentsList = activeChildren.map { student ->
                val className = student.classEnrollments.find { it.isActive }?.schoolClass?.className ?: "Not Assigned"
                "${student.user.fullName} - $className"
            }
            data["students"] = if (studentsList.isNotEmpty()) studentsList.joinToString(", ", "[", "]") else "None"
            
            data["student_name"] = if (activeChildren.isNotEmpty()) {
                activeChildren.joinToString(", ") { it.user.fullName ?: "Student" }
            } else {
                "your children"
            }
            
            val classes = activeChildren.mapNotNull { student ->
                student.classEnrollments.find { it.isActive }?.schoolClass?.className
            }.distinct()
            
            data["class_name"] = if (classes.isNotEmpty()) classes.joinToString(", ") else ""
            
            // Financial Data
            val currentSession = academicSessionRepository.findBySchoolIdAndIsCurrentSessionAndIsActive(schoolId, true, true)
            val currentTerm = termRepository.findBySchoolIdAndIsCurrentTermAndIsActive(schoolId, true, true).orElse(null)
            
            data["current_session"] = currentSession?.sessionName ?: ""
            data["current_term"] = currentTerm?.termName ?: ""
            data["academic_year"] = currentSession?.sessionYear ?: ""
            data["term_number"] = currentTerm?.termNumber?.toString() ?: ""

            val breakdown = financialService.getFeeBreakdown(parent, currentSession?.id, currentTerm?.id)
            val currentBill = breakdown["totalFees"] as? BigDecimal ?: BigDecimal.ZERO
            val settledBill = breakdown["totalSettled"] as? BigDecimal ?: BigDecimal.ZERO
            val termBalance = currentBill.subtract(settledBill)
            
            // Truly all-time balance: Total Owed (All Time) - Total Paid (All Time)
            // Calculate the higher of invoices vs fee structure PER STUDENT and sum them up
            var totalOwedAllTime = BigDecimal.ZERO
            
            activeChildren.forEach { student ->
                // Invoices for this student
                val studentInvoices = invoiceRepository.findByStudentIdAndSchoolIdAndIsActive(student.id!!, schoolId, true)
                    .filter { it.status != InvoiceStatus.DRAFT && it.status != InvoiceStatus.CANCELLED }
                    
                val invoiceTotal = studentInvoices.fold(BigDecimal.ZERO) { acc, inv ->
                    acc.add(BigDecimal.valueOf(inv.totalAmount.toLong(), 2))
                }
                
                // Structure for this student
                val structureTotal = financialService.calculateAllTimeFees(student)
                
                // Take the higher of the two ensuring we don't miss any debt for this specific child
                totalOwedAllTime = totalOwedAllTime.add(invoiceTotal.max(structureTotal))
            }

            // Settlements (Total paid by parent - successful only)
            val studentIds = activeChildren.mapNotNull { it.id }
            val settlements = settlementRepository.findByParentContext(
                schoolId,
                parent.id!!,
                studentIds,
                parent.user.email,
                null, 
                null
            )
            val totalSettledAllTime = settlements.sumOf { it.amount }
            
            val netBalance = totalOwedAllTime.subtract(totalSettledAllTime)
            
            // netBalance is the TOTAL outstanding (Debt minus any payments)
            // outstandingAmount in the template usually refers to PREVIOUS terms' balance
            val outstandingAmountFromPast = netBalance.subtract(termBalance)
            
            // For the 'outstanding' parameter, we show the past debt (0 if they are in credit or fully paid)
            val outstanding = if (outstandingAmountFromPast.compareTo(BigDecimal.ZERO) > 0) outstandingAmountFromPast else BigDecimal.ZERO
            
            // Important: totalBill is the amount they actually owe RIGHT NOW (inclusive of current term and past balance/credit)
            val totalBill = netBalance.max(BigDecimal.ZERO)
            
            data["term_fees"] = currencyFormatter.format(currentBill)
            data["settled_bill"] = currencyFormatter.format(settledBill)
            data["outstanding"] = currencyFormatter.format(outstanding)
            data["amount"] = currencyFormatter.format(totalBill)
            data["balance"] = currencyFormatter.format(totalBill)
            data["current_bill"] = currencyFormatter.format(totalBill)
            data["current_balance"] = currencyFormatter.format(totalBill)
            data["total_bill"] = currencyFormatter.format(totalBill)
            data["net_balance"] = currencyFormatter.format(netBalance) // Can be negative if they have credit
            
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
            data["dedicated_account"] = accountDetails
        }

        val staff = staffRepository.findByUserIdAndSchoolId(user.id!!, schoolId)
        if (staff != null && staff.isActive) {
            data["staff_name"] = user.fullName
            data["cadre"] = staff.designation ?: ""
            data["department"] = staff.department ?: ""
        }

        // Student-specific (if the user is the student themselves)
        val student = studentRepository.findByUserIdAndSchoolId(user.id!!, schoolId)
        if (student != null) {
            data["student_name"] = user.fullName
            val className = student.classEnrollments.find { it.isActive }?.schoolClass?.className
            data["class_name"] = className ?: ""
        }

        return data
    }
}

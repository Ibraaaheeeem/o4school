package com.haneef._school.controller

import com.haneef._school.dto.InitiatePayoutDto
import com.haneef._school.dto.RecordReimbursementDto
import com.haneef._school.entity.SchoolReimbursement
import com.haneef._school.entity.SettlementType
import com.haneef._school.repository.*
import com.haneef._school.service.CustomUserDetails
import com.haneef._school.service.PaystackRecipientService
import com.haneef._school.service.PaystackService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

@Controller
@RequestMapping("/system-admin/financial")
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
class SystemAdminFinancialController(
    private val schoolRepository: SchoolRepository,
    private val settlementRepository: SettlementRepository,
    private val schoolReimbursementRepository: SchoolReimbursementRepository,
    private val userRepository: UserRepository,
    private val academicSessionRepository: AcademicSessionRepository,
    private val termRepository: TermRepository,
    private val schoolBankAccountRepository: SchoolBankAccountRepository,
    private val paystackRecipientService: PaystackRecipientService,
    private val paystackService: PaystackService
) {

    @GetMapping("/reimbursements")
    fun listSchools(model: Model, authentication: Authentication): String {
        val customUser = authentication.principal as CustomUserDetails
        val schools = schoolRepository.findAll()
        
        val schoolData = schools.map { school ->
            // Find current session and term for this school
            val currentSession = academicSessionRepository.findBySchoolIdAndIsCurrentSessionAndIsActive(school.id!!, true, true)
            val currentTerm = currentSession?.let { session ->
                termRepository.findByAcademicSessionIdAndIsCurrentTermAndIsActive(session.id!!, true, true).orElse(null)
            }

            // Get settlements for current term - ONLY AUTO for calculations
            val termSettlements = settlementRepository.findBySchoolIdAndAcademicSessionIdAndTermId(
                school.id!!, 
                currentSession?.id, 
                currentTerm?.id
            ).filter { it.status.equals("success", ignoreCase = true) }
            
            val termAutoSettlements = termSettlements.filter { it.settlementType == SettlementType.AUTO }
            
            // Get reimbursements for current term
            val termReimbursements = schoolReimbursementRepository.findBySchoolIdAndAcademicSessionIdAndTermIdOrderByReimbursementDateDesc(
                school.id!!,
                currentSession?.id,
                currentTerm?.id
            ).filter { it.status == "COMPLETED" }
            
            // Get lifetime totals - ONLY AUTO for calculations
            val allSettlements = settlementRepository.findBySchoolId(school.id!!)
                .filter { it.status.equals("success", ignoreCase = true) }
            
            val allAutoSettlements = allSettlements.filter { it.settlementType == SettlementType.AUTO }
            
            val allReimbursements = schoolReimbursementRepository.findBySchoolIdOrderByReimbursementDateDesc(school.id!!)
                .filter { it.status == "COMPLETED" }

            val termSettled = termAutoSettlements.sumOf { it.amount }
            val termReimbursed = termReimbursements.sumOf { it.amount }
            val termPending = termSettled - termReimbursed

            val lifetimeSettled = allAutoSettlements.sumOf { it.amount }
            val lifetimeReimbursed = allReimbursements.sumOf { it.amount }
            val lifetimePending = lifetimeSettled - lifetimeReimbursed
            
            mapOf(
                "id" to school.id,
                "name" to school.name,
                "currentSession" to currentSession?.sessionYear,
                "currentTerm" to currentTerm?.termName,
                "termSettled" to termSettled,
                "termReimbursed" to termReimbursed,
                "termPending" to termPending,
                "lifetimeSettled" to lifetimeSettled,
                "lifetimeReimbursed" to lifetimeReimbursed,
                "lifetimePending" to lifetimePending,
                "lastReimbursement" to allReimbursements.firstOrNull()?.reimbursementDate
            )
        }
        
        val platformTotalSettled = schoolData.sumOf { it["lifetimeSettled"] as BigDecimal }
        val platformTotalReimbursed = schoolData.sumOf { it["lifetimeReimbursed"] as BigDecimal }
        val platformPendingAmount = platformTotalSettled - platformTotalReimbursed
        
        // Fetch Paystack Account Balance
        val paystackBalances = paystackService.getAccountBalance()
        val ngnBalance = paystackBalances.find { it["currency"] == "NGN" }?.get("balance") as? Number
        val formattedBalance = ngnBalance?.let { BigDecimal(it.toLong()).divide(BigDecimal(100)) } ?: BigDecimal.ZERO
        
        model.addAttribute("paystackBalance", formattedBalance)
        model.addAttribute("user", customUser.user)
        model.addAttribute("schools", schoolData)
        model.addAttribute("platformTotalSettled", platformTotalSettled)
        model.addAttribute("platformTotalReimbursed", platformTotalReimbursed)
        model.addAttribute("platformPendingAmount", platformPendingAmount)
        model.addAttribute("userRole", "System Administrator")
        model.addAttribute("dashboardType", "system-admin")
        
        return "system-admin/financial/reimbursements-list"
    }

    @GetMapping("/reimbursements/school/{schoolId}")
    fun schoolReimbursements(
        @PathVariable schoolId: UUID,
        @RequestParam(required = false) academicSessionId: UUID?,
        @RequestParam(required = false) termId: UUID?,
        model: Model,
        authentication: Authentication
    ): String {
        val customUser = authentication.principal as CustomUserDetails
        val school = schoolRepository.findById(schoolId).orElseThrow { RuntimeException("School not found") }
        
        // Get all sessions for this school
        val academicSessions = academicSessionRepository.findBySchoolIdAndIsActiveOrderByYearDesc(schoolId, true)
        
        // Determine selected session (default to current or first)
        val selectedSession = academicSessionId?.let { sessionId ->
            academicSessions.find { it.id == sessionId }
        } ?: academicSessions.find { it.isCurrentSession } ?: academicSessions.firstOrNull()
        
        // Get terms for selected session
        val terms = if (selectedSession != null) {
            termRepository.findByAcademicSessionIdAndIsActiveOrderByStartDate(selectedSession.id!!, true)
        } else {
            emptyList()
        }
        
        // Determine selected term
        val selectedTerm = termId?.let { tId ->
            terms.find { it.id == tId }
        }
        
        // Use improved repository methods for filtering
        val filteredSettlements = settlementRepository.findBySchoolIdAndAcademicSessionIdAndTermId(
            schoolId, 
            selectedSession?.id, 
            selectedTerm?.id
        ).filter { it.status.equals("success", ignoreCase = true) }
        
        val autoSettlements = filteredSettlements.filter { it.settlementType == SettlementType.AUTO }
        val manualSettlements = filteredSettlements.filter { it.settlementType == SettlementType.MANUAL }
        
        val reimbursements = schoolReimbursementRepository.findBySchoolIdAndAcademicSessionIdAndTermIdOrderByReimbursementDateDesc(
            schoolId,
            selectedSession?.id,
            selectedTerm?.id
        )
        
        val totalSettled = autoSettlements.sumOf { it.amount }
        val totalReimbursed = reimbursements.filter { it.status == "COMPLETED" }.sumOf { it.amount }
        val totalManualSettled = manualSettlements.sumOf { it.amount }
        
        // Lifetime totals for this school
        val allSettlements = settlementRepository.findBySchoolId(schoolId).filter { it.status.equals("success", ignoreCase = true) }
        val allAutoSettlements = allSettlements.filter { it.settlementType == SettlementType.AUTO }
        val allManualSettlements = allSettlements.filter { it.settlementType == SettlementType.MANUAL }
        
        val allReimbursements = schoolReimbursementRepository.findBySchoolIdOrderByReimbursementDateDesc(schoolId).filter { it.status == "COMPLETED" }
        
        val lifetimeSettled = allAutoSettlements.sumOf { it.amount }
        val lifetimeReimbursed = allReimbursements.sumOf { it.amount }
        val lifetimeManualSettled = allManualSettlements.sumOf { it.amount }
        
        // Unassigned settlements (missing session or term) - ONLY AUTO for calculations
        val unassignedSettlements = allAutoSettlements.filter { it.academicSession == null || it.term == null }
        val unassignedSettled = unassignedSettlements.sumOf { it.amount }

        // Fetch Paystack Account Balance
        val paystackBalances = paystackService.getAccountBalance()
        val ngnBalance = paystackBalances.find { it["currency"] == "NGN" }?.get("balance") as? Number
        val formattedBalance = ngnBalance?.let { BigDecimal(it.toLong()).divide(BigDecimal(100)) } ?: BigDecimal.ZERO
        
        model.addAttribute("paystackBalance", formattedBalance)
        model.addAttribute("user", customUser.user)
        model.addAttribute("school", school)
        model.addAttribute("reimbursements", reimbursements)
        model.addAttribute("totalSettled", totalSettled)
        model.addAttribute("totalReimbursed", totalReimbursed)
        model.addAttribute("pendingAmount", totalSettled - totalReimbursed)
        model.addAttribute("lifetimeSettled", lifetimeSettled)
        model.addAttribute("lifetimeReimbursed", lifetimeReimbursed)
        model.addAttribute("lifetimePending", lifetimeSettled - lifetimeReimbursed)
        model.addAttribute("totalManualSettled", totalManualSettled)
        model.addAttribute("lifetimeManualSettled", lifetimeManualSettled)
        model.addAttribute("manualSettlements", manualSettlements)
        model.addAttribute("unassignedSettled", unassignedSettled)
        model.addAttribute("unassignedSettlements", unassignedSettlements)
        model.addAttribute("userRole", "System Administrator")
        model.addAttribute("dashboardType", "system-admin")
        model.addAttribute("academicSessions", academicSessions)
        model.addAttribute("terms", terms)
        model.addAttribute("selectedSessionId", selectedSession?.id)
        model.addAttribute("selectedTermId", selectedTerm?.id)
        model.addAttribute("selectedSessionName", selectedSession?.sessionYear)
        model.addAttribute("selectedTermName", selectedTerm?.termName)
        
        // Add bank account details
        val bankAccount = schoolBankAccountRepository.findBySchoolId(schoolId)
        model.addAttribute("bankAccount", bankAccount)
        
        return "system-admin/financial/school-reimbursements"
    }

    @PostMapping("/reimbursements/record")
    fun recordReimbursement(
        @ModelAttribute dto: RecordReimbursementDto,
        authentication: Authentication,
        redirectAttributes: RedirectAttributes
    ): String {
        val customUser = authentication.principal as CustomUserDetails
        val school = schoolRepository.findById(dto.schoolId).orElseThrow { RuntimeException("School not found") }
        
        if (schoolReimbursementRepository.findByReference(dto.reference) != null) {
            redirectAttributes.addFlashAttribute("error", "Reference already exists")
            return "redirect:/system-admin/financial/reimbursements/school/${dto.schoolId}"
        }
        
        // Get academic session and term if provided
        val academicSession = dto.academicSessionId?.let { 
            academicSessionRepository.findById(it).orElse(null) 
        }
        val term = dto.termId?.let { 
            termRepository.findById(it).orElse(null) 
        }
        
        val reimbursement = SchoolReimbursement(
            school = school,
            amount = dto.amount,
            reference = dto.reference,
            status = "COMPLETED",
            reimbursementDate = dto.date?.atStartOfDay() ?: LocalDateTime.now(),
            academicSession = academicSession,
            term = term,
            notes = dto.notes,
            recordedBy = customUser.user
        )
        
        schoolReimbursementRepository.save(reimbursement)
        
        // Build redirect URL with current filters
        val redirectUrl = StringBuilder("/system-admin/financial/reimbursements/school/${dto.schoolId}")
        val params = mutableListOf<String>()
        
        if (dto.academicSessionId != null) {
            params.add("academicSessionId=${dto.academicSessionId}")
        }
        if (dto.termId != null) {
            params.add("termId=${dto.termId}")
        }
        
        if (params.isNotEmpty()) {
            redirectUrl.append("?").append(params.joinToString("&"))
        }
        
        redirectAttributes.addFlashAttribute("success", "Reimbursement recorded successfully")
        
        return "redirect:$redirectUrl"
    }

    @PostMapping("/reimbursements/generate-recipient")
    fun generateRecipientCode(
        @RequestParam bankAccountId: UUID,
        @RequestParam schoolId: UUID,
        redirectAttributes: RedirectAttributes
    ): String {
        // Validate that the bank account belongs to the school
        val bankAccount = schoolBankAccountRepository.findById(bankAccountId).orElse(null)
        if (bankAccount == null || bankAccount.school.id != schoolId) {
            redirectAttributes.addFlashAttribute("error", "Invalid bank account for this school")
            return "redirect:/system-admin/financial/reimbursements/school/$schoolId"
        }

        val result = paystackRecipientService.createTransferRecipient(bankAccountId)
        
        if (result.isSuccess) {
            redirectAttributes.addFlashAttribute("success", "Paystack recipient code generated successfully: ${result.getOrNull()}")
        } else {
            redirectAttributes.addFlashAttribute("error", "Failed to generate recipient code: ${result.exceptionOrNull()?.message}")
        }
        
        return "redirect:/system-admin/financial/reimbursements/school/$schoolId"
    }
    @PostMapping("/reimbursements/payout")
    fun initiatePayout(
        @ModelAttribute dto: InitiatePayoutDto,
        authentication: Authentication,
        redirectAttributes: RedirectAttributes
    ): String {
        val customUser = authentication.principal as CustomUserDetails
        val school = schoolRepository.findById(dto.schoolId).orElseThrow { RuntimeException("School not found") }
        val bankAccount = schoolBankAccountRepository.findBySchoolId(dto.schoolId)
            ?: throw RuntimeException("Bank account not found for school")

        val recipientCode = bankAccount.recipientCode
        if (recipientCode.isNullOrBlank()) {
            redirectAttributes.addFlashAttribute("error", "Recipient code not found. Please generate it first.")
            return "redirect:/system-admin/financial/reimbursements/school/${dto.schoolId}"
        }

        // Generate reference: SCHOOL_FEES_DATE_TIME_AMOUNT
        val now = LocalDateTime.now()
        val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
        val reference = "SCHOOL_FEES_${now.format(formatter)}_${dto.amount.toInt()}"

        val result = paystackService.initiateTransfer(
            amount = dto.amount,
            recipientCode = recipientCode,
            reason = "SCHOOL FEES PAYOUT",
            reference = reference
        )

        if (result != null && result["status"] == true) {
            // Record the payout in our database
            val academicSession = dto.academicSessionId?.let { academicSessionRepository.findById(it).orElse(null) }
            val term = dto.termId?.let { termRepository.findById(it).orElse(null) }

            val reimbursement = SchoolReimbursement(
                school = school,
                amount = dto.amount,
                reference = reference,
                status = "COMPLETED",
                reimbursementDate = LocalDateTime.now(),
                academicSession = academicSession,
                term = term,
                notes = "Automated Paystack Payout: SCHOOL FEES PAYOUT",
                recordedBy = customUser.user
            )
            schoolReimbursementRepository.save(reimbursement)

            redirectAttributes.addFlashAttribute("success", "Payout initiated successfully via Paystack. Reference: $reference")
        } else {
            val message = result?.get("message") as? String ?: "Unknown error from Paystack"
            redirectAttributes.addFlashAttribute("error", "Failed to initiate payout: $message")
        }

        return "redirect:/system-admin/financial/reimbursements/school/${dto.schoolId}"
    }
}

package com.haneef._school.service

import com.haneef._school.entity.Parent
import com.haneef._school.entity.Student
import com.haneef._school.entity.StudentClass
import com.haneef._school.entity.SchoolClass
import com.haneef._school.entity.AcademicSession
import com.haneef._school.entity.Term
import com.haneef._school.repository.ClassFeeItemRepository
import com.haneef._school.repository.SettlementRepository
import com.haneef._school.repository.StudentClassRepository
import com.haneef._school.dto.PaymentAnalyticsDto
import com.haneef._school.dto.TrendPoint
import com.haneef._school.entity.SettlementType
import java.time.LocalDateTime
import java.time.LocalDate

import org.springframework.beans.factory.ObjectProvider

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

@Service
open class FinancialService(
    private val classFeeItemRepository: ClassFeeItemRepository,
    private val settlementRepository: SettlementRepository,
    private val academicSessionRepository: com.haneef._school.repository.AcademicSessionRepository,
    private val termRepository: com.haneef._school.repository.TermRepository,
    private val parentRepository: com.haneef._school.repository.ParentRepository,
    private val paymentDistributionService: PaymentDistributionService,
    private val userSchoolRoleRepository: com.haneef._school.repository.UserSchoolRoleRepository,
    private val studentOptionalFeeRepository: com.haneef._school.repository.StudentOptionalFeeRepository,
    private val studentRepository: com.haneef._school.repository.StudentRepository,
    private val studentClassRepository: StudentClassRepository,
    private val schoolReimbursementRepository: com.haneef._school.repository.SchoolReimbursementRepository,
    private val whatsappServiceProvider: ObjectProvider<WhatsAppService>
) {

    @Transactional
    open fun logManualSettlement(
        parentId: UUID,
        amount: BigDecimal,
        sessionId: UUID,
        termId: UUID?,
        schoolId: UUID,
        notes: String? = null
    ): com.haneef._school.entity.Settlement {
        val parent = parentRepository.findById(parentId).orElseThrow { RuntimeException("Parent not found") }
                
        val session = academicSessionRepository.findById(sessionId).orElseThrow { RuntimeException("Session not found") }
        val term = termId?.let { termRepository.findById(it).orElse(null) }

        val settlement = com.haneef._school.entity.Settlement(
            amount = amount,
            reference = "MANUAL-${java.util.UUID.randomUUID().toString().substring(0, 8).uppercase()}",
            status = "success",
            paymentChannel = "MANUAL",
            academicSession = session,
            term = term,
            settlementType = com.haneef._school.entity.SettlementType.MANUAL,
            reimbursed = true // Manual settlements are not considered for reimbursement
        ).apply {
            this.schoolId = schoolId
            this.parent = parent
            this.rawPayload = notes
        }

        val savedSettlement = settlementRepository.save(settlement)
        
        // Trigger payment distribution
        paymentDistributionService.distributePaymentSequentially(savedSettlement)
        
        return savedSettlement
    }

    @Transactional(readOnly = true)
    open fun calculateParentFinancialStatus(parent: Parent, sessionId: UUID? = null, termId: UUID? = null): ParentFinancialStatus {
        val schoolId = parent.schoolId!!
        val children = parent.studentRelationships.map { it.student }
        
        // Resolve Academic Context for "Current Bill"
        val targetSession = if (sessionId != null) {
            academicSessionRepository.findById(sessionId).orElse(null)
        } else {
            academicSessionRepository.findBySchoolIdAndIsCurrentSessionAndIsActive(schoolId, true, true)
                ?: academicSessionRepository.findBySchoolIdAndIsActiveOrderByYearDesc(schoolId, true).firstOrNull()
        }
        
        val targetTerm = if (termId != null) {
            termRepository.findById(termId).orElse(null)
        } else {
            targetSession?.let {
                termRepository.findBySchoolIdAndIsCurrentTermAndIsActive(schoolId, true, true).orElse(null)
            }
        }

        // 1. Calculate Total Owed (All Time) and Student Debt data
        var totalOwedAllTime = BigDecimal.ZERO
        val studentStatusList = mutableListOf<MutableMap<String, Any?>>()
        
        children.forEach { student ->
            // All Time Debt
            val allTimeResult = calculateDetailedFees(student)
            totalOwedAllTime = totalOwedAllTime.add(allTimeResult.total)
            
            // Current Term Bill (based on resolved context)
            val currentBillResult = if (targetSession != null && targetTerm != null) {
                calculateDetailedFees(student, targetSession.id!!, targetTerm.id!!)
            } else {
                DetailedFeeResult(BigDecimal.ZERO, emptyMap())
            }

            studentStatusList.add(mutableMapOf(
                "studentId" to student.id!!,
                "studentName" to student.user.fullName,
                "allTimeFees" to allTimeResult.total,
                "currentBill" to currentBillResult.total,
                "currentBillBreakdown" to currentBillResult.breakdown,
                "walletAllocated" to BigDecimal.ZERO,
                "studentUuid" to student.id!!.toString() // For sorting/distribution
            ))
        }

        // 2. Calculate Total Paid (All Time)
    var totalSettledAllTime = BigDecimal.ZERO
    
    // Direct settlements to parent (All Time)
    val parentSettlements = settlementRepository.findByParentId(parent.id!!)
    totalSettledAllTime = totalSettledAllTime.add(
        parentSettlements.filter { it.status.equals("success", ignoreCase = true) }.sumOf { it.amount }
    )

    // Settlements from parent wallets (All Time)
    val wallets = listOfNotNull(parent.paystackWallet, parent.squadWallet)
        wallets.forEach { wallet ->
            val settlements = if (wallet is com.haneef._school.entity.PaystackParentWallet) {
                settlementRepository.findByPaystackWalletId(wallet.id!!)
            } else {
                settlementRepository.findBySquadWalletId(wallet.id!!)
            }
            totalSettledAllTime = totalSettledAllTime.add(
                settlements.filter { it.status.equals("success", ignoreCase = true) }.sumOf { it.amount }
            )
        }

        // Manual/orphan settlements by email (All Time)
        parent.user.email?.let { email ->
            val manualSettlements = settlementRepository.findByPayerEmail(email)
                .filter { s -> 
                    s.schoolId == schoolId && 
                    s.status.equals("success", ignoreCase = true) &&
                    s.paystackWallet == null && s.squadWallet == null // Only add if not already counted via wallets
                }
            totalSettledAllTime = totalSettledAllTime.add(manualSettlements.sumOf { it.amount })
        }

        // 3. Distribute Total Settled across students
        var remainingToDistribute = totalSettledAllTime
        if (remainingToDistribute > BigDecimal.ZERO && studentStatusList.isNotEmpty()) {
            if (parent.paymentDistributionType == "SEQUENTIAL") {
                val priorityOrder = parent.paymentPriorityOrder?.split(",")?.map { UUID.fromString(it.trim()) } ?: emptyList()
                studentStatusList.sortWith(Comparator { a, b ->
                    val idA = a["studentId"] as UUID
                    val idB = b["studentId"] as UUID
                    val indexA = priorityOrder.indexOf(idA)
                    val indexB = priorityOrder.indexOf(idB)
                    if (indexA != -1 && indexB != -1) indexA.compareTo(indexB)
                    else if (indexA != -1) -1
                    else if (indexB != -1) 1
                    else 0
                })
                for (status in studentStatusList) {
                    if (remainingToDistribute <= BigDecimal.ZERO) break
                    val debt = status["allTimeFees"] as BigDecimal
                    val allocation = if (remainingToDistribute >= debt) debt else remainingToDistribute
                    status["walletAllocated"] = allocation
                    remainingToDistribute = remainingToDistribute.subtract(allocation)
                }
            } else { // SPREAD
                while (remainingToDistribute > BigDecimal.ZERO) {
                    val studentsWithDebt = studentStatusList.filter { 
                        (it["allTimeFees"] as BigDecimal).subtract(it["walletAllocated"] as BigDecimal) > BigDecimal.ZERO 
                    }
                    if (studentsWithDebt.isEmpty()) break
                    val share = remainingToDistribute.divide(BigDecimal(studentsWithDebt.size), 2, java.math.RoundingMode.DOWN)
                    if (share <= BigDecimal.ZERO) {
                         // Final distribution of tiny remainder
                        for (status in studentsWithDebt) {
                            if (remainingToDistribute <= BigDecimal.ZERO) break
                            val debt = (status["allTimeFees"] as BigDecimal).subtract(status["walletAllocated"] as BigDecimal)
                            val allocation = if (remainingToDistribute >= debt) debt else remainingToDistribute
                            status["walletAllocated"] = (status["walletAllocated"] as BigDecimal).add(allocation)
                            remainingToDistribute = remainingToDistribute.subtract(allocation)
                        }
                        break
                    }
                    var distributedInRound = BigDecimal.ZERO
                    for (status in studentsWithDebt) {
                        val debt = (status["allTimeFees"] as BigDecimal).subtract(status["walletAllocated"] as BigDecimal)
                        val allocation = if (share >= debt) debt else share
                        status["walletAllocated"] = (status["walletAllocated"] as BigDecimal).add(allocation)
                        distributedInRound = distributedInRound.add(allocation)
                    }
                    remainingToDistribute = remainingToDistribute.subtract(distributedInRound)
                    if (distributedInRound <= BigDecimal.ZERO) break
                }
            }
        }

        val studentResults = studentStatusList.map { status ->
            val allTimeFees = status["allTimeFees"] as BigDecimal
            val paid = status["walletAllocated"] as BigDecimal
            val currentBill = status["currentBill"] as BigDecimal
            val currentBillBreakdown = (status["currentBillBreakdown"] as? Map<*, *>)
                ?.mapNotNull { (key, value) ->
                    val name = key as? String
                    val amount = value as? BigDecimal
                    if (name != null && amount != null) name to amount else null
                }
                ?.toMap()
                ?: emptyMap()
            StudentFinancialStatus(
                studentId = status["studentId"] as UUID,
                studentName = status["studentName"] as String,
                allTimeFees = allTimeFees,
                currentBill = currentBill,
                currentBillBreakdown = currentBillBreakdown,
                outstanding = allTimeFees.subtract(currentBill).max(BigDecimal.ZERO), // Past Outstanding
                currentBalance = allTimeFees.subtract(paid).max(BigDecimal.ZERO)
            )
        }

        val balance = totalOwedAllTime.subtract(totalSettledAllTime).max(BigDecimal.ZERO)
        return ParentFinancialStatus(totalOwedAllTime, totalSettledAllTime, balance, studentResults)
    }

    @Transactional(readOnly = true)
    open fun calculateParentBalance(parent: Parent): BigDecimal {
        return calculateParentFinancialStatus(parent).balance
    }

    data class ParentFinancialStatus(
        val totalOwed: BigDecimal,
        val totalPaid: BigDecimal,
        val balance: BigDecimal,
        val students: List<StudentFinancialStatus> = emptyList()
    )

    data class StudentFinancialStatus(
        val studentId: UUID,
        val studentName: String,
        val allTimeFees: BigDecimal, // New field
        val currentBill: BigDecimal,
        val currentBillBreakdown: Map<String, BigDecimal>,
        val outstanding: BigDecimal, // Past Outstanding (Total Debt - Current Bill)
        val currentBalance: BigDecimal // All Time Balance (Total Debt - Total Paid for this student)
    )

    data class DetailedFeeResult(
        val total: BigDecimal,
        val breakdown: Map<String, BigDecimal>
    )

    /**
     * Calculates the total fees assigned to a student across all their historical class enrollments.
     * This serves as the 'debt' side of the ledger when invoices are missing or incomplete.
     */
    @Transactional(readOnly = true)
    open fun calculateAllTimeFees(student: Student): BigDecimal {
        return calculateDetailedFees(student).total
    }

    /**
     * Calculates detailed fees (total and breakdown) for a student, optionally filtered by session and term.
     */
    @Transactional(readOnly = true)
    open fun calculateDetailedFees(student: Student, sessionId: UUID? = null, termId: UUID? = null): DetailedFeeResult {
        val enrollments = studentClassRepository.findByStudentIdAndIsActive(student.id!!, true).filter {
            (sessionId == null || it.academicSession.id == sessionId) &&
            (termId == null || it.term.id == termId)
        }
        var total = BigDecimal.ZERO
        val breakdown = mutableMapOf<String, BigDecimal>()
        
        enrollments.forEach { enrollment ->
            val fees = classFeeItemRepository.findBySchoolClassIdAndAcademicSessionIdAndTermIdFilters(
                enrollment.schoolClass.id!!,
                enrollment.academicSession.id!!,
                enrollment.term.id!!,
                true
            )
            
            fees.forEach { fee ->
                val feeItem = fee.feeItem
                
                // 1. Check Gender Eligibility
                val genderMatch = when (feeItem.genderEligibility) {
                    com.haneef._school.entity.GenderEligibility.ALL -> true
                    com.haneef._school.entity.GenderEligibility.MALE -> student.gender == com.haneef._school.entity.Gender.MALE
                    com.haneef._school.entity.GenderEligibility.FEMALE -> student.gender == com.haneef._school.entity.Gender.FEMALE
                }
                
                // 2. Check Student Status Eligibility (New vs Returning)
                val statusMatch = when (feeItem.studentStatusEligibility) {
                    com.haneef._school.entity.StudentStatusEligibility.ALL -> true
                    com.haneef._school.entity.StudentStatusEligibility.NEW -> student.isNew
                    com.haneef._school.entity.StudentStatusEligibility.RETURNING -> !student.isNew
                }
                
                if (genderMatch && statusMatch) {
                    // 3. Check if student opted in for optional fees or if it's mandatory
                    val isMandatory = feeItem.isMandatory
                    val isOptedIn = if (isMandatory) true else studentOptionalFeeRepository.existsByStudentIdAndClassFeeItemIdAndIsActive(student.id!!, fee.id!!, true)
                    
                    if (isOptedIn) {
                        var amount = fee.effectiveAmount
                        // 4. Apply staff discount if applicable
                        if (feeItem.staffDiscountType != com.haneef._school.entity.DiscountType.NONE && isStaffChild(student)) {
                            if (feeItem.staffDiscountType == com.haneef._school.entity.DiscountType.FLAT_AMOUNT) {
                                amount = amount.subtract(feeItem.staffDiscountAmount)
                            } else if (feeItem.staffDiscountType == com.haneef._school.entity.DiscountType.PERCENTAGE) {
                                val discount = amount.multiply(feeItem.staffDiscountAmount).divide(BigDecimal(100))
                                amount = amount.subtract(discount)
                            }
                        }
                        if (amount > BigDecimal.ZERO) {
                            total = total.add(amount)
                            val feeNameWithTerm = if (sessionId == null) "${feeItem.name} (${enrollment.term.termName})" else feeItem.name
                            breakdown[feeNameWithTerm!!] = breakdown.getOrDefault(feeNameWithTerm, BigDecimal.ZERO).add(amount)
                        }
                    }
                }
            }
        }
        return DetailedFeeResult(total, breakdown)
    }

    @Transactional(readOnly = true)
    open fun getSchoolFeeStats(schoolId: UUID, sessionId: UUID?, termId: UUID?): Map<String, Any> {
        val selectedSession = sessionId?.let { academicSessionRepository.findById(it).orElse(null) }
        val selectedTerm = termId?.let { termRepository.findById(it).orElse(null) }
            
        val students = studentRepository.findBySchoolIdAndIsActive(schoolId, true)
        
        var totalExpected = BigDecimal.ZERO
        var totalOptional = BigDecimal.ZERO
        val breakdown = mutableMapOf<String, MutableMap<String, Any>>() 

        students.forEach { student ->
            val feeItems = calculateStudentFees(student, selectedSession, selectedTerm)
            
            val studentMandatoryTotal = feeItems.filter { it.isMandatory }.sumOf { it.amount }
            val studentOptionalTotal = feeItems.filter { !it.isMandatory }.sumOf { it.amount }
            
            totalExpected = totalExpected.add(studentMandatoryTotal)
            totalOptional = totalOptional.add(studentOptionalTotal)
            
            // Aggregate breakdown by CURRENT class (for modal report)
            val currentEnrollment = student.classEnrollments.find { it.isActive && (selectedSession == null || it.academicSession.id == selectedSession.id) }
                ?: student.classEnrollments.find { it.isActive }
            
            val className = currentEnrollment?.schoolClass?.className ?: "Unknown"
            val classData = breakdown.computeIfAbsent(className) {
                mutableMapOf(
                    "className" to className,
                    "total" to BigDecimal.ZERO,
                    "optionalTotal" to BigDecimal.ZERO,
                    "feeItems" to mutableMapOf<String, MutableMap<String, Any>>()
                )
            }
            
            classData["total"] = (classData["total"] as BigDecimal).add(studentMandatoryTotal)
            classData["optionalTotal"] = (classData["optionalTotal"] as BigDecimal).add(studentOptionalTotal)
            
            @Suppress("UNCHECKED_CAST")
            val classFeeItemsMap = classData["feeItems"] as MutableMap<String, MutableMap<String, Any>>
            
            feeItems.forEach { item ->
                val feeName = item.name
                val itemData = classFeeItemsMap.computeIfAbsent(feeName) {
                    mutableMapOf(
                        "name" to feeName,
                        "amount" to BigDecimal.ZERO,
                        "count" to 0,
                        "isMandatory" to item.isMandatory
                    )
                }
                itemData["amount"] = (itemData["amount"] as BigDecimal).add(item.amount)
                itemData["count"] = (itemData["count"] as Int) + 1
            }
        }
        
        // Convert breakdown to list and filter out classes with no fees
        val breakdownList = breakdown.values
            .filter { (it["total"] as BigDecimal) > BigDecimal.ZERO || (it["optionalTotal"] as BigDecimal) > BigDecimal.ZERO }
            .map { classData ->
                 @Suppress("UNCHECKED_CAST")
                 val itemsMap = classData["feeItems"] as Map<String, Map<String, Any>>
                 classData["feeItems"] = itemsMap.values.toList().sortedBy { it["name"] as String }
                 classData
            }.sortedBy { it["className"] as String }

        return mapOf(
            "expectedTotal" to totalExpected,
            "optionalTotal" to totalOptional,
            "breakdown" to breakdownList
        )
    }

    private data class StudentFeeItemResult(val name: String, val amount: BigDecimal, val isMandatory: Boolean)

    private fun calculateStudentFees(
        student: Student, 
        session: com.haneef._school.entity.AcademicSession?, 
        term: com.haneef._school.entity.Term?
    ): List<StudentFeeItemResult> {
        val results = mutableListOf<StudentFeeItemResult>()
        
        val enrollments = student.classEnrollments.filter { enrollment ->
            enrollment.isActive && 
            (session == null || enrollment.academicSession.id == session.id) &&
            (term == null || enrollment.term.id == term.id)
        }

        enrollments.forEach { enrollment ->
            val schoolClass = enrollment.schoolClass
            val feeItems = classFeeItemRepository.findBySchoolClassIdAndAcademicSessionIdAndTermIdFilters(
                schoolClass.id!!, 
                enrollment.academicSession.id!!,
                enrollment.term.id,
                true
            )

            feeItems.forEach { classFeeItem ->
                val feeItem = classFeeItem.feeItem
                
                // 1. Check Gender Eligibility
                val genderMatch = when (feeItem.genderEligibility) {
                    com.haneef._school.entity.GenderEligibility.ALL -> true
                    com.haneef._school.entity.GenderEligibility.MALE -> student.gender == com.haneef._school.entity.Gender.MALE
                    com.haneef._school.entity.GenderEligibility.FEMALE -> student.gender == com.haneef._school.entity.Gender.FEMALE
                }
                
                // 2. Check Student Status Eligibility (New vs Returning)
                val statusMatch = when (feeItem.studentStatusEligibility) {
                    com.haneef._school.entity.StudentStatusEligibility.ALL -> true
                    com.haneef._school.entity.StudentStatusEligibility.NEW -> student.isNew
                    com.haneef._school.entity.StudentStatusEligibility.RETURNING -> !student.isNew
                }
                
                if (genderMatch && statusMatch) {
                    val isMandatory = feeItem.isMandatory
                    val isOptedIn = if (isMandatory) true else studentOptionalFeeRepository.existsByStudentIdAndClassFeeItemIdAndIsActive(student.id!!, classFeeItem.id!!, true)
                    
                    if (isOptedIn) {
                        var amount = classFeeItem.effectiveAmount
                        
                        if (feeItem.staffDiscountType != com.haneef._school.entity.DiscountType.NONE) {
                            if (isStaffChild(student)) {
                                if (feeItem.staffDiscountType == com.haneef._school.entity.DiscountType.FLAT_AMOUNT) {
                                    amount = amount.subtract(feeItem.staffDiscountAmount)
                                } else if (feeItem.staffDiscountType == com.haneef._school.entity.DiscountType.PERCENTAGE) {
                                    val discount = amount.multiply(feeItem.staffDiscountAmount).divide(BigDecimal(100))
                                    amount = amount.subtract(discount)
                                }
                                if (amount < BigDecimal.ZERO) amount = BigDecimal.ZERO
                            }
                        }
                        
                        results.add(StudentFeeItemResult(feeItem.name, amount, isMandatory))
                    }
                }
            }
        }
        return results
    }

    @Transactional(readOnly = true)
    open fun getFeeBreakdown(parent: Parent, sessionId: UUID? = null, termId: UUID? = null): Map<String, Any> {
        val isAllTime = sessionId == null
        val selectedSession = sessionId?.let { academicSessionRepository.findById(it).orElse(null) }
            ?: academicSessionRepository.findBySchoolIdAndIsCurrentSessionAndIsActive(parent.schoolId!!, true, true)
            ?: academicSessionRepository.findBySchoolIdAndIsActiveOrderByYearDesc(parent.schoolId!!, true).firstOrNull()
            
        val selectedTerm = termId?.let { termRepository.findById(it).orElse(null) }
            ?: if (isAllTime) null else termRepository.findBySchoolIdAndIsCurrentTermAndIsActive(parent.schoolId!!, true, true).orElse(null)
        
        if (selectedSession == null && !isAllTime) {
            return mapOf(
                "totalFees" to BigDecimal.ZERO,
                "totalSettled" to BigDecimal.ZERO,
                "walletSettled" to BigDecimal.ZERO,
                "balance" to BigDecimal.ZERO,
                "feeBreakdown" to emptyList<Map<String, Any>>()
            )
        }

        var totalFees = BigDecimal.ZERO
        var totalSettled = BigDecimal.ZERO 
        
        // 1. Calculate Wallet Settlements
        var walletSettled = BigDecimal.ZERO
        val wallets = listOfNotNull(parent.paystackWallet, parent.squadWallet)
        
        wallets.forEach { wallet ->
            val settlements = if (isAllTime) {
                if (wallet is com.haneef._school.entity.PaystackParentWallet) {
                    settlementRepository.findByPaystackWalletId(wallet.id!!)
                } else {
                    settlementRepository.findBySquadWalletId(wallet.id!!)
                }
            } else if (selectedTerm != null) {
                if (wallet is com.haneef._school.entity.PaystackParentWallet) {
                    settlementRepository.findByPaystackWalletIdAndAcademicSessionIdAndTermId(
                        wallet.id!!, selectedSession!!.id!!, selectedTerm.id!!
                    )
                } else {
                    settlementRepository.findBySquadWalletIdAndAcademicSessionIdAndTermId(
                        wallet.id!!, selectedSession!!.id!!, selectedTerm.id!!
                    )
                }
            } else {
                if (wallet is com.haneef._school.entity.PaystackParentWallet) {
                    settlementRepository.findByPaystackWalletId(wallet.id!!).filter { 
                        it.academicSession?.id == selectedSession!!.id 
                    }
                } else {
                    settlementRepository.findBySquadWalletId(wallet.id!!).filter { 
                        it.academicSession?.id == selectedSession!!.id 
                    }
                }
            }
            settlements.forEach { settlement ->
                if (settlement.status.equals("success", ignoreCase = true)) {
                    walletSettled = walletSettled.add(settlement.amount)
                }
            }
        }

        // Also include settlements found by email (Manual or Orphan) OR direct parent link
        val parentEmail = parent.user.email
        val emailSettlements = if (parentEmail != null) {
            if (isAllTime) {
                settlementRepository.findByPayerEmail(parentEmail).filter { it.schoolId == parent.schoolId }
            } else {
                settlementRepository.findByPayerEmail(parentEmail)
                    .filter { 
                        it.schoolId == parent.schoolId &&
                        it.academicSession?.id == selectedSession!!.id && 
                        (selectedTerm == null || it.term?.id == selectedTerm.id) 
                    }
            }
        } else emptyList()

        val directSettlements = if (isAllTime) {
            settlementRepository.findByParentId(parent.id!!)
        } else {
            settlementRepository.findByParentId(parent.id!!)
                .filter { 
                    it.academicSession?.id == selectedSession!!.id && 
                    (selectedTerm == null || it.term?.id == selectedTerm.id) 
                }
        }
        
        val additionalSettlements = (emailSettlements + directSettlements).distinctBy { it.id }
        
        additionalSettlements.forEach { settlement ->
            // Check if this settlement is already linked to one of the parent's wallets
            val isLinkedToPaystack = settlement.paystackWallet != null && wallets.any { it.id == settlement.paystackWallet?.id }
            val isLinkedToSquad = settlement.squadWallet != null && wallets.any { it.id == settlement.squadWallet?.id }
            
            // Only add if NOT already counted (i.e., not linked to a known wallet) and status is success
            if (!isLinkedToPaystack && !isLinkedToSquad && settlement.status.equals("success", ignoreCase = true)) {
                walletSettled = walletSettled.add(settlement.amount)
            }
        }
        totalSettled = totalSettled.add(walletSettled)

        // 2. Prepare basic student data (Fees & Invoice Payments)
        val children = parent.activeStudentRelationships.map { it.student }
        val studentDataList = mutableListOf<MutableMap<String, Any?>>()
        
        children.forEach { student ->
            var studentTotal = BigDecimal.ZERO
            var studentInvoicedPaid = BigDecimal.ZERO
            val studentFeeItems = mutableListOf<Map<String, Any>>()

            // Calculate Fees
            val relevantEnrollments = if (isAllTime) {
                student.classEnrollments.filter { it.isActive }
            } else {
                student.classEnrollments.filter { it.isActive && it.academicSession.id == selectedSession!!.id && (selectedTerm == null || it.term.id == selectedTerm.id) }
            }

            relevantEnrollments.forEach { enrollment ->
                val schoolClass = enrollment.schoolClass
                val feeItems = classFeeItemRepository.findBySchoolClassIdAndAcademicSessionIdAndTermIdFilters(
                    schoolClass.id!!, 
                    enrollment.academicSession.id!!,
                    enrollment.term.id!!,
                    true
                )

                feeItems.forEach { classFeeItem ->
                    val feeItem = classFeeItem.feeItem
                    
                    // 1. Check Gender Eligibility
                    val genderMatch = when (feeItem.genderEligibility) {
                        com.haneef._school.entity.GenderEligibility.ALL -> true
                        com.haneef._school.entity.GenderEligibility.MALE -> student.gender == com.haneef._school.entity.Gender.MALE
                        com.haneef._school.entity.GenderEligibility.FEMALE -> student.gender == com.haneef._school.entity.Gender.FEMALE
                    }
                    
                    // 2. Check Student Status Eligibility (New vs Returning)
                    val statusMatch = when (feeItem.studentStatusEligibility) {
                        com.haneef._school.entity.StudentStatusEligibility.ALL -> true
                        com.haneef._school.entity.StudentStatusEligibility.NEW -> student.isNew
                        com.haneef._school.entity.StudentStatusEligibility.RETURNING -> !student.isNew
                    }
                    
                    if (genderMatch && statusMatch) {
                        // Check if fee is applicable (mandatory or opted-in)
                        val isMandatory = feeItem.isMandatory
                        val isOptedIn = if (isMandatory) true else studentOptionalFeeRepository.existsByStudentIdAndClassFeeItemIdAndIsActive(student.id!!, classFeeItem.id!!, true)
                        
                        var amount = classFeeItem.effectiveAmount
                        
                        // Apply Staff Discount
                        if (feeItem.staffDiscountType != com.haneef._school.entity.DiscountType.NONE && isStaffChild(student)) {
                            if (feeItem.staffDiscountType == com.haneef._school.entity.DiscountType.FLAT_AMOUNT) {
                                amount = amount.subtract(feeItem.staffDiscountAmount)
                            } else if (feeItem.staffDiscountType == com.haneef._school.entity.DiscountType.PERCENTAGE) {
                                val discount = amount.multiply(feeItem.staffDiscountAmount).divide(BigDecimal(100))
                                amount = amount.subtract(discount)
                            }
                        }
                        if (amount < BigDecimal.ZERO) amount = BigDecimal.ZERO
                        
                        if (isOptedIn) {
                            studentTotal = studentTotal.add(amount)
                        }

                        // Add to fee items list if it's the current session/term or if in all-time mode
                        if (isAllTime || (enrollment.academicSession.id == selectedSession!!.id && (selectedTerm == null || enrollment.term.id == selectedTerm.id))) {
                             // Check if individual lockdown
                            val studentOptionalFee = if (!isMandatory) {
                                studentOptionalFeeRepository.findByStudentIdAndClassFeeItemId(student.id!!, classFeeItem.id!!)
                            } else null
                            val isStudentFeeLocked = studentOptionalFee?.isLocked ?: false

                            studentFeeItems.add(mapOf(
                                "id" to classFeeItem.id!!,
                                "name" to feeItem.name,
                                "amount" to amount,
                                "isMandatory" to isMandatory,
                                "isOptedIn" to isOptedIn,
                                "isLocked" to isStudentFeeLocked,
                                "termName" to enrollment.term.termName
                            ))
                        }
                    }
                }
            }

            // Invoices are legacy/redundant for modern payment tracking via Settlements
            studentInvoicedPaid = BigDecimal.ZERO
            
            totalFees = totalFees.add(studentTotal)
            // totalSettled = totalSettled.add(studentInvoicedPaid) // Removed to prevent double-counting and leaks from other parents

            studentDataList.add(mutableMapOf(
                "studentUuid" to student.id!!.toString(), 
                "studentName" to student.user.fullName,
                "studentId" to student.studentId,
                "total" to studentTotal,
                "invoicedPaid" to studentInvoicedPaid,
                "walletAllocated" to BigDecimal.ZERO, 
                "items" to studentFeeItems
            ))
        }

        // 3. Distribute Wallet Settlements
        var remainingWalletSettled = walletSettled
        
        if (remainingWalletSettled > BigDecimal.ZERO) {
            if (parent.paymentDistributionType == "SEQUENTIAL") {
                // Parse priority order
                val priorityOrder = parent.paymentPriorityOrder?.split(",")?.map { UUID.fromString(it.trim()) } ?: emptyList()
                
                // Sort students: Priority list first, then others
                studentDataList.sortWith(Comparator { a, b ->
                    val idA = UUID.fromString(a["studentUuid"] as String)
                    val idB = UUID.fromString(b["studentUuid"] as String)
                    val indexA = priorityOrder.indexOf(idA)
                    val indexB = priorityOrder.indexOf(idB)
                    
                    if (indexA != -1 && indexB != -1) indexA.compareTo(indexB)
                    else if (indexA != -1) -1
                    else if (indexB != -1) 1
                    else 0 // Keep original order if neither in priority list
                })
                
                // Allocate sequentially
                for (studentData in studentDataList) {
                    if (remainingWalletSettled <= BigDecimal.ZERO) break
                    
                    val total = studentData["total"] as BigDecimal
                    val invoiced = studentData["invoicedPaid"] as BigDecimal
                    val outstanding = total.subtract(invoiced)
                    
                    if (outstanding > BigDecimal.ZERO) {
                        val allocation = if (remainingWalletSettled >= outstanding) outstanding else remainingWalletSettled
                        studentData["walletAllocated"] = allocation
                        remainingWalletSettled = remainingWalletSettled.subtract(allocation)
                    }
                }
                
            } else { // SPREAD (Default)
                // Distribute equally among students with outstanding balance
                // Loop until wallet empty or all paid. 
                // Simple approach: Proportional or Iterative equal chunks?
                // "Split equally" usually means TotalWallet / N. 
                // But if a student owes less than their share, the remainder should go to others.
                
                // Let's do an iterative approach to be precise
                while (remainingWalletSettled > BigDecimal.ZERO) {
                    val studentsWithDebt = studentDataList.filter { 
                        val total = it["total"] as BigDecimal
                        val invoiced = it["invoicedPaid"] as BigDecimal
                        val allocated = it["walletAllocated"] as BigDecimal
                        total.subtract(invoiced).subtract(allocated) > BigDecimal.ZERO
                    }
                    
                    if (studentsWithDebt.isEmpty()) break // All paid off
                    
                    val share = remainingWalletSettled.divide(BigDecimal(studentsWithDebt.size), 2, java.math.RoundingMode.DOWN)
                    if (share <= BigDecimal.ZERO) break // Too small to split
                    
                    var distributedInRound = BigDecimal.ZERO
                    
                    for (studentData in studentsWithDebt) {
                        val total = studentData["total"] as BigDecimal
                        val invoiced = studentData["invoicedPaid"] as BigDecimal
                        val previouslyAllocated = studentData["walletAllocated"] as BigDecimal
                        val currentDebt = total.subtract(invoiced).subtract(previouslyAllocated)
                        
                        val allocation = if (share >= currentDebt) currentDebt else share
                        studentData["walletAllocated"] = previouslyAllocated.add(allocation)
                        distributedInRound = distributedInRound.add(allocation)
                    }
                    
                    remainingWalletSettled = remainingWalletSettled.subtract(distributedInRound)
                    
                    // Safety break if no progress (e.g. rounding issues)
                    if (distributedInRound == BigDecimal.ZERO) break 
                }
                
                // If any remainder due to rounding, give to first student with debt
                if (remainingWalletSettled > BigDecimal.ZERO) {
                     val firstDebtor = studentDataList.firstOrNull { 
                        val total = it["total"] as BigDecimal
                        val invoiced = it["invoicedPaid"] as BigDecimal
                        val allocated = it["walletAllocated"] as BigDecimal
                        total.subtract(invoiced).subtract(allocated) > BigDecimal.ZERO
                    }
                    if (firstDebtor != null) {
                         val prev = firstDebtor["walletAllocated"] as BigDecimal
                         firstDebtor["walletAllocated"] = prev.add(remainingWalletSettled)
                    }
                }
            }
        }

        // 4. Finalize Breakdown List
        val feeBreakdown = studentDataList.map { data ->
            val total = data["total"] as BigDecimal
            val invoiced = data["invoicedPaid"] as BigDecimal
            val allocated = data["walletAllocated"] as BigDecimal
            val totalPaid = invoiced.add(allocated)
            
            mapOf(
                "studentUuid" to data["studentUuid"],
                "studentName" to data["studentName"],
                "studentId" to data["studentId"],
                "total" to total,
                "settled" to totalPaid,
                "invoicedPaid" to invoiced,
                "walletAllocated" to allocated,
                "balance" to total.subtract(totalPaid),
                "items" to data["items"]
            )
        }

        return mapOf(
            "totalFees" to totalFees,
            "totalSettled" to totalSettled,
            "walletSettled" to walletSettled,
            "balance" to totalFees.subtract(totalSettled).max(BigDecimal.ZERO),
            "feeBreakdown" to feeBreakdown,
            "isAllTime" to isAllTime
        )
    }

    private fun isStaffChild(student: Student): Boolean {
        // A student is a staff child if ANY of their parents is a staff member
        return student.parentRelationships.any { relationship ->
            val parentUser = relationship.parent.user
            val schoolId = student.schoolId
            if (schoolId == null) return@any false
            
            val roles = userSchoolRoleRepository.findByUserAndSchoolId(parentUser, schoolId)
            roles.any { 
                it.role.roleType == com.haneef._school.entity.RoleType.STAFF || 
                it.role.roleType == com.haneef._school.entity.RoleType.SCHOOL_ADMIN 
            }
        }
    }

    @Transactional(readOnly = true)
    open fun getPaymentAnalytics(
        schoolId: UUID,
        startDate: LocalDate?,
        endDate: LocalDate?,
        sessionId: UUID?,
        termId: UUID?
    ): PaymentAnalyticsDto {
        val startDateTime = startDate?.atStartOfDay()
        val endDateTime = endDate?.atTime(23, 59, 59)

        val settlements = settlementRepository.findByFilters(schoolId, sessionId, termId, startDateTime, endDateTime)
        val reimbursements = schoolReimbursementRepository.findByFilters(schoolId, sessionId, termId, startDateTime, endDateTime)

        val totalSettlements = settlements.sumOf { it.amount }
        val totalReimbursements = reimbursements.sumOf { it.amount }
        val totalManualPayments = settlements.filter { it.settlementType == SettlementType.MANUAL }.sumOf { it.amount }
        val netRevenue = totalSettlements.subtract(totalReimbursements)

        // Group by date for trends
        val settlementTrend = settlements
            .groupBy { it.transactionDate.toLocalDate() }
            .map { (date, list) -> TrendPoint(date, list.sumOf { it.amount }) }
            .sortedBy { it.date }

        val reimbursementTrend = reimbursements
            .groupBy { it.reimbursementDate.toLocalDate() }
            .map { (date, list) -> TrendPoint(date, list.sumOf { it.amount }) }
            .sortedBy { it.date }

        return PaymentAnalyticsDto(
            totalSettlements = totalSettlements,
            totalReimbursements = totalReimbursements,
            totalManualPayments = totalManualPayments,
            netRevenue = netRevenue,
            settlementTrend = settlementTrend,
            reimbursementTrend = reimbursementTrend
        )
    }

    @Transactional
    open fun processSettlement(settlement: com.haneef._school.entity.Settlement) {
        // Trigger payment distribution logic
        // This distributes the settlement amount to student invoices based on parent's preference
        paymentDistributionService.distributePaymentSequentially(settlement)

        // Send WhatsApp Notification
        try {
            sendWhatsAppPaymentNotification(settlement)
        } catch (e: Exception) {
            println("Failed to send WhatsApp payment notification: ${e.message}")
        }
    }

    private fun sendWhatsAppPaymentNotification(settlement: com.haneef._school.entity.Settlement) {
        val parent = settlement.paystackWallet?.parent 
            ?: settlement.squadWallet?.parent
            ?: parentRepository.findByUserEmail(settlement.payerEmail ?: "").orElse(null)
            ?: return

        val phoneNumber = parent.user.phoneNumber ?: return
        val currentBalance = calculateParentBalance(parent)
        val schoolName = "4School" // Default name

        val message = """
            Dear Parent,
            
            We have received your payment of ${settlement.currency} ${settlement.amount}.
            
            Reference: ${settlement.reference}
            Date: ${settlement.transactionDate.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))}
            
            Your current outstanding balance is: ${settlement.currency} $currentBalance
            
            Thank you for your payment.
            $schoolName Team
        """.trimIndent()

        whatsappServiceProvider.ifAvailable { service ->
            service.sendTextMessage(phoneNumber, message, parent.user)
        }
    }

}

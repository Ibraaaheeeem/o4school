package com.haneef._school.service

import com.haneef._school.entity.Parent
import com.haneef._school.entity.Student
import com.haneef._school.entity.StudentClass
import com.haneef._school.entity.SchoolClass
import com.haneef._school.entity.AcademicSession
import com.haneef._school.entity.Term
import com.haneef._school.repository.ClassFeeItemRepository
import com.haneef._school.repository.InvoiceRepository
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
    private val invoiceRepository: InvoiceRepository,
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
            this.rawPayload = notes
        }

        val savedSettlement = settlementRepository.save(settlement)
        
        // Trigger payment distribution
        paymentDistributionService.distributePaymentSequentially(savedSettlement)
        
        return savedSettlement
    }

    @Transactional(readOnly = true)
    open fun calculateParentBalance(parent: Parent): BigDecimal {
        val schoolId = parent.schoolId!!
        val children = parent.studentRelationships.map { it.student }
        
        // 1. Calculate Total Owed (All Time)
        // Similar to TemplateParameterResolver, we calculate the higher of invoices vs fee structure PER STUDENT and sum them up
        var totalOwedAllTime = BigDecimal.ZERO
        
        children.forEach { student ->
            // Invoices for this student
            val studentInvoices = invoiceRepository.findByStudentIdAndSchoolIdAndIsActive(student.id!!, schoolId, true)
                .filter { it.status != com.haneef._school.entity.InvoiceStatus.DRAFT && it.status != com.haneef._school.entity.InvoiceStatus.CANCELLED }
                
            val invoiceTotal = studentInvoices.fold(BigDecimal.ZERO) { acc, inv ->
                acc.add(BigDecimal.valueOf(inv.totalAmount.toLong(), 2))
            }
            
            // Structure for this student
            val structureTotal = calculateAllTimeFees(student)
            
            // Take the higher of the two ensuring we don't miss any debt for this specific child
            totalOwedAllTime = totalOwedAllTime.add(invoiceTotal.max(structureTotal))
        }

        // 2. Calculate Total Paid (All Time)
        val studentIds = children.mapNotNull { it.id }
        val settlements = settlementRepository.findByParentContext(
            schoolId,
            parent.id!!,
            studentIds,
            parent.user.email,
            null,
            null
        )
        val totalSettledAllTime = settlements.sumOf { it.amount }

        val balance = totalOwedAllTime.subtract(totalSettledAllTime)
        return balance.max(BigDecimal.ZERO)
    }

    /**
     * Calculates the total fees assigned to a student across all their historical class enrollments.
     * This serves as the 'debt' side of the ledger when invoices are missing or incomplete.
     */
    @Transactional(readOnly = true)
    open fun calculateAllTimeFees(student: Student): BigDecimal {
        val enrollments = studentClassRepository.findByStudentIdAndIsActive(student.id!!, true)
        var total = BigDecimal.ZERO
        
        enrollments.forEach { enrollment ->
            val fees = classFeeItemRepository.findBySchoolClassIdAndAcademicSessionIdAndTermIdFilters(
                enrollment.schoolClass.id!!,
                enrollment.academicSession.id!!,
                enrollment.term.id!!,
                true
            )
            
            fees.forEach { fee ->
                // Check if student opted in for optional fees or if it's mandatory
                val isMandatory = fee.feeItem.isMandatory
                val isOptedIn = if (isMandatory) true else studentOptionalFeeRepository.existsByStudentIdAndClassFeeItemIdAndIsActive(student.id!!, fee.id!!, true)
                
                if (isOptedIn) {
                    var amount = fee.effectiveAmount
                    // Apply staff discount if applicable
                    if (fee.feeItem.staffDiscountType != com.haneef._school.entity.DiscountType.NONE && isStaffChild(student)) {
                        if (fee.feeItem.staffDiscountType == com.haneef._school.entity.DiscountType.FLAT_AMOUNT) {
                            amount = amount.subtract(fee.feeItem.staffDiscountAmount)
                        } else if (fee.feeItem.staffDiscountType == com.haneef._school.entity.DiscountType.PERCENTAGE) {
                            val discount = amount.multiply(fee.feeItem.staffDiscountAmount).divide(BigDecimal(100))
                            amount = amount.subtract(discount)
                        }
                    }
                    if (amount > BigDecimal.ZERO) {
                        total = total.add(amount)
                    }
                }
            }
        }
        return total
    }

    @Transactional(readOnly = true)
    open fun getSchoolFeeStats(schoolId: UUID, sessionId: UUID?, termId: UUID?): Map<String, Any> {
        val selectedSession = sessionId?.let { academicSessionRepository.findById(it).orElse(null) }
            ?: academicSessionRepository.findBySchoolIdAndIsCurrentSessionAndIsActive(schoolId, true, true)
            ?: academicSessionRepository.findBySchoolIdAndIsActiveOrderByYearDesc(schoolId, true).firstOrNull()
            
        val selectedTerm = termId?.let { termRepository.findById(it).orElse(null) }
            ?: termRepository.findBySchoolIdAndIsCurrentTermAndIsActive(schoolId, true, true).orElse(null)
            
        if (selectedSession == null) {
            return mapOf(
                "expectedTotal" to BigDecimal.ZERO,
                "optionalTotal" to BigDecimal.ZERO,
                "breakdown" to emptyList<Map<String, Any>>()
            )
        }

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
            
            // Aggregate breakdown
            val className = student.classEnrollments.find { it.isActive }?.schoolClass?.className ?: "Unknown"
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
        session: com.haneef._school.entity.AcademicSession, 
        term: com.haneef._school.entity.Term?
    ): List<StudentFeeItemResult> {
        val results = mutableListOf<StudentFeeItemResult>()
        
        student.classEnrollments.filter { it.isActive }.forEach { enrollment ->
            val schoolClass = enrollment.schoolClass
            val feeItems = classFeeItemRepository.findBySchoolClassIdAndAcademicSessionIdAndTermIdFilters(
                schoolClass.id!!, 
                session.id!!,
                term?.id,
                true
            )

            feeItems.forEach { classFeeItem ->
                val isMandatory = classFeeItem.feeItem.isMandatory
                val isOptedIn = if (isMandatory) true else studentOptionalFeeRepository.existsByStudentIdAndClassFeeItemIdAndIsActive(student.id!!, classFeeItem.id!!, true)
                
                if (isOptedIn) {
                    var amount = classFeeItem.effectiveAmount
                    
                    if (classFeeItem.feeItem.staffDiscountType != com.haneef._school.entity.DiscountType.NONE) {
                        if (isStaffChild(student)) {
                            if (classFeeItem.feeItem.staffDiscountType == com.haneef._school.entity.DiscountType.FLAT_AMOUNT) {
                                amount = amount.subtract(classFeeItem.feeItem.staffDiscountAmount)
                            } else if (classFeeItem.feeItem.staffDiscountType == com.haneef._school.entity.DiscountType.PERCENTAGE) {
                                val discount = amount.multiply(classFeeItem.feeItem.staffDiscountAmount).divide(BigDecimal(100))
                                amount = amount.subtract(discount)
                            }
                            if (amount < BigDecimal.ZERO) amount = BigDecimal.ZERO
                        }
                    }
                    
                    results.add(StudentFeeItemResult(classFeeItem.feeItem.name, amount, isMandatory))
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
        
        // 1. Calculate Settlements (Wallets, Email, Manual Allocations)
        val studentIds = parent.studentRelationships.map { it.student.id!! }
        val settlements = settlementRepository.findByParentContext(
            parent.schoolId!!,
            parent.id!!,
            studentIds,
            parent.user.email,
            if (isAllTime) null else selectedSession?.id,
            if (isAllTime) null else selectedTerm?.id
        )
        val walletSettled = settlements.sumOf { it.amount }
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
                    // Check if fee is applicable (mandatory or opted-in)
                    val isMandatory = classFeeItem.feeItem.isMandatory
                    val isOptedIn = if (isMandatory) true else studentOptionalFeeRepository.existsByStudentIdAndClassFeeItemIdAndIsActive(student.id!!, classFeeItem.id!!, true)
                    
                    if (isOptedIn) {
                        var amount = classFeeItem.effectiveAmount
                        
                        // Apply Staff Discount
                        if (classFeeItem.feeItem.staffDiscountType != com.haneef._school.entity.DiscountType.NONE && isStaffChild(student)) {
                            if (classFeeItem.feeItem.staffDiscountType == com.haneef._school.entity.DiscountType.FLAT_AMOUNT) {
                                amount = amount.subtract(classFeeItem.feeItem.staffDiscountAmount)
                            } else if (classFeeItem.feeItem.staffDiscountType == com.haneef._school.entity.DiscountType.PERCENTAGE) {
                                val discount = amount.multiply(classFeeItem.feeItem.staffDiscountAmount).divide(BigDecimal(100))
                                amount = amount.subtract(discount)
                            }
                        }
                        if (amount < BigDecimal.ZERO) amount = BigDecimal.ZERO
                        
                        studentTotal = studentTotal.add(amount)
                        
                        // Add to fee items list if it's the current session/term or if in all-time mode
                        if (isAllTime || (enrollment.academicSession.id == selectedSession!!.id && (selectedTerm == null || enrollment.term.id == selectedTerm.id))) {
                             // Check if individual lockdown
                            val studentOptionalFee = if (!isMandatory) {
                                studentOptionalFeeRepository.findByStudentIdAndClassFeeItemId(student.id!!, classFeeItem.id!!)
                            } else null
                            val isStudentFeeLocked = studentOptionalFee?.isLocked ?: false

                            studentFeeItems.add(mapOf(
                                "id" to classFeeItem.id!!,
                                "name" to classFeeItem.feeItem.name,
                                "amount" to amount,
                                "isMandatory" to isMandatory,
                                "isOptedIn" to isOptedIn,
                                "isLocked" to (classFeeItem.isLocked || isStudentFeeLocked),
                                "termName" to enrollment.term.termName
                            ))
                        }
                    }
                }
            }

            // Calculate Invoice Payments
            val invoices = if (isAllTime) {
                invoiceRepository.findByStudentIdAndSchoolIdAndIsActive(student.id!!, parent.schoolId!!, true)
                    .filter { it.status != com.haneef._school.entity.InvoiceStatus.DRAFT && it.status != com.haneef._school.entity.InvoiceStatus.CANCELLED }
            } else {
                invoiceRepository.findByStudentIdAndAcademicSessionIdAndTermAndIsActive(
                    student.id!!,
                    selectedSession!!.id!!,
                    selectedTerm?.termName,
                    true
                )
            }

            invoices.forEach { invoice ->
                studentInvoicedPaid = studentInvoicedPaid.add(BigDecimal(invoice.amountPaid).divide(BigDecimal(100)))
            }
            
            totalFees = totalFees.add(studentTotal)
            totalSettled = totalSettled.add(studentInvoicedPaid)

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

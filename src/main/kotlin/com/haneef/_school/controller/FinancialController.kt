package com.haneef._school.controller

import com.haneef._school.dto.*
import com.haneef._school.entity.*
import com.haneef._school.repository.*
import com.haneef._school.repository.InvoiceRepository
import com.haneef._school.service.CustomUserDetails
import com.haneef._school.service.PaymentDistributionService
import com.haneef._school.service.FinancialService
import com.haneef._school.service.SchoolWalletService
import com.haneef._school.service.PaystackService
import jakarta.servlet.http.HttpSession
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@Controller
@RequestMapping("/admin/financial")
@PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'SCHOOL_ADMIN', 'ADMIN', 'PRINCIPAL', 'STAFF', 'TEACHER')")
class FinancialController(
    private val feeItemRepository: FeeItemRepository,
    private val classFeeItemRepository: ClassFeeItemRepository,
    private val schoolClassRepository: SchoolClassRepository,
    private val academicSessionRepository: AcademicSessionRepository,
    private val schoolRepository: SchoolRepository,
    private val settlementRepository: SettlementRepository,
    private val termRepository: TermRepository,
    private val parentRepository: ParentRepository,
    private val studentRepository: StudentRepository,
    private val parentWalletRepository: ParentWalletRepository,
    private val paymentDistributionService: PaymentDistributionService,
    private val financialService: FinancialService,
    private val invoiceRepository: InvoiceRepository,
    private val schoolWalletService: SchoolWalletService,
    private val paystackService: PaystackService,
    private val authorizationService: com.haneef._school.service.AuthorizationService,
    private val studentOptionalFeeRepository: StudentOptionalFeeRepository
) {

    private val logger = LoggerFactory.getLogger(FinancialController::class.java)

    @GetMapping
    fun financialHome(model: Model, authentication: Authentication, session: HttpSession): String {
        val customUser = authentication.principal as CustomUserDetails
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/select-school"

        // Get school information
        val school = schoolRepository.findById(selectedSchoolId).orElseThrow { 
            RuntimeException("School not found") 
        }

        // Get financial statistics
        val totalFeeItems = feeItemRepository.countBySchoolIdAndIsActive(selectedSchoolId, true)
        val totalClassFeeItems = classFeeItemRepository.countBySchoolIdAndIsActive(selectedSchoolId, true)
        val activeClasses = schoolClassRepository.countBySchoolIdAndIsActive(selectedSchoolId, true)

        // Get wallet information
        val wallet = schoolWalletService.getWalletBySchoolId(selectedSchoolId)
        val providers = if (wallet == null) paystackService.getAvailableProviders() else emptyList()

        model.addAttribute("user", customUser.user)
        model.addAttribute("userRole", "School Administrator")
        model.addAttribute("school", school)
        model.addAttribute("wallet", wallet)
        model.addAttribute("providers", providers)
        model.addAttribute("financialStats", mapOf(
            "feeItems" to totalFeeItems,
            "classFeeItems" to totalClassFeeItems,
            "activeClasses" to activeClasses
        ))

        return "admin/financial/home"
    }

    @PostMapping("/create-wallet")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
    fun createWallet(
        @RequestParam(defaultValue = "wema-bank") preferredBank: String,
        session: HttpSession,
        model: Model
    ): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "fragments/error :: error-message"

        val school = schoolRepository.findById(selectedSchoolId).orElseThrow()
        val result = schoolWalletService.createWalletForSchool(school, preferredBank)

        if (result.isSuccess) {
            model.addAttribute("success", "School wallet created successfully!")
        } else {
            model.addAttribute("error", "Error creating wallet: ${result.exceptionOrNull()?.message}")
        }

        // Reload dashboard data for the fragment
        val totalFeeItems = feeItemRepository.countBySchoolIdAndIsActive(selectedSchoolId, true)
        val totalClassFeeItems = classFeeItemRepository.countBySchoolIdAndIsActive(selectedSchoolId, true)
        val activeClasses = schoolClassRepository.countBySchoolIdAndIsActive(selectedSchoolId, true)
        
        val wallet = schoolWalletService.getWalletBySchoolId(selectedSchoolId)
        val providers = if (wallet == null) paystackService.getAvailableProviders() else emptyList()

        model.addAttribute("school", school)
        model.addAttribute("wallet", wallet)
        model.addAttribute("providers", providers)
        model.addAttribute("financialStats", mapOf(
            "feeItems" to totalFeeItems,
            "classFeeItems" to totalClassFeeItems,
            "activeClasses" to activeClasses
        ))

        return "admin/financial/home :: wallet-section"
    }

    // Fee Items Management
    @GetMapping("/fee-items")
    fun feeItemsList(
        model: Model, 
        authentication: Authentication, 
        session: HttpSession,
        @RequestParam(required = false) category: FeeCategory?,
        @RequestParam(required = false) search: String?
    ): String {
        val customUser = authentication.principal as CustomUserDetails
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/select-school"

        // Get school information
        val school = schoolRepository.findById(selectedSchoolId).orElseThrow { 
            RuntimeException("School not found") 
        }

        val feeItems = feeItemRepository.findBySchoolIdAndFilters(selectedSchoolId, true, category, search)

        model.addAttribute("user", customUser.user)
        model.addAttribute("school", school)
        model.addAttribute("feeItems", feeItems)
        model.addAttribute("feeCategories", FeeCategory.values())
        model.addAttribute("recurrenceTypes", RecurrenceType.values())
        model.addAttribute("selectedCategory", category)
        model.addAttribute("search", search)

        return "admin/financial/fee-items"
    }

    @GetMapping("/fee-items/new/modal")
    fun getNewFeeItemModal(model: Model, authentication: Authentication, session: HttpSession): String {
        val customUser = authentication.principal as CustomUserDetails
        val selectedSchoolId = authorizationService.validateSchoolAccess(
            session.getAttribute("selectedSchoolId") as? UUID
        )
        
        val academicSessions = academicSessionRepository.findBySchoolIdAndIsActiveOrderByYearDesc(selectedSchoolId, true)
        val currentSession = academicSessions.find { it.isCurrentSession } ?: academicSessions.firstOrNull()
        
        val terms = currentSession?.let { 
            termRepository.findByAcademicSessionIdAndIsActiveOrderByStartDate(it.id!!, true)
        } ?: emptyList()
        
        model.addAttribute("user", customUser.user)
        model.addAttribute("feeItem", FeeItem())
        model.addAttribute("feeCategories", FeeCategory.values())
        model.addAttribute("recurrenceTypes", RecurrenceType.values())
        model.addAttribute("academicSessions", academicSessions)
        model.addAttribute("terms", terms)
        model.addAttribute("isEdit", false)
        
        return "admin/financial/fee-item-modal"
    }

    @GetMapping("/fee-items/{id}/modal")
    fun getEditFeeItemModal(@PathVariable id: UUID, model: Model, authentication: Authentication, session: HttpSession): String {
        val customUser = authentication.principal as CustomUserDetails
        val selectedSchoolId = authorizationService.validateSchoolAccess(
            session.getAttribute("selectedSchoolId") as? UUID
        )
            
        // Use secure validation
        val feeItem = authorizationService.validateAndGetFeeItem(id, selectedSchoolId)
        
        val academicSessions = academicSessionRepository.findBySchoolIdAndIsActiveOrderByYearDesc(selectedSchoolId, true)
        val selectedSession = feeItem.academicSession ?: academicSessions.find { it.isCurrentSession } ?: academicSessions.firstOrNull()
        
        val terms = selectedSession?.let { 
            termRepository.findByAcademicSessionIdAndIsActiveOrderByStartDate(it.id!!, true)
        } ?: emptyList()
        
        model.addAttribute("user", customUser.user)
        model.addAttribute("feeItem", feeItem)
        model.addAttribute("feeCategories", FeeCategory.values())
        model.addAttribute("recurrenceTypes", RecurrenceType.values())
        model.addAttribute("academicSessions", academicSessions)
        model.addAttribute("terms", terms)
        model.addAttribute("isEdit", true)
        
        return "admin/financial/fee-item-modal"
    }

    @PostMapping("/fee-items/save-htmx")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
    fun saveFeeItemHtmx(
        @RequestParam(required = false) id: UUID?,
        @jakarta.validation.Valid @ModelAttribute feeItemDto: FeeItemDto,
        bindingResult: org.springframework.validation.BindingResult,
        session: HttpSession,
        model: Model
    ): String {
        if (bindingResult.hasErrors()) {
            model.addAttribute("error", bindingResult.allErrors.first().defaultMessage)
            return "fragments/error :: error-message"
        }
        val selectedSchoolId = authorizationService.validateSchoolAccess(
            session.getAttribute("selectedSchoolId") as? UUID
        )

        try {
            // Validate and convert IDs
            val academicSessionIdUUID = try {
                if (feeItemDto.academicSessionId.isNullOrBlank()) {
                    throw IllegalArgumentException("Academic Session is required")
                }
                UUID.fromString(feeItemDto.academicSessionId)
            } catch (e: IllegalArgumentException) {
                model.addAttribute("error", if (e.message?.contains("required") == true) e.message else "Invalid academic session ID format")
                return "fragments/error :: error-message"
            }
            
            val termIdUUID = try {
                if (feeItemDto.termId.isNullOrBlank()) {
                    throw IllegalArgumentException("Term is required")
                }
                UUID.fromString(feeItemDto.termId)
            } catch (e: IllegalArgumentException) {
                model.addAttribute("error", if (e.message?.contains("required") == true) e.message else "Invalid term ID format")
                return "fragments/error :: error-message"
            }

            val academicSession = authorizationService.validateAndGetAcademicSession(academicSessionIdUUID, selectedSchoolId)
            val term = authorizationService.validateAndGetTerm(termIdUUID, selectedSchoolId)

            if (id != null) {
                // Update existing fee item - use secure validation
                val existingFeeItem = authorizationService.validateAndGetFeeItem(id, selectedSchoolId)
                
                existingFeeItem.name = feeItemDto.name ?: ""
                existingFeeItem.amount = feeItemDto.amount ?: BigDecimal.ZERO
                existingFeeItem.feeCategory = feeItemDto.feeCategory ?: FeeCategory.TUITION
                existingFeeItem.description = feeItemDto.description
                existingFeeItem.isMandatory = feeItemDto.isMandatory
                existingFeeItem.isRecurring = feeItemDto.isRecurring
                existingFeeItem.recurrenceType = if (feeItemDto.isRecurring) feeItemDto.recurrenceType else null
                existingFeeItem.academicSession = academicSession
                existingFeeItem.term = term
                existingFeeItem.genderEligibility = feeItemDto.genderEligibility ?: GenderEligibility.ALL
                existingFeeItem.studentStatusEligibility = feeItemDto.studentStatusEligibility ?: StudentStatusEligibility.ALL
                existingFeeItem.staffDiscountType = feeItemDto.staffDiscountType ?: DiscountType.NONE
                existingFeeItem.staffDiscountAmount = feeItemDto.staffDiscountAmount ?: BigDecimal.ZERO
                
                feeItemRepository.save(existingFeeItem)
                model.addAttribute("message", "Fee item updated successfully!")
            } else {
                // Create new fee item
                val newFeeItem = FeeItem(
                    name = feeItemDto.name ?: "",
                    amount = feeItemDto.amount ?: BigDecimal.ZERO,
                    feeCategory = feeItemDto.feeCategory ?: FeeCategory.TUITION
                ).apply {
                    this.schoolId = selectedSchoolId
                    this.description = feeItemDto.description
                    this.isMandatory = feeItemDto.isMandatory
                    this.isRecurring = feeItemDto.isRecurring
                    this.recurrenceType = if (feeItemDto.isRecurring) feeItemDto.recurrenceType else null
                    this.academicSession = academicSession
                    this.term = term
                    this.genderEligibility = feeItemDto.genderEligibility ?: GenderEligibility.ALL
                    this.studentStatusEligibility = feeItemDto.studentStatusEligibility ?: StudentStatusEligibility.ALL
                    this.staffDiscountType = feeItemDto.staffDiscountType ?: DiscountType.NONE
                    this.staffDiscountAmount = feeItemDto.staffDiscountAmount ?: BigDecimal.ZERO
                    this.isActive = true
                }
                feeItemRepository.save(newFeeItem)
                model.addAttribute("message", "Fee item created successfully!")
            }

            // Fetch updated list for OOB update
            val feeItems = feeItemRepository.findBySchoolIdAndFilters(selectedSchoolId, true, null, null)
            model.addAttribute("feeItems", feeItems)

            return "admin/financial/fragments/fee-item-save-success"
        } catch (e: Exception) {
            model.addAttribute("error", "Error saving fee item: ${e.message}")
            return "fragments/error :: error-message"
        }
    }

    @PostMapping("/fee-items/delete/{id}")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
    fun deleteFeeItem(@PathVariable id: UUID, session: HttpSession, model: Model): String {
        val selectedSchoolId = authorizationService.validateSchoolAccess(
            session.getAttribute("selectedSchoolId") as? UUID
        )
            
        try {
            // Use secure validation
            val feeItem = authorizationService.validateAndGetFeeItem(id, selectedSchoolId)
            
            feeItem.isActive = false
            feeItemRepository.save(feeItem)
            
            // Fetch updated list
            val feeItems = feeItemRepository.findBySchoolIdAndFilters(selectedSchoolId, true, null, null)
            model.addAttribute("feeItems", feeItems)
            
            return "admin/financial/fee-items :: feeItemsList"
        } catch (e: Exception) {
            model.addAttribute("error", "Error deleting fee item: ${e.message}")
            return "fragments/error :: error-message"
        }
    }

    // Class Assignment for Fee Items
    @GetMapping("/fee-items/{feeItemId}/classes/modal")
    fun getClassAssignmentModal(@PathVariable feeItemId: UUID, model: Model, authentication: Authentication, session: HttpSession): String {
        try {
            val customUser = authentication.principal as CustomUserDetails
            val selectedSchoolId = authorizationService.validateSchoolAccess(
                session.getAttribute("selectedSchoolId") as? UUID
            )
            
            // Use secure validation
            val feeItem = authorizationService.validateAndGetFeeItem(feeItemId, selectedSchoolId)
            
            val allClasses = schoolClassRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
            val assignedClassFeeItems = classFeeItemRepository.findByFeeItemIdAndIsActive(feeItemId, true)
            val assignedClassIds = java.util.HashSet(assignedClassFeeItems.map { it.schoolClass.id })
            
            val academicSessions = academicSessionRepository.findBySchoolIdAndIsActiveOrderByYearDesc(selectedSchoolId, true)
            val currentSession = academicSessions.find { it.isCurrentSession } ?: academicSessions.firstOrNull()
            
            val terms = currentSession?.let { 
                termRepository.findByAcademicSessionIdAndIsActiveOrderByStartDate(it.id!!, true)
            } ?: emptyList()
            
            model.addAttribute("user", customUser.user)
            model.addAttribute("feeItem", feeItem)
            model.addAttribute("allClasses", allClasses)
            model.addAttribute("assignedClassFeeItems", assignedClassFeeItems)
            model.addAttribute("assignedClassIds", assignedClassIds)
            model.addAttribute("academicSessions", academicSessions)
            model.addAttribute("terms", terms)
            
            return "admin/financial/class-assignment-modal-basic"
        } catch (e: Exception) {
            model.addAttribute("error", "Error loading class assignment modal: ${e.message}")
            return "fragments/error :: error-message"
        }
    }

    @GetMapping("/fee-items/{feeItemId}/classes/list")
    fun getAssignedClassesList(@PathVariable feeItemId: UUID, model: Model, session: HttpSession): String {
        val selectedSchoolId = authorizationService.validateSchoolAccess(
            session.getAttribute("selectedSchoolId") as? UUID
        )
        val feeItem = authorizationService.validateAndGetFeeItem(feeItemId, selectedSchoolId)
        val assignedClassFeeItems = classFeeItemRepository.findByFeeItemIdAndIsActive(feeItemId, true)
        
        model.addAttribute("feeItem", feeItem)
        model.addAttribute("assignedClassFeeItems", assignedClassFeeItems)
        
        return "admin/financial/class-assignment-modal-basic :: assignedClassesList"
    }

    @PostMapping("/fee-items/{feeItemId}/classes/assign")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
    fun assignClassToFeeItem(
        @PathVariable feeItemId: UUID,
        @ModelAttribute assignmentDto: ClassFeeAssignmentDto,
        session: HttpSession,
        model: Model
    ): String {
        val selectedSchoolId = authorizationService.validateSchoolAccess(
            session.getAttribute("selectedSchoolId") as? UUID
        )

        try {
            // Use secure validation
            val feeItem = authorizationService.validateAndGetFeeItem(feeItemId, selectedSchoolId)
            
            // Validate that FeeItem has session info
            val academicSession = feeItem.academicSession
            if (academicSession == null) {
                 model.addAttribute("error", "Fee Item does not have an academic session defined.")
                 return "fragments/error :: error-message"
            }
            
            val term = feeItem.term
            if (feeItem.recurrenceType == RecurrenceType.TERMLY && term == null) {
                 model.addAttribute("error", "Fee Item is termly but does not have a term defined.")
                 return "fragments/error :: error-message"
            }

            val classIds = assignmentDto.classIds
            if (classIds.isNullOrEmpty()) {
                model.addAttribute("error", "Please select at least one class.")
                return "fragments/error :: error-message"
            }

            var assignedCount = 0
            val skippedClasses = mutableListOf<String>()
            val errorClasses = mutableListOf<String>()
            
            for (classIdStr in classIds) {
                var className = "ID: $classIdStr"
                try {
                    val classIdUUID = UUID.fromString(classIdStr)
                    val schoolClass = authorizationService.validateAndGetSchoolClass(classIdUUID, selectedSchoolId)
                    className = schoolClass.className

                    // Check for ANY existing record (active or inactive)
                    val existingOpt = classFeeItemRepository.findBySchoolClassIdAndFeeItemIdAndAcademicSessionIdAndTermId(
                        classIdUUID, feeItemId, academicSession.id!!, term
                    )
                    
                    if (existingOpt.isPresent) {
                        val existing = existingOpt.get()
                        if (existing.isActive) {
                            skippedClasses.add(className)
                        } else {
                            // Reactivate
                            existing.isActive = true
                            existing.customAmount = assignmentDto.customAmount
                            classFeeItemRepository.save(existing)
                            assignedCount++
                        }
                    } else {
                        val classFeeItem = ClassFeeItem(
                            schoolClass = schoolClass,
                            feeItem = feeItem
                        ).apply {
                            this.schoolId = selectedSchoolId
                            this.academicSession = academicSession
                            this.academicYear = academicSession.sessionName ?: ""
                            this.termId = if (feeItem.recurrenceType == RecurrenceType.TERMLY) term else null
                            this.customAmount = assignmentDto.customAmount
                            this.isActive = true
                        }
                        
                        classFeeItemRepository.save(classFeeItem)
                        assignedCount++
                    }
                } catch (e: Exception) {
                    errorClasses.add("$className (${e.message})")
                    e.printStackTrace() // Log to console for debugging
                }
            }
            
            if (assignedCount > 0) {
                var msg = "$assignedCount classes assigned successfully."
                if (skippedClasses.isNotEmpty()) msg += " Skipped ${skippedClasses.size} duplicates."
                if (errorClasses.isNotEmpty()) msg += " Errors: ${errorClasses.size}."
                model.addAttribute("message", msg)
                
                // Refresh list data for OOB update
                val assignedClassFeeItems = classFeeItemRepository.findByFeeItemIdAndIsActive(feeItemId, true)
                model.addAttribute("assignedClassFeeItems", assignedClassFeeItems)
                model.addAttribute("feeItem", feeItem)
                
                // Also refresh the main fee items list
                val feeItems = feeItemRepository.findBySchoolIdAndFilters(selectedSchoolId, true, null, null)
                model.addAttribute("feeItems", feeItems)
                
                return "admin/financial/fragments/class-assignment-response"
            } else {
                if (errorClasses.isNotEmpty()) {
                    model.addAttribute("error", "Failed to assign classes. Errors: ${errorClasses.joinToString("; ")}")
                    return "fragments/error :: error-message"
                } else if (skippedClasses.isNotEmpty()) {
                    model.addAttribute("message", "No new classes assigned. Skipped duplicates: ${skippedClasses.joinToString(", ")}")
                    return "fragments/success :: success-message"
                } else {
                     model.addAttribute("error", "No classes processed.")
                     return "fragments/error :: error-message"
                }
            }

        } catch (e: Exception) {
            model.addAttribute("error", "Error assigning classes: ${e.message}")
            return "fragments/error :: error-message"
        }
    }

    @PostMapping("/fee-items/class-assignments/{id}/toggle-lock")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
    fun toggleClassFeeLock(
        @PathVariable id: UUID,
        @RequestParam locked: Boolean,
        session: HttpSession,
        model: Model
    ): String {
        val selectedSchoolId = authorizationService.validateSchoolAccess(
            session.getAttribute("selectedSchoolId") as? UUID
        )
        
        try {
            val classFeeItem = classFeeItemRepository.findById(id).orElseThrow { 
                RuntimeException("Assignment not found") 
            }
            
            if (classFeeItem.schoolClass.schoolId != selectedSchoolId) {
                model.addAttribute("error", "Unauthorized access")
                return "fragments/error :: error-message"
            }
            
            classFeeItem.isLocked = locked
            classFeeItemRepository.save(classFeeItem)
            
            // Refresh list data
            val assignedClassFeeItems = classFeeItemRepository.findByFeeItemIdAndIsActive(classFeeItem.feeItem.id!!, true)
            model.addAttribute("assignedClassFeeItems", assignedClassFeeItems)
            model.addAttribute("feeItem", classFeeItem.feeItem)
            
            // Also refresh the main fee items list
            val feeItems = feeItemRepository.findBySchoolIdAndFilters(selectedSchoolId, true, null, null)
            model.addAttribute("feeItems", feeItems)
            
            return "admin/financial/fragments/class-assignment-response"
        } catch (e: Exception) {
            model.addAttribute("error", "Error updating lock status: ${e.message}")
            return "fragments/error :: error-message"
        }
    }

    @PostMapping("/fee-items/class-assignments/{id}/remove")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
    fun removeFeeItemAssignment(
        @PathVariable id: UUID,
        session: HttpSession,
        model: Model
    ): String {
        val selectedSchoolId = authorizationService.validateSchoolAccess(
            session.getAttribute("selectedSchoolId") as? UUID
        )
            
        try {
            val classFeeItem = classFeeItemRepository.findById(id).orElseThrow { 
                RuntimeException("Assignment not found") 
            }
            
            // Validate school access
            if (classFeeItem.schoolClass.schoolId != selectedSchoolId) {
                model.addAttribute("error", "Unauthorized access to class assignment")
                return "fragments/error :: error-message"
            }
            
            val feeItem = classFeeItem.feeItem
            
            classFeeItem.isActive = false
            classFeeItemRepository.save(classFeeItem)
            
            model.addAttribute("message", "Assignment removed successfully!")
            
            // Refresh list data for OOB update
            val assignedClassFeeItems = classFeeItemRepository.findByFeeItemIdAndIsActive(feeItem.id!!, true)
            model.addAttribute("assignedClassFeeItems", assignedClassFeeItems)
            model.addAttribute("feeItem", feeItem)
            
            // Also refresh the main fee items list
            val feeItems = feeItemRepository.findBySchoolIdAndFilters(selectedSchoolId, true, null, null)
            model.addAttribute("feeItems", feeItems)
            
            return "admin/financial/fragments/class-assignment-response"
        } catch (e: Exception) {
            model.addAttribute("error", "Error removing assignment: ${e.message}")
            return "fragments/error :: error-message"
        }
    }

//    @PostMapping("/migration/update-class-fee-items")
//    @ResponseBody
//    fun migrateClassFeeItems(session: HttpSession): String {
//        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
//            ?: return "Error: No school selected"
//
//        val currentSession = academicSessionRepository.findBySchoolIdAndIsCurrentSessionAndIsActive(selectedSchoolId, true, true)
//            ?: return "Error: No current session found for this school"
//        
//        val currentTerm = termRepository.findBySchoolIdAndIsCurrentTermAndIsActive(selectedSchoolId, true, true).orElse(null)
//
//        val classFeeItems = classFeeItemRepository.findBySchoolIdAndIsActiveOrderBySchoolClassAscFeeItemAsc(selectedSchoolId, true)
//        var updatedCount = 0
//
//        classFeeItems.forEach { cfi ->
//            var changed = false
//            
//            if (cfi.academicSession == null) {
//                cfi.academicSession = currentSession
//                changed = true
//            }
//
//            if (cfi.termId == null && currentTerm != null) {
//                cfi.termId = currentTerm
//                changed = true
//            }
//
//            if (changed) {
//                classFeeItemRepository.save(cfi)
//                updatedCount++
//            }
//        }
//
//        return "Successfully updated $updatedCount ClassFeeItems with current session and term."
//    }

//    @PostMapping("/migration/update-settlements")
//    @ResponseBody
//    fun migrateSettlements(session: HttpSession): String {
//        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
//            ?: return "Error: No school selected"
//
//        val currentSession = academicSessionRepository.findBySchoolIdAndIsCurrentSessionAndIsActive(selectedSchoolId, true, true)
//            ?: return "Error: No current session found for this school"
//        
//        val currentTerm = termRepository.findBySchoolIdAndIsCurrentTermAndIsActive(selectedSchoolId, true, true).orElse(null)
//
//        val settlements = settlementRepository.findAll().filter { it.schoolId == selectedSchoolId }
//        var updatedCount = 0
//
//        settlements.forEach { settlement ->
//            var changed = false
//            
//            if (settlement.academicSession == null) {
//                settlement.academicSession = currentSession
//                changed = true
//            }
//
//            if (settlement.term == null && currentTerm != null) {
//                settlement.term = currentTerm
//                changed = true
//            }
//
//            if (settlement.settlementType == null) {
//                settlement.settlementType = com.haneef._school.entity.SettlementType.AUTO
//                changed = true
//            }
//
//            if (changed) {
//                settlementRepository.save(settlement)
//                updatedCount++
//            }
//        }
//
//        return "Successfully updated $updatedCount Settlements with current session and term."
//    }

    // Optional Fees Management
    @GetMapping("/optional-fees")
    fun optionalFeesManagement(
        model: Model,
        authentication: Authentication,
        session: HttpSession,
        @RequestParam(required = false) academicSessionId: UUID?,
        @RequestParam(required = false) termId: UUID?,
        @RequestParam(required = false) search: String?
    ): String {
        logger.info("=== OPTIONAL FEES MANAGEMENT REQUEST ===")
        
        val customUser = authentication.principal as CustomUserDetails
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
        
        logger.info("User: ${customUser.username}, School ID: $selectedSchoolId")
        
        if (selectedSchoolId == null) {
            logger.warn("No selected school ID, redirecting to school selection")
            return "redirect:/select-school"
        }

        try {
            // Get school information
            val school = schoolRepository.findById(selectedSchoolId).orElseThrow { 
                RuntimeException("School not found") 
            }
            logger.info("School found: ${school.name}")

            // Get academic sessions and terms for dropdowns
            val academicSessions = academicSessionRepository.findBySchoolIdAndIsActiveOrderByYearDesc(selectedSchoolId, true)
            val currentSession = academicSessions.find { it.isCurrentSession } ?: academicSessions.firstOrNull()
            val selectedSession = academicSessionId?.let { sessionId ->
                academicSessions.find { it.id == sessionId }
            } ?: currentSession
            
            logger.info("Academic sessions found: ${academicSessions.size}, Selected session: ${selectedSession?.sessionName}")

            // Get terms based on selected session
            val terms = if (selectedSession != null) {
                termRepository.findByAcademicSessionIdAndIsActiveOrderByStartDate(selectedSession.id!!, true)
            } else {
                emptyList()
            }
            val currentTerm = terms.find { it.isCurrentTerm } ?: terms.firstOrNull()
            val selectedTerm = termId?.let { tId ->
                terms.find { it.id == tId }
            } ?: currentTerm
            
            logger.info("Terms found: ${terms.size}, Selected term: ${selectedTerm?.termName}")

            // Get student optional fees instead of class fee items
            val studentOptionalFees = try {
                logger.info("Querying student optional fees with parameters:")
                logger.info("  selectedSchoolId: $selectedSchoolId")
                logger.info("  selectedSession?.id: ${selectedSession?.id}")
                logger.info("  selectedTerm?.id: ${selectedTerm?.id}")
                
                studentOptionalFeeRepository.findActiveOptionalFeesBySchool(
                    selectedSchoolId,
                    selectedSession?.id,
                    selectedTerm?.id
                )
                
            } catch (e: Exception) {
                logger.error("Error querying student optional fees", e)
                emptyList()
            }
            
            logger.info("Student optional fees found: ${studentOptionalFees.size}")

            // Filter by search if provided
            val filteredFees = if (!search.isNullOrBlank()) {
                try {
                    studentOptionalFees.filter { 
                        it.classFeeItem.feeItem.name.contains(search, ignoreCase = true) ||
                        it.student.user.firstName?.contains(search, ignoreCase = true) == true ||
                        it.student.user.lastName?.contains(search, ignoreCase = true) == true ||
                        "${it.student.user.firstName ?: ""} ${it.student.user.lastName ?: ""}".contains(search, ignoreCase = true)
                    }
                } catch (e: Exception) {
                    logger.error("Error filtering student optional fees", e)
                    studentOptionalFees
                }
            } else {
                studentOptionalFees
            }
            
            logger.info("Filtered student optional fees: ${filteredFees.size}")

            // Calculate statistics
            val totalOptionalFees = filteredFees.size
            val lockedCount = filteredFees.count { it.isLocked }
            val unlockedCount = totalOptionalFees - lockedCount
            
            logger.info("Stats - Total: $totalOptionalFees, Locked: $lockedCount, Unlocked: $unlockedCount")

            // Add basic attributes first
            model.addAttribute("user", customUser.user)
            model.addAttribute("school", school)
            model.addAttribute("search", search ?: "")
            
            // Add collections
            model.addAttribute("academicSessions", academicSessions)
            model.addAttribute("terms", terms)
            model.addAttribute("studentOptionalFees", filteredFees)
            
            // Add selected values
            model.addAttribute("selectedSessionId", selectedSession?.id)
            model.addAttribute("selectedTermId", selectedTerm?.id)
            model.addAttribute("selectedSession", selectedSession)
            model.addAttribute("selectedTerm", selectedTerm)
            
            // Add stats
            model.addAttribute("stats", mapOf(
                "total" to totalOptionalFees,
                "locked" to lockedCount,
                "unlocked" to unlockedCount
            ))

            logger.info("All model attributes added successfully")
            logger.info("Returning template: admin/financial/optional-fees")
            
            return "admin/financial/optional-fees"
            
        } catch (e: Exception) {
            logger.error("Error in optional fees management", e)
            model.addAttribute("error", "An error occurred while loading optional fees: ${e.message}")
            model.addAttribute("user", customUser.user)
            return "error/simple-error"
        }
    }

    @PostMapping("/optional-fees/{id}/toggle-lock")
    @ResponseBody
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
    fun toggleOptionalFeeLock(
        @PathVariable id: UUID,
        @RequestParam locked: Boolean,
        authentication: Authentication,
        session: HttpSession
    ): ResponseEntity<Map<String, Any>> {
        logger.info("=== TOGGLE OPTIONAL FEE LOCK REQUEST ===")
        logger.info("Fee ID: $id, Locked: $locked")
        
        val customUser = authentication.principal as CustomUserDetails
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
        
        if (selectedSchoolId == null) {
            return ResponseEntity.badRequest().body(mapOf(
                "success" to false,
                "message" to "No school selected"
            ))
        }

        try {
            val studentOptionalFee = studentOptionalFeeRepository.findById(id).orElse(null)
            if (studentOptionalFee == null) {
                return ResponseEntity.badRequest().body(mapOf(
                    "success" to false,
                    "message" to "Student optional fee not found"
                ))
            }

            // Verify the fee belongs to the selected school
            if (studentOptionalFee.classFeeItem.schoolId != selectedSchoolId) {
                return ResponseEntity.badRequest().body(mapOf(
                    "success" to false,
                    "message" to "Unauthorized access"
                ))
            }

            studentOptionalFee.isLocked = locked
            studentOptionalFeeRepository.save(studentOptionalFee)
            
            val action = if (locked) "locked" else "unlocked"
            logger.info("Successfully $action optional fee for student ${studentOptionalFee.student.user.firstName} ${studentOptionalFee.student.user.lastName}")
            
            return ResponseEntity.ok(mapOf(
                "success" to true,
                "message" to "Fee $action successfully",
                "isLocked" to locked
            ))
            
        } catch (e: Exception) {
            logger.error("Error toggling optional fee lock", e)
            return ResponseEntity.badRequest().body(mapOf(
                "success" to false,
                "message" to "Error updating fee: ${e.message}"
            ))
        }
    }

    @PostMapping("/apply-optional-fee")
    @ResponseBody
    fun applyOptionalFeeToStudent(
        @RequestParam studentId: UUID,
        @RequestParam classFeeItemId: UUID,
        @RequestParam(required = false) customAmount: BigDecimal?,
        @RequestParam(required = false) notes: String?,
        authentication: Authentication,
        session: HttpSession
    ): ResponseEntity<Map<String, Any>> {
        logger.info("=== APPLY OPTIONAL FEE TO STUDENT REQUEST ===")
        logger.info("Student ID: $studentId, ClassFeeItem ID: $classFeeItemId, Custom Amount: $customAmount")
        
        val customUser = authentication.principal as CustomUserDetails
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
        
        if (selectedSchoolId == null) {
            return ResponseEntity.badRequest().body(mapOf(
                "success" to false,
                "message" to "No school selected"
            ))
        }

        try {
            // Validate student belongs to the school
            val student = studentRepository.findById(studentId).orElse(null)
            if (student == null || student.schoolId != selectedSchoolId) {
                return ResponseEntity.badRequest().body(mapOf(
                    "success" to false,
                    "message" to "Student not found or doesn't belong to this school"
                ))
            }

            // Validate class fee item belongs to the school and is optional
            val classFeeItem = classFeeItemRepository.findById(classFeeItemId).orElse(null)
            if (classFeeItem == null || classFeeItem.schoolId != selectedSchoolId) {
                return ResponseEntity.badRequest().body(mapOf(
                    "success" to false,
                    "message" to "Fee item not found or doesn't belong to this school"
                ))
            }

            if (classFeeItem.feeItem.isMandatory) {
                return ResponseEntity.badRequest().body(mapOf(
                    "success" to false,
                    "message" to "Cannot apply mandatory fees individually. Use class assignment instead."
                ))
            }

            // Check if student already has this optional fee
            val existingFee = studentOptionalFeeRepository.findByStudentIdAndClassFeeItemId(studentId, classFeeItemId)
            if (existingFee != null && existingFee.isActive) {
                return ResponseEntity.badRequest().body(mapOf(
                    "success" to false,
                    "message" to "Student already has this optional fee applied"
                ))
            }

            // Get current academic session and term as fallback
            val currentAcademicSession = academicSessionRepository.findBySchoolIdAndIsActiveOrderByYearDesc(selectedSchoolId, true)
                .find { it.isCurrentSession }
            val currentTerm = if (currentAcademicSession != null) {
                termRepository.findByAcademicSessionIdAndIsActiveOrderByStartDate(currentAcademicSession.id!!, true)
                    .find { it.isCurrentTerm }
            } else null

            // Use session/term from ClassFeeItem if available (primary source), otherwise fallback to current
            val targetSession = classFeeItem.academicSession ?: currentAcademicSession
            val targetTerm = classFeeItem.termId ?: currentTerm
            

            // Create or reactivate the student optional fee
            val studentOptionalFee = if (existingFee != null) {
                // Reactivate existing record
                existingFee.isActive = true
                existingFee.isLocked = true // Locked by default for admin-applied fees
                existingFee.optedInBy = "ADMIN:${customUser.user.id}"
                existingFee.optedInAt = java.time.LocalDateTime.now()
                existingFee.academicSession = targetSession
                existingFee.term = targetTerm
                existingFee.customAmount = customAmount
                existingFee.notes = notes
                existingFee
            } else {
                // Create new record
                val newFee = com.haneef._school.entity.StudentOptionalFee(
                    student = student,
                    classFeeItem = classFeeItem,
                    optedInBy = "ADMIN:${customUser.user.id}"
                )
                newFee.schoolId = selectedSchoolId
                newFee.academicSession = targetSession
                newFee.term = targetTerm
                newFee.isActive = true
                newFee.isLocked = true // Locked by default for admin-applied fees
                newFee.customAmount = customAmount
                newFee.notes = notes
                newFee
            }

            studentOptionalFeeRepository.save(studentOptionalFee)
            
            logger.info("Successfully applied optional fee to student ${student.user.firstName} ${student.user.lastName}")
            
            return ResponseEntity.ok(mapOf<String, Any>(
                "success" to true,
                "message" to "Optional fee applied successfully to ${student.user.firstName} ${student.user.lastName}",
                "studentOptionalFeeId" to studentOptionalFee.id!!
            ))
            
        } catch (e: Exception) {
            logger.error("Error applying optional fee to student", e)
            return ResponseEntity.badRequest().body(mapOf(
                "success" to false,
                "message" to "Error applying fee: ${e.message}"
            ))
        }
    }

    @GetMapping("/api/students")
    @ResponseBody
    fun getStudentsForSchool(
        authentication: Authentication,
        session: HttpSession
    ): ResponseEntity<List<Map<String, Any>>> {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
        
        if (selectedSchoolId == null) {
            return ResponseEntity.badRequest().body(emptyList())
        }

        try {
            val students = studentRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
            val studentData = students.map { student ->
                mapOf<String, Any>(
                    "id" to student.id!!,
                    "name" to "${student.user.firstName} ${student.user.lastName}",
                    "admissionNumber" to (student.admissionNumber ?: "N/A")
                )
            }
            
            return ResponseEntity.ok(studentData)
        } catch (e: Exception) {
            logger.error("Error loading students", e)
            return ResponseEntity.badRequest().body(emptyList())
        }
    }

    @GetMapping("/api/optional-fee-items")
    @ResponseBody
    fun getOptionalFeeItemsForSchool(
        authentication: Authentication,
        session: HttpSession
    ): ResponseEntity<List<Map<String, Any>>> {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
        
        if (selectedSchoolId == null) {
            return ResponseEntity.badRequest().body(emptyList())
        }

        try {
            // Get current academic session
            val currentSession = academicSessionRepository.findBySchoolIdAndIsActiveOrderByYearDesc(selectedSchoolId, true)
                .find { it.isCurrentSession }
            
            if (currentSession == null) {
                return ResponseEntity.ok(emptyList())
            }

            // Get optional fee items for current session using the existing method
            val classFeeItems = classFeeItemRepository.findOptionalFees(
                selectedSchoolId, 
                currentSession.id!!, 
                null // Get all terms
            )
            
            val feeItemData = classFeeItems.map { classFeeItem ->
                mapOf<String, Any>(
                    "id" to classFeeItem.id!!,
                    "name" to classFeeItem.feeItem.name,
                    "amount" to classFeeItem.effectiveAmount,
                    "className" to classFeeItem.schoolClass.className,
                    "description" to (classFeeItem.feeItem.description ?: "")
                )
            }
            
            return ResponseEntity.ok(feeItemData)
        } catch (e: Exception) {
            logger.error("Error loading optional fee items", e)
            return ResponseEntity.badRequest().body(emptyList())
        }
    }

    @GetMapping("/api/student-fees/{studentId}")
    @ResponseBody
    fun getOptionalFeesForStudent(
        @PathVariable studentId: UUID,
        authentication: Authentication,
        session: HttpSession
    ): ResponseEntity<List<Map<String, Any>>> {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
        
        if (selectedSchoolId == null) {
            return ResponseEntity.badRequest().body(emptyList())
        }

        try {
            // Validate student belongs to school
            val student = studentRepository.findById(studentId).orElse(null)
            if (student == null || student.schoolId != selectedSchoolId) {
                return ResponseEntity.badRequest().body(emptyList())
            }

            // Get current academic session
            val currentSession = academicSessionRepository.findBySchoolIdAndIsActiveOrderByYearDesc(selectedSchoolId, true)
                .find { it.isCurrentSession }
            
            if (currentSession == null) {
                return ResponseEntity.ok(emptyList())
            }

            // Get student's class enrollments
            val classEnrollments = student.classEnrollments.filter { it.isActive }
            val classIds = classEnrollments.map { it.schoolClass.id!! }

            if (classIds.isEmpty()) {
                return ResponseEntity.ok(emptyList())
            }

            // Get optional fees for student's classes
            val classFeeItems = classFeeItemRepository.findOptionalFees(
                selectedSchoolId,
                currentSession.id!!,
                null
            ).filter { it.schoolClass.id in classIds }

            
            val feeItemData = classFeeItems.map { classFeeItem ->
                mapOf<String, Any>(
                    "id" to classFeeItem.id!!,
                    "name" to classFeeItem.feeItem.name,
                    "amount" to classFeeItem.effectiveAmount,
                    "className" to classFeeItem.schoolClass.className,
                    "description" to (classFeeItem.feeItem.description ?: ""),
                )
            }

            return ResponseEntity.ok(feeItemData)
        } catch (e: Exception) {
            logger.error("Error loading student fees", e)
            return ResponseEntity.badRequest().body(emptyList())
        }
    }

//    @GetMapping("/optional-fees-test")
//    fun optionalFeesTest(model: Model, authentication: Authentication): String {
//        logger.info("=== OPTIONAL FEES TEST REQUEST ===")
//        
//        val customUser = authentication.principal as CustomUserDetails
//        
//        model.addAttribute("user", customUser.user)
//        model.addAttribute("message", "This is a test page to verify the endpoint works")
//        
//        return "error/simple-error"
//    }

    // Payments Management
    @GetMapping("/payments")
    fun paymentsHome(
        model: Model, 
        authentication: Authentication, 
        session: HttpSession,
        @RequestParam(defaultValue = "parents") viewBy: String,
        @RequestParam(defaultValue = "desc") sortOrder: String,
        @RequestParam(required = false) search: String?,
        @RequestParam(required = false) academicSessionId: UUID?,
        @RequestParam(required = false) termId: UUID?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestHeader(value = "X-Requested-With", required = false) requestedWith: String?
    ): String {
        val customUser = authentication.principal as CustomUserDetails
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/select-school"

        // Get school information
        val school = schoolRepository.findById(selectedSchoolId).orElseThrow { 
            RuntimeException("School not found") 
        }

        // Get academic sessions and terms for dropdowns
        val academicSessions = academicSessionRepository.findBySchoolIdAndIsActiveOrderByYearDesc(selectedSchoolId, true)
        val currentSession = academicSessions.find { it.isCurrentSession } ?: academicSessions.firstOrNull()
        val selectedSession = academicSessionId?.let { sessionId ->
            academicSessions.find { it.id == sessionId }
        } ?: currentSession
        
        // Get terms based on selected session
        val terms = if (selectedSession != null) {
            termRepository.findByAcademicSessionIdAndIsActiveOrderByStartDate(selectedSession.id!!, true)
        } else {
            termRepository.findBySchoolIdAndIsActiveOrderByStartDate(selectedSchoolId, true)
        }
        val currentTerm = terms.find { it.isCurrentTerm } ?: terms.firstOrNull()
        val selectedTerm = termId?.let { tId ->
            terms.find { it.id == tId }
        } ?: currentTerm

        // Get payment statistics
        val paymentStats = getPaymentStatistics(selectedSchoolId, selectedSession?.id, selectedTerm?.id)
        
        // Get payment data based on view type with pagination
        val allPaymentData = when (viewBy) {
            "students" -> getStudentPaymentData(selectedSchoolId, sortOrder, search, selectedSession?.id, selectedTerm?.id)
            "dates" -> getDatePaymentData(selectedSchoolId, sortOrder, search, selectedSession?.id, selectedTerm?.id, startDate, endDate)
            else -> getParentPaymentData(selectedSchoolId, sortOrder, search, selectedSession?.id, selectedTerm?.id)
        }
        
        // Apply pagination
        val totalItems = allPaymentData.size
        val totalPages = (totalItems + size - 1) / size
        val startIndex = page * size
        val endIndex = minOf(startIndex + size, totalItems)
        val paginatedData = if (startIndex < totalItems) allPaymentData.subList(startIndex, endIndex) else emptyList()

        model.addAttribute("user", customUser.user)
        model.addAttribute("school", school)
        model.addAttribute("paymentStats", paymentStats)
        model.addAttribute("paymentData", paginatedData)
        model.addAttribute("totalItems", totalItems)
        model.addAttribute("totalPages", totalPages)
        model.addAttribute("currentPage", page)
        model.addAttribute("pageSize", size)
        model.addAttribute("hasNext", page < totalPages - 1)
        model.addAttribute("hasPrevious", page > 0)
        model.addAttribute("viewBy", viewBy)
        model.addAttribute("sortOrder", sortOrder)
        model.addAttribute("search", search)
        model.addAttribute("startDate", startDate)
        model.addAttribute("endDate", endDate)
        model.addAttribute("academicSessions", academicSessions)
        model.addAttribute("terms", terms)
        model.addAttribute("selectedSessionId", selectedSession?.id)
        model.addAttribute("selectedTermId", selectedTerm?.id)
        model.addAttribute("selectedSession", selectedSession)
        model.addAttribute("selectedTerm", selectedTerm)

        return if (requestedWith == "XMLHttpRequest") {
            "admin/financial/payments :: paymentList"
        } else {
            "admin/financial/payments"
        }
    }

    @GetMapping("/payments/api/details/{parentId}")
    @ResponseBody
    fun getPaymentDetails(
        @PathVariable parentId: UUID,
        @RequestParam(required = false) sessionId: UUID?,
        @RequestParam(required = false) termId: UUID?,
        session: HttpSession
    ): Map<String, Any?> {
        return try {
            val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
                ?: return mapOf("error" to "No school selected")
            
            val parent = parentRepository.findById(parentId).orElse(null)
                ?: return mapOf("error" to "Parent not found")
            
            if (parent.schoolId != selectedSchoolId) {
                return mapOf("error" to "Unauthorized access to parent")
            }
            
            val wallet = parentWalletRepository.findByParentId(parentId)
            val allSettlements = wallet?.let { settlementRepository.findByWalletId(it.id!!) } ?: emptyList()
            
            // Filter settlements by session and term if provided
            val filteredSettlements = if (sessionId != null && termId != null) {
                allSettlements.filter { settlement ->
                    settlement.academicSession?.id == sessionId && settlement.term?.id == termId
                }
            } else if (sessionId != null) {
                allSettlements.filter { settlement ->
                    settlement.academicSession?.id == sessionId
                }
            } else {
                allSettlements
            }
            
            val totalPaid = filteredSettlements.sumOf { it.amount }
            val totalFees = calculateTotalFeesForParent(parent, sessionId, termId)
            val outstanding = totalFees - totalPaid
            
            // Get children details
            val children = parent.activeStudentRelationships
                .filter { it.student.isActive }
                .map { rel ->
                    val student = rel.student
                    val studentFees = calculateTotalFeesForStudent(student, sessionId, termId)
                    mapOf(
                        "studentId" to student.id,
                        "studentName" to "${student.user.firstName} ${student.user.lastName}",
                        "className" to (student.classEnrollments.find { it.isActive }?.schoolClass?.className ?: "N/A"),
                        "relationshipType" to rel.relationshipType,
                        "fees" to studentFees
                    )
                }
            
            mapOf(
                "parentName" to "${parent.user.firstName} ${parent.user.lastName}",
                "parentEmail" to parent.user.email,
                "parentPhone" to parent.user.phoneNumber,
                "totalFees" to totalFees,
                "totalPaid" to totalPaid,
                "outstanding" to outstanding,
                "paymentPercentage" to if (totalFees > BigDecimal.ZERO) {
                    (totalPaid.divide(totalFees, 4, java.math.RoundingMode.HALF_UP) * BigDecimal(100)).toDouble()
                } else 0.0,
                "children" to children,
                "settlements" to filteredSettlements.sortedByDescending { it.transactionDate }.map { settlement ->
                    mapOf(
                        "id" to settlement.id,
                        "reference" to settlement.reference,
                        "amount" to settlement.amount,
                        "currency" to settlement.currency,
                        "status" to settlement.status,
                        "paymentChannel" to settlement.paymentChannel,
                        "transactionDate" to settlement.transactionDate,
                        "academicSession" to settlement.academicSession?.sessionYear,
                        "term" to settlement.term?.termName,
                        "payerEmail" to settlement.payerEmail
                    )
                }
            )
        } catch (e: Exception) {
            mapOf("error" to "Failed to load payment details: ${e.message}")
        }
    }

    @GetMapping("/payments/api/settlement-details/{settlementId}")
    @ResponseBody
    fun getSettlementDetails(
        @PathVariable settlementId: UUID,
        session: HttpSession
    ): Map<String, Any?> {
        return try {
            val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
                ?: return mapOf("error" to "No school selected")
            
            val settlement = settlementRepository.findById(settlementId).orElse(null)
                ?: return mapOf("error" to "Settlement not found")
            
            if (settlement.schoolId != selectedSchoolId) {
                return mapOf("error" to "Unauthorized access to settlement")
            }

            val parent = settlement.wallet.parent
            
            mapOf(
                "id" to settlement.id,
                "reference" to settlement.reference,
                "amount" to settlement.amount,
                "currency" to settlement.currency,
                "status" to settlement.status,
                "paymentChannel" to settlement.paymentChannel,
                "transactionDate" to settlement.transactionDate,
                "academicSession" to settlement.academicSession?.sessionYear,
                "term" to settlement.term?.termName,
                "payerEmail" to settlement.payerEmail,
                "parentName" to "${parent.user.firstName} ${parent.user.lastName}",
                "parentEmail" to parent.user.email,
                "parentPhone" to parent.user.phoneNumber,
                "rawPayload" to settlement.rawPayload
            )
        } catch (e: Exception) {
            mapOf("error" to "Failed to load settlement details: ${e.message}")
        }
    }

    @GetMapping("/payments/api/student-details/{studentId}")
    @ResponseBody
    fun getStudentPaymentDetails(
        @PathVariable studentId: UUID,
        @RequestParam(required = false) sessionId: UUID?,
        @RequestParam(required = false) termId: UUID?,
        session: HttpSession
    ): Map<String, Any> {
        return try {
            val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
                ?: return mapOf("error" to "No school selected")
            
            val student = studentRepository.findById(studentId).orElse(null)
                ?: return mapOf("error" to "Student not found")
            
            if (student.schoolId != selectedSchoolId) {
                return mapOf("error" to "Unauthorized access to student")
            }
            
            // Get all parents for this student
            val parents = student.parentRelationships
                .filter { it.isActive }
                .map { it.parent }
                .distinctBy { it.id }
            
            // Calculate total allocated from all parents
            var totalWalletAllocated = BigDecimal.ZERO
            val studentInvoicedPaid = calculateInvoicedPaidForStudent(student, sessionId, termId)
            
            parents.forEach { parent ->
                val breakdown = financialService.getFeeBreakdown(parent, sessionId, termId)
                @Suppress("UNCHECKED_CAST")
                val feeBreakdown = breakdown["feeBreakdown"] as List<Map<String, Any>>
                val studentEntry = feeBreakdown.find { it["studentId"] == student.studentId }
                if (studentEntry != null) {
                    totalWalletAllocated = totalWalletAllocated.add(studentEntry["walletAllocated"] as? BigDecimal ?: BigDecimal.ZERO)
                }
            }
            
            val totalPaid = studentInvoicedPaid.add(totalWalletAllocated)
            val totalFees = calculateTotalFeesForStudent(student, sessionId, termId)
            val outstanding = totalFees - totalPaid
            
            // Get payment allocations for this student (sequential distribution)
            val allocations = paymentDistributionService.getStudentPaymentAllocations(studentId, sessionId, termId)
            
            // Get parent details
            val parentDetails = parents.map { parent ->
                mapOf(
                    "parentId" to parent.id,
                    "parentName" to "${parent.user.firstName} ${parent.user.lastName}",
                    "parentEmail" to parent.user.email,
                    "parentPhone" to parent.user.phoneNumber,
                    "distributionType" to parent.paymentDistributionType,
                    "relationshipType" to student.parentRelationships
                        .find { it.parent.id == parent.id && it.isActive }?.relationshipType
                )
            }
            
            mapOf(
                "studentName" to "${student.user.firstName} ${student.user.lastName}",
                "studentId" to student.studentId,
                "className" to (student.classEnrollments.find { it.isActive }?.schoolClass?.className ?: "N/A"),
                "totalFees" to totalFees,
                "totalPaid" to totalPaid,
                "outstanding" to outstanding,
                "paymentPercentage" to if (totalFees > BigDecimal.ZERO) {
                    (totalPaid.divide(totalFees, 4, java.math.RoundingMode.HALF_UP) * BigDecimal(100)).toDouble()
                } else 0.0,
                "parents" to parentDetails,
                "allocations" to allocations.map { allocation ->
                    mapOf(
                        "id" to allocation.id,
                        "settlementReference" to allocation.settlement.reference,
                        "allocatedAmount" to allocation.allocatedAmount,
                        "allocationOrder" to allocation.allocationOrder,
                        "allocationMethod" to allocation.allocationMethod,
                        "remainingBalanceBefore" to allocation.remainingBalanceBefore,
                        "remainingBalanceAfter" to allocation.remainingBalanceAfter,
                        "allocationDate" to allocation.allocationDate,
                        "academicSession" to allocation.settlement.academicSession?.sessionYear,
                        "term" to allocation.settlement.term?.termName,
                        "paymentChannel" to allocation.settlement.paymentChannel,
                        "status" to allocation.settlement.status,
                        "notes" to allocation.notes
                    )
                }
            )
        } catch (e: Exception) {
            mapOf("error" to "Failed to load student payment details: ${e.message}")
        }
    }

//    @GetMapping("/payments/api/parent-debug/{parentId}")
//    @ResponseBody
//    fun debugParentRelationships(@PathVariable parentId: UUID): Map<String, Any?> {
//        return try {
//            val parent = parentRepository.findById(parentId).orElse(null)
//            if (parent != null) {
//                val activeRelationships = parent.activeStudentRelationships
//                val uniqueStudents = activeRelationships
//                    .filter { it.student.isActive }
//                    .map { it.student }
//                    .distinctBy { it.id }
//                
//                mapOf(
//                    "parentName" to "${parent.user.firstName} ${parent.user.lastName}",
//                    "parentEmail" to parent.user.email,
//                    "totalRelationships" to parent.studentRelationships.size,
//                    "activeRelationships" to activeRelationships.size,
//                    "uniqueActiveStudents" to uniqueStudents.size,
//                    "note" to "Using activeStudentRelationships computed property",
//                    "relationshipDetails" to activeRelationships.map { rel ->
//                        mapOf(
//                            "studentName" to "${rel.student.user.firstName} ${rel.student.user.lastName}",
//                            "studentId" to rel.student.id,
//                            "relationshipType" to rel.relationshipType,
//                            "studentIsActive" to rel.student.isActive
//                        )
//                    },
//                    "uniqueStudentDetails" to uniqueStudents.map { student ->
//                        mapOf(
//                            "studentName" to "${student.user.firstName} ${student.user.lastName}",
//                            "studentId" to student.id
//                        )
//                    }
//                )
//            } else {
//                mapOf("error" to "Parent not found")
//            }
//        } catch (e: Exception) {
//            mapOf("error" to e.message)
//        }
//    }

//    @GetMapping("/payments/debug/parent/{parentId}")
//    @ResponseBody
//    fun debugParentRelationships2(@PathVariable parentId: UUID): Map<String, Any?> {
//        return try {
//            val parent = parentRepository.findById(parentId).orElse(null)
//            if (parent != null) {
//                val relationships = parent.studentRelationships
//                val activeRelationships = relationships.filter { it.isActive }
//                val uniqueStudents = activeRelationships.map { it.student }.distinctBy { it.id }
//                
//                mapOf(
//                    "parentName" to "${parent.user.firstName} ${parent.user.lastName}",
//                    "totalRelationships" to relationships.size,
//                    "activeRelationships" to activeRelationships.size,
//                    "uniqueActiveStudents" to uniqueStudents.size,
//                    "relationshipDetails" to activeRelationships.map { rel ->
//                        mapOf(
//                            "studentName" to "${rel.student.user.firstName} ${rel.student.user.lastName}",
//                            "studentId" to rel.student.id,
//                            "relationshipType" to rel.relationshipType,
//                            "isActive" to rel.isActive
//                        )
//                    },
//                    "uniqueStudentDetails" to uniqueStudents.map { student ->
//                        mapOf(
//                            "studentName" to "${student.user.firstName} ${student.user.lastName}",
//                            "studentId" to student.id
//                        )
//                    }
//                )
//            } else {
//                mapOf("error" to "Parent not found")
//            }
//        } catch (e: Exception) {
//            mapOf("error" to e.message)
//        }
//    }

//    @PostMapping("/payments/distribute/{settlementId}")
//    @ResponseBody
//    fun distributePayment(@PathVariable settlementId: UUID, session: HttpSession): Map<String, Any> {
//        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
//            ?: return mapOf("error" to "No school selected")
//            
//        return try {
//            val settlement = settlementRepository.findById(settlementId).orElse(null)
//                ?: return mapOf("error" to "Settlement not found")
//            
//            if (settlement.schoolId != selectedSchoolId) {
//                return mapOf("error" to "Unauthorized access to settlement")
//            }
//            
//            val allocations = paymentDistributionService.distributePaymentSequentially(settlement)
//            
//            mapOf(
//                "success" to true,
//                "message" to "Payment distributed successfully",
//                "allocationsCount" to allocations.size,
//                "totalAllocated" to allocations.sumOf { it.allocatedAmount },
//                "allocations" to allocations.map { allocation ->
//                    mapOf(
//                        "studentName" to "${allocation.student.user.firstName} ${allocation.student.user.lastName}",
//                        "allocatedAmount" to allocation.allocatedAmount,
//                        "allocationOrder" to allocation.allocationOrder,
//                        "remainingBalanceAfter" to allocation.remainingBalanceAfter
//                    )
//                }
//            )
//        } catch (e: Exception) {
//            mapOf("error" to "Failed to distribute payment: ${e.message}")
//        }
//    }

    @GetMapping("/payments/api/terms/{sessionId}")
    @ResponseBody
    fun getTermsForSession(@PathVariable sessionId: UUID): List<Map<String, Any?>> {
        return try {
            val terms = termRepository.findByAcademicSessionIdAndIsActiveOrderByStartDate(sessionId, true)
            terms.map { term ->
                mapOf(
                    "id" to term.id,
                    "termName" to term.termName,
                    "isCurrentTerm" to term.isCurrentTerm
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @GetMapping("/payments/breakdown/modal")
    fun getPaymentBreakdownModal(
        model: Model, 
        session: HttpSession,
        @RequestParam(required = false) academicSessionId: UUID?,
        @RequestParam(required = false) termId: UUID?
    ): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "fragments/error :: error-message"
            
        val feeStats = financialService.getSchoolFeeStats(selectedSchoolId, academicSessionId, termId)
        
        model.addAttribute("feeStats", feeStats)
        
        return "admin/financial/fragments/payment-breakdown-modal"
    }

    private fun getPaymentStatistics(schoolId: UUID, sessionId: UUID? = null, termId: UUID? = null): Map<String, Any> {
        val totalParents = parentRepository.countBySchoolIdAndIsActive(schoolId, true)
        val totalStudents = studentRepository.countBySchoolIdAndIsActive(schoolId, true)
        
        // Get settlements filtered by session and term if provided
        val allSettlements = if (sessionId != null && termId != null) {
            settlementRepository.findAll().filter { settlement ->
                settlement.schoolId == schoolId && 
                settlement.academicSession?.id == sessionId && 
                settlement.term?.id == termId
            }
        } else if (sessionId != null) {
            settlementRepository.findAll().filter { settlement ->
                settlement.schoolId == schoolId && 
                settlement.academicSession?.id == sessionId
            }
        } else {
            settlementRepository.findAll().filter { it.schoolId == schoolId }
        }
        
        val totalPayments = allSettlements.size
        val totalAmount = allSettlements.sumOf { it.amount }
        
        // Get parents with wallets who made payments in the selected period
        val parentsWithWallets = parentWalletRepository.findBySchoolId(schoolId)
        val parentsWithPayments = parentsWithWallets.filter { wallet ->
            allSettlements.any { it.wallet.id == wallet.id }
        }.size
        
        // Get expected fees
        val feeStats = financialService.getSchoolFeeStats(schoolId, sessionId, termId)
        val expectedTotal = feeStats["expectedTotal"] as BigDecimal
        
        return mapOf(
            "totalParents" to totalParents,
            "totalStudents" to totalStudents,
            "totalPayments" to totalPayments,
            "totalAmount" to totalAmount,
            "expectedTotal" to expectedTotal,
            "outstandingTotal" to expectedTotal.subtract(totalAmount),
            "parentsWithPayments" to parentsWithPayments,
            "paymentPercentage" to if (totalParents > 0) (parentsWithPayments * 100 / totalParents) else 0
        )
    }

    private fun getDatePaymentData(
        schoolId: UUID, 
        sortOrder: String, 
        search: String?, 
        sessionId: UUID? = null, 
        termId: UUID? = null,
        startDate: LocalDate? = null,
        endDate: LocalDate? = null
    ): List<Map<String, Any?>> {
        // Fetch all settlements for the school
        var settlements = settlementRepository.findAll().filter { it.schoolId == schoolId }

        // Filter by session/term
        if (sessionId != null) {
            settlements = settlements.filter { it.academicSession?.id == sessionId }
        }
        if (termId != null) {
            settlements = settlements.filter { it.term?.id == termId }
        }

        // Filter by date range
        if (startDate != null) {
            settlements = settlements.filter { !it.transactionDate.toLocalDate().isBefore(startDate) }
        }
        if (endDate != null) {
            settlements = settlements.filter { !it.transactionDate.toLocalDate().isAfter(endDate) }
        }

        // Filter by search (parent name or reference)
        if (!search.isNullOrBlank()) {
            val searchLower = search.lowercase()
            settlements = settlements.filter { settlement ->
                val parentName = "${settlement.wallet.parent.user.firstName} ${settlement.wallet.parent.user.lastName}".lowercase()
                parentName.contains(searchLower) || settlement.reference.lowercase().contains(searchLower)
            }
        }

        // Map to data
        val paymentData = settlements.map { settlement ->
            mapOf<String, Any?>(
                "id" to settlement.id!!,
                "date" to settlement.transactionDate,
                "reference" to settlement.reference,
                "parentName" to "${settlement.wallet.parent.user.firstName} ${settlement.wallet.parent.user.lastName}",
                "amount" to settlement.amount,
                "channel" to settlement.paymentChannel,
                "status" to settlement.status,
                "notes" to ""
                
            )
        }

        // Sort
        return when (sortOrder) {
            "asc" -> paymentData.sortedBy { it["date"] as? LocalDateTime }
            else -> paymentData.sortedByDescending { it["date"] as? LocalDateTime }
        }
    }

    private fun getParentPaymentData(schoolId: UUID, sortOrder: String, search: String?, sessionId: UUID? = null, termId: UUID? = null): List<Map<String, Any?>> {
        val allParents = parentRepository.findBySchoolIdAndIsActive(schoolId, true)
        val parents = if (search.isNullOrBlank()) {
            allParents
        } else {
            allParents.filter { parent ->
                val fullName = "${parent.user.firstName} ${parent.user.lastName}".lowercase()
                fullName.contains(search.lowercase())
            }
        }
        
        val paymentData = parents.map { parent ->
            val wallet = parentWalletRepository.findByParentId(parent.id!!)
            
            // Filter settlements by session and term if provided
            val settlements = wallet?.let { w ->
                val allSettlements = settlementRepository.findByWalletId(w.id!!)
                if (sessionId != null && termId != null) {
                    allSettlements.filter { settlement ->
                        settlement.academicSession?.id == sessionId && settlement.term?.id == termId
                    }
                } else if (sessionId != null) {
                    allSettlements.filter { settlement ->
                        settlement.academicSession?.id == sessionId
                    }
                } else {
                    allSettlements
                }
            } ?: emptyList()
            
            val totalPaid = settlements.sumOf { it.amount }
            val totalFees = calculateTotalFeesForParent(parent, sessionId, termId)
            val paymentPercentage = if (totalFees > BigDecimal.ZERO) {
                (totalPaid.divide(totalFees, 4, java.math.RoundingMode.HALF_UP) * BigDecimal(100)).toDouble()
            } else 0.0
            
            val lastPaymentDate: Any? = settlements.maxByOrNull { it.transactionDate }?.transactionDate
            
            mapOf<String, Any?>(
                "id" to parent.id!!,
                "name" to "${parent.user.firstName} ${parent.user.lastName}",
                "email" to (parent.user.email ?: "N/A"),
                "phone" to (parent.user.phoneNumber ?: "N/A"),
                "totalFees" to totalFees,
                "totalPaid" to totalPaid,
                "outstanding" to (totalFees - totalPaid),
                "paymentPercentage" to paymentPercentage,
                "paymentsCount" to settlements.size,
                "lastPaymentDate" to lastPaymentDate,
                "childrenCount" to parent.activeStudentRelationships
                    .filter { it.student.isActive }
                    .map { it.student.id }
                    .distinct()
                    .size
            )
        }
        
        return when (sortOrder) {
            "asc" -> paymentData.sortedBy { it["paymentPercentage"] as Double }
            else -> paymentData.sortedByDescending { it["paymentPercentage"] as Double }
        }
    }

    private fun getStudentPaymentData(schoolId: UUID, sortOrder: String, search: String?, sessionId: UUID? = null, termId: UUID? = null): List<Map<String, Any?>> {
        val allStudents = studentRepository.findBySchoolIdAndIsActive(schoolId, true)
        val students = if (search.isNullOrBlank()) {
            allStudents
        } else {
            allStudents.filter { student ->
                val fullName = "${student.user.firstName} ${student.user.lastName}".lowercase()
                val studentId = student.studentId.lowercase()
                fullName.contains(search.lowercase()) || studentId.contains(search.lowercase())
            }
        }
        
        // Cache for parent breakdowns to avoid redundant calculations
        val parentBreakdownCache = mutableMapOf<UUID, Map<String, Any>>()
        
        val paymentData = students.map { student ->
            val parents = student.parentRelationships.filter { it.isActive }.map { it.parent }.distinctBy { it.id }
            
            // Calculate total allocated from all parents
            var totalWalletAllocated = BigDecimal.ZERO
            val studentInvoicedPaid = calculateInvoicedPaidForStudent(student, sessionId, termId)
            
            parents.forEach { parent ->
                val breakdown = parentBreakdownCache.getOrPut(parent.id!!) {
                    financialService.getFeeBreakdown(parent, sessionId, termId)
                }
                
                @Suppress("UNCHECKED_CAST")
                val feeBreakdown = breakdown["feeBreakdown"] as List<Map<String, Any>>
                val studentEntry = feeBreakdown.find { it["studentId"] == student.studentId }
                if (studentEntry != null) {
                    totalWalletAllocated = totalWalletAllocated.add(studentEntry["walletAllocated"] as? BigDecimal ?: BigDecimal.ZERO)
                }
            }
            
            val totalPaid = studentInvoicedPaid.add(totalWalletAllocated)
            val totalFees = calculateTotalFeesForStudent(student, sessionId, termId)
            val paymentPercentage = if (totalFees > BigDecimal.ZERO) {
                (totalPaid.divide(totalFees, 4, java.math.RoundingMode.HALF_UP) * BigDecimal(100)).toDouble()
            } else 0.0
            
            // Get the last payment allocation for this student
            val allocations = paymentDistributionService.getStudentPaymentAllocations(student.id!!, sessionId, termId)
            val lastPaymentDate: Any? = allocations.maxByOrNull { it.allocationDate }?.allocationDate
            
            mapOf<String, Any?>(
                "id" to student.id!!,
                "name" to "${student.user.firstName} ${student.user.lastName}",
                "studentId" to student.studentId,
                "className" to (student.classEnrollments.find { it.isActive }?.schoolClass?.className ?: "N/A"),
                "totalFees" to totalFees,
                "totalPaid" to totalPaid,
                "outstanding" to (totalFees - totalPaid),
                "paymentPercentage" to paymentPercentage,
                "parentsCount" to parents.size,
                "lastPaymentDate" to lastPaymentDate,
                "allocationsCount" to allocations.size
            )
        }
        
        return when (sortOrder) {
            "asc" -> paymentData.sortedBy { it["paymentPercentage"] as Double }
            else -> paymentData.sortedByDescending { it["paymentPercentage"] as Double }
        }
    }

    private fun calculateTotalFeesForParent(parent: Parent, sessionId: UUID? = null, termId: UUID? = null): BigDecimal {
        // Use activeStudentRelationships property for filtered relationships
        return parent.activeStudentRelationships
            .map { it.student }
            .filter { it.isActive }  // Only include active students
            .distinctBy { it.id }  // Ensure we only count each student once
            .sumOf { calculateTotalFeesForStudent(it, sessionId, termId) }
    }

    private fun calculateInvoicedPaidForStudent(student: Student, sessionId: UUID? = null, termId: UUID? = null): BigDecimal {
        val selectedSession = sessionId?.let { academicSessionRepository.findById(it).orElse(null) }
            ?: academicSessionRepository.findBySchoolIdAndIsCurrentSessionAndIsActive(student.schoolId!!, true, true)
            ?: academicSessionRepository.findBySchoolIdAndIsActiveOrderByYearDesc(student.schoolId!!, true).firstOrNull()
            
        val selectedTerm = termId?.let { termRepository.findById(it).orElse(null) }
            ?: termRepository.findBySchoolIdAndIsCurrentTermAndIsActive(student.schoolId!!, true, true).orElse(null)
            
        if (selectedSession == null) return BigDecimal.ZERO
        
        var studentInvoicedPaid = BigDecimal.ZERO
        val invoices = invoiceRepository.findByStudentIdAndAcademicSessionIdAndTermAndIsActive(
            student.id!!,
            selectedSession.id!!,
            selectedTerm?.termName,
            true
        )
        invoices.forEach { invoice ->
            studentInvoicedPaid = studentInvoicedPaid.add(BigDecimal(invoice.amountPaid).divide(BigDecimal(100)))
        }
        return studentInvoicedPaid
    }

    private fun calculateTotalFeesForStudent(student: Student, sessionId: UUID? = null, termId: UUID? = null): BigDecimal {
        val selectedSession = sessionId?.let { academicSessionRepository.findById(it).orElse(null) }
            ?: academicSessionRepository.findBySchoolIdAndIsCurrentSessionAndIsActive(student.schoolId!!, true, true)
            ?: academicSessionRepository.findBySchoolIdAndIsActiveOrderByYearDesc(student.schoolId!!, true).firstOrNull()
            
        val selectedTerm = termId?.let { termRepository.findById(it).orElse(null) }
            ?: termRepository.findBySchoolIdAndIsCurrentTermAndIsActive(student.schoolId!!, true, true).orElse(null)
            
        if (selectedSession == null) return BigDecimal.ZERO

        val currentEnrollment = student.classEnrollments.find { it.isActive }
        return if (currentEnrollment != null) {
            val classFeeItems = classFeeItemRepository.findBySchoolClassIdAndAcademicSessionIdAndTermIdFilters(
                currentEnrollment.schoolClass.id!!, 
                selectedSession.id!!, 
                selectedTerm?.id, 
                true
            )
            classFeeItems.sumOf { it.customAmount ?: it.feeItem.amount }
        } else {
            BigDecimal.ZERO
        }
    }

    @GetMapping("/payments/manual/modal")
    fun getManualSettlementModal(model: Model, authentication: Authentication, session: HttpSession): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/select-school"
        
        val parents = parentRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
            .filter { it.wallet != null }
            .sortedBy { it.user.fullName }
            
        val academicSessions = academicSessionRepository.findBySchoolIdAndIsActiveOrderByYearDesc(selectedSchoolId, true)
        val currentSession = academicSessions.find { it.isCurrentSession } ?: academicSessions.firstOrNull()
        
        val terms = currentSession?.let { 
            termRepository.findByAcademicSessionIdAndIsActiveOrderByStartDate(it.id!!, true)
        } ?: emptyList()
        val currentTerm = terms.find { it.isCurrentTerm } ?: terms.firstOrNull()

        model.addAttribute("parents", parents)
        model.addAttribute("academicSessions", academicSessions)
        model.addAttribute("terms", terms)
        model.addAttribute("selectedSessionId", currentSession?.id)
        model.addAttribute("selectedTermId", currentTerm?.id)
        
        return "admin/financial/manual-settlement-modal"
    }

    @PostMapping("/payments/manual/save")
    fun saveManualSettlement(
        @ModelAttribute settlementDto: ManualSettlementDto,
        session: HttpSession,
        model: Model
    ): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "fragments/error :: error-message"

        try {
            // Convert String IDs to UUIDs
            val parentIdUUID = try {
                UUID.fromString(settlementDto.parentId)
            } catch (e: IllegalArgumentException) {
                model.addAttribute("error", "Invalid parent ID format")
                return "fragments/error :: error-message"
            }
            
            val academicSessionIdUUID = try {
                UUID.fromString(settlementDto.academicSessionId)
            } catch (e: IllegalArgumentException) {
                model.addAttribute("error", "Invalid academic session ID format")
                return "fragments/error :: error-message"
            }
            
            val termIdUUID = settlementDto.termId?.let { 
                try {
                    UUID.fromString(it)
                } catch (e: IllegalArgumentException) {
                    model.addAttribute("error", "Invalid term ID format")
                    return "fragments/error :: error-message"
                }
            }
            
            // Validate parent belongs to school
            val parent = parentRepository.findById(parentIdUUID).orElseThrow()
            if (parent.schoolId != selectedSchoolId) {
                model.addAttribute("error", "Unauthorized access to parent")
                return "fragments/error :: error-message"
            }
            
            financialService.logManualSettlement(
                parentId = parentIdUUID,
                amount = settlementDto.amount ?: BigDecimal.ZERO,
                sessionId = academicSessionIdUUID,
                termId = termIdUUID,
                schoolId = selectedSchoolId,
                notes = settlementDto.notes
            )
            model.addAttribute("message", "Manual settlement logged successfully!")
            return "fragments/success :: success-message"
        } catch (e: Exception) {
            model.addAttribute("error", "Error logging settlement: ${e.message}")
            return "fragments/error :: error-message"
        }
    }
}
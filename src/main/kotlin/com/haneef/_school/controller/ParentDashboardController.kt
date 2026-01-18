package com.haneef._school.controller

import com.haneef._school.entity.InvoiceStatus
import com.haneef._school.entity.Student
import com.haneef._school.repository.SchoolRepository
import com.haneef._school.repository.ClassFeeItemRepository
import com.haneef._school.repository.InvoiceRepository
import com.haneef._school.repository.ParentRepository
import com.haneef._school.repository.AcademicSessionRepository
import com.haneef._school.repository.TermRepository
import com.haneef._school.service.CustomUserDetails
import com.haneef._school.service.CustomUserDetailsService
import com.haneef._school.service.FinancialService
import com.haneef._school.service.ParentWalletService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import com.haneef._school.service.PaystackService
import java.util.UUID

@Controller
@RequestMapping("/parent")
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'PARENT')")
class ParentDashboardController(
    private val userDetailsService: CustomUserDetailsService,
    private val parentRepository: ParentRepository,
    private val schoolRepository: SchoolRepository,
    private val financialService: FinancialService,
    private val parentWalletService: ParentWalletService,
    private val paystackService: PaystackService,
    private val authorizationService: com.haneef._school.service.AuthorizationService,
    private val studentOptionalFeeRepository: com.haneef._school.repository.StudentOptionalFeeRepository,
    private val classFeeItemRepository: ClassFeeItemRepository,
    private val studentRepository: com.haneef._school.repository.StudentRepository,
    private val academicSessionRepository: AcademicSessionRepository,
    private val termRepository: TermRepository,
    private val studentClassRepository: com.haneef._school.repository.StudentClassRepository,
    private val attendanceRepository: com.haneef._school.repository.AttendanceRepository,
    private val educationTrackRepository: com.haneef._school.repository.EducationTrackRepository,
    private val parentStudentRepository: com.haneef._school.repository.ParentStudentRepository,
    private val schoolClassRepository: com.haneef._school.repository.SchoolClassRepository
) {

    @GetMapping("/dashboard")
    @Transactional
    fun parentDashboard(model: Model, authentication: Authentication, request: HttpServletRequest, response: jakarta.servlet.http.HttpServletResponse): String {
        val userDetails = userDetailsService.loadUserByUsername(authentication.name)
        val customUser = userDetails as CustomUserDetails
        
        model.addAttribute("user", customUser.user)
        model.addAttribute("userRole", "Parent")
        model.addAttribute("dashboardType", "parent")
        
        val parents = parentRepository.findByUserIdWithWallet(customUser.user.id!!)
        val parent = parents.firstOrNull() ?: return "redirect:/login"
        
        // Validate parent access - ensure parent belongs to user
        if (parent.user.id != customUser.user.id) {
            throw org.springframework.security.access.AccessDeniedException("Unauthorized access to parent data")
        }
        
        // Get school information from session
        val selectedSchoolId = request.session.getAttribute("selectedSchoolId") as? UUID
        
        populateDashboardModel(model, parent, selectedSchoolId)
        
        // If it's an HTMX request, return only the fragment
        if (request.getHeader("HX-Request") != null) {
            // If we were polling and the account is now ready, trigger a full page refresh
            val wallet = parent.wallet
            if (wallet != null && wallet.accountNumber != null) {
                response.setHeader("HX-Refresh", "true")
            }
            return "dashboard/parent-dashboard :: fees-overview-section"
        }
        
        return "dashboard/parent-dashboard"
    }

    @GetMapping("/view-as/{parentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHOOL_ADMIN')")
    @Transactional
    fun viewParentDashboardAsAdmin(
        @org.springframework.web.bind.annotation.PathVariable parentId: UUID,
        model: Model,
        request: HttpServletRequest,
        authentication: Authentication
    ): String {
        val parent = parentRepository.findById(parentId).orElseThrow { IllegalArgumentException("Parent not found") }
        
        // Get school information from session
        val selectedSchoolId = request.session.getAttribute("selectedSchoolId") as? UUID
        
        // Add user info for the header (showing the admin is viewing)
        val userDetails = userDetailsService.loadUserByUsername(authentication.name)
        val customUser = userDetails as CustomUserDetails
        model.addAttribute("user", customUser.user)
        model.addAttribute("userRole", "Admin (Viewing as Parent)")
        model.addAttribute("dashboardType", "parent")
        model.addAttribute("isViewAs", true)
        model.addAttribute("viewingAsName", parent.user.fullName)

        populateDashboardModel(model, parent, selectedSchoolId)
        
        return "dashboard/parent-dashboard"
    }

    private fun populateDashboardModel(model: Model, parent: com.haneef._school.entity.Parent, selectedSchoolId: UUID?) {
        if (selectedSchoolId != null) {
            val school = schoolRepository.findById(selectedSchoolId).orElse(null)
            model.addAttribute("school", school)
        }
        
        // Ensure wallet is loaded (it's a OneToOne, so it might be lazy)
        model.addAttribute("parent", parent)
        model.addAttribute("wallet", parent.wallet)
        
        // Fetch available providers if wallet doesn't exist
        if (parent.wallet == null) {
            val providers = paystackService.getAvailableProviders()
            model.addAttribute("providers", providers)
        }
        
        val financialData = financialService.getFeeBreakdown(parent)
        
        var children = parent.activeStudentRelationships.map { it.student }
        
        // Sort children based on saved priority order
        if (!parent.paymentPriorityOrder.isNullOrEmpty()) {
            try {
                val orderIds = parent.paymentPriorityOrder!!.split(",").map { UUID.fromString(it.trim()) }
                val childMap = children.associateBy { it.id }
                
                val orderedChildren = orderIds.mapNotNull { childMap[it] }.toMutableList()
                val remainingChildren = children.filter { !orderIds.contains(it.id) }
                orderedChildren.addAll(remainingChildren)
                
                children = orderedChildren
            } catch (e: Exception) {
                // Fallback to default order if parsing fails
                e.printStackTrace()
            }
        }
        
        model.addAttribute("children", children)
        model.addAttribute("totalFees", financialData["totalFees"])
        model.addAttribute("totalSettled", financialData["totalSettled"])
        model.addAttribute("balance", financialData["balance"])
        model.addAttribute("feeBreakdown", financialData["feeBreakdown"])
        
        // Lock settings if any payment has been made
        val totalSettled = financialData["totalSettled"] as java.math.BigDecimal
        val paymentLocked = totalSettled > java.math.BigDecimal.ZERO
        model.addAttribute("paymentLocked", paymentLocked)
    }

    @PostMapping("/create-wallet")
    fun createWallet(
        @RequestParam(defaultValue = "wema-bank") preferredBank: String,
        model: Model, 
        authentication: Authentication,
        request: HttpServletRequest,
        redirectAttributes: org.springframework.web.servlet.mvc.support.RedirectAttributes
    ): String {
        val userDetails = userDetailsService.loadUserByUsername(authentication.name)
        val customUser = userDetails as CustomUserDetails
        
        val parents = parentRepository.findByUserIdWithWallet(customUser.user.id!!)
        val parent = parents.firstOrNull() ?: return "redirect:/login"
        
        // Validate parent access - ensure parent belongs to user
        if (parent.user.id != customUser.user.id) {
            throw org.springframework.security.access.AccessDeniedException("Unauthorized access to parent data")
        }
        
        val result = parentWalletService.createWalletForParent(parent, preferredBank)
        
        if (result.isSuccess) {
            redirectAttributes.addFlashAttribute("success", "Wallet created successfully! Your account number is being generated.")
        } else {
            redirectAttributes.addFlashAttribute("error", "Error creating wallet: ${result.exceptionOrNull()?.message}")
        }
        
        return "redirect:/parent/dashboard"
    }
    private val logger = org.slf4j.LoggerFactory.getLogger(ParentDashboardController::class.java)

    @PostMapping("/update-payment-settings")
    @Transactional
    fun updatePaymentSettings(
        @RequestParam distributionType: String,
        @RequestParam(value = "childPriority[]", required = false) childPriority: List<UUID>?,
        authentication: Authentication,
        model: Model,
        request: HttpServletRequest,
        response: jakarta.servlet.http.HttpServletResponse
    ): String {
        logger.info("Updating payment settings. Type: $distributionType, Priority: $childPriority")
        val userDetails = userDetailsService.loadUserByUsername(authentication.name)
        val customUser = userDetails as CustomUserDetails
        
        val parents = parentRepository.findByUserIdWithWallet(customUser.user.id!!)
        val parent = parents.firstOrNull() ?: return "redirect:/login"
        
        // Validate parent access - ensure parent belongs to user
        if (parent.user.id != customUser.user.id) {
            throw org.springframework.security.access.AccessDeniedException("Unauthorized access to parent data")
        }
        
        parent.paymentDistributionType = distributionType
        
        // Check if payments have started to prevent modification
        val financialData = financialService.getFeeBreakdown(parent)
        val totalSettled = financialData["totalSettled"] as java.math.BigDecimal
        
        if (totalSettled > java.math.BigDecimal.ZERO) {
             model.addAttribute("error", "Payment settings cannot be changed after payments have started.")
             parentDashboard(model, authentication, request, response)
             return "dashboard/parent-dashboard :: #payment-settings-container"
        }
        
        if (distributionType == "SEQUENTIAL") {
            if (childPriority != null && childPriority.isNotEmpty()) {
                parent.paymentPriorityOrder = childPriority.joinToString(",")
            }
        }
        
        parentRepository.save(parent)
        
        model.addAttribute("success", "Payment settings updated successfully")
        
        // If HTMX request, return only the settings fragment
        // We need to reload the dashboard data to ensure the model has 'parent' with updated settings
        parentDashboard(model, authentication, request, response)
        
        // Check if it's an HTMX request (simplified check, ideally check headers)
        return "dashboard/parent-dashboard :: #payment-settings-container" 
    }

    @PostMapping("/student/{studentId}/toggle-fee")
    @Transactional
    fun toggleFee(
        @org.springframework.web.bind.annotation.PathVariable studentId: UUID,
        @RequestParam feeItemId: UUID,
        @RequestParam optedIn: Boolean,
        model: Model,
        authentication: Authentication,
        request: HttpServletRequest,
        response: jakarta.servlet.http.HttpServletResponse
    ): String {
        val userDetails = userDetailsService.loadUserByUsername(authentication.name)
        val customUser = userDetails as CustomUserDetails
        
        val parents = parentRepository.findByUserIdWithWallet(customUser.user.id!!)
        val parent = parents.firstOrNull() ?: return "fragments/error :: error-message"
        
        val student = studentRepository.findById(studentId).orElseThrow()
        // Check if student belongs to parent
        if (!parent.activeStudentRelationships.any { it.student.id == studentId }) {
             return "fragments/error :: error-message"
        }
        
        val classFeeItem = classFeeItemRepository.findById(feeItemId).orElseThrow()
        
        // Security Check: Ensure fee item belongs to the student's school
        if (classFeeItem.schoolClass.schoolId != student.schoolId) {
             return "fragments/error :: error-message"
        }
        
        logger.info("Toggle Fee: student=$studentId, feeItem=$feeItemId, optedIn=$optedIn, classFeeItemLocked=${classFeeItem.isLocked}")

        // Get existing selection
        val selection = studentOptionalFeeRepository.findByStudentIdAndClassFeeItemId(studentId, feeItemId)
        logger.info("Current selection: $selection, isActive=${selection?.isActive}, selectionLocked=${selection?.isLocked}")
        
        // Get current academic session and term for the school
        val currentAcademicSession = academicSessionRepository.findBySchoolIdAndIsActiveOrderByYearDesc(student.schoolId!!, true)
            .find { it.isCurrentSession }
        val currentTerm = if (currentAcademicSession != null) {
            termRepository.findByAcademicSessionIdAndIsActiveOrderByStartDate(currentAcademicSession.id!!, true)
                .find { it.isCurrentTerm }
        } else null
        
        logger.info("Using current session: ${currentAcademicSession?.sessionName}, current term: ${currentTerm?.termName}")

        // Check if the fee item or selection is locked
        val isLocked = classFeeItem.isLocked || (selection?.isLocked == true)
        
        if (isLocked) {
            if (!optedIn) {
                model.addAttribute("error", "Cannot remove fee after it has been locked/processed.")
            } else {
                model.addAttribute("error", "Fee selection is locked.")
            }
        } else {
            if (optedIn) {
                // User wants to opt in to the fee
                if (selection == null) {
                    // Create new selection
                    val newSelection = com.haneef._school.entity.StudentOptionalFee(
                        student = student,
                        classFeeItem = classFeeItem,
                        optedInBy = customUser.user.id.toString()
                    )
                    newSelection.schoolId = student.schoolId
                    // Use current academic session and term instead of classFeeItem values
                    newSelection.academicSession = currentAcademicSession
                    newSelection.term = currentTerm
                    newSelection.isActive = true
                    
                    logger.info("Creating StudentOptionalFee with current session: ${currentAcademicSession?.sessionName}, current term: ${currentTerm?.termName}")
                    studentOptionalFeeRepository.save(newSelection)
                    logger.info("Created new optional fee selection for student $studentId, fee $feeItemId")
                } else {
                    // Reactivate existing selection and update term/session info
                    if (!selection.isActive) {
                        selection.isActive = true
                        selection.optedInBy = customUser.user.id.toString()
                        selection.optedInAt = java.time.LocalDateTime.now()
                        // Update to current academic session and term
                        selection.academicSession = currentAcademicSession
                        selection.term = currentTerm
                        
                        logger.info("Reactivating StudentOptionalFee with current session: ${currentAcademicSession?.sessionName}, current term: ${currentTerm?.termName}")
                        studentOptionalFeeRepository.save(selection)
                        logger.info("Reactivated optional fee selection for student $studentId, fee $feeItemId")
                    }
                }
            } else {
                // User wants to opt out of the fee
                if (selection != null && selection.isActive) {
                    selection.isActive = false
                    studentOptionalFeeRepository.save(selection)
                    logger.info("Deactivated optional fee selection for student $studentId, fee $feeItemId")
                }
            }
            studentOptionalFeeRepository.flush()
        }
        
        parentDashboard(model, authentication, request, response)
        return "dashboard/parent-dashboard :: fees-overview-section"
    }

    @GetMapping("/child/{studentId}")
    fun getStudentProfile(
        @org.springframework.web.bind.annotation.PathVariable studentId: UUID,
        model: Model,
        authentication: Authentication,
        request: HttpServletRequest
    ): String {
        val userDetails = userDetailsService.loadUserByUsername(authentication.name)
        val customUser = userDetails as CustomUserDetails
        
        val parents = parentRepository.findByUserIdWithWallet(customUser.user.id!!)
        val parent = parents.firstOrNull() ?: return "redirect:/login"
        
        // Validate parent access - ensure parent belongs to user
        if (parent.user.id != customUser.user.id) {
            throw org.springframework.security.access.AccessDeniedException("Unauthorized access to parent data")
        }
        
        // Validate student belongs to parent
        if (!parent.activeStudentRelationships.any { it.student.id == studentId }) {
            throw org.springframework.security.access.AccessDeniedException("Unauthorized access to student data")
        }
        
        val student = studentRepository.findById(studentId).orElseThrow()
        val schoolId = student.schoolId!!
        
        // Get school information
        val school = schoolRepository.findById(schoolId).orElse(null)
        
        // Get current enrollments
        val currentSession = academicSessionRepository.findBySchoolIdAndIsCurrentSessionAndIsActive(schoolId, true, true)
        val enrollments = if (currentSession != null) {
             studentClassRepository.findByStudentIdAndAcademicSessionIdAndIsActive(studentId, currentSession.id!!, true)
                .filter { it.schoolClass.schoolId == schoolId }
        } else {
            emptyList()
        }
        
        // Get attendance stats
        val attendanceRecords = attendanceRepository.findByStudentIdAndSchoolIdAndIsActive(studentId, schoolId, true)
        val presentCount = attendanceRecords.count { it.status == com.haneef._school.entity.AttendanceStatus.PRESENT }
        val absentCount = attendanceRecords.count { it.status == com.haneef._school.entity.AttendanceStatus.ABSENT }
        val lateCount = attendanceRecords.count { it.status == com.haneef._school.entity.AttendanceStatus.LATE }
        val totalAttendance = attendanceRecords.size
        val attendancePercentage = if (totalAttendance > 0) (presentCount.toDouble() / totalAttendance * 100).toInt() else 0
        
        // Get parents/guardians (for display)
        val parentRelationships = parentStudentRepository.findByStudentIdWithParentDetails(studentId)
        
        // Get all tracks
        val allTracks = educationTrackRepository.findBySchoolIdAndIsActive(schoolId, true)
        
        // Get all classes
        val allClasses = schoolClassRepository.findBySchoolIdAndIsActiveWithTrack(schoolId, true)
        val classesByTrackId = allClasses.filter { it.track != null }.groupBy { it.track!!.id }
        
        model.addAttribute("student", student)
        model.addAttribute("enrollments", enrollments)
        model.addAttribute("school", school)
        model.addAttribute("attendancePercentage", attendancePercentage)
        model.addAttribute("presentCount", presentCount)
        model.addAttribute("absentCount", absentCount)
        model.addAttribute("lateCount", lateCount)
        model.addAttribute("totalAttendance", totalAttendance)
        model.addAttribute("parentRelationships", parentRelationships)
        model.addAttribute("allTracks", allTracks)
        model.addAttribute("classesByTrackId", classesByTrackId)
        
        // Add user info for header
        model.addAttribute("user", customUser.user)
        model.addAttribute("userRole", "Parent")
        
        return "staff/student-profile" // Reuse the same template
    }
}
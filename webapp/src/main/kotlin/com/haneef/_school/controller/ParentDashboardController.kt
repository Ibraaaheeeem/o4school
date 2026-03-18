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
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.util.UUID

@Controller
@RequestMapping("/parent")
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'PARENT')")
class ParentDashboardController(
    private val userDetailsService: CustomUserDetailsService,
    private val parentRepository: ParentRepository,
    private val schoolRepository: SchoolRepository,
    private val financialService: FinancialService,
    private val paystackParentWalletService: com.haneef._school.service.PaystackParentWalletService,
    private val squadParentWalletService: com.haneef._school.service.SquadParentWalletService,
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
    private val schoolClassRepository: com.haneef._school.repository.SchoolClassRepository,
    private val squadService: com.haneef._school.service.SquadService,
    private val schoolCalendarRepository: com.haneef._school.repository.SchoolCalendarRepository,
    private val activityLogRepository: com.haneef._school.repository.ActivityLogRepository,
    private val userRepository: com.haneef._school.repository.UserRepository,
    private val passwordEncoder: PasswordEncoder,
    @org.springframework.beans.factory.annotation.Value("\${paystack.public.key:}") private val paystackPublicKey: String,
    @org.springframework.beans.factory.annotation.Value("\${squad.public.key:}") private val squadPublicKey: String
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
            // Return the updated fees section which contains the wallet cards
            // The template logic will handle removing the polling attributes once the account number is present
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
            
            // Fetch current session and term for parent dashboard logic (events, etc.)
            val currentSession = academicSessionRepository.findBySchoolIdAndIsActiveOrderByYearDesc(selectedSchoolId, true)
                .find { it.isCurrentSession }
            val currentTerm = if (currentSession != null) {
                termRepository.findByAcademicSessionIdAndIsActiveOrderByStartDate(currentSession.id!!, true)
                    .find { it.isCurrentTerm }
            } else null
            
            if (currentSession != null && currentTerm != null) {
                val now = java.time.LocalDate.now()
                // Fetch upcoming events for current session/term
                val pageable = org.springframework.data.domain.PageRequest.of(0, 5)
                val upcomingEvents = schoolCalendarRepository.findUpcomingEvents(
                    selectedSchoolId,
                    currentSession.id!!,
                    currentTerm.id,
                    now,
                    pageable
                )
                model.addAttribute("upcomingEvents", upcomingEvents)
            }

            // Fetch explicitly to avoid proxy issues in the template
            val subscription = try {
                org.springframework.web.context.support.WebApplicationContextUtils.getRequiredWebApplicationContext(
                    (org.springframework.web.context.request.RequestContextHolder.getRequestAttributes() as org.springframework.web.context.request.ServletRequestAttributes).request.servletContext
                ).getBean(com.haneef._school.repository.SchoolSubscriptionRepository::class.java).findBySchoolId(selectedSchoolId)
            } catch (e: Exception) {
                null
            }
            model.addAttribute("subscription", subscription)
        }
        
        // Ensure wallet is loaded (it's a OneToOne, so it might be lazy)
        model.addAttribute("parent", parent)
        model.addAttribute("paystackWallet", parent.paystackWallet)
        model.addAttribute("squadWallet", parent.squadWallet)
        model.addAttribute("wallet", parent.paystackWallet ?: parent.squadWallet)
        
        // Fetch available providers if wallet doesn't exist
        if (parent.paystackWallet == null && parent.squadWallet == null) {
            val providers = paystackService.getAvailableProviders()
            model.addAttribute("providers", providers)
        }
        
        val financialStatus = financialService.calculateParentFinancialStatus(parent)
        model.addAttribute("financialStatus", financialStatus)
        
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
        model.addAttribute("totalFees", financialStatus.totalOwed)
        model.addAttribute("totalSettled", financialStatus.totalPaid)
        model.addAttribute("balance", financialStatus.balance)
        
        // Enrich feeBreakdown with specific status data for UI
        val statusByStudent = financialStatus.students.associateBy { it.studentId }
        val enrichedBreakdown = (financialData["feeBreakdown"] as List<Map<String, Any>>).map { item ->
            val studentUuidStr = item["studentUuid"] as? String
            val studentUuid = try { studentUuidStr?.let { UUID.fromString(it) } } catch (e: Exception) { null }
            val status = if (studentUuid != null) statusByStudent[studentUuid] else null
            item + mapOf(
                "currentBillStatus" to (status?.currentBill ?: java.math.BigDecimal.ZERO),
                "outstandingStatus" to (status?.outstanding ?: java.math.BigDecimal.ZERO),
                "totalBalanceStatus" to (status?.currentBalance ?: java.math.BigDecimal.ZERO)
            )
        }
        model.addAttribute("feeBreakdown", enrichedBreakdown)
        model.addAttribute("paystackPublicKey", paystackPublicKey)
        model.addAttribute("squadPublicKey", squadPublicKey)
        
        // Lock settings if any payment has been made
        val totalSettled = financialData["totalSettled"] as java.math.BigDecimal
        val paymentLocked = totalSettled > java.math.BigDecimal.ZERO
        model.addAttribute("paymentLocked", paymentLocked)
        
        // Fetch recent activities related to parent's children
        if (selectedSchoolId != null && children.isNotEmpty()) {
            val studentIds = children.mapNotNull { it.user.id }
            
            // Define activity types relevant to parents
            val relevantActivityTypes = listOf(
                com.haneef._school.entity.ActivityType.STUDENT_ENROLLED,
                com.haneef._school.entity.ActivityType.STUDENT_UPDATED,
                com.haneef._school.entity.ActivityType.GRADE_ENTERED,
                com.haneef._school.entity.ActivityType.ASSIGNMENT_SUBMITTED,
                com.haneef._school.entity.ActivityType.PAYMENT_RECEIVED,
                com.haneef._school.entity.ActivityType.EXAM_SCHEDULED,
                com.haneef._school.entity.ActivityType.ATTENDANCE,
                com.haneef._school.entity.ActivityType.ASSIGNMENT_CREATED
            )
            
            val pageable = org.springframework.data.domain.PageRequest.of(0, 5)
            val recentActivities = if (studentIds.isNotEmpty()) {
                activityLogRepository.findRecentActivitiesByTargetUserIds(
                    selectedSchoolId,
                    studentIds,
                    relevantActivityTypes,
                    pageable
                )
            } else {
                emptyList()
            }
            
            model.addAttribute("recentActivities", recentActivities)
        } else {
            model.addAttribute("recentActivities", emptyList<com.haneef._school.entity.ActivityLog>())
        }
    }

    @PostMapping("/create-wallet")
    fun createWallet(
        @RequestParam(defaultValue = "wema-bank") preferredBank: String,
        @RequestParam(defaultValue = "paystack") provider: String,
        @RequestParam(required = false) bvn: String?,
        @RequestParam(required = false) dob: String?,
        @RequestParam(required = false) gender: String?,
        @RequestParam(required = false) address: String?,
        model: Model, 
        authentication: Authentication,
        request: HttpServletRequest,
        redirectAttributes: org.springframework.web.servlet.mvc.support.RedirectAttributes
    ): String {
        val userDetails = userDetailsService.loadUserByUsername(authentication.name)
        val customUser = userDetails as CustomUserDetails
        
        val parents = parentRepository.findByUserIdWithWallet(customUser.user.id!!)
        val parent = parents.firstOrNull() ?: return "redirect:/login"
        
        // Get school information from session
        val selectedSchoolId = request.session.getAttribute("selectedSchoolId") as? UUID
        
        // Validate parent access - ensure parent belongs to user
        if (parent.user.id != customUser.user.id) {
            throw org.springframework.security.access.AccessDeniedException("Unauthorized access to parent data")
        }
        
        val errorAttr = if (provider.equals("squad", ignoreCase = true)) "squadError" else "paystackError"
        val successAttr = if (provider.equals("squad", ignoreCase = true)) "squadSuccess" else "paystackSuccess"

        val result = if (provider.equals("squad", ignoreCase = true)) {
            if (bvn.isNullOrBlank() || dob.isNullOrBlank() || gender.isNullOrBlank() || address.isNullOrBlank()) {
                val errorMessage = "All fields (BVN, DOB, Gender, Address) are required for Squad account creation."
                if (request.getHeader("HX-Request") != null) {
                    model.addAttribute(errorAttr, errorMessage)
                    // Reload dashboard data to render the fragment correctly
                    populateDashboardModel(model, parent, selectedSchoolId)
                    return "dashboard/parent-dashboard :: fees-overview-section"
                }
                redirectAttributes.addFlashAttribute(errorAttr, errorMessage)
                return "redirect:/parent/dashboard"
            }
            squadParentWalletService.createWalletForParent(parent, bvn, dob, gender, address)
        } else {
            paystackParentWalletService.createWalletForParent(parent, preferredBank)
        }
        
        if (result.isSuccess) {
            val successMessage = "Wallet created successfully! Your account number is being generated."
            if (request.getHeader("HX-Request") != null) {
                model.addAttribute(successAttr, successMessage)
                // Reload dashboard data to render the fragment correctly
                populateDashboardModel(model, parent, selectedSchoolId)
                return "dashboard/parent-dashboard :: fees-overview-section"
            }
            redirectAttributes.addFlashAttribute(successAttr, successMessage)
        } else {
            val errorMessage = "Error creating wallet: ${result.exceptionOrNull()?.message}"
            if (request.getHeader("HX-Request") != null) {
                model.addAttribute(errorAttr, errorMessage)
                // Reload dashboard data to render the fragment correctly
                populateDashboardModel(model, parent, selectedSchoolId)
                return "dashboard/parent-dashboard :: fees-overview-section"
            }
            redirectAttributes.addFlashAttribute(errorAttr, errorMessage)
        }
        
        return "redirect:/parent/dashboard"
    }

    @PostMapping("/create-dynamic-account")
    @org.springframework.web.bind.annotation.ResponseBody
    fun createDynamicAccount(
        @RequestParam amount: java.math.BigDecimal,
        authentication: Authentication
    ): Map<String, Any> {
        val userDetails = userDetailsService.loadUserByUsername(authentication.name)
        val customUser = userDetails as CustomUserDetails
        
        val transactionRef = "DYN-${UUID.randomUUID().toString().substring(0, 12).uppercase()}"
        val response = squadService.createDynamicVirtualAccount(transactionRef, amount, customUser.user.email!!)
        
        val responseData = response?.data
        return if (response != null && response.success && responseData != null) {
            mapOf(
                "success" to true,
                "accountNumber" to (responseData.accountNumber ?: ""),
                "bankName" to (responseData.bankName ?: ""),
                "accountName" to "Squad Virtual Account",
                "expiresInSeconds" to 120 // 2 minutes
            )
        } else {
            mapOf(
                "success" to false,
                "message" to (response?.message ?: "Failed to create dynamic account")
            )
        }
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
        } else {
            if (distributionType == "SEQUENTIAL") {
                if (childPriority != null && childPriority.isNotEmpty()) {
                    parent.paymentPriorityOrder = childPriority.joinToString(",")
                }
            }
            
            parentRepository.save(parent)
            model.addAttribute("success", "Payment settings updated successfully")
        }
        
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
        
        logger.info("Toggle Fee: student=$studentId, feeItem=$feeItemId, optedIn=$optedIn")

        // Get existing selection
        val selection = studentOptionalFeeRepository.findByStudentIdAndClassFeeItemId(studentId, feeItemId)
        logger.info("Current selection: $selection, isActive=${selection?.isActive}")
        
        // Get current academic session and term for the school
        val currentAcademicSession = academicSessionRepository.findBySchoolIdAndIsActiveOrderByYearDesc(student.schoolId!!, true)
            .find { it.isCurrentSession }
        val currentTerm = if (currentAcademicSession != null) {
            termRepository.findByAcademicSessionIdAndIsActiveOrderByStartDate(currentAcademicSession.id!!, true)
                .find { it.isCurrentTerm }
        } else null
        
        logger.info("Using current session: ${currentAcademicSession?.sessionName}, current term: ${currentTerm?.termName}")

        // Check if the fee item or selection is locked
        val isLocked = selection?.isLocked == true
        
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
        
        // Resolve academic context (session/term)
        val (effectiveSession, effectiveTerm) = getEffectiveSessionAndTerm(customUser.user.id!!, schoolId, request)
        
        // Get enrollments with track information, filtered by session and term
        val enrollments = if (effectiveSession != null && effectiveTerm != null) {
            studentClassRepository.findByStudentIdAndAcademicSessionIdAndTermIdWithClassAndTrack(
                studentId, effectiveSession.id!!, effectiveTerm.id!!
            )
        } else {
            // Fallback to all active enrollments if context cannot be fully resolved
            studentClassRepository.findByStudentIdWithClassAndTrack(studentId)
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
        
        
        model.addAttribute("student", student)
        model.addAttribute("enrollments", enrollments)
        model.addAttribute("school", school)
        model.addAttribute("attendancePercentage", attendancePercentage)
        model.addAttribute("presentCount", presentCount)
        model.addAttribute("absentCount", absentCount)
        model.addAttribute("lateCount", lateCount)
        model.addAttribute("totalAttendance", totalAttendance)
        model.addAttribute("parentRelationships", parentRelationships)
        
        // Add user info for header
        model.addAttribute("user", customUser.user)
        model.addAttribute("userRole", "Parent")
        
        return "staff/student-profile" // Reuse the same template
    }

    @PostMapping("/student/{studentId}/set-password")
    @Transactional
    fun setStudentPassword(
        @org.springframework.web.bind.annotation.PathVariable studentId: UUID,
        @RequestParam password: String,
        @RequestParam confirmPassword: String,
        authentication: Authentication,
        redirectAttributes: RedirectAttributes
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
        
        if (password != confirmPassword) {
            redirectAttributes.addFlashAttribute("error", "Passwords do not match")
            return "redirect:/parent/child/$studentId"
        }
        
        if (password.length < 6) {
            redirectAttributes.addFlashAttribute("error", "Password must be at least 6 characters long")
            return "redirect:/parent/child/$studentId"
        }
        
        val student = studentRepository.findById(studentId).orElseThrow()
        val studentUser = student.user
        
        studentUser.passwordHash = passwordEncoder.encode(password)
        
        // Activate user if not already
        if (studentUser.status != com.haneef._school.entity.UserStatus.ACTIVE) {
            studentUser.status = com.haneef._school.entity.UserStatus.ACTIVE
            studentUser.isVerified = true
            studentUser.emailVerified = true // Assuming verify means capable of login
        }
        
        userRepository.save(studentUser)
        
        redirectAttributes.addFlashAttribute("success", "Student login password has been set successfully")
        return "redirect:/parent/child/$studentId"
    }

    private fun getEffectiveSessionAndTerm(userId: UUID, schoolId: UUID, request: HttpServletRequest): Pair<com.haneef._school.entity.AcademicSession?, com.haneef._school.entity.Term?> {
        val session = request.session
        val selectedSessionId = session.getAttribute("selectedSessionId") as? UUID
        val selectedTermId = session.getAttribute("selectedTermId") as? UUID
        
        // 1. Try to get from session attributes
        var effectiveSession = if (selectedSessionId != null) {
            academicSessionRepository.findById(selectedSessionId).orElse(null)
        } else {
            null
        }
        
        var effectiveTerm = if (selectedTermId != null) {
            termRepository.findById(selectedTermId).orElse(null)
        } else {
            null
        }

        // 2. Fallback to current session/term if not selected
        if (effectiveSession == null) {
            effectiveSession = academicSessionRepository.findBySchoolIdAndIsCurrentSessionAndIsActive(schoolId, true, true)
            // If still null, try most recent
            if (effectiveSession == null) {
                effectiveSession = academicSessionRepository.findBySchoolIdAndIsActiveOrderByYearDesc(schoolId, true).firstOrNull()
            }
        }
        
        if (effectiveTerm == null && effectiveSession != null) {
             val terms = termRepository.findByAcademicSessionIdAndIsActiveOrderByStartDate(effectiveSession.id!!, true)
             effectiveTerm = terms.find { it.isCurrentTerm } ?: terms.firstOrNull()
        }
        
        return Pair(effectiveSession, effectiveTerm)
    }
}
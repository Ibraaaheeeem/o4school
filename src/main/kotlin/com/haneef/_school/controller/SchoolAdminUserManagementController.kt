package com.haneef._school.controller

import com.haneef._school.entity.*
import com.haneef._school.repository.*
import com.haneef._school.service.CustomUserDetails
import com.haneef._school.service.EmailService
import jakarta.servlet.http.HttpSession
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@Controller
@RequestMapping("/admin/community")
@PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
class SchoolAdminUserManagementController(
    private val userRepository: UserRepository,
    private val userSchoolRoleRepository: UserSchoolRoleRepository,
    private val staffRepository: StaffRepository,
    private val parentRepository: ParentRepository,
    private val studentRepository: StudentRepository,
    private val emailService: EmailService,
    private val parentWalletRepository: ParentWalletRepository,
    private val parentWalletService: com.haneef._school.service.ParentWalletService
) {

    data class UserManagementDTO(
        val roleId: UUID,
        val userId: UUID,
        val fullName: String,
        val email: String?,
        val phoneNumber: String,
        val roleName: String,
        val registrationDate: LocalDateTime,
        val isVerified: Boolean,
        val isActive: Boolean,
        val status: UserStatus,
        val details: String,
        val isPendingApproval: Boolean,
        val hasWallet: Boolean = false,
        val parentId: UUID? = null,
        val accountNumber: String? = null,
        val bankName: String? = null,
        val accountName: String? = null,
        val walletBalance: java.math.BigDecimal? = null
    )

    @GetMapping("/approvals")
    fun userManagement(
        @RequestParam(required = false) roleFilters: List<String>?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(defaultValue = "pending") tab: String,
        model: Model, 
        authentication: Authentication, 
        session: HttpSession
    ): String {
        val customUser = authentication.principal as CustomUserDetails
        val schoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/select-school"

        val pageable = org.springframework.data.domain.PageRequest.of(page, size, org.springframework.data.domain.Sort.by("assignedAt").descending())
        
        val rolesPage = if (!roleFilters.isNullOrEmpty()) {
            userSchoolRoleRepository.findBySchoolIdAndRoleNameIn(schoolId, roleFilters, pageable)
        } else {
            userSchoolRoleRepository.findBySchoolId(schoolId, pageable)
        }
        
        val userDTOs = rolesPage.content.map { role ->
            val user = role.user
            var details = ""
            var hasWallet = false
            var parentId: UUID? = null
            var accountNumber: String? = null
            var bankName: String? = null
            var accountName: String? = null
            var walletBalance: java.math.BigDecimal? = null
            
            when (role.role.name) {
                "STAFF", "TEACHER" -> {
                    val staff = staffRepository.findByUserIdAndSchoolId(user.id!!, schoolId)
                    details = staff?.designation ?: "Teacher"
                }
                "PARENT" -> {
                    val parent = parentRepository.findByUserIdAndSchoolId(user.id!!, schoolId)
                    parentId = parent?.id
                    details = if (parent != null) {
                        val childrenCount = parent.studentRelationships.size
                        val wallet = parentWalletRepository.findByParentId(parent.id!!)
                        if (wallet != null) {
                            hasWallet = true
                            accountNumber = wallet.accountNumber
                            bankName = wallet.bankName
                            accountName = wallet.accountName
                            walletBalance = wallet.balance
                        }
                        "$childrenCount linked children"
                    } else {
                        "0 linked children"
                    }
                }
                "STUDENT" -> {
                    val student = studentRepository.findByUserIdAndSchoolId(user.id!!, schoolId)
                    val hasParent = student?.parentRelationships?.isNotEmpty() ?: false
                    details = if (hasParent) "Parent linked" else "No parent linked"
                }
            }

            println("DEBUG: roleId=${role.id}, userId=${user.id}, fullName=${user.fullName}, phoneNumber=${user.phoneNumber}, roleName=${role.role.name}, registrationDate=${role.assignedAt}, isVerified=${user.emailVerified}, isActive=${user.isActive && role.isActive}, status=${user.status}, details=$details")
            
            UserManagementDTO(
                roleId = role.id!!,
                userId = user.id!!,
                fullName = user.fullName ?: "User",
                email = user.email,
                phoneNumber = user.phoneNumber,
                roleName = role.role.name,
                registrationDate = role.assignedAt,
                isVerified = user.emailVerified,
                isActive = user.isActive && role.isActive,
                status = user.status,
                details = details,
                isPendingApproval = !role.isActive && user.status == UserStatus.PENDING,
                hasWallet = hasWallet,
                parentId = parentId,
                accountNumber = accountNumber,
                bankName = bankName,
                accountName = accountName,
                walletBalance = walletBalance
            )
        }

        // Get all roles for the filter dropdown
        val allRolesForSchool = userSchoolRoleRepository.findBySchoolId(schoolId)
        val availableRoles = allRolesForSchool.map { it.role.name }.distinct().sorted()

        model.addAttribute("user", customUser.user)
        model.addAttribute("users", userDTOs)
        model.addAttribute("availableRoles", availableRoles)
        model.addAttribute("selectedRoles", roleFilters ?: emptyList<String>())
        model.addAttribute("userRole", "School Administrator")
        model.addAttribute("dashboardType", "school-admin")
        model.addAttribute("activeTab", tab)
        
        // Pagination attributes
        model.addAttribute("currentPage", page)
        model.addAttribute("totalPages", rolesPage.totalPages)
        model.addAttribute("totalItems", rolesPage.totalElements)
        model.addAttribute("pageSize", size)
        
        return "admin/community/approvals"
    }

    @PostMapping("/approvals/approve/{roleId}")
    fun approveUser(
        @PathVariable roleId: UUID,
        authentication: Authentication,
        session: HttpSession,
        redirectAttributes: RedirectAttributes
    ): String {
        val schoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/select-school"

        val userSchoolRole = userSchoolRoleRepository.findById(roleId).orElseThrow { RuntimeException("Role not found") }
        
        if (userSchoolRole.schoolId != schoolId) {
            redirectAttributes.addFlashAttribute("error", "Unauthorized access")
            return "redirect:/admin/community/approvals"
        }

        val user = userSchoolRole.user
        
        // Activate the role
        userSchoolRole.isActive = true
        userSchoolRole.assignedAt = LocalDateTime.now()
        userSchoolRole.assignedBy = (authentication.principal as CustomUserDetails).user.id
        userSchoolRoleRepository.save(userSchoolRole)
        
        // Activate the user if they were pending
        if (user.status == UserStatus.PENDING) {
            user.status = UserStatus.ACTIVE
            user.approvalStatus = "approved"
            user.approvedAt = LocalDateTime.now()
            user.approvedBy = (authentication.principal as CustomUserDetails).user.id
            userRepository.save(user)
        }

        // Create Staff or Parent entity if it doesn't exist
        when (userSchoolRole.role.name) {
            "STAFF", "TEACHER" -> {
                if (staffRepository.findByUserIdAndSchoolId(user.id!!, schoolId) == null) {
                    val staff = Staff(
                        user = user,
                        staffId = "STF-${UUID.randomUUID().toString().take(6).uppercase()}",
                        hireDate = LocalDate.now()
                    ).apply {
                        this.schoolId = schoolId
                        this.isActive = true
                    }
                    staffRepository.save(staff)
                }
            }
            "PARENT" -> {
                if (parentRepository.findByUserIdAndSchoolId(user.id!!, schoolId) == null) {
                    val parent = Parent(
                        user = user
                    ).apply {
                        this.schoolId = schoolId
                        this.isActive = true
                    }
                    parentRepository.save(parent)
                }
            }
        }
        
        // Send email notification
        emailService.sendApprovalEmail(user.email ?: "", user.fullName ?: "User", userSchoolRole.role.name)
        
        redirectAttributes.addFlashAttribute("success", "User ${user.fullName ?: "User"} has been approved and notified.")
        return "redirect:/admin/community/approvals"
    }

    @PostMapping("/approvals/reject/{roleId}")
    fun rejectUser(
        @PathVariable roleId: UUID,
        session: HttpSession,
        redirectAttributes: RedirectAttributes
    ): String {
        val schoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/select-school"

        val userSchoolRole = userSchoolRoleRepository.findById(roleId).orElseThrow { RuntimeException("Role not found") }
        
        if (userSchoolRole.schoolId != schoolId) {
            redirectAttributes.addFlashAttribute("error", "Unauthorized access")
            return "redirect:/admin/community/approvals"
        }

        userSchoolRoleRepository.delete(userSchoolRole)
        
        redirectAttributes.addFlashAttribute("success", "Registration request has been rejected.")
        return "redirect:/admin/community/approvals"
    }

    @PostMapping("/users/send-reminder/{userId}")
    fun sendVerificationReminder(
        @PathVariable userId: UUID,
        session: HttpSession,
        redirectAttributes: RedirectAttributes
    ): String {
        val user = userRepository.findById(userId).orElseThrow { RuntimeException("User not found") }
        
        // Verify user belongs to this school
        val schoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/select-school"
            
        val userRoles = userSchoolRoleRepository.findBySchoolId(schoolId)
        if (userRoles.none { it.user.id == userId }) {
            redirectAttributes.addFlashAttribute("error", "User not found in this school")
            return "redirect:/admin/community/approvals"
        }

        if (user.emailVerified) {
            redirectAttributes.addFlashAttribute("error", "User is already verified")
            return "redirect:/admin/community/approvals"
        }

        val otp = (100000..999999).random().toString()
        user.otpCode = otp
        user.otpExpires = LocalDateTime.now().plusMinutes(15)
        userRepository.save(user)

        emailService.sendOtpEmail(user.email!!, otp)
        
        redirectAttributes.addFlashAttribute("success", "Verification reminder sent to ${user.email}")
        return "redirect:/admin/community/approvals"
    }

    @PostMapping("/users/soft-delete/{roleId}")
    fun softDeleteUser(
        @PathVariable roleId: UUID,
        session: HttpSession,
        redirectAttributes: RedirectAttributes
    ): String {
        val schoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/select-school"

        val role = userSchoolRoleRepository.findById(roleId).orElseThrow { RuntimeException("Role not found") }
        if (role.schoolId != schoolId) {
            redirectAttributes.addFlashAttribute("error", "Unauthorized access")
            return "redirect:/admin/community/approvals"
        }

        role.isActive = false
        userSchoolRoleRepository.save(role)

        redirectAttributes.addFlashAttribute("success", "User has been deactivated (soft deleted).")
        return "redirect:/admin/community/approvals"
    }

    @PostMapping("/users/hard-delete/{roleId}")
    fun hardDeleteUser(
        @PathVariable roleId: UUID,
        session: HttpSession,
        redirectAttributes: RedirectAttributes
    ): String {
        val schoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/select-school"

        val role = userSchoolRoleRepository.findById(roleId).orElseThrow { RuntimeException("Role not found") }
        if (role.schoolId != schoolId) {
            redirectAttributes.addFlashAttribute("error", "Unauthorized access")
            return "redirect:/admin/community/approvals"
        }

        if (role.isActive) {
            redirectAttributes.addFlashAttribute("error", "Only inactive users can be hard deleted.")
            return "redirect:/admin/community/approvals"
        }

        userSchoolRoleRepository.delete(role)

        redirectAttributes.addFlashAttribute("success", "User record has been permanently deleted from this school.")
        return "redirect:/admin/community/approvals"
    }

    @PostMapping("/users/restore/{roleId}")
    fun restoreUser(
        @PathVariable roleId: UUID,
        session: HttpSession,
        redirectAttributes: RedirectAttributes
    ): String {
        val schoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/select-school"

        val role = userSchoolRoleRepository.findById(roleId).orElseThrow { RuntimeException("Role not found") }
        if (role.schoolId != schoolId) {
            redirectAttributes.addFlashAttribute("error", "Unauthorized access")
            return "redirect:/admin/community/approvals"
        }

        role.isActive = true
        userSchoolRoleRepository.save(role)

        redirectAttributes.addFlashAttribute("success", "User has been restored.")
        return "redirect:/admin/community/approvals"
    }

    @PostMapping("/users/create-parent-wallet/{parentId}")
    fun createParentWallet(
        @PathVariable parentId: UUID,
        @RequestParam(defaultValue = "wema-bank") preferredBank: String,
        redirectAttributes: RedirectAttributes,
        request: jakarta.servlet.http.HttpServletRequest,
        model: Model
    ): String {
        val schoolId = request.session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/select-school"

        val parent = parentRepository.findById(parentId).orElseThrow { RuntimeException("Parent not found") }
        
        if (parent.schoolId != schoolId) {
            redirectAttributes.addFlashAttribute("error", "Unauthorized access")
            return "redirect:/admin/community/approvals"
        }
        
        val result = parentWalletService.createWalletForParent(parent, preferredBank)
        
        if (result.isSuccess) {
            if (request.getHeader("HX-Request") != null) {
                val wallet = parentWalletRepository.findByParentId(parentId)
                val updatedU = UserManagementDTO(
                    roleId = UUID.randomUUID(), // Dummy ID, not used in fragment
                    userId = parent.user.id!!,
                    fullName = parent.user.fullName ?: "",
                    email = parent.user.email,
                    phoneNumber = parent.user.phoneNumber,
                    roleName = "PARENT",
                    registrationDate = LocalDateTime.now(),
                    isVerified = true,
                    isActive = true,
                    status = UserStatus.ACTIVE,
                    details = "",
                    isPendingApproval = false,
                    hasWallet = true,
                    parentId = parentId,
                    accountNumber = wallet?.accountNumber,
                    bankName = wallet?.bankName,
                    accountName = wallet?.accountName,
                    walletBalance = wallet?.balance
                )
                model.addAttribute("u", updatedU)
                return "admin/community/approvals :: wallet-section"
            }
            redirectAttributes.addFlashAttribute("success", "Virtual account created successfully for ${parent.user.fullName}")
        } else {
            if (request.getHeader("HX-Request") != null) {
                 // For HTMX error, we might want to return the button again but maybe with an error message?
                 // Or just return the same fragment which will re-render the button.
                 // Ideally we should show an error. For now, let's just return the button (no change)
                 // but we can't easily pass the error to the fragment without changing the DTO or model significantly.
                 // Let's rely on the fact that if it fails, the button remains.
                 // We could set a header to trigger a toast if we had that setup.
                 // For now, let's just return the original state (button).
                 val updatedU = UserManagementDTO(
                    roleId = UUID.randomUUID(),
                    userId = parent.user.id!!,
                    fullName = parent.user.fullName ?: "",
                    email = parent.user.email,
                    phoneNumber = parent.user.phoneNumber,
                    roleName = "PARENT",
                    registrationDate = LocalDateTime.now(),
                    isVerified = true,
                    isActive = true,
                    status = UserStatus.ACTIVE,
                    details = "",
                    isPendingApproval = false,
                    hasWallet = false, // Still no wallet
                    parentId = parentId
                )
                model.addAttribute("u", updatedU)
                return "admin/community/approvals :: wallet-section"
            }
            redirectAttributes.addFlashAttribute("error", "Failed to create virtual account: ${result.exceptionOrNull()?.message}")
        }
        
        return "redirect:/admin/community/approvals"
    }

    @GetMapping("/users/export/all")
    fun exportAllUsers(
        @RequestParam(required = false) roleFilters: List<String>?,
        @RequestParam(defaultValue = "pending") tab: String,
        session: HttpSession,
        response: jakarta.servlet.http.HttpServletResponse
    ) {
        val schoolId = session.getAttribute("selectedSchoolId") as? UUID ?: return
        
        val users = if (!roleFilters.isNullOrEmpty()) {
            userSchoolRoleRepository.findBySchoolIdAndRoleNameIn(schoolId, roleFilters)
        } else {
            userSchoolRoleRepository.findBySchoolId(schoolId)
        }

        val filteredUsers = when (tab) {
            "pending" -> users.filter { !it.isActive && it.user.status == UserStatus.PENDING }
            "active" -> users.filter { it.isActive && it.user.status != UserStatus.PENDING }
            "inactive" -> users.filter { !it.isActive && it.user.status != UserStatus.PENDING }
            else -> users
        }

        response.contentType = "text/csv"
        response.setHeader("Content-Disposition", "attachment; filename=users_export_${tab}.csv")
        
        val writer = response.writer
        writer.println("Full Name,Email,Phone,Role,Status,Details,Registration Date")
        
        filteredUsers.forEach { role ->
            val user = role.user
            val roleName = role.role.name
            val status = if (role.isActive) "Active" else "Inactive"
            val date = role.assignedAt.toLocalDate().toString()
            
            val details = when (roleName) {
                "STAFF", "TEACHER" -> staffRepository.findByUserIdAndSchoolId(user.id!!, schoolId)?.designation ?: ""
                "PARENT" -> "${parentRepository.findByUserIdAndSchoolId(user.id!!, schoolId)?.studentRelationships?.size ?: 0} children"
                "STUDENT" -> if (studentRepository.findByUserIdAndSchoolId(user.id!!, schoolId)?.parentRelationships?.isNotEmpty() == true) "Parent linked" else "No parent"
                else -> ""
            }
            
            writer.println("\"${user.fullName}\",\"${user.email ?: ""}\",\"${user.phoneNumber}\",\"$roleName\",\"$status\",\"$details\",\"$date\"")
        }
    }

    @GetMapping("/users/export/parents")
    fun exportParents(
        session: HttpSession,
        response: jakarta.servlet.http.HttpServletResponse
    ) {
        val schoolId = session.getAttribute("selectedSchoolId") as? UUID ?: return
        
        val parentRoles = userSchoolRoleRepository.findBySchoolIdAndRoleNameIn(schoolId, listOf("PARENT"))
            .filter { it.isActive }

        response.contentType = "text/csv"
        response.setHeader("Content-Disposition", "attachment; filename=parents_wallet_export.csv")
        
        val writer = response.writer
        writer.println("Full Name,Email,Phone,Bank Name,Account Name,Account Number,Children Count")
        
        parentRoles.forEach { role ->
            val user = role.user
            val parent = parentRepository.findByUserIdAndSchoolId(user.id!!, schoolId)
            val wallet = parent?.let { parentWalletRepository.findByParentId(it.id!!) }
            
            val bank = wallet?.bankName ?: "N/A"
            val accountName = wallet?.accountName ?: "N/A"
            val account = wallet?.accountNumber ?: "N/A"
            val children = parent?.studentRelationships?.size ?: 0
            
            writer.println("\"${user.fullName}\",\"${user.email ?: ""}\",\"${user.phoneNumber}\",\"$bank\",\"$accountName\",\"$account\",$children")
        }
    }
}

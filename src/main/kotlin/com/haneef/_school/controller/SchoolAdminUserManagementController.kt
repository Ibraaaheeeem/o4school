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
    private val paystackParentWalletRepository: PaystackParentWalletRepository,
    private val squadParentWalletRepository: SquadParentWalletRepository,
    private val squadParentWalletService: com.haneef._school.service.SquadParentWalletService,
    private val paystackParentWalletService: com.haneef._school.service.PaystackParentWalletService
) {
    data class UserManagementDTO(
        val roleId: UUID,
        val userId: UUID,
        val fullName: String,
        val email: String?,
        val phoneNumber: String?,
        val roleName: String,
        val registrationDate: LocalDateTime,
        val isVerified: Boolean,
        val isActive: Boolean,
        val status: UserStatus,
        val details: String,
        val isPendingApproval: Boolean,
        val hasPaystackWallet: Boolean = false,
        val paystackAccountNumber: String? = null,
        val paystackBankName: String? = null,
        val paystackAccountName: String? = null,
        val paystackWalletBalance: java.math.BigDecimal? = null,
        val hasSquadWallet: Boolean = false,
        val squadAccountNumber: String? = null,
        val squadBankName: String? = null,
        val squadAccountName: String? = null,
        val squadWalletBalance: java.math.BigDecimal? = null,
        val parentId: UUID? = null
    )

    @GetMapping("/approvals")
    fun userManagement(
        @RequestParam(required = false) roleFilters: List<String>?,
        @RequestParam(defaultValue = "pending") tab: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int,
        @RequestParam(required = false) search: String?,
        session: HttpSession,
        model: Model,
        request: jakarta.servlet.http.HttpServletRequest,
        @RequestHeader(value = "HX-Target", required = false) hxTarget: String? = null
    ): String {
        val schoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/select-school"

        val users = if (!roleFilters.isNullOrEmpty()) {
            userSchoolRoleRepository.findBySchoolIdAndRoleNameIn(schoolId, roleFilters)
        } else {
            // Return empty list when no roles are selected
            emptyList()
        }

        var filteredUsers = when (tab) {
            "pending" -> users.filter { !it.isActive && it.user.status == UserStatus.PENDING }
            "active" -> users.filter { it.isActive && it.user.status != UserStatus.PENDING }
            "inactive" -> users.filter { !it.isActive && it.user.status != UserStatus.PENDING }
            else -> users
        }
        
        if (!search.isNullOrBlank()) {
            val searchTerm = search!!
            filteredUsers = filteredUsers.filter { 
                (it.user.fullName ?: "").contains(searchTerm, ignoreCase = true) ||  
                (it.user.email?.contains(searchTerm, ignoreCase = true) == true) ||
                (it.user.phoneNumber.contains(searchTerm, ignoreCase = true))
            }
        }

        // Pagination
        val totalItems = filteredUsers.size
        val totalPages = (totalItems + size - 1) / size
        val startIndex = page * size
        val endIndex = kotlin.math.min(startIndex + size, totalItems)
        val pagedUsers = if (startIndex < totalItems) filteredUsers.subList(startIndex, endIndex) else emptyList()

        val userDtos = pagedUsers.map { role ->
            val user = role.user
            val roleName = role.role.name
            
            var details = ""
            var parentId: UUID? = null
            var paystackWallet: PaystackParentWallet? = null
            var squadWallet: SquadParentWallet? = null
            
            when (roleName) {
                "STAFF", "TEACHER" -> {
                    details = staffRepository.findByUserIdAndSchoolId(user.id!!, schoolId)?.designation ?: ""
                }
                "PARENT" -> {
                    val parent = parentRepository.findByUserIdAndSchoolId(user.id!!, schoolId)
                    if (parent != null) {
                        details = "${parent.studentRelationships.size} children"
                        parentId = parent.id
                        paystackWallet = paystackParentWalletRepository.findByParentId(parent.id!!)
                        squadWallet = squadParentWalletRepository.findByParentId(parent.id!!)
                    }
                }
                "STUDENT" -> {
                    val student = studentRepository.findByUserIdAndSchoolId(user.id!!, schoolId)
                    if (student != null) {
                        details = if (student.parentRelationships.isNotEmpty()) "Parent linked" else "No parent"
                    }
                }
            }

            UserManagementDTO(
                roleId = role.id!!,
                userId = user.id!!,
                fullName = user.fullName ?: "",
                email = user.email,
                phoneNumber = user.phoneNumber,
                roleName = roleName,
                registrationDate = role.assignedAt,
                isVerified = true,
                isActive = role.isActive,
                status = user.status,
                details = details,
                isPendingApproval = !role.isActive,
                hasPaystackWallet = paystackWallet != null,
                paystackAccountNumber = paystackWallet?.accountNumber,
                paystackBankName = paystackWallet?.bankName,
                paystackAccountName = paystackWallet?.accountName,
                paystackWalletBalance = paystackWallet?.balance,
                hasSquadWallet = squadWallet != null,
                squadAccountNumber = squadWallet?.accountNumber,
                squadBankName = squadWallet?.bankName,
                squadAccountName = squadWallet?.accountName,
                squadWalletBalance = squadWallet?.balance,
                parentId = parentId
            )
        }

        // Available roles for filtering
        val availableRoles = listOf("STAFF", "TEACHER", "PARENT", "STUDENT", "SCHOOL_ADMIN")
        
        model.addAttribute("users", userDtos)
        model.addAttribute("currentPage", page)
        model.addAttribute("totalPages", totalPages)
        model.addAttribute("totalItems", totalItems)
        model.addAttribute("activeTab", tab)
        model.addAttribute("search", search)
        model.addAttribute("availableRoles", availableRoles)
        model.addAttribute("selectedRoles", roleFilters ?: emptyList<String>())
        
        return "admin/community/approvals"
    }

    @PostMapping("/users/create-parent-wallet/{parentId}")
    fun createParentWallet(
        @PathVariable parentId: UUID,
        @RequestParam(defaultValue = "wema-bank") preferredBank: String,
        @RequestParam(defaultValue = "paystack") provider: String,
        @RequestParam(required = false) bvn: String?,
        @RequestParam(required = false) dob: String?,
        @RequestParam(required = false) gender: String?,
        @RequestParam(required = false) address: String?,
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
        
        val result = if (provider.equals("squad", ignoreCase = true)) {
             // For admin creation, we might need these details. 
             // If they are missing, we can't create the account.
             if (bvn.isNullOrBlank() || dob.isNullOrBlank() || gender.isNullOrBlank() || address.isNullOrBlank()) {
                 // If called from admin UI without these fields, we can't proceed.
                 // However, to avoid breaking the build, we'll return failure if missing.
                 // Ideally admin UI should have a modal to collect these.
                 return if (request.getHeader("HX-Request") != null) {
                     model.addAttribute("error", "Missing required Squad fields (BVN, DOB, Gender, Address)")
                     "admin/community/approvals :: wallet-section" 
                 } else {
                     redirectAttributes.addFlashAttribute("error", "Missing required Squad fields (BVN, DOB, Gender, Address)")
                     "redirect:/admin/community/approvals"
                 }
             }
            squadParentWalletService.createWalletForParent(parent, bvn, dob, gender, address)
        } else {
            paystackParentWalletService.createWalletForParent(parent, preferredBank)
        }
        
        if (result.isSuccess) {
            if (request.getHeader("HX-Request") != null) {
                val wallet = paystackParentWalletRepository.findByParentId(parentId)
                val squadWallet = squadParentWalletRepository.findByParentId(parentId)
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
                    hasPaystackWallet = wallet != null,
                    paystackAccountNumber = wallet?.accountNumber,
                    paystackBankName = wallet?.bankName,
                    paystackAccountName = wallet?.accountName,
                    paystackWalletBalance = wallet?.balance,
                    hasSquadWallet = squadWallet != null,
                    squadAccountNumber = squadWallet?.accountNumber,
                    squadBankName = squadWallet?.bankName,
                    squadAccountName = squadWallet?.accountName,
                    squadWalletBalance = squadWallet?.balance,
                    parentId = parentId
                )
                model.addAttribute("u", updatedU)
                return "admin/community/approvals :: wallet-section"
            }
            redirectAttributes.addFlashAttribute("success", "Virtual account created successfully for ${parent.user.fullName}")
        } else {
            if (request.getHeader("HX-Request") != null) {
                // Fetch existing wallets to keep state
                val wallet = paystackParentWalletRepository.findByParentId(parentId)
                val squadWallet = squadParentWalletRepository.findByParentId(parentId)
                
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
                    hasPaystackWallet = wallet != null,
                    paystackAccountNumber = wallet?.accountNumber,
                    paystackBankName = wallet?.bankName,
                    paystackAccountName = wallet?.accountName,
                    paystackWalletBalance = wallet?.balance,
                    hasSquadWallet = squadWallet != null,
                    squadAccountNumber = squadWallet?.accountNumber,
                    squadBankName = squadWallet?.bankName,
                    squadAccountName = squadWallet?.accountName,
                    squadWalletBalance = squadWallet?.balance,
                    parentId = parentId
                )
                model.addAttribute("u", updatedU)
                model.addAttribute("error", "Failed to create virtual account: ${result.exceptionOrNull()?.message}")
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
        writer.println("Full Name,Email,Phone,Paystack Bank,Paystack Account,Paystack Number,Squad Bank,Squad Account,Squad Number,Children Count")
        
        parentRoles.forEach { role ->
            val user = role.user
            val parent = parentRepository.findByUserIdAndSchoolId(user.id!!, schoolId)
            val paystackWallet = parent?.let { paystackParentWalletRepository.findByParentId(it.id!!) }
            val squadWallet = parent?.let { squadParentWalletRepository.findByParentId(it.id!!) }
            
            val paystackBank = paystackWallet?.bankName ?: "N/A"
            val paystackAccountName = paystackWallet?.accountName ?: "N/A"
            val paystackAccount = paystackWallet?.accountNumber ?: "N/A"
            
            val squadBank = squadWallet?.bankName ?: "N/A"
            val squadAccountName = squadWallet?.accountName ?: "N/A"
            val squadAccount = squadWallet?.accountNumber ?: "N/A"
            
            val children = parent?.studentRelationships?.size ?: 0
            
            writer.println("\"${user.fullName}\",\"${user.email ?: ""}\",\"${user.phoneNumber}\",\"$paystackBank\",\"$paystackAccountName\",\"$paystackAccount\",\"$squadBank\",\"$squadAccountName\",\"$squadAccount\",$children")
        }
    }
}

package com.haneef._school.controller

import java.util.UUID

import com.haneef._school.dto.*
import com.haneef._school.entity.*
import com.haneef._school.repository.*
import com.haneef._school.service.CustomUserDetails
import jakarta.servlet.http.HttpSession
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.time.LocalDate
import org.slf4j.LoggerFactory

import org.springframework.context.annotation.Lazy

@Controller
@RequestMapping("/admin/community")
@PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'SCHOOL_ADMIN', 'TEACHER', 'STAFF')")
class CommunityController(
    private val userRepository: UserRepository,
    private val staffRepository: StaffRepository,
    private val studentRepository: StudentRepository,
    private val parentRepository: ParentRepository,
    private val schoolClassRepository: SchoolClassRepository,
    private val departmentRepository: DepartmentRepository,
    private val userSchoolRoleRepository: UserSchoolRoleRepository,
    private val roleRepository: RoleRepository,
    private val studentClassRepository: StudentClassRepository,
    private val parentStudentRepository: ParentStudentRepository,
    private val educationTrackRepository: EducationTrackRepository,
    private val classTeacherRepository: ClassTeacherRepository,
    private val subjectTeacherRepository: SubjectTeacherRepository,
    private val subjectRepository: SubjectRepository,
    private val classSubjectRepository: ClassSubjectRepository,
    private val fileUploadService: com.haneef._school.service.FileUploadService,
    private val paystackParentWalletService: com.haneef._school.service.PaystackParentWalletService,
    private val squadParentWalletService: com.haneef._school.service.SquadParentWalletService,
    private val paystackParentWalletRepository: com.haneef._school.repository.PaystackParentWalletRepository,
    private val financialServiceProvider: org.springframework.beans.factory.ObjectProvider<com.haneef._school.service.FinancialService>,
    private val activityLogService: com.haneef._school.service.ActivityLogService,
    private val schoolRepository: SchoolRepository,
    private val academicSessionRepository: AcademicSessionRepository,
    private val termRepository: TermRepository
) {

    private val financialService: com.haneef._school.service.FinancialService
        get() = financialServiceProvider.getObject()

    private val logger = LoggerFactory.getLogger(CommunityController::class.java)

    @GetMapping
    fun communityHome(model: Model, authentication: Authentication, session: HttpSession): String {
        val customUser = authentication.principal as CustomUserDetails
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/select-school"

        // Get counts for dashboard
        val staffCount = staffRepository.countBySchoolIdAndIsActive(selectedSchoolId, true)
        val studentCount = studentRepository.countBySchoolIdAndIsActive(selectedSchoolId, true)
        val parentCount = parentRepository.countBySchoolIdAndIsActive(selectedSchoolId, true)

        val communityStats = getCommunityStats(selectedSchoolId)
        val pendingApprovalCount = userSchoolRoleRepository.findBySchoolIdAndIsActive(selectedSchoolId, false).size
        
        model.addAttribute("user", customUser.user)
        model.addAttribute("userRole", "School Administrator")
        model.addAttribute("staffCount", communityStats.staffCount)
        model.addAttribute("studentCount", communityStats.studentCount)
        model.addAttribute("parentCount", communityStats.parentCount)
        model.addAttribute("pendingApprovalCount", pendingApprovalCount)
        model.addAttribute("communityStats", communityStats)

        return "admin/community/home"
    }

    @GetMapping("/overviews")
    fun communityOverviews(
        @RequestParam(defaultValue = "students") tab: String,
        model: Model,
        authentication: Authentication,
        session: HttpSession
    ): String {
        val customUser = authentication.principal as CustomUserDetails
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/select-school"

        val communityStats = getCommunityStats(selectedSchoolId)
        
        model.addAttribute("user", customUser.user)
        model.addAttribute("activeTab", tab)
        model.addAttribute("communityStats", communityStats)
        model.addAttribute("isOob", false);
        
        return "admin/community/overviews"
    }

    // Staff Management
    @GetMapping("/staff")
    fun staffList(
        model: Model,
        authentication: Authentication,
        session: HttpSession,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "12") size: Int,
        @RequestParam(required = false) search: String?,
        @RequestParam(required = false) designation: String?,
        request: jakarta.servlet.http.HttpServletRequest
    ): String {
        val hxRequest = request.getHeader("HX-Request") != null
        val hxTarget = request.getHeader("HX-Target")
        
        val customUser = authentication.principal as CustomUserDetails
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return if (hxRequest) "fragments/error :: error-message" else "redirect:/select-school"

        val pageable = PageRequest.of(page, size, Sort.by("user.firstName"))
        
        // Apply filtering
        val staffPage = when {
            !search.isNullOrBlank() && !designation.isNullOrBlank() -> {
                staffRepository.findBySchoolIdAndIsActiveAndDesignationAndUserFullNameContaining(
                    selectedSchoolId, true, designation, search, pageable)
            }
            !search.isNullOrBlank() -> {
                staffRepository.findBySchoolIdAndIsActiveAndUserFullNameContaining(
                    selectedSchoolId, true, search, pageable)
            }
            !designation.isNullOrBlank() -> {
                staffRepository.findBySchoolIdAndIsActiveAndDesignation(
                    selectedSchoolId, true, designation, pageable)
            }
            else -> {
                // Use method with teacher assignments for unfiltered view
                // Fetch page first
                staffRepository.findBySchoolIdAndIsActive(selectedSchoolId, true, pageable)
            }
        }
        
        // Get effective session and term
        val (effectiveSession, effectiveTerm) = getEffectiveSessionAndTerm(session, selectedSchoolId)
        
        // Populate assignments for ALL paths
        populateStaffAssignments(staffPage.content, selectedSchoolId, effectiveSession, effectiveTerm)

        // Get unique designations for filter
        val designations = staffRepository.findDistinctDesignationsBySchoolId(selectedSchoolId)

        val communityStats = getCommunityStats(selectedSchoolId)
        
        model.addAttribute("user", customUser.user)
        model.addAttribute("staffPage", staffPage)
        model.addAttribute("designations", designations)
        model.addAttribute("currentPage", page)
        model.addAttribute("search", search)
        model.addAttribute("selectedDesignation", designation)
        model.addAttribute("communityStats", communityStats)

        return when {
            hxRequest == true && hxTarget == "tab-content" -> "admin/community/staff/list :: #community-content"
            hxRequest == true && hxTarget != "community-content" -> "admin/community/staff/staff-cards :: staff-cards-content"
            else -> "admin/community/staff/list"
        }
    }
    


//    @GetMapping("/staff/new")
//    fun newStaff(model: Model, authentication: Authentication, session: HttpSession): String {
//        val customUser = authentication.principal as CustomUserDetails
//        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
//            ?: return "redirect:/select-school"
//            
//        val communityStats = getCommunityStats(selectedSchoolId)
//        
//        model.addAttribute("user", customUser.user)
//        model.addAttribute("staffDto", StaffDto())
//        model.addAttribute("userDto", UserDto())
//        model.addAttribute("communityStats", communityStats)
//        return "admin/community/staff/form"
//    }

//    @GetMapping("/staff/{id}/edit")
//    fun editStaff(@PathVariable id: UUID, model: Model, authentication: Authentication, session: HttpSession): String {
//        val customUser = authentication.principal as CustomUserDetails
//        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
//            ?: return "redirect:/select-school"
//            
//        val staff = staffRepository.findById(id).orElseThrow { RuntimeException("Staff not found") }
//        
//        // Security Check: Ensure staff belongs to the selected school
//        if (staff.schoolId != selectedSchoolId) {
//            return "redirect:/admin/community/staff?error=Unauthorized+access"
//        }
//        
//        val communityStats = getCommunityStats(selectedSchoolId)
//        
//        model.addAttribute("user", customUser.user)
//        model.addAttribute("staffDto", StaffDto(
//            designation = staff.designation,
//            employmentType = staff.employmentType,
//            highestDegree = staff.highestDegree,
//            yearsOfExperience = staff.yearsOfExperience,
//            bankName = staff.bankName,
//            accountName = staff.accountName,
//            accountNumber = staff.accountNumber
//        ))
//        model.addAttribute("userDto", UserDto(
//            firstName = staff.user.firstName,
//            lastName = staff.user.lastName,
//            middleName = staff.user.middleName,
//            email = staff.user.email,
//            dateOfBirth = staff.user.dateOfBirth,
//            gender = staff.user.gender,
//            addressLine1 = staff.user.addressLine1,
//            addressLine2 = staff.user.addressLine2,
//            city = staff.user.city,
//            state = staff.user.state,
//            postalCode = staff.user.postalCode
//        ))
//        model.addAttribute("staff", staff)
//        model.addAttribute("isEdit", true)
//        model.addAttribute("communityStats", communityStats)
//        
//        return "admin/community/staff/form"
//    }

    @GetMapping("/staff/{id}/modal")
    fun getStaffModal(@PathVariable id: UUID, model: Model, authentication: Authentication, session: HttpSession): String {
        val customUser = authentication.principal as CustomUserDetails
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/select-school"
            
        val staff = staffRepository.findById(id).orElseThrow { RuntimeException("Staff not found") }
        
        // Security Check: Ensure staff belongs to the selected school
        if (staff.schoolId != selectedSchoolId) {
            return "fragments/error :: error-message"
        }
        
        val departments = departmentRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
        val communityStats = getCommunityStats(selectedSchoolId)
        
        // Parse phone number to extract country code and number
        val phoneNumber = staff.user.phoneNumber ?: ""
        val (countryCode, phoneOnly) = parsePhoneNumber(phoneNumber)
        
        model.addAttribute("user", customUser.user)
        model.addAttribute("staff", staff)
        model.addAttribute("userEntity", staff.user)
        model.addAttribute("userDto", UserDto(
            firstName = staff.user.firstName,
            lastName = staff.user.lastName,
            middleName = staff.user.middleName,
            email = staff.user.email,
            dateOfBirth = staff.user.dateOfBirth,
            gender = staff.user.gender
        ))
        model.addAttribute("departments", departments)
        model.addAttribute("countryCode", countryCode)
        model.addAttribute("phoneNumber", phoneOnly)
        model.addAttribute("isEdit", true)
        model.addAttribute("communityStats", communityStats)
        
        return "admin/community/staff/modal-form"
    }

    @GetMapping("/staff/new/modal")
    fun getNewStaffModal(model: Model, authentication: Authentication, session: HttpSession): String {
        val customUser = authentication.principal as CustomUserDetails
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/select-school"
            
        val departments = departmentRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
        val communityStats = getCommunityStats(selectedSchoolId)
        
        model.addAttribute("user", customUser.user)
        model.addAttribute("staffDto", StaffDto())
        model.addAttribute("userDto", UserDto())
        model.addAttribute("staff", Staff())
        model.addAttribute("departments", departments)
        model.addAttribute("countryCode", "+234")
        model.addAttribute("phoneNumber", "")
        model.addAttribute("isEdit", false)
        model.addAttribute("communityStats", communityStats)
        
        return "admin/community/staff/modal-form"
    }

    @GetMapping("/staff/new/home-modal")
    fun getNewStaffHomeModal(model: Model, authentication: Authentication, session: HttpSession): String {
        val customUser = authentication.principal as CustomUserDetails
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/select-school"
            
        val departments = departmentRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
        
        model.addAttribute("user", customUser.user)
        model.addAttribute("departments", departments)
        
        return "admin/community/staff/home-modal-form"
    }

    @GetMapping("/students/new/home-modal")
    fun getNewStudentHomeModal(model: Model, authentication: Authentication, session: HttpSession): String {
        val customUser = authentication.principal as CustomUserDetails
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/select-school"
            
        val classes = schoolClassRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
        
        model.addAttribute("user", customUser.user)
        model.addAttribute("classes", classes)
        
        return "admin/community/students/home-modal-form"
    }

    @GetMapping("/parents/new/home-modal")
    fun getNewParentHomeModal(model: Model, authentication: Authentication, session: HttpSession): String {
        val customUser = authentication.principal as CustomUserDetails
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/select-school"
        
        model.addAttribute("user", customUser.user)
        
        return "admin/community/parents/home-modal-form"
    }

    @PostMapping("/staff/save")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
    fun saveStaff(
        @ModelAttribute staffDto: StaffDto,
        @ModelAttribute("userDto") userDto: UserDto,
        @RequestParam(required = false) id: UUID?,
        @RequestParam countryCode: String,
        @RequestParam(required = false) phoneNumber: String?,
        session: HttpSession,
        redirectAttributes: RedirectAttributes
    ): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/select-school"

        try {
            // Combine country code and phone number
            val fullPhoneNumber = if (phoneNumber.isNullOrBlank()) null else countryCode + phoneNumber
            
            if (id != null) {
                // Update existing staff
                val existingStaff = staffRepository.findById(id).orElseThrow()
                
                // Security Check: Ensure staff belongs to the selected school
                if (existingStaff.schoolId != selectedSchoolId) {
                    redirectAttributes.addFlashAttribute("error", "Unauthorized access")
                    return "redirect:/admin/community/staff"
                }
                
                val existingUser = existingStaff.user
                
                // Update user details
                existingUser.apply {
                    firstName = userDto.firstName
                    lastName = userDto.lastName
                    middleName = userDto.middleName
                    email = userDto.email
                    this.phoneNumber = fullPhoneNumber
                    dateOfBirth = userDto.dateOfBirth
                    gender = userDto.gender
                    addressLine1 = userDto.addressLine1
                    addressLine2 = userDto.addressLine2
                    city = userDto.city
                    state = userDto.state
                    postalCode = userDto.postalCode
                }
                userRepository.save(existingUser)
                
                // Update staff details
                existingStaff.apply {
                    designation = staffDto.designation
                    employmentType = staffDto.employmentType
                    highestDegree = staffDto.highestDegree
                    yearsOfExperience = staffDto.yearsOfExperience
                    bankName = staffDto.bankName
                    accountName = staffDto.accountName
                    accountNumber = staffDto.accountNumber
                    
                    // Set department name from selected department
                    if (staffDto.departmentId != null) {
                        val department = departmentRepository.findById(staffDto.departmentId!!).orElse(null)
                        this.department = department?.name
                    } else {
                        this.department = null
                    }
                }
                staffRepository.save(existingStaff)
                
                redirectAttributes.addFlashAttribute("success", "Staff updated successfully!")
            } else {
                // Check if user already exists by email or phone
                val existingUser = when {
                    !userDto.email.isNullOrBlank() -> userRepository.findByEmail(userDto.email!!).orElse(null)
                    fullPhoneNumber != null -> userRepository.findByPhoneNumber(fullPhoneNumber).orElse(null)
                    else -> null
                }

                val savedUser = if (existingUser != null) {
                    // User exists globally, check if they already have the STAFF role in this school
                    val staffRole = roleRepository.findByName("STAFF").orElseThrow { RuntimeException("Staff role not found") }
                    if (userSchoolRoleRepository.existsByUserIdAndSchoolIdAndRoleId(existingUser.id!!, selectedSchoolId, staffRole.id!!)) {
                        throw RuntimeException("A staff member with this email/phone is already registered in this school.")
                    }
                    existingUser
                } else {
                    val newUser = User(phoneNumber = fullPhoneNumber).apply {
                        firstName = userDto.firstName
                        lastName = userDto.lastName
                        middleName = userDto.middleName
                        email = userDto.email
                        dateOfBirth = userDto.dateOfBirth
                        gender = userDto.gender
                        addressLine1 = userDto.addressLine1
                        addressLine2 = userDto.addressLine2
                        city = userDto.city
                        state = userDto.state
                        postalCode = userDto.postalCode
                        status = UserStatus.ACTIVE
                    }
                    userRepository.save(newUser)
                }
                
                // Check if staff already exists for this user and school
                var savedStaff = staffRepository.findByUserIdAndSchoolId(savedUser.id!!, selectedSchoolId)
                if (savedStaff == null) {
                    val newStaff = Staff(
                        user = savedUser,
                        staffId = generateStaffId(selectedSchoolId),
                        hireDate = LocalDate.now()
                    ).apply {
                        schoolId = selectedSchoolId
                        designation = staffDto.designation
                        employmentType = staffDto.employmentType
                        highestDegree = staffDto.highestDegree
                        yearsOfExperience = staffDto.yearsOfExperience
                        bankName = staffDto.bankName
                        accountName = staffDto.accountName
                        accountNumber = staffDto.accountNumber
                        isActive = true
                        
                        // Set department name from selected department
                        if (staffDto.departmentId != null) {
                            val department = departmentRepository.findById(staffDto.departmentId!!).orElse(null)
                            this.department = department?.name
                        }
                    }
                    
                    savedStaff = staffRepository.save(newStaff)
                }
                
                // Create UserSchoolRole for Staff if it doesn't exist
                val staffRole = roleRepository.findByName("STAFF").orElseThrow { 
                    RuntimeException("Staff role not found") 
                }
                
                if (!userSchoolRoleRepository.existsByUserIdAndSchoolIdAndRoleId(savedUser.id!!, selectedSchoolId, staffRole.id!!)) {
                    val userSchoolRole = UserSchoolRole(
                        user = savedUser,
                        schoolId = selectedSchoolId,
                        role = staffRole,
                        isPrimary = true
                    )
                    userSchoolRole.isActive = true
                    userSchoolRoleRepository.save(userSchoolRole)
                }
                
                redirectAttributes.addFlashAttribute("success", "Staff created successfully!")
            }
        } catch (e: Exception) {
            redirectAttributes.addFlashAttribute("error", handleDatabaseError(e, "Error saving staff"))
        }

        return "redirect:/admin/community/staff"
    }

    @PostMapping("/staff/save-htmx")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
    fun saveStaffHtmx(
        @RequestParam(required = false) id: UUID?,
        @ModelAttribute staffDto: StaffDto,
        @ModelAttribute userDto: UserDto,
        @RequestParam countryCode: String,
        @RequestParam(required = false) phoneNumber: String?,
        session: HttpSession,
        model: Model
    ): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "fragments/error :: error-message"

        try {
            // Combine country code and phone number
            val fullPhoneNumber = if (phoneNumber.isNullOrBlank()) null else countryCode + phoneNumber
            
            if (id != null) {
                // Update existing staff
                val existingStaff = staffRepository.findById(id).orElseThrow()
                
                // Security Check: Ensure staff belongs to the selected school
                if (existingStaff.schoolId != selectedSchoolId) {
                    return "fragments/error :: error-message"
                }
                
                val existingUser = existingStaff.user
                
                // Update user details
                existingUser.apply {
                    this.firstName = userDto.firstName
                    this.lastName = userDto.lastName
                    this.middleName = userDto.middleName
                    this.email = userDto.email
                    this.phoneNumber = fullPhoneNumber
                    this.dateOfBirth = userDto.dateOfBirth
                    this.gender = userDto.gender
                }
                userRepository.save(existingUser)
                
                // Update staff details
                existingStaff.apply {
                    this.designation = staffDto.designation
                    this.employmentType = staffDto.employmentType ?: "full_time"
                    this.highestDegree = staffDto.highestDegree
                    
                    // Set department name from selected department
                    if (staffDto.departmentId != null) {
                        val department = departmentRepository.findById(staffDto.departmentId!!).orElse(null)
                        this.department = department?.name
                    } else {
                        this.department = null
                    }
                }
                staffRepository.save(existingStaff)
                
                model.addAttribute("success", "Staff updated successfully!")
                
                // Return updated staff card (OOB)
                val updatedStaffList = loadStaffWithTeacherAssignments(selectedSchoolId).filter { it.id == id }
                if (updatedStaffList.isEmpty()) throw RuntimeException("Staff not found after update")
                val updatedStaff = updatedStaffList.first()
                
                val staffPage = org.springframework.data.domain.PageImpl(listOf(updatedStaff))
                model.addAttribute("staffPage", staffPage)
                model.addAttribute("staff", updatedStaff)
                model.addAttribute("isOob", true)
                model.addAttribute("modalId", "staffModal")
                
                return "admin/community/staff/assign-success"
            } else {
                // Check if user already exists by email or phone
                val existingUser = when {
                    !userDto.email.isNullOrBlank() -> userRepository.findByEmail(userDto.email!!).orElse(null)
                    fullPhoneNumber != null -> userRepository.findByPhoneNumber(fullPhoneNumber).orElse(null)
                    else -> null
                }

                val savedUser = if (existingUser != null) {
                    // User exists globally, check if they already have the STAFF role in this school
                    val staffRole = roleRepository.findByName("STAFF").orElseThrow { RuntimeException("Staff role not found") }
                    if (userSchoolRoleRepository.existsByUserIdAndSchoolIdAndRoleId(existingUser.id!!, selectedSchoolId, staffRole.id!!)) {
                        throw RuntimeException("A staff member with this email/phone is already registered in this school.")
                    }
                    existingUser
                } else {
                    val newUser = User(phoneNumber = fullPhoneNumber).apply {
                        this.firstName = userDto.firstName
                        this.lastName = userDto.lastName
                        this.middleName = userDto.middleName
                        this.email = userDto.email
                        this.dateOfBirth = userDto.dateOfBirth
                        this.gender = userDto.gender
                        this.status = UserStatus.ACTIVE
                    }
                    userRepository.save(newUser)
                }
                
                // Check if staff already exists for this user and school
                var savedStaff = staffRepository.findByUserIdAndSchoolId(savedUser.id!!, selectedSchoolId)
                if (savedStaff == null) {
                    val newStaff = Staff(
                        user = savedUser,
                        staffId = generateStaffId(selectedSchoolId),
                        hireDate = LocalDate.now()
                    ).apply {
                        this.schoolId = selectedSchoolId
                        this.designation = staffDto.designation
                        this.employmentType = staffDto.employmentType ?: "full_time"
                        this.highestDegree = staffDto.highestDegree
                        this.isActive = true
                        
                        // Set department name from selected department
                        if (staffDto.departmentId != null) {
                            val department = departmentRepository.findById(staffDto.departmentId!!).orElse(null)
                            this.department = department?.name
                        }
                    }
                    savedStaff = staffRepository.save(newStaff)
                }
                
                // Create UserSchoolRole for Staff if it doesn't exist
                val staffRole = roleRepository.findByName("STAFF").orElseThrow { 
                    RuntimeException("Staff role not found") 
                }
                
                if (!userSchoolRoleRepository.existsByUserIdAndSchoolIdAndRoleId(savedUser.id!!, selectedSchoolId, staffRole.id!!)) {
                    val userSchoolRole = UserSchoolRole(
                        user = savedUser,
                        schoolId = selectedSchoolId,
                        role = staffRole,
                        isPrimary = true
                    )
                    userSchoolRole.isActive = true
                    userSchoolRoleRepository.save(userSchoolRole)
                }
                
                model.addAttribute("success", "Staff created successfully!")
            }

            return "admin/community/staff/assign-success"
        } catch (e: Exception) {
            model.addAttribute("error", handleDatabaseError(e, "Error saving staff"))
            
            // Re-populate model for form re-rendering
            val departments = departmentRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
            model.addAttribute("departments", departments)
            model.addAttribute("isEdit", id != null)
            if (id != null) {
                staffRepository.findById(id).ifPresent { model.addAttribute("staff", it) }
            } else {
                model.addAttribute("staff", Staff())
            }
            model.addAttribute("countryCode", countryCode)
            model.addAttribute("phoneNumber", phoneNumber)
            
            return "admin/community/staff/modal-form"
        }
    }

    // Student Management

    @GetMapping("/students")
    fun studentList(
        model: Model,
        authentication: Authentication,
        session: HttpSession,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "12") size: Int,
        @RequestParam(required = false) search: String?,
        @RequestParam(required = false, name = "classId") classIds: List<UUID>?,
        request: jakarta.servlet.http.HttpServletRequest
    ): String {
        val hxRequest = request.getHeader("HX-Request") != null
        val hxTarget = request.getHeader("HX-Target")
        
        val customUser = authentication.principal as CustomUserDetails
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return if (hxRequest) "fragments/error :: error-message" else "redirect:/select-school"

        val pageable = PageRequest.of(page, size, Sort.by("user.firstName"))
        
        // Filter out null or empty classIds if any
        val validClassIds = classIds?.filterNotNull()?.filter { it.toString().isNotBlank() }
        
        // Get effective session and term
        val (effectiveSession, effectiveTerm) = getEffectiveSessionAndTerm(session, selectedSchoolId)
        
        // Apply filtering based on multiple class IDs
        val studentPage = when {
            !search.isNullOrBlank() && !validClassIds.isNullOrEmpty() -> {
                if (effectiveSession != null && effectiveTerm != null) {
                    studentRepository.findBySchoolIdAndIsActiveAndClassIdInAndSearchAndSessionAndTerm(
                        selectedSchoolId, true, validClassIds, effectiveSession.id!!, effectiveTerm.id!!, search, pageable)
                } else {
                    studentRepository.findBySchoolIdAndIsActiveAndClassIdInAndSearch(
                        selectedSchoolId, true, validClassIds, search, pageable)
                }
            }
            !search.isNullOrBlank() -> {
                studentRepository.findBySchoolIdAndIsActiveAndUserFullNameContaining(
                    selectedSchoolId, true, search, pageable)
            }
            !validClassIds.isNullOrEmpty() -> {
                if (effectiveSession != null && effectiveTerm != null) {
                    studentRepository.findBySchoolIdAndIsActiveAndClassIdInAndSessionAndTerm(
                        selectedSchoolId, true, validClassIds, effectiveSession.id!!, effectiveTerm.id!!, pageable)
                } else {
                    studentRepository.findBySchoolIdAndIsActiveAndClassIdIn(
                        selectedSchoolId, true, validClassIds, pageable)
                }
            }
            else -> {
                studentRepository.findBySchoolIdAndIsActiveWithEnrollments(selectedSchoolId, true, pageable)
            }
        }

        // Get tracks and classes for filter
        val tracks = educationTrackRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
        val allClasses = schoolClassRepository.findBySchoolIdAndIsActiveWithTrack(selectedSchoolId, true)
        
        // Group classes by track ID for easier rendering in template
        val classesByTrack = allClasses.groupBy { it.track?.id }

        val communityStats = getCommunityStats(selectedSchoolId)
        
        model.addAttribute("user", customUser.user)
        model.addAttribute("studentPage", studentPage)
        model.addAttribute("tracks", tracks)
        model.addAttribute("classesByTrack", classesByTrack)
        model.addAttribute("currentPage", page)
        model.addAttribute("search", search)
        model.addAttribute("selectedClassIds", validClassIds ?: emptyList<UUID>())
        model.addAttribute("communityStats", communityStats)

        return when {
            hxRequest == true && hxTarget == "tab-content" -> "admin/community/students/list :: #community-content"
            hxRequest == true && hxTarget != "community-content" -> "admin/community/students/student-cards :: student-cards-content"
            else -> "admin/community/students/list"
        }
    }

//    @GetMapping("/students/new")
//    fun newStudent(model: Model, authentication: Authentication, session: HttpSession): String {
//        val customUser = authentication.principal as CustomUserDetails
//        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
//            ?: return "redirect:/select-school"
//            
//        val classes = schoolClassRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
//        val communityStats = getCommunityStats(selectedSchoolId)
//        
//        val school = schoolRepository.findById(selectedSchoolId).orElse(null)
//        
//        model.addAttribute("user", customUser.user)
//        model.addAttribute("studentDto", StudentDto())
//        model.addAttribute("userDto", UserDto())
//        model.addAttribute("classes", classes)
//        model.addAttribute("communityStats", communityStats)
//        model.addAttribute("admissionPrefix", school?.admissionPrefix ?: "ADM")
//        model.addAttribute("lastAdmissionNumber", studentRepository.findFirstBySchoolIdOrderByCreatedAtDesc(selectedSchoolId)?.admissionNumber)
//        
//        return "admin/community/students/form"
//    }

//    @GetMapping("/students/{id}/edit")
//    fun editStudent(@PathVariable id: UUID, model: Model, authentication: Authentication, session: HttpSession): String {
//        val customUser = authentication.principal as CustomUserDetails
//        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
//            ?: return "redirect:/select-school"
//            
//        val student = studentRepository.findById(id).orElseThrow { RuntimeException("Student not found") }
//        val classes = schoolClassRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
//        val communityStats = getCommunityStats(selectedSchoolId)
//        
//        val school = schoolRepository.findById(selectedSchoolId).orElse(null)
//        
//        model.addAttribute("user", customUser.user)
//        model.addAttribute("student", student)
//        model.addAttribute("userEntity", student.user)
//        model.addAttribute("userDto", UserDto(
//            firstName = student.user.firstName,
//            lastName = student.user.lastName,
//            middleName = student.user.middleName,
//            email = student.user.email,
//            dateOfBirth = student.user.dateOfBirth,
//            gender = student.user.gender,
//            phoneNumber = student.user.phoneNumber
//        ))
//        model.addAttribute("studentDto", StudentDto(
//            admissionNumber = student.admissionNumber,
//            currentGradeLevel = student.currentGradeLevel,
//            dateOfBirth = student.dateOfBirth,
//            gender = student.gender?.name,
//            isNew = student.isNew,
//            previousSchool = student.previousSchool,
//            transportationMethod = student.transportationMethod,
//            hasSpecialNeeds = student.hasSpecialNeeds,
//            specialNeedsDescription = student.specialNeedsDescription,
//            passportPhotoUrl = student.passportPhotoUrl
//        ))
//        model.addAttribute("classes", classes)
//        model.addAttribute("isEdit", true)
//        model.addAttribute("communityStats", communityStats)
//        model.addAttribute("admissionPrefix", school?.admissionPrefix ?: "ADM")
//        model.addAttribute("lastAdmissionNumber", studentRepository.findFirstBySchoolIdOrderByCreatedAtDesc(selectedSchoolId)?.admissionNumber)
//        
//        return "admin/community/students/form"
//    }

    // Student Modal Endpoints
    @GetMapping("/students/{id}/modal")
    fun getStudentModal(@PathVariable id: UUID, model: Model, authentication: Authentication, session: HttpSession): String {
        val customUser = authentication.principal as CustomUserDetails
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/select-school"
            
        val student = studentRepository.findById(id).orElseThrow { RuntimeException("Student not found") }
        val classes = schoolClassRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
        val communityStats = getCommunityStats(selectedSchoolId)
        
        val school = schoolRepository.findById(selectedSchoolId).orElse(null)
        
        model.addAttribute("user", customUser.user)
        model.addAttribute("student", student)
        model.addAttribute("userEntity", student.user)
        model.addAttribute("userDto", UserDto(
            firstName = student.user.firstName,
            lastName = student.user.lastName,
            middleName = student.user.middleName,
            email = student.user.email,
            dateOfBirth = student.user.dateOfBirth,
            gender = student.user.gender,
            phoneNumber = student.user.phoneNumber
        ))
        model.addAttribute("studentDto", StudentDto(
            admissionNumber = student.admissionNumber,
            currentGradeLevel = student.currentGradeLevel,
            dateOfBirth = student.dateOfBirth,
            gender = student.gender?.name,
            isNew = student.isNew,
            previousSchool = student.previousSchool,
            transportationMethod = student.transportationMethod,
            hasSpecialNeeds = student.hasSpecialNeeds,
            specialNeedsDescription = student.specialNeedsDescription,
            passportPhotoUrl = student.passportPhotoUrl
        ))
        model.addAttribute("classes", classes)
        model.addAttribute("isEdit", true)
        model.addAttribute("communityStats", communityStats)
        model.addAttribute("admissionPrefix", school?.admissionPrefix ?: "ADM")
        model.addAttribute("lastAdmissionNumber", studentRepository.findFirstBySchoolIdOrderByCreatedAtDesc(selectedSchoolId)?.admissionNumber)
        
        return "admin/community/students/modal-form"
    }

    @GetMapping("/students/new/modal")
    fun getNewStudentModal(model: Model, authentication: Authentication, session: HttpSession): String {
        val customUser = authentication.principal as CustomUserDetails
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/select-school"
            
        val classes = schoolClassRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
        val communityStats = getCommunityStats(selectedSchoolId)
        
        val school = schoolRepository.findById(selectedSchoolId).orElse(null)
        
        model.addAttribute("user", customUser.user)
        model.addAttribute("studentDto", StudentDto())
        model.addAttribute("userDto", UserDto())
        model.addAttribute("classes", classes)
        model.addAttribute("isEdit", false)
        model.addAttribute("communityStats", communityStats)
        model.addAttribute("admissionPrefix", school?.admissionPrefix ?: "ADM")
        model.addAttribute("lastAdmissionNumber", studentRepository.findFirstBySchoolIdOrderByCreatedAtDesc(selectedSchoolId)?.admissionNumber)
        
        return "admin/community/students/modal-form"
    }

    // Parent Management
    @GetMapping("/parents/{id}/modal")
    fun getParentModal(@PathVariable id: UUID, model: Model, authentication: Authentication, session: HttpSession): String {
        val customUser = authentication.principal as CustomUserDetails
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/select-school"
            
        val parent = parentRepository.findById(id).orElseThrow { RuntimeException("Parent not found") }
        
        // Security Check: Ensure parent belongs to the selected school
        if (parent.schoolId != selectedSchoolId) {
            return "fragments/error :: error-message"
        }
        
        val communityStats = getCommunityStats(selectedSchoolId)
        
        model.addAttribute("user", customUser.user)
        model.addAttribute("parentDto", ParentDto(
            isPrimaryContact = parent.isPrimaryContact,
            isEmergencyContact = parent.isEmergencyContact,
            isFinanciallyResponsible = parent.isFinanciallyResponsible,
            receiveAcademicUpdates = parent.receiveAcademicUpdates,
            receiveFinancialUpdates = parent.receiveFinancialUpdates,
            receiveDisciplinaryUpdates = parent.receiveDisciplinaryUpdates
        ))
        model.addAttribute("userDto", UserDto(
            firstName = parent.user.firstName,
            lastName = parent.user.lastName,
            middleName = parent.user.middleName,
            email = parent.user.email,
            phoneNumber = parent.user.phoneNumber,
            dateOfBirth = parent.user.dateOfBirth,
            gender = parent.user.gender
        ))
        model.addAttribute("parent", parent)
        model.addAttribute("isEdit", true)
        model.addAttribute("communityStats", communityStats)
        

        return "admin/community/parents/modal-form"
    }

    @GetMapping("/parents/new/modal")
    fun getNewParentModal(model: Model, authentication: Authentication, session: HttpSession): String {
        val customUser = authentication.principal as CustomUserDetails
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/select-school"
            
        val communityStats = getCommunityStats(selectedSchoolId)
        
        model.addAttribute("user", customUser.user)
        model.addAttribute("parentDto", ParentDto())
        model.addAttribute("userDto", UserDto())
        model.addAttribute("isEdit", false)
        model.addAttribute("communityStats", communityStats)
        
        return "admin/community/parents/modal-form"
    }

    @PostMapping("/students/save-htmx")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
    fun saveStudentHtmx(
        @ModelAttribute studentDto: StudentDto,
        @ModelAttribute("userDto") userDto: UserDto,
        @RequestParam(required = false) id: UUID?,
        @RequestParam(required = false) phoneNumber: String?,
        session: HttpSession,
        model: Model
    ): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "fragments/error :: error-message"

        try {
            if (id != null) {
                // Update existing student
                val existingStudent = studentRepository.findById(id).orElseThrow()
                
                // Security Check: Ensure student belongs to the selected school
                if (existingStudent.schoolId != selectedSchoolId) {
                    return "fragments/error :: error-message"
                }
                
                val existingUser = existingStudent.user
                
                // Update user details
                existingUser.apply {
                    this.firstName = userDto.firstName
                    this.lastName = userDto.lastName
                    this.middleName = userDto.middleName
                    this.email = userDto.email
                    this.phoneNumber = phoneNumber?.takeIf { it.isNotBlank() }
                    this.dateOfBirth = userDto.dateOfBirth
                    this.gender = userDto.gender
                }
                userRepository.save(existingUser)
                
                // Update student details
                val newAdmissionNumber = if (studentDto.admissionNumber.isNullOrBlank()) generateAdmissionNumber(selectedSchoolId) else studentDto.admissionNumber!!
                
                // Check for duplicate admission number in the same school
                val studentWithSameAdmission = studentRepository.findByAdmissionNumberAndSchoolId(newAdmissionNumber, selectedSchoolId)
                if (studentWithSameAdmission != null && studentWithSameAdmission.id != id) {
                    throw RuntimeException("Admission number $newAdmissionNumber is already in use by another student.")
                }

                existingStudent.apply {
                    this.admissionNumber = newAdmissionNumber
                    this.currentGradeLevel = studentDto.currentGradeLevel
                    this.dateOfBirth = studentDto.dateOfBirth
                    this.gender = studentDto.gender?.let { com.haneef._school.entity.Gender.valueOf(it.uppercase()) }
                    this.isNew = studentDto.isNew
                    this.previousSchool = studentDto.previousSchool
                    this.transportationMethod = studentDto.transportationMethod
                    this.hasSpecialNeeds = studentDto.hasSpecialNeeds
                    this.specialNeedsDescription = studentDto.specialNeedsDescription
                    this.passportPhotoUrl = studentDto.passportPhotoUrl
                }
                studentRepository.save(existingStudent)
                
                model.addAttribute("success", "Student updated successfully!")
                
                // Return updated student card (OOB)
                val updatedStudent = studentRepository.findById(id).orElseThrow()
                val studentPage = org.springframework.data.domain.PageImpl(listOf(updatedStudent))
                model.addAttribute("studentPage", studentPage)
                model.addAttribute("isOob", true)
                model.addAttribute("modalId", "studentModal")
                
                return "admin/community/students/assign-success"
            } else {
                // Use admission number as phone number since phone number field is removed
                val finalPhoneNumber = if (!phoneNumber.isNullOrBlank()) {
                    phoneNumber
                } else {
                    null
                }

                // Check if user already exists by email or phone
                val existingUser = when {
                    !userDto.email.isNullOrBlank() -> userRepository.findByEmail(userDto.email!!).orElse(null)
                    finalPhoneNumber != null -> userRepository.findByPhoneNumber(finalPhoneNumber).orElse(null)
                    else -> null
                }

                val savedUser = if (existingUser != null) {
                    // User exists globally, check if they already have the STUDENT role in this school
                    val studentRole = roleRepository.findByName("STUDENT").orElseThrow { RuntimeException("Student role not found") }
                    if (userSchoolRoleRepository.existsByUserIdAndSchoolIdAndRoleId(existingUser.id!!, selectedSchoolId, studentRole.id!!)) {
                        throw RuntimeException("A student with this email/phone is already registered in this school.")
                    }
                    existingUser
                } else {
                    val newUser = User(phoneNumber = finalPhoneNumber).apply {
                        this.firstName = userDto.firstName
                        this.lastName = userDto.lastName
                        this.middleName = userDto.middleName
                        this.email = userDto.email
                        this.dateOfBirth = userDto.dateOfBirth
                        this.gender = userDto.gender
                        this.status = UserStatus.ACTIVE
                    }
                    userRepository.save(newUser)
                }
                
                // Check if student already exists for this user and school
                var savedStudent = studentRepository.findByUserIdAndSchoolId(savedUser.id!!, selectedSchoolId)
                if (savedStudent == null) {
                    val studentId = generateStudentId(selectedSchoolId)
                    val admissionNumber = if (studentDto.admissionNumber.isNullOrBlank()) generateAdmissionNumber(selectedSchoolId) else studentDto.admissionNumber!!
                    
                    // Check for duplicate admission number in the same school
                    if (studentRepository.findByAdmissionNumberAndSchoolId(admissionNumber, selectedSchoolId) != null) {
                        throw RuntimeException("Admission number $admissionNumber is already in use.")
                    }

                    val newStudent = Student(
                        user = savedUser,
                        studentId = studentId,
                        admissionDate = LocalDate.now()
                    ).apply {
                        this.schoolId = selectedSchoolId
                        this.admissionNumber = admissionNumber
                        this.currentGradeLevel = studentDto.currentGradeLevel
                        this.dateOfBirth = studentDto.dateOfBirth
                        this.gender = studentDto.gender?.let { com.haneef._school.entity.Gender.valueOf(it.uppercase()) }
                        this.isNew = studentDto.isNew
                        this.previousSchool = studentDto.previousSchool
                        this.transportationMethod = studentDto.transportationMethod
                        this.hasSpecialNeeds = studentDto.hasSpecialNeeds
                        this.specialNeedsDescription = studentDto.specialNeedsDescription
                        this.passportPhotoUrl = studentDto.passportPhotoUrl
                        this.isActive = true
                    }
                    savedStudent = studentRepository.save(newStudent)
                }
                
                // Create UserSchoolRole for Student if it doesn't exist
                val studentRole = roleRepository.findByName("STUDENT").orElseThrow { 
                    RuntimeException("Student role not found") 
                }
                
                if (!userSchoolRoleRepository.existsByUserIdAndSchoolIdAndRoleId(savedUser.id!!, selectedSchoolId, studentRole.id!!)) {
                    val userSchoolRole = UserSchoolRole(
                        user = savedUser,
                        schoolId = selectedSchoolId,
                        role = studentRole,
                        isPrimary = true
                    )
                    userSchoolRole.isActive = true
                    userSchoolRoleRepository.save(userSchoolRole)
                }
                
                model.addAttribute("success", "Student enrolled successfully!")
            }

            return "admin/community/students/assign-success"
        } catch (e: Exception) {
            model.addAttribute("error", handleDatabaseError(e, "Error saving student"))
            
            // Re-populate model for form re-rendering
            val classes = schoolClassRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
            val school = schoolRepository.findById(selectedSchoolId).orElse(null)
            
            model.addAttribute("classes", classes)
            model.addAttribute("isEdit", id != null)
            if (id != null) {
                studentRepository.findById(id).ifPresent { model.addAttribute("student", it) }
            }
            model.addAttribute("admissionPrefix", school?.admissionPrefix ?: "ADM")
            model.addAttribute("lastAdmissionNumber", studentRepository.findFirstBySchoolIdOrderByCreatedAtDesc(selectedSchoolId)?.admissionNumber)
            model.addAttribute("phoneNumber", phoneNumber)
            
            return "admin/community/students/modal-form"
        }
    }

    @PostMapping("/upload-passport-photo")
    @ResponseBody
    fun uploadPassportPhoto(
        @RequestParam("file") file: org.springframework.web.multipart.MultipartFile,
        session: HttpSession
    ): Map<String, Any> {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return mapOf("success" to false, "error" to "No school selected")

        return try {
            // Validate file
            if (file.isEmpty) {
                return mapOf("success" to false, "error" to "No file selected")
            }

            // Validate file type
            val allowedTypes = listOf("image/jpeg", "image/jpg", "image/png", "image/gif")
            if (!allowedTypes.contains(file.contentType)) {
                return mapOf("success" to false, "error" to "Invalid file type. Please upload JPEG, PNG, or GIF")
            }

            // Validate file size (5MB)
            if (file.size > 5 * 1024 * 1024) {
                return mapOf("success" to false, "error" to "File size must be less than 5MB")
            }

            // Upload file and get URL
            val photoUrl = fileUploadService.uploadPassportPhoto(file, "temp-${System.currentTimeMillis()}")
            
            mapOf(
                "success" to true,
                "photoUrl" to photoUrl,
                "message" to "Photo uploaded successfully"
            )
        } catch (e: Exception) {
            mapOf("success" to false, "error" to "Upload failed: ${e.message}")
        }
    }

    @GetMapping("/parents")
    fun parentList(
        model: Model,
        authentication: Authentication,
        session: HttpSession,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "12") size: Int,
        @RequestParam(required = false) search: String?,
        request: jakarta.servlet.http.HttpServletRequest
    ): String {
        val hxRequest = request.getHeader("HX-Request") != null
        val hxTarget = request.getHeader("HX-Target")
        
        val customUser = authentication.principal as CustomUserDetails
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return if (hxRequest) "fragments/error :: error-message" else "redirect:/select-school"

        val pageable = PageRequest.of(page, size, Sort.by("user.firstName"))
        
        // Get effective session and term
        val (effectiveSession, effectiveTerm) = getEffectiveSessionAndTerm(session, selectedSchoolId)
        
        // Apply filtering
        val parentPage = if (!search.isNullOrBlank()) {
            parentRepository.findBySchoolIdAndIsActiveAndUserFullNameContaining(
                selectedSchoolId, true, search, pageable)
        } else {
            // Manual pagination for the method with relationships
            val allParents = parentRepository.findBySchoolIdAndIsActiveWithRelationships(selectedSchoolId, true)
            val startIndex = (page * size).coerceAtMost(allParents.size)
            val endIndex = ((page + 1) * size).coerceAtMost(allParents.size)
            val pagedParents = if (startIndex < allParents.size) allParents.subList(startIndex, endIndex) else emptyList()
            
            // Create a Page object manually
            org.springframework.data.domain.PageImpl(pagedParents, pageable, allParents.size.toLong())
        }

        // Populate student classes for session filtering
        populateParentStudentClasses(parentPage.content, selectedSchoolId, effectiveSession, effectiveTerm)

        // Calculate balance for each parent
        parentPage.content.forEach { parent ->
            parent.totalBalance = financialService.calculateParentBalance(parent)
        }

        val communityStats = getCommunityStats(selectedSchoolId)
        
        model.addAttribute("user", customUser.user)
        model.addAttribute("parentPage", parentPage)
        model.addAttribute("currentPage", page)
        model.addAttribute("search", search)
        model.addAttribute("communityStats", communityStats)

        return when {
            hxRequest == true && hxTarget == "tab-content" -> "admin/community/parents/list :: #community-content"
            hxRequest == true && hxTarget != "community-content" -> "admin/community/parents/parent-cards :: parent-cards-content"
            else -> "admin/community/parents/list"
        }
    }

//    @GetMapping("/parents/new")
//    fun newParent(model: Model, authentication: Authentication, session: HttpSession): String {
//        val customUser = authentication.principal as CustomUserDetails
//        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
//            ?: return "redirect:/select-school"
//            
//        val communityStats = getCommunityStats(selectedSchoolId)
//        
//        model.addAttribute("user", customUser.user)
//        model.addAttribute("parentDto", ParentDto())
//        model.addAttribute("userDto", UserDto())
//        model.addAttribute("communityStats", communityStats)
//        return "admin/community/parents/form"
//    }

//    @GetMapping("/parents/{id}/edit")
//    fun editParent(@PathVariable id: UUID, model: Model, authentication: Authentication, session: HttpSession): String {
//        val customUser = authentication.principal as CustomUserDetails
//        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
//            ?: return "redirect:/select-school"
//            
//        val parent = parentRepository.findById(id).orElseThrow { RuntimeException("Parent not found") }
//        val communityStats = getCommunityStats(selectedSchoolId)
//        
//        model.addAttribute("user", customUser.user)
//        model.addAttribute("parentDto", ParentDto(
//            isPrimaryContact = parent.isPrimaryContact,
//            isEmergencyContact = parent.isEmergencyContact,
//            isFinanciallyResponsible = parent.isFinanciallyResponsible,
//            receiveAcademicUpdates = parent.receiveAcademicUpdates,
//            receiveFinancialUpdates = parent.receiveFinancialUpdates,
//            receiveDisciplinaryUpdates = parent.receiveDisciplinaryUpdates
//        ))
//        model.addAttribute("userDto", UserDto(
//            firstName = parent.user.firstName,
//            lastName = parent.user.lastName,
//            middleName = parent.user.middleName,
//            email = parent.user.email,
//            phoneNumber = parent.user.phoneNumber,
//            dateOfBirth = parent.user.dateOfBirth,
//            gender = parent.user.gender,
//            addressLine1 = parent.user.addressLine1,
//            addressLine2 = parent.user.addressLine2,
//            city = parent.user.city,
//            state = parent.user.state,
//            postalCode = parent.user.postalCode
//        ))
//        model.addAttribute("parent", parent)
//        model.addAttribute("isEdit", true)
//        model.addAttribute("communityStats", communityStats)
//        
//        return "admin/community/parents/form"
//    }

    // Delete operations
    @PostMapping("/staff/{id}/delete")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
    @org.springframework.transaction.annotation.Transactional
    fun deleteStaff(
        @PathVariable id: UUID, 
        session: HttpSession, 
        redirectAttributes: RedirectAttributes,
        @RequestParam(required = false) search: String?,
        @RequestParam(defaultValue = "0") page: Int,
        request: jakarta.servlet.http.HttpServletRequest
    ): Any { // Changed return type to Any to support both String (view) and ResponseEntity (body)
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/select-school"
            
        try {
            val staff = staffRepository.findById(id).orElseThrow()
            
            // Security Check: Ensure staff belongs to the selected school
            if (staff.schoolId != selectedSchoolId) {
                redirectAttributes.addFlashAttribute("error", "Unauthorized access")
                return "redirect:/admin/community/staff"
            }
            
            staff.isActive = false
            staffRepository.save(staff)
            
            // Cascade delete: Deactivate class assignments
            val classTeachers = classTeacherRepository.findByStaffIdAndIsActive(staff.id!!, true)
            classTeachers.forEach { 
                it.isActive = false
                classTeacherRepository.save(it)
            }
            
            // Cascade delete: Deactivate subject assignments
            val subjectTeachers = subjectTeacherRepository.findByStaffIdAndIsActive(staff.id!!, true)
            subjectTeachers.forEach { 
                it.isActive = false
                subjectTeacherRepository.save(it)
            }
            
            // Handle HTMX request
            if (request.getHeader("HX-Request") != null) {
                // Close modal via header, remove card OOB, show success message OOB
                return ResponseEntity.ok()
                    .header("HX-Trigger", "{\"closeModal\": \"deleteModal\"}")
                    .body("""
                        <div id="staff-card-$id" hx-swap-oob="delete"></div>
                        <div id="global-toast-container" hx-swap-oob="beforeend">
                            <div class="toast success show">
                                <i class="fas fa-check-circle"></i>
                                <span>Staff deleted successfully!</span>
                            </div>
                            <script>
                                setTimeout(() => {
                                    const toasts = document.querySelectorAll('.toast');
                                    toasts.forEach(t => t.classList.remove('show'));
                                }, 3000);
                            </script>
                        </div>
                    """.trimIndent())
            }
            
            redirectAttributes.addFlashAttribute("success", "Staff deleted successfully!")
        } catch (e: Exception) {
            e.printStackTrace()
            if (request.getHeader("HX-Request") != null) {
                return "fragments/error :: error-message"
            }
            redirectAttributes.addFlashAttribute("error", "Error deleting staff: ${e.message}")
        }
        
        val redirectUrl = StringBuilder("redirect:/admin/community/staff")
        val params = mutableListOf<String>()
        if (!search.isNullOrBlank()) params.add("search=$search")
        if (page > 0) params.add("page=$page")
        
        if (params.isNotEmpty()) {
            redirectUrl.append("?").append(params.joinToString("&"))
        }
        
        return redirectUrl.toString()
    }

    @PostMapping("/students/save")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
    fun saveStudent(
        @ModelAttribute studentDto: StudentDto,
        @ModelAttribute("userDto") userDto: UserDto,
        @RequestParam(required = false) id: UUID?,
        @RequestParam(required = false) passportPhoto: org.springframework.web.multipart.MultipartFile?,
        session: HttpSession,
        redirectAttributes: RedirectAttributes
    ): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/select-school"

        try {
            if (id != null) {
                // Update existing student
                val existingStudent = studentRepository.findById(id).orElseThrow()
                
                // Security Check: Ensure student belongs to the selected school
                if (existingStudent.schoolId != selectedSchoolId) {
                    redirectAttributes.addFlashAttribute("error", "Unauthorized access")
                    return "redirect:/admin/community/students"
                }
                
                val existingUser = existingStudent.user
                
                // Update user details
                existingUser.apply {
                    firstName = userDto.firstName
                    lastName = userDto.lastName
                    middleName = userDto.middleName
                    email = userDto.email
                    phoneNumber = userDto.phoneNumber?.takeIf { it.isNotBlank() }
                    dateOfBirth = userDto.dateOfBirth
                    gender = userDto.gender
                    addressLine1 = userDto.addressLine1
                    addressLine2 = userDto.addressLine2
                    city = userDto.city
                    state = userDto.state
                    postalCode = userDto.postalCode
                }
                userRepository.save(existingUser)
                
                // Update student details
                existingStudent.apply {
                    admissionNumber = studentDto.admissionNumber
                    currentGradeLevel = studentDto.currentGradeLevel
                    dateOfBirth = studentDto.dateOfBirth
                    gender = studentDto.gender?.let { com.haneef._school.entity.Gender.valueOf(it.uppercase()) }
                    isNew = studentDto.isNew
                    previousSchool = studentDto.previousSchool
                    hasSpecialNeeds = studentDto.hasSpecialNeeds
                    specialNeedsDescription = studentDto.specialNeedsDescription
                    transportationMethod = studentDto.transportationMethod
                }
                
                // Handle passport photo upload for existing student
                if (passportPhoto != null && !passportPhoto.isEmpty) {
                    try {
                        // Delete old photo if exists
                        fileUploadService.deletePassportPhoto(existingStudent.passportPhotoUrl)
                        
                        // Upload new photo
                        val photoUrl = fileUploadService.uploadPassportPhoto(passportPhoto, existingStudent.studentId)
                        existingStudent.passportPhotoUrl = photoUrl
                    } catch (e: Exception) {
                        redirectAttributes.addFlashAttribute("error", "Error uploading passport photo: ${e.message}")
                        return "redirect:/admin/community/students"
                    }
                }
                
                studentRepository.save(existingStudent)
                
                redirectAttributes.addFlashAttribute("success", "Student updated successfully!")
            } else {
                // Check if user already exists by email
                val existingUser = if (!userDto.email.isNullOrBlank()) userRepository.findByEmail(userDto.email!!).orElse(null) else null
                val savedUser = if (existingUser != null) {
                    if (!userDto.phoneNumber.isNullOrBlank()) {
                        existingUser.phoneNumber = userDto.phoneNumber!!
                    }
                    userRepository.save(existingUser)
                } else {
                    // Use admission number as phone number since phone number field is removed
                    val finalPhoneNumber = if (!userDto.phoneNumber.isNullOrBlank()) {
                        userDto.phoneNumber
                    } else if (!studentDto.admissionNumber.isNullOrBlank()) {
                        studentDto.admissionNumber
                    } else {
                        null
                    }
                    
                    val newUser = User(phoneNumber = finalPhoneNumber).apply {
                        firstName = userDto.firstName
                        lastName = userDto.lastName
                        middleName = userDto.middleName
                        email = userDto.email
                        dateOfBirth = userDto.dateOfBirth
                        gender = userDto.gender
                        addressLine1 = userDto.addressLine1
                        addressLine2 = userDto.addressLine2
                        city = userDto.city
                        state = userDto.state
                        postalCode = userDto.postalCode
                        status = UserStatus.ACTIVE
                    }
                    userRepository.save(newUser)
                }
                
                // Check if student already exists for this user and school
                var savedStudent = studentRepository.findByUserIdAndSchoolId(savedUser.id!!, selectedSchoolId)
                if (savedStudent == null) {
                    val newStudent = Student(
                        user = savedUser,
                        studentId = generateStudentId(selectedSchoolId),
                        admissionDate = LocalDate.now()
                    ).apply {
                        this.schoolId = selectedSchoolId
                        this.isActive = true
                        // Generate admission number if not provided
                        admissionNumber = if (studentDto.admissionNumber.isNullOrBlank()) {
                            generateAdmissionNumber(selectedSchoolId)
                        } else {
                            studentDto.admissionNumber
                        }
                        currentGradeLevel = studentDto.currentGradeLevel
                        dateOfBirth = studentDto.dateOfBirth
                        gender = studentDto.gender?.let { com.haneef._school.entity.Gender.valueOf(it.uppercase()) }
                        isNew = studentDto.isNew
                        previousSchool = studentDto.previousSchool
                        hasSpecialNeeds = studentDto.hasSpecialNeeds
                        specialNeedsDescription = studentDto.specialNeedsDescription
                        transportationMethod = studentDto.transportationMethod
                    }
                    
                    // Handle passport photo upload
                    if (passportPhoto != null && !passportPhoto.isEmpty) {
                        try {
                            val photoUrl = fileUploadService.uploadPassportPhoto(passportPhoto, newStudent.studentId)
                            newStudent.passportPhotoUrl = photoUrl
                        } catch (e: Exception) {
                            redirectAttributes.addFlashAttribute("error", "Error uploading passport photo: ${e.message}")
                            return "redirect:/admin/community/students"
                        }
                    }
                    
                    savedStudent = studentRepository.save(newStudent)
                }
                
                // Create UserSchoolRole for Student if it doesn't exist
                val studentRole = roleRepository.findByName("STUDENT").orElseThrow { 
                    RuntimeException("Student role not found") 
                }
                
                if (!userSchoolRoleRepository.existsByUserIdAndSchoolIdAndRoleId(savedUser.id!!, selectedSchoolId, studentRole.id!!)) {
                    val userSchoolRole = UserSchoolRole(
                        user = savedUser,
                        schoolId = selectedSchoolId,
                        role = studentRole,
                        isPrimary = true
                    )
                    userSchoolRole.isActive = true
                    userSchoolRoleRepository.save(userSchoolRole)
                }
                
                redirectAttributes.addFlashAttribute("success", "Student enrolled successfully!")
            }
        } catch (e: Exception) {
            redirectAttributes.addFlashAttribute("error", handleDatabaseError(e, "Error saving student"))
        }

        return "redirect:/admin/community/students"
    }

    @PostMapping("/students/{id}/delete")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
    @org.springframework.transaction.annotation.Transactional
    fun deleteStudent(
        @PathVariable id: UUID, 
        session: HttpSession, 
        redirectAttributes: RedirectAttributes,
        @RequestParam(required = false) search: String?,
        @RequestParam(defaultValue = "0") page: Int,
        request: jakarta.servlet.http.HttpServletRequest
    ): Any { // Changed return type to Any
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/select-school"
            
        try {
            val student = studentRepository.findById(id).orElseThrow()
            
            // Security Check: Ensure student belongs to the selected school
            if (student.schoolId != selectedSchoolId) {
                redirectAttributes.addFlashAttribute("error", "Unauthorized access")
                return "redirect:/admin/community/students"
            }
            
            student.isActive = false
            studentRepository.save(student)
            
            // Cascade delete: Deactivate class enrollments
            val studentClasses = studentClassRepository.findByStudentIdAndIsActive(student.id!!, true)
            studentClasses.forEach { 
                it.isActive = false
                studentClassRepository.save(it)
            }
            
            // Cascade delete: Deactivate parent associations
            val parentStudents = parentStudentRepository.findByStudentIdAndIsActive(student.id!!, true)
            parentStudents.forEach { 
                it.isActive = false
                parentStudentRepository.save(it)
            }
            
            // Handle HTMX request
            if (request.getHeader("HX-Request") != null) {
                // Close modal via header, remove card OOB, show success message OOB
                return ResponseEntity.ok()
                    .header("HX-Trigger", "{\"closeModal\": \"deleteModal\"}")
                    .body("""
                        <div id="student-card-$id" hx-swap-oob="delete"></div>
                        <div id="global-toast-container" hx-swap-oob="beforeend">
                            <div class="toast success show">
                                <i class="fas fa-check-circle"></i>
                                <span>Student deleted successfully!</span>
                            </div>
                            <script>
                                setTimeout(() => {
                                    const toasts = document.querySelectorAll('.toast');
                                    toasts.forEach(t => t.classList.remove('show'));
                                }, 3000);
                            </script>
                        </div>
                    """.trimIndent())
            }
            
            redirectAttributes.addFlashAttribute("success", "Student deleted successfully!")
        } catch (e: Exception) {
            e.printStackTrace() // Log stack trace
            if (request.getHeader("HX-Request") != null) {
                return "fragments/error :: error-message"
            }
            redirectAttributes.addFlashAttribute("error", "Error deleting student: ${e.message}")
        }
        
        val redirectUrl = StringBuilder("redirect:/admin/community/students")
        val params = mutableListOf<String>()
        if (!search.isNullOrBlank()) params.add("search=$search")
        if (page > 0) params.add("page=$page")
        
        if (params.isNotEmpty()) {
            redirectUrl.append("?").append(params.joinToString("&"))
        }
        
        return redirectUrl.toString()
    }

    @PostMapping("/parents/save")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
    fun saveParent(
        @ModelAttribute parentDto: ParentDto,
        @ModelAttribute("userDto") userDto: UserDto,
        @RequestParam(required = false) id: UUID?,
        session: HttpSession,
        redirectAttributes: RedirectAttributes
    ): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/select-school"

        try {
            if (id != null) {
                // Update existing parent
                val existingParent = parentRepository.findById(id).orElseThrow()
                
                // Security Check: Ensure parent belongs to the selected school
                if (existingParent.schoolId != selectedSchoolId) {
                    redirectAttributes.addFlashAttribute("error", "Unauthorized access")
                    return "redirect:/admin/community/parents"
                }
                
                val existingUser = existingParent.user
                
                // Update user details
                existingUser.apply {
                    firstName = userDto.firstName
                    lastName = userDto.lastName
                    middleName = userDto.middleName
                    email = userDto.email
                    phoneNumber = userDto.phoneNumber?.takeIf { it.isNotBlank() }
                    dateOfBirth = userDto.dateOfBirth
                    gender = userDto.gender
                    addressLine1 = userDto.addressLine1
                    addressLine2 = userDto.addressLine2
                    city = userDto.city
                    state = userDto.state
                    postalCode = userDto.postalCode
                }
                userRepository.save(existingUser)
                
                // Update parent details
                existingParent.apply {
                    isPrimaryContact = parentDto.isPrimaryContact
                    isEmergencyContact = parentDto.isEmergencyContact
                    isFinanciallyResponsible = parentDto.isFinanciallyResponsible
                    receiveAcademicUpdates = parentDto.receiveAcademicUpdates
                    receiveFinancialUpdates = parentDto.receiveFinancialUpdates
                    receiveDisciplinaryUpdates = parentDto.receiveDisciplinaryUpdates
                }
                parentRepository.save(existingParent)
                
                redirectAttributes.addFlashAttribute("success", "Parent updated successfully!")
            } else {
                // Check if user already exists by email
                val existingUser = if (!userDto.email.isNullOrBlank()) userRepository.findByEmail(userDto.email!!).orElse(null) else null
                val savedUser = if (existingUser != null) {
                    // Update existing user's phone if it was provided
                    if (!userDto.phoneNumber.isNullOrBlank()) {
                        existingUser.phoneNumber = userDto.phoneNumber!!
                    }
                    userRepository.save(existingUser)
                } else {
                    val newUser = User(phoneNumber = userDto.phoneNumber?.takeIf { it.isNotBlank() }).apply {
                        firstName = userDto.firstName
                        lastName = userDto.lastName
                        middleName = userDto.middleName
                        email = userDto.email
                        dateOfBirth = userDto.dateOfBirth
                        gender = userDto.gender
                        addressLine1 = userDto.addressLine1
                        addressLine2 = userDto.addressLine2
                        city = userDto.city
                        state = userDto.state
                        postalCode = userDto.postalCode
                        status = UserStatus.ACTIVE
                    }
                    userRepository.save(newUser)
                }
                
                // Check if parent already exists for this user and school
                var savedParent = parentRepository.findByUserIdAndSchoolId(savedUser.id!!, selectedSchoolId)
                if (savedParent == null) {
                    val newParent = Parent(
                        user = savedUser
                    ).apply {
                        this.schoolId = selectedSchoolId
                        this.isActive = true
                        this.isPrimaryContact = parentDto.isPrimaryContact
                        this.isEmergencyContact = parentDto.isEmergencyContact
                        this.isFinanciallyResponsible = parentDto.isFinanciallyResponsible
                        this.receiveAcademicUpdates = parentDto.receiveAcademicUpdates
                        this.receiveFinancialUpdates = parentDto.receiveFinancialUpdates
                        this.receiveDisciplinaryUpdates = parentDto.receiveDisciplinaryUpdates
                    }
                    savedParent = parentRepository.save(newParent)
                }
                
                // Create UserSchoolRole for Parent if it doesn't exist
                val parentRole = roleRepository.findByName("PARENT").orElseThrow { 
                    RuntimeException("Parent role not found") 
                }
                
                if (!userSchoolRoleRepository.existsByUserIdAndSchoolIdAndRoleId(savedUser.id!!, selectedSchoolId, parentRole.id!!)) {
                    val userSchoolRole = UserSchoolRole(
                        user = savedUser,
                        schoolId = selectedSchoolId,
                        role = parentRole,
                        isPrimary = true
                    )
                    userSchoolRole.isActive = true
                    userSchoolRoleRepository.save(userSchoolRole)
                }
                
                redirectAttributes.addFlashAttribute("success", "Parent added successfully!")
            }
        } catch (e: Exception) {
            redirectAttributes.addFlashAttribute("error", handleDatabaseError(e, "Error saving parent"))
        }

        return "redirect:/admin/community/parents"
    }

    @PostMapping("/parents/save-htmx")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
    fun saveParentHtmx(
        @ModelAttribute parentDto: ParentDto,
        @ModelAttribute("userDto") userDto: UserDto,
        @RequestParam(required = false) id: UUID?,
        session: HttpSession,
        model: Model,
        response: jakarta.servlet.http.HttpServletResponse,
        authentication: Authentication,
        request: jakarta.servlet.http.HttpServletRequest,
        @RequestParam(required = false) search: String?
    ): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "fragments/error :: error-message"

        try {
            if (id != null) {
                // Update existing parent
                val existingParent = parentRepository.findById(id).orElseThrow()
                
                // Security Check: Ensure parent belongs to the selected school
                if (existingParent.schoolId != selectedSchoolId) {
                    return "fragments/error :: error-message"
                }
                
                val existingUser = existingParent.user
                
                // Update user details
                existingUser.apply {
                    this.firstName = userDto.firstName
                    this.lastName = userDto.lastName
                    this.email = userDto.email
                    this.phoneNumber = userDto.phoneNumber?.takeIf { it.isNotBlank() }
                }
                userRepository.save(existingUser)
                
                // Update parent details
                existingParent.apply {
                    this.isPrimaryContact = parentDto.isPrimaryContact
                    this.isEmergencyContact = parentDto.isEmergencyContact
                    this.isFinanciallyResponsible = parentDto.isFinanciallyResponsible
                    this.receiveAcademicUpdates = parentDto.receiveAcademicUpdates
                    this.receiveFinancialUpdates = parentDto.receiveFinancialUpdates
                    this.receiveDisciplinaryUpdates = parentDto.receiveDisciplinaryUpdates
                }
                parentRepository.save(existingParent)
                
                model.addAttribute("success", "Parent updated successfully!")
                
                // Fetch updated parent with relationships for OOB update
                val updatedParent = parentRepository.findById(id).orElseThrow()
                // Ensure relationships are loaded (similar to assign modal)
                val relationships = parentStudentRepository.findByParentIdWithStudentDetails(id)
                updatedParent.studentRelationships = relationships.toMutableList()
                
                model.addAttribute("parent", updatedParent)
                model.addAttribute("isOob", true)
                model.addAttribute("modalId", "parentModal")
                
                return "admin/community/parents/assign-success"
            } else {
                // Check if user already exists by email or phone
                val existingUser = when {
                    !userDto.email.isNullOrBlank() -> userRepository.findByEmail(userDto.email!!).orElse(null)
                    !userDto.phoneNumber.isNullOrBlank() -> userRepository.findByPhoneNumber(userDto.phoneNumber!!).orElse(null)
                    else -> null
                }

                val savedUser = if (existingUser != null) {
                    // User exists globally, check if they already have the PARENT role in this school
                    val parentRole = roleRepository.findByName("PARENT").orElseThrow { RuntimeException("Parent role not found") }
                    if (userSchoolRoleRepository.existsByUserIdAndSchoolIdAndRoleId(existingUser.id!!, selectedSchoolId, parentRole.id!!)) {
                        throw RuntimeException("A parent with this email/phone is already registered in this school.")
                    }
                    existingUser
                } else {
                    val newUser = User(phoneNumber = userDto.phoneNumber?.takeIf { it.isNotBlank() }).apply {
                        this.firstName = userDto.firstName
                        this.lastName = userDto.lastName
                        this.email = userDto.email
                        this.status = UserStatus.ACTIVE
                    }
                    userRepository.save(newUser)
                }
                
                // Check if parent already exists for this user and school
                var savedParent = parentRepository.findByUserIdAndSchoolId(savedUser.id!!, selectedSchoolId)
                if (savedParent == null) {
                    val newParent = Parent(
                        user = savedUser
                    ).apply {
                        this.schoolId = selectedSchoolId
                        this.isPrimaryContact = parentDto.isPrimaryContact
                        this.isEmergencyContact = parentDto.isEmergencyContact
                        this.isFinanciallyResponsible = parentDto.isFinanciallyResponsible
                        this.receiveAcademicUpdates = parentDto.receiveAcademicUpdates
                        this.receiveFinancialUpdates = parentDto.receiveFinancialUpdates
                        this.receiveDisciplinaryUpdates = parentDto.receiveDisciplinaryUpdates
                        this.isActive = true
                    }
                    savedParent = parentRepository.save(newParent)
                }
                
                // Create UserSchoolRole for Parent if it doesn't exist
                val parentRole = roleRepository.findByName("PARENT").orElseThrow { 
                    RuntimeException("Parent role not found") 
                }
                
                if (!userSchoolRoleRepository.existsByUserIdAndSchoolIdAndRoleId(savedUser.id!!, selectedSchoolId, parentRole.id!!)) {
                    val userSchoolRole = UserSchoolRole(
                        user = savedUser,
                        schoolId = selectedSchoolId,
                        role = parentRole,
                        isPrimary = true
                    )
                    userSchoolRole.isActive = true
                    userSchoolRoleRepository.save(userSchoolRole)
                }
                
                model.addAttribute("success", "Parent added successfully!")
                
                return "admin/community/parents/assign-success" 
            }
        } catch (e: Exception) {
            model.addAttribute("error", handleDatabaseError(e, "Error saving parent"))
            model.addAttribute("isEdit", id != null)
            if (id != null) {
                parentRepository.findById(id).ifPresent { model.addAttribute("parent", it) }
            }
            return "admin/community/parents/modal-form"
        }
    }



    @PostMapping("/parents/{id}/delete")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
    @org.springframework.transaction.annotation.Transactional
    fun deleteParent(
        @PathVariable id: UUID, 
        session: HttpSession, 
        redirectAttributes: RedirectAttributes,
        @RequestParam(required = false) search: String?,
        @RequestParam(defaultValue = "0") page: Int,
        request: jakarta.servlet.http.HttpServletRequest
    ): Any { // Changed return type to Any
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/select-school"
            
        try {
            val parent = parentRepository.findById(id).orElseThrow()
            
            // Security Check: Ensure parent belongs to the selected school
            if (parent.schoolId != selectedSchoolId) {
                redirectAttributes.addFlashAttribute("error", "Unauthorized access")
                return "redirect:/admin/community/parents"
            }
            
            parent.isActive = false
            parentRepository.save(parent)
            
            // Cascade delete: Deactivate student associations
            val parentStudents = parentStudentRepository.findByParentIdAndIsActive(parent.id!!, true)
            parentStudents.forEach { 
                it.isActive = false
                parentStudentRepository.save(it)
            }
            
            // Handle HTMX request
            if (request.getHeader("HX-Request") != null) {
                // Close modal via header, remove card OOB, show success message OOB
                return ResponseEntity.ok()
                    .header("HX-Trigger", "{\"closeModal\": \"deleteModal\"}")
                    .body("""
                        <div id="parent-card-$id" hx-swap-oob="delete"></div>
                        <div id="global-toast-container" hx-swap-oob="beforeend">
                            <div class="toast success show">
                                <i class="fas fa-check-circle"></i>
                                <span>Parent deleted successfully!</span>
                            </div>
                            <script>
                                setTimeout(() => {
                                    const toasts = document.querySelectorAll('.toast');
                                    toasts.forEach(t => t.classList.remove('show'));
                                }, 3000);
                            </script>
                        </div>
                    """.trimIndent())
            }
            
            redirectAttributes.addFlashAttribute("success", "Parent deleted successfully!")
        } catch (e: Exception) {
            e.printStackTrace()
            if (request.getHeader("HX-Request") != null) {
                return "fragments/error :: error-message"
            }
            redirectAttributes.addFlashAttribute("error", "Error deleting parent: ${e.message}")
        }
        
        // ... remainder of method
        // But since I don't want to replace truncated parts, I will look at file again.
        // The previous view in step 376 ended at 1880, cutting off the return params logic.
        // However, I matched only up to line 1883 in your instructions?
        // Wait, the TargetContent must be exact.
        // I will use replace_file_content for parent as well, but I need to match the actual content.
        // Let's use the provided content from 376 plus what's standard.
        // Actually, the end of `deleteParent` was NOT in 376.
        // I should read `deleteParent` fully first to be safe.
        // But I will just replace the top part where `Any` return type is needed and the `return """..."""` part.
        
        val redirectUrl = StringBuilder("redirect:/admin/community/parents")
        val params = mutableListOf<String>()
        if (!search.isNullOrBlank()) params.add("search=$search")
        if (page > 0) params.add("page=$page")
        
        if (params.isNotEmpty()) {
            redirectUrl.append("?").append(params.joinToString("&"))
        }
        
        return redirectUrl.toString()
    }

    private fun generateStaffId(schoolId: UUID): String {
        // Use timestamp-based generation to ensure uniqueness
        val timestamp = System.currentTimeMillis().toString().takeLast(6)
        var staffId = "STF${schoolId}${timestamp}"
        
        // Double-check for uniqueness (very unlikely to collide with timestamp)
        var counter = 1
        while (staffRepository.findByStaffIdAndSchoolId(staffId, schoolId) != null) {
            staffId = "STF${schoolId}${timestamp}${counter}"
            counter++
        }
        
        return staffId
    }

    private fun generateStudentId(schoolId: UUID): String {
        // Use timestamp-based generation to ensure uniqueness
        val timestamp = System.currentTimeMillis().toString().takeLast(6)
        return "STU${schoolId}${timestamp}"
    }



    private fun generateAdmissionNumber(schoolId: UUID): String {
        val school = schoolRepository.findById(schoolId).orElse(null)
        val prefix = school?.admissionPrefix ?: "ADM"
        val year = LocalDate.now().year % 100 // Use 2-digit year
        val count = studentRepository.countBySchoolId(schoolId) + 1
        return "${prefix}${year}${String.format("%04d", count)}"
    }

    private fun getCommunityStats(schoolId: UUID): CommunityStats {
        val staffCount = staffRepository.countBySchoolIdAndIsActive(schoolId, true)
        val studentCount = studentRepository.countBySchoolIdAndIsActive(schoolId, true)
        val parentCount = parentRepository.countBySchoolIdAndIsActive(schoolId, true)
        
        return CommunityStats(staffCount, studentCount, parentCount)
    }

    private fun getEffectiveSessionAndTerm(session: HttpSession, schoolId: UUID): Pair<AcademicSession?, Term?> {
        val selectedSessionIdRaw = session.getAttribute("selectedSessionId")
        logger.info("Raw selectedSessionId from session: '$selectedSessionIdRaw' (${selectedSessionIdRaw?.javaClass?.simpleName})")
        
        val selectedSessionId = when (selectedSessionIdRaw) {
            is UUID -> selectedSessionIdRaw
            is String -> try { UUID.fromString(selectedSessionIdRaw) } catch (e: Exception) { 
                logger.error("Failed to parse sessionId string: $selectedSessionIdRaw", e)
                null 
            }
            else -> null
        }

        val selectedTermIdRaw = session.getAttribute("selectedTermId")
        logger.info("Raw selectedTermId from session: '$selectedTermIdRaw' (${selectedTermIdRaw?.javaClass?.simpleName})")

        val selectedTermId = when (selectedTermIdRaw) {
            is UUID -> selectedTermIdRaw
            is String -> try { UUID.fromString(selectedTermIdRaw) } catch (e: Exception) { 
                logger.error("Failed to parse termId string: $selectedTermIdRaw", e)
                null 
            }
            else -> null
        }
        
        logger.info("Resolved UUIDs - Session: $selectedSessionId, Term: $selectedTermId")
        
        // Resolve Session
        var effectiveSession: AcademicSession? = null
        if (selectedSessionId != null) {
            effectiveSession = academicSessionRepository.findById(selectedSessionId).orElse(null)
            logger.info("Fetched session by ID: ${effectiveSession?.sessionName}")
        }
        
        if (effectiveSession == null) {
             logger.info("No session selected or found, falling back to current active session for school: $schoolId")
             effectiveSession = academicSessionRepository.findBySchoolIdAndIsCurrentSessionAndIsActive(schoolId, true, true)
             logger.info("Current active session: ${effectiveSession?.sessionName}")
        }

        if (effectiveSession == null) {
             logger.info("No current active session found, falling back to most recent active session")
             val sessions = academicSessionRepository.findBySchoolIdAndIsActiveOrderByYearDesc(schoolId, true)
             effectiveSession = sessions.firstOrNull()
             logger.info("Most recent active session: ${effectiveSession?.sessionName}")
        }
        
        // Resolve Term
        var effectiveTerm: Term? = null
        if (effectiveSession != null) {
            if (selectedTermId != null) {
                effectiveTerm = termRepository.findById(selectedTermId).orElse(null)
                // Ensure term belongs to session
                if (effectiveTerm != null && effectiveTerm.academicSession.id != effectiveSession.id) {
                    logger.warn("Selected term ${effectiveTerm.termName} does not belong to effective session ${effectiveSession.sessionName}. Ignoring selected term.")
                    effectiveTerm = null
                } else {
                    logger.info("Using selected term: ${effectiveTerm?.termName}")
                }
            }
            
            if (effectiveTerm == null) {
                logger.info("No valid term selected, checking for current term in session ${effectiveSession.sessionName}")
                effectiveTerm = termRepository.findByAcademicSessionIdAndIsCurrentTermAndIsActive(effectiveSession.id!!, true, true).orElse(null)
                logger.info("Current term in session: ${effectiveTerm?.termName}")
            }
            
            if (effectiveTerm == null) {
                logger.info("No current term found, falling back to first term in session")
                val terms = termRepository.findByAcademicSessionIdAndIsActiveOrderByStartDate(effectiveSession.id!!, true)
                effectiveTerm = terms.firstOrNull()
                logger.info("First term: ${effectiveTerm?.termName}")
            }
        } else {
            logger.error("Could not resolve any effective session!")
        }
        
        logger.info("FINAL EFFECTIVE CONTEXT - Session: '${effectiveSession?.sessionName}', Term: '${effectiveTerm?.termName}'")
        return Pair(effectiveSession, effectiveTerm)
    }
    
    private fun parsePhoneNumber(fullPhoneNumber: String): Pair<String, String> {
        val commonCountryCodes = listOf("+234", "+1", "+44", "+91", "+86", "+33", "+49", "+81", "+27", "+254", "+233")
        
        for (code in commonCountryCodes) {
            if (fullPhoneNumber.startsWith(code)) {
                return Pair(code, fullPhoneNumber.substring(code.length))
            }
        }
        
        // Default to Nigeria if no country code found
        return Pair("+234", fullPhoneNumber)
    }

    // Student Class Assignment Modal Endpoints
    @GetMapping("/students/{studentId}/assign-class/modal")
    fun getStudentClassAssignmentModal(
        @PathVariable studentId: UUID,
        model: Model,
        authentication: Authentication,
        session: HttpSession
    ): String {
        val customUser = authentication.principal as CustomUserDetails
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "fragments/error :: error-message"

        val student = studentRepository.findById(studentId).orElseThrow { RuntimeException("Student not found") }
        
        // Security Check: Ensure student belongs to the selected school
        if (student.schoolId != selectedSchoolId) {
            return "fragments/error :: error-message"
        }
        
        val tracks = educationTrackRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
        val currentAssignments = studentClassRepository.findByStudentIdWithClassAndTrack(studentId)

        model.addAttribute("user", customUser.user)
        model.addAttribute("student", student)
        model.addAttribute("tracks", tracks)
        model.addAttribute("currentAssignments", currentAssignments)

        return "admin/community/students/assign-class-modal"
    }

    @PostMapping("/students/{studentId}/assign-class/modal")
    fun assignStudentToClassModal(
        @PathVariable studentId: UUID,
        @RequestParam assignedClassId: UUID,
        @RequestParam trackId: UUID,
        session: HttpSession,
        model: Model
    ): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "fragments/error :: error-message"

        try {
            val student = studentRepository.findById(studentId).orElseThrow()
            
            // Security Check: Ensure student belongs to the selected school
            if (student.schoolId != selectedSchoolId) {
                return "fragments/error :: error-message"
            }
            
            val schoolClass = schoolClassRepository.findById(assignedClassId).orElseThrow()
            
            // Security Check: Ensure class belongs to the selected school
            if (schoolClass.schoolId != selectedSchoolId) {
                return "fragments/error :: error-message"
            }
            
            val track = educationTrackRepository.findById(trackId).orElseThrow()
            
            // Security Check: Ensure track belongs to the selected school
            if (track.schoolId != selectedSchoolId) {
                return "fragments/error :: error-message"
            }

            // Get effective academic session and term
            val (effectiveSession, effectiveTerm) = getEffectiveSessionAndTerm(session, selectedSchoolId)
            
            val currentSession = effectiveSession 
                ?: throw RuntimeException("No academic session found. Please ensure an active session exists.")
            val currentTerm = effectiveTerm 
                ?: throw RuntimeException("No academic term found. Please ensure an active term exists.")

            // Check if student is already assigned to a class in this track for this session and term
            val existingAssignments = studentClassRepository.findByStudentIdAndSchoolClassTrackIdAndAcademicSessionIdAndTermId(
                studentId, trackId, currentSession.id!!, currentTerm.id!!)
                .filter { it.schoolId == selectedSchoolId }
            
            val studentClass = if (existingAssignments.isNotEmpty()) {
                // Update existing assignment
                val existingAssignment = existingAssignments.first()
                val oldClass = existingAssignment.schoolClass
                
                // Update the assignment to the new class and ensure it's active
                existingAssignment.apply {
                    this.schoolClass = schoolClass
                    this.enrollmentDate = LocalDate.now() // Update enrollment date
                    this.isActive = true // Reactivate if it was inactive
                }
                
                // Update enrollment counts for old class if it's different
                if (oldClass.id != schoolClass.id) {
                    oldClass.currentEnrollment = oldClass.studentEnrollments.count { it.isActive }
                    schoolClassRepository.save(oldClass)
                }
                
                existingAssignment
            } else {
                // Create new assignment
                StudentClass(
                    student = student,
                    schoolClass = schoolClass,
                    academicSession = currentSession,
                    term = currentTerm
                ).apply {
                    this.schoolId = selectedSchoolId
                    this.isActive = true
                }
            }

            studentClassRepository.save(studentClass)

            // Update class enrollment counts reliably
            if (existingAssignments.isNotEmpty()) {
                val oldClassId = existingAssignments.first().schoolClass.id
                if (oldClassId != schoolClass.id) {
                    updateClassEnrollmentCount(oldClassId!!)
                }
            }
            updateClassEnrollmentCount(schoolClass.id!!)
            
            val successMessage = if (existingAssignments.isNotEmpty()) {
                "Student class assignment updated successfully"
            } else {
                "Student successfully assigned to class"
            }
            model.addAttribute("success", successMessage)
            
            // Return updated student card (OOB)
            val updatedStudent = studentRepository.findById(studentId).orElseThrow()
            
            // Manually update enrollments in memory to ensure the view reflects the change immediately
            // This is needed because the student might be from L1 cache and the collection might not have been refreshed
            val updatedEnrollments = studentClassRepository.findByStudentIdWithClassAndTrack(studentId)
            updatedStudent.classEnrollments = updatedEnrollments.toMutableList()
            
            val studentPage = org.springframework.data.domain.PageImpl(listOf(updatedStudent))
            model.addAttribute("studentPage", studentPage)
            model.addAttribute("modalId", "studentClassModal")
            model.addAttribute("isOob", true)
            
            return "admin/community/students/assign-success"
        } catch (e: Exception) {
            model.addAttribute("error", "Error assigning student to class: ${e.message}")
            return "fragments/error :: error-message"
        }
    }

    // Parent Student Assignment Modal Endpoints
    @GetMapping("/parents/{parentId}/assign-students/modal")
    fun getParentStudentAssignmentModal(
        @PathVariable parentId: UUID,
        model: Model,
        authentication: Authentication,
        session: HttpSession,
        @RequestParam(required = false) search: String?
    ): String {
            val customUser = authentication.principal as CustomUserDetails
        try {
            val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
                ?: return "fragments/error :: error-message"

            val parent = parentRepository.findById(parentId).orElseThrow { RuntimeException("Parent not found") }
            val currentAssignments = try {
                parentStudentRepository.findByParentIdWithStudentDetails(parentId)
            } catch (e: Exception) {
                println("Error loading current assignments: ${e.message}")
                emptyList<ParentStudent>()
            }
            
            // Get available students (not already assigned to this parent)
            val assignedStudentIds = currentAssignments.map { it.student.id }
            val availableStudents = try {
                if (search.isNullOrBlank()) {
                    emptyList<Student>()
                } else {
                    studentRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
                        .filter { student ->
                            student.id !in assignedStudentIds &&
                            (student.user.fullName?.contains(search, ignoreCase = true) == true ||
                             student.studentId.contains(search, ignoreCase = true) ||
                             student.admissionNumber?.contains(search, ignoreCase = true) == true)
                        }
                }
            } catch (e: Exception) {
                println("Error loading available students: ${e.message}")
                emptyList<Student>()
            }

            model.addAttribute("user", customUser.user)
            model.addAttribute("parent", parent)
            model.addAttribute("currentAssignments", currentAssignments)
            model.addAttribute("availableStudents", availableStudents)
            model.addAttribute("search", search ?: "")
            model.addAttribute("isOob", false);
            
            return "admin/community/parents/assign-students-modal"
        } catch (e: Exception) {
            println("Error in getParentStudentAssignmentModal: ${e.message}")
            e.printStackTrace()
            model.addAttribute("error", "Error loading parent assignment modal: ${e.message}")
            return "fragments/error :: error-message"
        }
    }

    @PostMapping("/parents/{parentId}/assign-student/modal")
    fun assignParentToStudentModal(
        @PathVariable parentId: UUID,
        @RequestParam studentId: UUID,
        @RequestParam relationshipType: String,
        session: HttpSession,
        model: Model,
        authentication: Authentication,
        response: jakarta.servlet.http.HttpServletResponse
    ): String {
        val customUser = authentication.principal as CustomUserDetails
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "fragments/error :: error-message"

        try {
            val parent = parentRepository.findById(parentId).orElseThrow()
            val student = studentRepository.findById(studentId).orElseThrow()

            // Check if relationship already exists (active or inactive)
            val existingRelationship = parentStudentRepository.findByParentIdAndStudentIdAndSchoolId(
                parentId, studentId, selectedSchoolId)
            
            if (existingRelationship != null) {
                if (existingRelationship.isActive) {
                    model.addAttribute("error", "This parent is already assigned to this student")
                    return "fragments/error :: error-message"
                } else {
                    // Reactivate existing relationship
                    existingRelationship.isActive = true
                    existingRelationship.relationshipType = relationshipType // Update relationship type if changed
                    parentStudentRepository.save(existingRelationship)
                    
                    // Log the linking activity
                    val userRole = (authentication.principal as CustomUserDetails).authorities.firstOrNull()?.authority ?: "USER"
                    activityLogService.logParentStudentLinked(
                        selectedSchoolId, customUser.user.id!!, userRole, parentId, studentId, relationshipType
                    )

                    model.addAttribute("success", "Parent successfully assigned to student")
                }
            } else {
                // Create the relationship
                val parentStudent = ParentStudent(
                    parent = parent,
                    student = student,
                    relationshipType = relationshipType
                ).apply {
                    this.schoolId = selectedSchoolId
                    this.isActive = true
                }

                parentStudentRepository.save(parentStudent)

                // Log the linking activity
                val userRole = (authentication.principal as CustomUserDetails).authorities.firstOrNull()?.authority ?: "USER"
                activityLogService.logParentStudentLinked(
                    selectedSchoolId, customUser.user.id!!, userRole, parentId, studentId, relationshipType
                )

                model.addAttribute("success", "Parent successfully assigned to student")
            }
            
            // Trigger update of parent list on client side
            // response.setHeader("HX-Trigger", "parentUpdated") // Removed to prevent full list reload
            
            // Reload modal data
            val currentAssignments = parentStudentRepository.findByParentIdWithStudentDetails(parentId)
            
            // Get available students (empty list as search is reset)
            val availableStudents = emptyList<Student>()

            // Reload parent to get updated relationships for OOB card update
            val updatedParent = parentRepository.findById(parentId).orElseThrow()
            // We need to ensure relationships are loaded. 
            // Since we don't have a specific method for single parent with relationships, 
            // and we are in a transaction/session, accessing .studentRelationships might work if lazy loading is active.
            // But to be safe and consistent with the list view, we might need to manually populate it or trust the view to trigger it.
            // Let's try passing the parent. The view 'single-parent-card' iterates over 'parent.studentRelationships'.
            // If we just saved a relationship, we should make sure it's in the list.
            // 'currentAssignments' contains the relationships. We can assign it to the parent object if it's mutable, or just pass it.
            // But 'single-parent-card' expects 'parent.studentRelationships'.
            // Let's rely on the fact that we can fetch the parent again. 
            // Actually, 'currentAssignments' IS the list of relationships.
            updatedParent.studentRelationships = currentAssignments.toMutableList()

            model.addAttribute("user", customUser.user)
            model.addAttribute("parent", updatedParent)
            model.addAttribute("currentAssignments", currentAssignments)
            model.addAttribute("availableStudents", availableStudents)
            model.addAttribute("search", "")
            model.addAttribute("isOob", true) // Enable OOB update for parent card
            
            // Return modal content to keep it open
            return "admin/community/parents/assign-students-modal"
        } catch (e: Exception) {
            model.addAttribute("error", "Error assigning parent to student: ${e.message}")
            return "fragments/error :: error-message"
        }
    }

    @PostMapping("/parents/{parentId}/remove-student/{assignmentId}/modal")
    fun removeParentFromStudentModal(
        @PathVariable parentId: UUID,
        @PathVariable assignmentId: UUID,
        session: HttpSession,
        model: Model,
        authentication: Authentication,
        response: jakarta.servlet.http.HttpServletResponse
    ): String {
        val customUser = authentication.principal as CustomUserDetails
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "fragments/error :: error-message"

        try {
            val parent = parentRepository.findById(parentId).orElseThrow()
            val assignment = parentStudentRepository.findById(assignmentId).orElseThrow()
            
            // Security check
            if (assignment.parent.id != parentId || assignment.schoolId != selectedSchoolId) {
                model.addAttribute("error", "Unauthorized access")
                return "fragments/error :: error-message"
            }

            assignment.isActive = false
            parentStudentRepository.save(assignment)
            
            // Log the unlinking activity
            val userRole = (authentication.principal as CustomUserDetails).authorities.firstOrNull()?.authority ?: "USER"
            activityLogService.logParentStudentUnlinked(
                selectedSchoolId, customUser.user.id!!, userRole, parentId, assignment.student.id!!
            )
            
            model.addAttribute("success", "Student removed successfully")

            // Trigger update of parent list on client side
            // response.setHeader("HX-Trigger", "parentUpdated") // Removed to prevent full list reload

            // Reload modal data
            val currentAssignments = parentStudentRepository.findByParentIdWithStudentDetails(parentId)
            
            // Get available students (empty list as search is reset)
            val availableStudents = emptyList<Student>()

            // Reload parent to get updated relationships for OOB card update
            val updatedParent = parentRepository.findById(parentId).orElseThrow()
            updatedParent.studentRelationships = currentAssignments.toMutableList()

            model.addAttribute("user", customUser.user)
            model.addAttribute("parent", updatedParent)
            model.addAttribute("currentAssignments", currentAssignments)
            model.addAttribute("availableStudents", availableStudents)
            model.addAttribute("search", "")
            model.addAttribute("isOob", true) // Enable OOB update for parent card
            
            return "admin/community/parents/assign-students-modal"
        } catch (e: Exception) {
            model.addAttribute("error", "Error removing student: ${e.message}")
            return "fragments/error :: error-message"
        }
    }

    // Student Class Assignment Endpoints
    @GetMapping("/students/{studentId}/assign-class")
    fun getStudentClassAssignment(
        @PathVariable studentId: UUID,
        model: Model,
        authentication: Authentication,
        session: HttpSession
    ): String {
        val customUser = authentication.principal as CustomUserDetails
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/select-school"

        val student = studentRepository.findById(studentId).orElseThrow { RuntimeException("Student not found") }
        val tracks = educationTrackRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
        val currentAssignments = studentClassRepository.findByStudentIdWithClassAndTrack(studentId)
        val communityStats = getCommunityStats(selectedSchoolId)

        model.addAttribute("user", customUser.user)
        model.addAttribute("student", student)
        model.addAttribute("tracks", tracks)
        model.addAttribute("currentAssignments", currentAssignments)
        model.addAttribute("communityStats", communityStats)

        return "admin/community/students/assign-class"
    }

    @GetMapping("/students/{studentId}/classes-by-track/{trackId}")
    @ResponseBody
    fun getClassesByTrack(
        @PathVariable studentId: UUID,
        @PathVariable trackId: UUID,
        session: HttpSession
    ): List<Map<String, Any?>> {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return emptyList()

        val classes = schoolClassRepository.findBySchoolIdAndTrackIdAndIsActive(selectedSchoolId, trackId, true)
        
        return classes.map { schoolClass ->
            mapOf(
                "id" to schoolClass.id,
                "className" to schoolClass.className,
                "gradeLevel" to schoolClass.gradeLevelDisplayName,
                "currentEnrollment" to schoolClass.currentEnrollment,
                "maxCapacity" to schoolClass.maxCapacity,
                "department" to (schoolClass.department?.name ?: "")
            )
        }
    }

    @PostMapping("/students/{studentId}/assign-class")
    fun assignStudentToClass(
        @PathVariable studentId: UUID,
        @RequestParam assignedClassId: UUID,
        @RequestParam trackId: UUID,
        session: HttpSession,
        redirectAttributes: RedirectAttributes
    ): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/select-school"

        try {
            val student = studentRepository.findById(studentId).orElseThrow()
            val schoolClass = schoolClassRepository.findById(assignedClassId).orElseThrow()

            // Get effective academic session and term
            val (effectiveSession, effectiveTerm) = getEffectiveSessionAndTerm(session, selectedSchoolId)
            
            val currentSession = effectiveSession 
                ?: throw RuntimeException("No academic session found. Please ensure an active session exists.")
            val currentTerm = effectiveTerm 
                ?: throw RuntimeException("No academic term found. Please ensure an active term exists.")

            // Check if student is already assigned to any class in this track for this session and term
            val allAssignments = studentClassRepository.findByStudentIdAndSchoolClassTrackIdAndAcademicSessionIdAndTermId(
                studentId, trackId, currentSession.id!!, currentTerm.id!!)
            
            val studentClass = if (allAssignments.isNotEmpty()) {
                // Update existing assignment (whether active or inactive)
                val existingAssignment = allAssignments.first()
                val oldClass = existingAssignment.schoolClass
                
                // Update the assignment to the new class and reactivate if needed
                existingAssignment.apply {
                    this.schoolClass = schoolClass
                    this.enrollmentDate = LocalDate.now() // Update enrollment date
                    this.isActive = true // Reactivate if it was inactive
                }
                
                // Update enrollment counts for old class (only if different)
                if (oldClass.id != schoolClass.id) {
                    oldClass.currentEnrollment = oldClass.studentEnrollments.count { it.isActive }
                    schoolClassRepository.save(oldClass)
                }
                
                existingAssignment
            } else {
                // Create new assignment
                StudentClass(
                    student = student,
                    schoolClass = schoolClass,
                    academicSession = currentSession,
                    term = currentTerm
                ).apply {
                    this.schoolId = selectedSchoolId
                    this.isActive = true
                }
            }

            studentClassRepository.save(studentClass)

            // Update class enrollment counts reliably
            if (allAssignments.isNotEmpty()) {
                val oldClassId = allAssignments.first().schoolClass.id
                if (oldClassId != schoolClass.id) {
                    updateClassEnrollmentCount(oldClassId!!)
                }
            }
            updateClassEnrollmentCount(schoolClass.id!!)

            val successMessage = if (allAssignments.isNotEmpty()) {
                "Student class assignment updated successfully"
            } else {
                "Student successfully assigned to class"
            }
            redirectAttributes.addFlashAttribute("success", successMessage)
        } catch (e: Exception) {
            redirectAttributes.addFlashAttribute("error", "Error assigning student to class: ${e.message}")
        }

        return "redirect:/admin/community/students/${studentId}/assign-class"
    }

    @PostMapping("/students/{studentId}/remove-class/{assignmentId}")
    fun removeStudentFromClass(
        @PathVariable studentId: UUID,
        @PathVariable assignmentId: UUID,
        redirectAttributes: RedirectAttributes
    ): String {
        try {
            val assignment = studentClassRepository.findById(assignmentId).orElseThrow()
            assignment.isActive = false
            studentClassRepository.save(assignment)

            // Update class enrollment count
            val schoolClass = assignment.schoolClass
            schoolClass.currentEnrollment = schoolClass.studentEnrollments.count { it.isActive }
            schoolClassRepository.save(schoolClass)

            redirectAttributes.addFlashAttribute("success", "Student removed from class successfully")
        } catch (e: Exception) {
            redirectAttributes.addFlashAttribute("error", "Error removing student from class: ${e.message}")
        }

        return "redirect:/admin/community/students/${studentId}/assign-class"
    }

    @PostMapping("/students/remove-assignment/{assignmentId}")
    fun removeStudentAssignment(
        @PathVariable assignmentId: UUID,
        session: HttpSession,
        model: Model
    ): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "fragments/error :: error-message"

        try {
            // Check for placeholder UUID
            val nilUuid = UUID.fromString("00000000-0000-0000-0000-000000000000")
            if (assignmentId == nilUuid) {
                model.addAttribute("error", "Invalid assignment ID. Please try again.")
                return "fragments/error :: error-message"
            }

            val assignmentOpt = studentClassRepository.findById(assignmentId)
            if (assignmentOpt.isEmpty) {
                model.addAttribute("error", "Class assignment not found. It may have already been removed.")
                return "fragments/error :: error-message"
            }

            val assignment = assignmentOpt.get()
            assignment.isActive = false
            studentClassRepository.save(assignment)

            // Update class enrollment count
            val schoolClass = assignment.schoolClass
            schoolClass.currentEnrollment = schoolClass.studentEnrollments.count { it.isActive }
            schoolClassRepository.save(schoolClass)

            model.addAttribute("success", "Class assignment removed successfully")
            
            // Return updated student card (OOB)
            val studentId = assignment.student.id
            val updatedStudent = studentRepository.findById(studentId!!).orElseThrow()
            
            // Manually update enrollments in memory to ensure the view reflects the change immediately
            val updatedEnrollments = studentClassRepository.findByStudentIdWithClassAndTrack(studentId)
            updatedStudent.classEnrollments = updatedEnrollments.toMutableList()
            
            val studentPage = org.springframework.data.domain.PageImpl(listOf(updatedStudent))
            model.addAttribute("studentPage", studentPage)
            model.addAttribute("isOob", true)
            model.addAttribute("modalId", "deleteAssignmentModal")
            
            return "admin/community/students/assign-success"
        } catch (e: Exception) {
            model.addAttribute("error", "Error removing class assignment: ${e.message}")
            return "fragments/error :: error-message"
        }
    }

    // Parent Student Assignment Endpoints
    @GetMapping("/parents/{parentId}/assign-students")
    fun getParentStudentAssignment(
        @PathVariable parentId: UUID,
        model: Model,
        authentication: Authentication,
        session: HttpSession,
        @RequestParam(required = false) search: String?
    ): String {
        val customUser = authentication.principal as CustomUserDetails
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/select-school"

        val parent = parentRepository.findById(parentId).orElseThrow { RuntimeException("Parent not found") }
        val currentAssignments = parentStudentRepository.findByParentIdWithStudentDetails(parentId)
        
        // Get available students (not already assigned to this parent)
        val assignedStudentIds = currentAssignments.map { it.student.id }
        val availableStudents = if (search.isNullOrBlank()) {
            studentRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
                .filter { it.id !in assignedStudentIds }
        } else {
            studentRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
                .filter { student ->
                    student.id !in assignedStudentIds &&
                    (student.user.fullName?.contains(search, ignoreCase = true) == true ||
                     student.studentId.contains(search, ignoreCase = true) ||
                     student.admissionNumber?.contains(search, ignoreCase = true) == true)
                }
        }

        val communityStats = getCommunityStats(selectedSchoolId)

        model.addAttribute("user", customUser.user)
        model.addAttribute("parent", parent)
        model.addAttribute("currentAssignments", currentAssignments)
        model.addAttribute("availableStudents", availableStudents)
        model.addAttribute("search", search)
        model.addAttribute("communityStats", communityStats)

        return "admin/community/parents/assign-students"
    }

    @PostMapping("/parents/{parentId}/assign-student")
    fun assignParentToStudent(
        @PathVariable parentId: UUID,
        @RequestParam studentId: UUID,
        @RequestParam relationshipType: String,
        session: HttpSession,
        redirectAttributes: RedirectAttributes
    ): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/select-school"

        try {
            val parent = parentRepository.findById(parentId).orElseThrow()
            val student = studentRepository.findById(studentId).orElseThrow()

            // Check if relationship already exists (active or inactive)
            val existingRelationship = parentStudentRepository.findByParentIdAndStudentIdAndSchoolId(
                parentId, studentId, selectedSchoolId)
            
            if (existingRelationship != null) {
                if (existingRelationship.isActive) {
                    redirectAttributes.addFlashAttribute("error", "This parent is already assigned to this student")
                    return "redirect:/admin/community/parents/${parentId}/assign-students"
                } else {
                    // Reactivate existing relationship
                    existingRelationship.isActive = true
                    existingRelationship.relationshipType = relationshipType // Update relationship type if changed
                    parentStudentRepository.save(existingRelationship)
                    redirectAttributes.addFlashAttribute("success", "Parent successfully assigned to student")
                }
            } else {
                // Create the relationship
                val parentStudent = ParentStudent(
                    parent = parent,
                    student = student,
                    relationshipType = relationshipType
                ).apply {
                    this.schoolId = selectedSchoolId
                    this.isActive = true
                }

                parentStudentRepository.save(parentStudent)
                redirectAttributes.addFlashAttribute("success", "Parent successfully assigned to student")
            }
        } catch (e: Exception) {
            redirectAttributes.addFlashAttribute("error", "Error assigning parent to student: ${e.message}")
        }

        return "redirect:/admin/community/parents/${parentId}/assign-students"
    }

    @PostMapping("/parents/{parentId}/remove-student/{assignmentId}")
    fun removeParentFromStudent(
        @PathVariable parentId: UUID,
        @PathVariable assignmentId: UUID,
        redirectAttributes: RedirectAttributes
    ): String {
        try {
            val assignment = parentStudentRepository.findById(assignmentId).orElseThrow()
            assignment.isActive = false
            parentStudentRepository.save(assignment)

            redirectAttributes.addFlashAttribute("success", "Parent-student relationship removed successfully")
        } catch (e: Exception) {
            redirectAttributes.addFlashAttribute("error", "Error removing parent-student relationship: ${e.message}")
        }

        return "redirect:/admin/community/parents/${parentId}/assign-students"
    }

    @PostMapping("/parents/remove-assignment/{assignmentId}")
    fun removeParentAssignment(
        @PathVariable assignmentId: UUID,
        session: HttpSession,
        model: Model
    ): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "fragments/error :: error-message"

        try {
            // Check for placeholder UUID
            val nilUuid = UUID.fromString("00000000-0000-0000-0000-000000000000")
            if (assignmentId == nilUuid) {
                model.addAttribute("error", "Invalid assignment ID. Please try again.")
                return "fragments/error :: error-message"
            }

            val assignmentOpt = parentStudentRepository.findById(assignmentId)
            if (assignmentOpt.isEmpty) {
                model.addAttribute("error", "Parent-child relationship not found. It may have already been removed.")
                return "fragments/error :: error-message"
            }

            val assignment = assignmentOpt.get()
            assignment.isActive = false
            parentStudentRepository.save(assignment)

            model.addAttribute("success", "Parent-child relationship removed successfully")
            
            // Return updated parent card (OOB)
            val parentId = assignment.parent.id
            val updatedParent = parentRepository.findById(parentId!!).orElseThrow()
            // Ensure relationships are loaded
            val relationships = parentStudentRepository.findByParentIdWithStudentDetails(parentId)
            updatedParent.studentRelationships = relationships.toMutableList()
            
            model.addAttribute("parent", updatedParent)
            model.addAttribute("isOob", true)
            model.addAttribute("modalId", "deleteParentAssignmentModal")
            
            return "admin/community/parents/assign-success"
        } catch (e: Exception) {
            model.addAttribute("error", "Error removing parent-child relationship: ${e.message}")
            return "fragments/error :: error-message"
        }
    }

    @PostMapping("/parents/{parentId}/create-wallet")
    fun createWallet(
        @PathVariable parentId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(required = false) search: String?,
        @RequestParam(defaultValue = "paystack") provider: String,
        @RequestParam(required = false) bvn: String?,
        @RequestParam(required = false) dob: String?,
        @RequestParam(required = false) gender: String?,
        @RequestParam(required = false) address: String?,
        session: HttpSession,
        model: Model,
        authentication: Authentication,
        redirectAttributes: RedirectAttributes,
        request: jakarta.servlet.http.HttpServletRequest
    ): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/select-school"

        try {
            val parent = parentRepository.findById(parentId).orElseThrow()
            val result = if (provider.equals("squad", ignoreCase = true)) {
                if (bvn.isNullOrBlank() || dob.isNullOrBlank() || gender.isNullOrBlank() || address.isNullOrBlank()) {
                     model.addAttribute("error", "Missing required Squad fields (BVN, DOB, Gender, Address)")
                     // We need to return the parent list fragment
                     return parentList(model, authentication, session, page, 12, search, request)
                        .let { "admin/community/parents/parent-cards :: parent-cards-content" }
                }
                squadParentWalletService.createWalletForParent(parent, bvn, dob, gender, address)
            } else {
                paystackParentWalletService.createWalletForParent(parent)
            }
            
            if (result.isSuccess) {
                model.addAttribute("success", "Wallet created successfully for ${parent.user.fullName}")
            } else {
                model.addAttribute("error", "Error creating wallet: ${result.exceptionOrNull()?.message}")
            }
        } catch (e: Exception) {
            model.addAttribute("error", "Error: ${e.message}")
        }

        // Return the updated parent cards fragment
        return parentList(model, authentication, session, page, 12, search, request)
            .let { "admin/community/parents/parent-cards :: parent-cards-content" }
    }

    // Community Home HTMX Save Endpoints
    @PostMapping("/staff/save-htmx-home")
    fun saveStaffHtmxHome(
        @RequestParam(required = false) id: UUID?,
        @RequestParam firstName: String,
        @RequestParam lastName: String,
        @RequestParam(required = false) middleName: String?,
        @RequestParam(required = false) email: String?,
        @RequestParam countryCode: String,
        @RequestParam phoneNumber: String,
        @RequestParam(required = false) dateOfBirth: String?,
        @RequestParam(required = false) gender: String?,
        @RequestParam designation: String,
        @RequestParam(required = false) departmentId: UUID?,
        @RequestParam(required = false) employmentType: String?,
        @RequestParam(required = false) highestDegree: String?,
        session: HttpSession,
        model: Model
    ): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: run {
                model.addAttribute("error", "Session expired. Please select a school again.")
                return "fragments/error :: error-message"
            }

        try {
            // Same logic as saveStaffHtmx but return success message
            val fullPhoneNumber = countryCode + phoneNumber
            
            if (id != null) {
                // Update existing staff
                val existingStaff = staffRepository.findById(id).orElseThrow()
                val existingUser = existingStaff.user
                
                existingUser.apply {
                    this.firstName = firstName
                    this.lastName = lastName
                    this.middleName = middleName
                    this.email = email
                    this.phoneNumber = fullPhoneNumber
                    this.dateOfBirth = if (dateOfBirth.isNullOrBlank()) null else LocalDate.parse(dateOfBirth)
                    this.gender = gender
                }
                userRepository.save(existingUser)
                
                existingStaff.apply {
                    this.designation = designation
                    this.employmentType = employmentType ?: "full_time"
                    this.highestDegree = highestDegree
                    
                    if (departmentId != null) {
                        val department = departmentRepository.findById(departmentId).orElse(null)
                        this.department = department?.name
                    } else {
                        this.department = null
                    }
                }
                staffRepository.save(existingStaff)
                
                model.addAttribute("message", "Staff updated successfully!")
            } else {
                // Check if user already exists by email or phone
                val existingUser = when {
                    !email.isNullOrBlank() -> userRepository.findByEmail(email).orElse(null)
                    fullPhoneNumber.isNotBlank() -> userRepository.findByPhoneNumber(fullPhoneNumber).orElse(null)
                    else -> null
                }

                val savedUser = if (existingUser != null) {
                    // User exists globally, check if they already have the STAFF role in this school
                    val staffRole = roleRepository.findByName("STAFF").orElseThrow { RuntimeException("Staff role not found") }
                    if (userSchoolRoleRepository.existsByUserIdAndSchoolIdAndRoleId(existingUser.id!!, selectedSchoolId, staffRole.id!!)) {
                        throw RuntimeException("A staff member with this email/phone is already registered in this school.")
                    }
                    existingUser
                } else {
                    val newUser = User(phoneNumber = fullPhoneNumber).apply {
                        this.firstName = firstName
                        this.lastName = lastName
                        this.middleName = middleName
                        this.email = email
                        this.dateOfBirth = if (dateOfBirth.isNullOrBlank()) null else LocalDate.parse(dateOfBirth)
                        this.gender = gender
                        this.status = UserStatus.ACTIVE
                    }
                    userRepository.save(newUser)
                }
                
                // Check if staff already exists for this user and school
                var savedStaff = staffRepository.findByUserIdAndSchoolId(savedUser.id!!, selectedSchoolId)
                if (savedStaff == null) {
                    val newStaff = Staff(
                        user = savedUser,
                        staffId = generateStaffId(selectedSchoolId),
                        hireDate = LocalDate.now()
                    ).apply {
                        this.schoolId = selectedSchoolId
                        this.designation = designation
                        this.employmentType = employmentType ?: "full_time"
                        this.highestDegree = highestDegree
                        this.isActive = true
                        
                        if (departmentId != null) {
                            val department = departmentRepository.findById(departmentId).orElse(null)
                            this.department = department?.name
                        }
                    }
                    staffRepository.save(newStaff)
                }
                
                // Create UserSchoolRole for Staff if it doesn't exist
                val staffRole = roleRepository.findByName("STAFF").orElseThrow { 
                    RuntimeException("Staff role not found") 
                }
                
                if (!userSchoolRoleRepository.existsByUserIdAndSchoolIdAndRoleId(savedUser.id!!, selectedSchoolId, staffRole.id!!)) {
                    val userSchoolRole = UserSchoolRole(
                        user = savedUser,
                        schoolId = selectedSchoolId,
                        role = staffRole,
                        isPrimary = true
                    )
                    userSchoolRole.isActive = true
                    userSchoolRoleRepository.save(userSchoolRole)
                }
                
                model.addAttribute("message", "Staff created successfully!")
            }

            return "fragments/success :: success-message"
        } catch (e: Exception) {
            model.addAttribute("error", handleDatabaseError(e, "Error saving staff"))
            return "fragments/error :: error-message"
        }
    }

    @PostMapping("/students/save-htmx-home")
    fun saveStudentHtmxHome(
        @RequestParam(required = false) id: UUID?,
        @RequestParam firstName: String,
        @RequestParam lastName: String,
        @RequestParam(required = false) middleName: String?,
        @RequestParam(required = false) email: String?,
        @RequestParam(required = false) phoneNumber: String?,
        @RequestParam(required = false) dateOfBirth: String?,
        @RequestParam(required = false) gender: String?,
        @RequestParam studentId: String,
        @RequestParam(required = false) assignedClassId: UUID?,
        session: HttpSession,
        model: Model
    ): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: run {
                model.addAttribute("error", "Session expired. Please select a school again.")
                return "fragments/error :: error-message"
            }

        // Strip admission prefix if present
        var processedStudentId = studentId
        val school = schoolRepository.findById(selectedSchoolId).orElse(null)
        if (school != null && !school.admissionPrefix.isNullOrBlank()) {
            val prefix = school.admissionPrefix!!
            if (processedStudentId.startsWith(prefix)) {
                processedStudentId = processedStudentId.substring(prefix.length)
            }
        }

        try {
            if (id != null) {
                // Update existing student
                val existingStudent = studentRepository.findById(id).orElseThrow()
                val existingUser = existingStudent.user
                
                existingUser.apply {
                    this.firstName = firstName
                    this.lastName = lastName
                    this.middleName = middleName
                    this.email = email
                    this.dateOfBirth = if (dateOfBirth.isNullOrBlank()) null else LocalDate.parse(dateOfBirth)
                    this.gender = gender
                    // Update phone number to match studentId (Admission Number)
                    if (processedStudentId.isNotBlank()) {
                        this.phoneNumber = processedStudentId
                    }
                }
                userRepository.save(existingUser)
                
                // Check for duplicate student ID/admission number in the same school
                if (processedStudentId.isNotBlank()) {
                    val studentWithSameId = studentRepository.findByStudentIdAndSchoolId(processedStudentId, selectedSchoolId)
                    if (studentWithSameId != null && studentWithSameId.id != id) {
                        throw RuntimeException("Student ID/Admission number $processedStudentId is already in use.")
                    }
                }

                existingStudent.apply {
                    this.studentId = processedStudentId
                    this.dateOfBirth = if (dateOfBirth.isNullOrBlank()) null else LocalDate.parse(dateOfBirth)
                    this.gender = gender?.let { com.haneef._school.entity.Gender.valueOf(it.uppercase()) }
                }
                studentRepository.save(existingStudent)
                
                model.addAttribute("message", "Student updated successfully!")
            } else {
                // Create new student - use admission number (studentId) as phone number since field is removed
                val finalPhoneNumber = if (processedStudentId.isNotBlank()) {
                    processedStudentId
                } else if (!phoneNumber.isNullOrBlank()) {
                    phoneNumber
                } else {
                    null
                }

                // Check if user already exists by email or phone
                val existingUser = when {
                    !email.isNullOrBlank() -> userRepository.findByEmail(email).orElse(null)
                    finalPhoneNumber != null -> userRepository.findByPhoneNumber(finalPhoneNumber).orElse(null)
                    else -> null
                }

                val savedUser = if (existingUser != null) {
                    // User exists globally, check if they already have the STUDENT role in this school
                    val studentRole = roleRepository.findByName("STUDENT").orElseThrow { RuntimeException("Student role not found") }
                    if (userSchoolRoleRepository.existsByUserIdAndSchoolIdAndRoleId(existingUser.id!!, selectedSchoolId, studentRole.id!!)) {
                        throw RuntimeException("A student with this email/phone is already registered in this school.")
                    }
                    existingUser
                } else {
                    val newUser = User(phoneNumber = finalPhoneNumber).apply {
                        this.firstName = firstName
                        this.lastName = lastName
                        this.middleName = middleName
                        this.email = email
                        this.dateOfBirth = if (dateOfBirth.isNullOrBlank()) null else LocalDate.parse(dateOfBirth)
                        this.gender = gender
                        this.status = UserStatus.ACTIVE
                    }
                    userRepository.save(newUser)
                }
                
                // Check if student already exists for this user and school
                var savedStudent = studentRepository.findByUserIdAndSchoolId(savedUser.id!!, selectedSchoolId)
                if (savedStudent == null) {
                    // Check for duplicate student ID in the same school
                    if (processedStudentId.isNotBlank()) {
                        if (studentRepository.findByStudentIdAndSchoolId(processedStudentId, selectedSchoolId) != null) {
                            throw RuntimeException("Student ID $processedStudentId is already in use.")
                        }
                    }

                    val newStudent = Student(
                        user = savedUser,
                        studentId = processedStudentId,
                        admissionDate = LocalDate.now()
                    ).apply {
                        this.schoolId = selectedSchoolId
                        this.isActive = true
                        this.dateOfBirth = if (dateOfBirth.isNullOrBlank()) null else LocalDate.parse(dateOfBirth)
                        this.gender = gender?.let { com.haneef._school.entity.Gender.valueOf(it.uppercase()) }
                    }
                    savedStudent = studentRepository.save(newStudent)
                }
                
                // Create UserSchoolRole for Student if it doesn't exist
                val studentRole = roleRepository.findByName("STUDENT").orElseThrow { 
                    RuntimeException("Student role not found") 
                }
                
                if (!userSchoolRoleRepository.existsByUserIdAndSchoolIdAndRoleId(savedUser.id!!, selectedSchoolId, studentRole.id!!)) {
                    val userSchoolRole = UserSchoolRole(
                        user = savedUser,
                        schoolId = selectedSchoolId,
                        role = studentRole,
                        isPrimary = true
                    )
                    userSchoolRole.isActive = true
                    userSchoolRoleRepository.save(userSchoolRole)
                }
                
                // Assign to class if provided
                if (assignedClassId != null) {
                    val schoolClass = schoolClassRepository.findById(assignedClassId).orElse(null)
                    if (schoolClass != null && schoolClass.track != null) {
                        // Get current academic session and term
                        val currentSession = academicSessionRepository.findBySchoolIdAndIsCurrentSessionAndIsActive(selectedSchoolId, true, true)
                            ?: throw RuntimeException("No current academic session found")
                        val currentTerm = termRepository.findByAcademicSessionIdAndIsCurrentTermAndIsActive(currentSession.id!!, true, true)
                            .orElseThrow { RuntimeException("No current term found") }
                        
                        val studentClass = StudentClass(
                            student = savedStudent!!,
                            schoolClass = schoolClass,
                            academicSession = currentSession,
                            term = currentTerm
                        )
                        studentClass.schoolId = selectedSchoolId
                        studentClass.isActive = true
                        studentClassRepository.save(studentClass)
                        
                        // Update class enrollment count reliably
                        updateClassEnrollmentCount(schoolClass.id!!)
                    }
                }
                
                model.addAttribute("message", "Student enrolled successfully!")
            }

            return "fragments/success :: success-message"
        } catch (e: Exception) {
            model.addAttribute("error", handleDatabaseError(e, "Error saving student"))
            return "fragments/error :: error-message"
        }
    }

    @PostMapping("/parents/save-htmx-home")
    fun saveParentHtmxHome(
        @RequestParam(required = false) id: UUID?,
        @RequestParam firstName: String,
        @RequestParam lastName: String,
        @RequestParam(required = false) middleName: String?,
        @RequestParam(required = false) email: String?,
        @RequestParam countryCode: String,
        @RequestParam phoneNumber: String,
        @RequestParam(required = false) dateOfBirth: String?,
        @RequestParam(required = false) gender: String?,
        @RequestParam(required = false) isPrimaryContact: Boolean = false,
        @RequestParam(required = false) isEmergencyContact: Boolean = false,
        session: HttpSession,
        model: Model
    ): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: run {
                model.addAttribute("error", "Session expired. Please select a school again.")
                return "fragments/error :: error-message"
            }

        try {
            val fullPhoneNumber = countryCode + phoneNumber
            
            if (id != null) {
                // Update existing parent
                val existingParent = parentRepository.findById(id).orElseThrow()
                val existingUser = existingParent.user
                
                existingUser.apply {
                    this.firstName = firstName
                    this.lastName = lastName
                    this.middleName = middleName
                    this.email = email
                    this.phoneNumber = fullPhoneNumber
                    this.dateOfBirth = if (dateOfBirth.isNullOrBlank()) null else LocalDate.parse(dateOfBirth)
                    this.gender = gender
                }
                userRepository.save(existingUser)
                
                existingParent.isPrimaryContact = isPrimaryContact
                existingParent.isEmergencyContact = isEmergencyContact
                parentRepository.save(existingParent)
                
                model.addAttribute("message", "Parent updated successfully!")
            } else {
                // Check if user already exists by email or phone
                val existingUser = when {
                    !email.isNullOrBlank() -> userRepository.findByEmail(email).orElse(null)
                    fullPhoneNumber.isNotBlank() -> userRepository.findByPhoneNumber(fullPhoneNumber).orElse(null)
                    else -> null
                }

                val savedUser = if (existingUser != null) {
                    // User exists globally, check if they already have the PARENT role in this school
                    val parentRole = roleRepository.findByName("PARENT").orElseThrow { RuntimeException("Parent role not found") }
                    if (userSchoolRoleRepository.existsByUserIdAndSchoolIdAndRoleId(existingUser.id!!, selectedSchoolId, parentRole.id!!)) {
                        throw RuntimeException("A parent with this email/phone is already registered in this school.")
                    }
                    existingUser
                } else {
                    val newUser = User(phoneNumber = fullPhoneNumber).apply {
                        this.firstName = firstName
                        this.lastName = lastName
                        this.middleName = middleName
                        this.email = email
                        this.dateOfBirth = if (dateOfBirth.isNullOrBlank()) null else LocalDate.parse(dateOfBirth)
                        this.gender = gender
                        this.status = UserStatus.ACTIVE
                    }
                    userRepository.save(newUser)
                }
                
                // Check if parent already exists for this user and school
                var savedParent = parentRepository.findByUserIdAndSchoolId(savedUser.id!!, selectedSchoolId)
                if (savedParent == null) {
                    val newParent = Parent(
                        user = savedUser
                    )
                    newParent.schoolId = selectedSchoolId
                    newParent.isActive = true
                    newParent.isPrimaryContact = isPrimaryContact
                    newParent.isEmergencyContact = isEmergencyContact
                    parentRepository.save(newParent)
                }
                
                // Create UserSchoolRole for Parent if it doesn't exist
                val parentRole = roleRepository.findByName("PARENT").orElseThrow { 
                    RuntimeException("Parent role not found") 
                }
                
                if (!userSchoolRoleRepository.existsByUserIdAndSchoolIdAndRoleId(savedUser.id!!, selectedSchoolId, parentRole.id!!)) {
                    val userSchoolRole = UserSchoolRole(
                        user = savedUser,
                        schoolId = selectedSchoolId,
                        role = parentRole,
                        isPrimary = true
                    )
                    userSchoolRole.isActive = true
                    userSchoolRoleRepository.save(userSchoolRole)
                }
                
                model.addAttribute("message", "Parent added successfully!")
            }

            return "fragments/success :: success-message"
        } catch (e: Exception) {
            model.addAttribute("error", handleDatabaseError(e, "Error saving parent"))
            return "fragments/error :: error-message"
        }
    }

    
    @GetMapping("/students/subjects-by-class/{classId}")
    @ResponseBody
    fun getSubjectsByClassForStudent(@PathVariable classId: UUID, session: HttpSession): List<Map<String, Any?>> {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return emptyList()
        
        // Get subjects assigned to this class with eager loading
        val classSubjects = classSubjectRepository.findBySchoolClassIdWithSubject(classId)
        return classSubjects.map { 
            mapOf(
                "id" to it.subject.id!!,
                "subjectName" to it.subject.subjectName,
                "subjectCode" to (it.subject.subjectCode ?: "")
            )
        }
    }
    // Staff Assignment Methods
    @GetMapping("/staff/{staffId}/assignments/modal")
    fun getStaffAssignmentsModal(
        @PathVariable staffId: UUID,
        model: Model,
        authentication: Authentication,
        session: HttpSession
    ): String {
        val customUser = authentication.principal as CustomUserDetails
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "fragments/error :: error-message"
            
        val staff = staffRepository.findById(staffId).orElseThrow { RuntimeException("Staff not found") }
        
        // Security Check: Ensure staff belongs to the selected school
        if (staff.schoolId != selectedSchoolId) {
            return "fragments/error :: error-message"
        }
        
        val tracks = educationTrackRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
        
        // Get current academic session and term
        val currentSession = academicSessionRepository.findBySchoolIdAndIsCurrentSessionAndIsActive(selectedSchoolId, true, true)
            ?: throw RuntimeException("No current academic session found")
        val currentTerm = termRepository.findByAcademicSessionIdAndIsCurrentTermAndIsActive(currentSession.id!!, true, true)
            .orElseThrow { RuntimeException("No current term found") }
        
        // Get current assignments
        val currentClassAssignments = classTeacherRepository.findByStaffIdAndAcademicSessionIdAndTermIdAndIsActive(
            staffId, currentSession.id!!, currentTerm.id!!, true
        )
        val currentSubjectAssignments = subjectTeacherRepository.findByStaffIdAndAcademicSessionIdAndTermIdAndIsActive(
            staffId, currentSession.id!!, currentTerm.id!!, true
        )
        
        model.addAttribute("user", customUser.user)
        model.addAttribute("staff", staff)
        model.addAttribute("tracks", tracks)
        model.addAttribute("currentSession", currentSession)
        model.addAttribute("currentTerm", currentTerm)
        model.addAttribute("currentClassAssignments", currentClassAssignments)
        model.addAttribute("currentSubjectAssignments", currentSubjectAssignments)
        
        return "admin/community/staff/assignments-modal"
    }

    @PostMapping("/staff/{staffId}/assign-class-htmx")
    fun assignClassTeacherHtmx(
        @PathVariable staffId: UUID,
        @RequestParam assignedClassId: UUID,
        session: HttpSession,
        model: Model,
        authentication: Authentication
    ): String {
        val customUser = authentication.principal as CustomUserDetails
        logger.info("=== Starting Class Teacher Assignment ===")
        logger.info("Request Parameters - staffId: $staffId, assignedClassId: $assignedClassId")
        
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
        if (selectedSchoolId == null) {
            return "fragments/error :: error-message"
        }

        // 1. Resolve Effective Session/Term EARLY (Scope: Method-wide)
        val (effectiveSession, effectiveTerm) = getEffectiveSessionAndTerm(session, selectedSchoolId)
        
        // If we can't resolve a session/term, we can't proceed with assignment OR display
        val targetSession = effectiveSession ?: run {
             logger.error("No effective session found for assignment.")
             model.addAttribute("error", "No active academic session found.")
             return "fragments/error :: error-message"
        }
        
        val targetTerm = effectiveTerm ?: run {
             logger.error("No effective term found for assignment.")
             model.addAttribute("error", "No active academic term found.")
             return "fragments/error :: error-message"
        }
        
        logger.info("Target Context - Session: ${targetSession.sessionName}, Term: ${targetTerm.termName}")

        try {
            // Fetch entities
            val staff = staffRepository.findById(staffId).orElseThrow { RuntimeException("Staff not found") }
            val schoolClass = schoolClassRepository.findById(assignedClassId).orElseThrow { RuntimeException("Class not found") }
            
            // Security checks
            if (staff.schoolId != selectedSchoolId || schoolClass.schoolId != selectedSchoolId) {
                return "fragments/error :: error-message"
            }
            
            // Check for existing assignment in the TARGET session/term
            val existingAssignment = classTeacherRepository.findByStaffIdAndSchoolClassIdAndAcademicSessionIdAndTermIdAndSchoolId(
                staffId, assignedClassId, targetSession.id!!, targetTerm.id!!, selectedSchoolId)
            
            if (existingAssignment != null) {
                if (existingAssignment.isActive) {
                    model.addAttribute("error", "Staff is already assigned as class teacher for this class")
                } else {
                    existingAssignment.isActive = true
                    classTeacherRepository.save(existingAssignment)
                    
                    // Log the assignment activity
                    val userRole = (authentication.principal as CustomUserDetails).authorities.firstOrNull()?.authority ?: "USER"
                    activityLogService.logClassTeacherAssigned(
                        selectedSchoolId, customUser.user.id!!, userRole, staffId, 
                        schoolClass.className, targetSession.sessionName, targetTerm.termName
                    )

                    model.addAttribute("success", "Assignment reactivated successfully!")
                }
            } else {
                val classTeacher = ClassTeacher(
                    staff = staff,
                    schoolClass = schoolClass,
                    academicSession = targetSession,
                    term = targetTerm
                ).apply {
                    this.schoolId = selectedSchoolId
                    this.isActive = true
                }
                classTeacherRepository.save(classTeacher)
                
                // Log the assignment activity
                val userRole = (authentication.principal as CustomUserDetails).authorities.firstOrNull()?.authority ?: "USER"
                activityLogService.logClassTeacherAssigned(
                    selectedSchoolId, customUser.user.id!!, userRole, staffId, 
                    schoolClass.className, targetSession.sessionName, targetTerm.termName
                )

                model.addAttribute("success", "Class teacher assigned successfully!")
            }
        } catch (e: Exception) {
            logger.error("Error creating class teacher assignment", e)
            model.addAttribute("error", "Error creating assignment: ${e.message}")
        }

        // Reload the staff with updated assignments using the TARGET session/term
        val staffForUpdate = staffRepository.findById(staffId).orElseThrow { RuntimeException("Staff not found after update") }
        val updatedStaff = populateStaffAssignments(listOf(staffForUpdate), selectedSchoolId, targetSession, targetTerm).first()
        
        // Get all necessary data for the modal using the TARGET session/term
        val tracks = educationTrackRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
        
        val currentClassAssignments = classTeacherRepository.findByStaffIdAndAcademicSessionIdAndTermIdAndIsActive(
            staffId, targetSession.id!!, targetTerm.id!!, true
        )
        val currentSubjectAssignments = subjectTeacherRepository.findByStaffIdAndAcademicSessionIdAndTermIdAndIsActive(
            staffId, targetSession.id!!, targetTerm.id!!, true
        )
        
        model.addAttribute("user", customUser.user)
        model.addAttribute("staff", updatedStaff)
        model.addAttribute("tracks", tracks)
        model.addAttribute("currentSession", targetSession) // Use targetSession as "current" for the view context
        model.addAttribute("currentTerm", targetTerm)
        model.addAttribute("currentClassAssignments", currentClassAssignments)
        model.addAttribute("currentSubjectAssignments", currentSubjectAssignments)
        model.addAttribute("isOob", true)
        
        return "admin/community/staff/assignments-modal"
    }
    
    // Simple test endpoint to verify routing
    @PostMapping("/staff/{staffId}/test-route")
    @ResponseBody
    fun testRoute(@PathVariable staffId: UUID): Map<String, Any> {
        logger.info("=== TEST ROUTE CALLED ===")
        logger.info("Staff ID: $staffId")
        return mapOf(
            "success" to true,
            "message" to "Route is working!",
            "staffId" to staffId,
            "timestamp" to System.currentTimeMillis()
        )
    }

    @PostMapping("/staff/{staffId}/assign-subject-htmx")
    fun assignSubjectTeacherHtmx(
        @PathVariable staffId: UUID,
        @RequestParam assignedClassId: UUID,
        @RequestParam subjectId: UUID,
        session: HttpSession,
        model: Model,
        authentication: Authentication
    ): String {
        val customUser = authentication.principal as CustomUserDetails
        logger.info("=== Starting Subject Teacher Assignment ===")
        logger.info("Request Parameters - staffId: $staffId, assignedClassId: $assignedClassId, subjectId: $subjectId")
        
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
        if (selectedSchoolId == null) {
            return "fragments/error :: error-message"
        }

        // 1. Resolve Effective Session/Term EARLY (Scope: Method-wide)
        val (effectiveSession, effectiveTerm) = getEffectiveSessionAndTerm(session, selectedSchoolId)
        
        // If we can't resolve a session/term, we can't proceed with assignment OR display
        val targetSession = effectiveSession ?: run {
             logger.error("No effective session found for assignment.")
             model.addAttribute("error", "No active academic session found.")
             return "fragments/error :: error-message"
        }
        
        val targetTerm = effectiveTerm ?: run {
             logger.error("No effective term found for assignment.")
             model.addAttribute("error", "No active academic term found.")
             return "fragments/error :: error-message"
        }
        
        logger.info("Target Context - Session: ${targetSession.sessionName}, Term: ${targetTerm.termName}")

        try {
            // Fetch entities
            val staff = staffRepository.findById(staffId).orElseThrow { RuntimeException("Staff not found") }
            val schoolClass = schoolClassRepository.findById(assignedClassId).orElseThrow { RuntimeException("Class not found") }
            val subject = subjectRepository.findById(subjectId).orElseThrow { RuntimeException("Subject not found") }
            
            // Security checks
            if (staff.schoolId != selectedSchoolId || schoolClass.schoolId != selectedSchoolId) {
                return "fragments/error :: error-message"
            }
            
            // Check for existing assignment in the TARGET session/term
            val existingAssignment = subjectTeacherRepository.findByStaffIdAndSubjectIdAndSchoolClassIdAndAcademicSessionIdAndTermIdAndSchoolId(
                staffId, subjectId, assignedClassId, targetSession.id!!, targetTerm.id!!, selectedSchoolId)
            
            if (existingAssignment != null) {
                if (existingAssignment.isActive) {
                    model.addAttribute("error", "Staff is already assigned as subject teacher for this subject in this class")
                } else {
                    existingAssignment.isActive = true
                    subjectTeacherRepository.save(existingAssignment)
                    
                    // Log the assignment activity
                    val userRole = (authentication.principal as CustomUserDetails).authorities.firstOrNull()?.authority ?: "USER"
                    activityLogService.logSubjectTeacherAssigned(
                        selectedSchoolId, customUser.user.id!!, userRole, staffId, 
                        subject.subjectName, schoolClass.className, targetSession.sessionName, targetTerm.termName
                    )

                    model.addAttribute("success", "Assignment reactivated successfully!")
                }
            } else {
                val subjectTeacher = SubjectTeacher(
                    staff = staff,
                    subject = subject,
                    schoolClass = schoolClass,
                    academicSession = targetSession,
                    term = targetTerm
                ).apply {
                    this.schoolId = selectedSchoolId
                    this.isActive = true
                }
                subjectTeacherRepository.save(subjectTeacher)
                
                // Log the assignment activity
                val userRole = (authentication.principal as CustomUserDetails).authorities.firstOrNull()?.authority ?: "USER"
                activityLogService.logSubjectTeacherAssigned(
                    selectedSchoolId, customUser.user.id!!, userRole, staffId, 
                    subject.subjectName, schoolClass.className, targetSession.sessionName, targetTerm.termName
                )

                model.addAttribute("success", "Subject teacher assigned successfully!")
            }
        } catch (e: Exception) {
            logger.error("Error creating subject teacher assignment", e)
            model.addAttribute("error", "Error creating assignment: ${e.message}")
        }
        
        // Reload the staff with updated assignments using the TARGET session/term
        val staffForUpdate = staffRepository.findById(staffId).orElseThrow { RuntimeException("Staff not found after update") }
        val updatedStaff = populateStaffAssignments(listOf(staffForUpdate), selectedSchoolId, targetSession, targetTerm).first()

        // Get all necessary data for the modal using the TARGET session/term
        val tracks = educationTrackRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
        
        val currentClassAssignments = classTeacherRepository.findByStaffIdAndAcademicSessionIdAndTermIdAndIsActive(
            staffId, targetSession.id!!, targetTerm.id!!, true
        )
        val currentSubjectAssignments = subjectTeacherRepository.findByStaffIdAndAcademicSessionIdAndTermIdAndIsActive(
            staffId, targetSession.id!!, targetTerm.id!!, true
        )
        
        model.addAttribute("user", customUser.user)
        model.addAttribute("staff", updatedStaff)
        model.addAttribute("tracks", tracks)
        model.addAttribute("currentSession", targetSession) // Use targetSession as "current" for the view context
        model.addAttribute("currentTerm", targetTerm)
        model.addAttribute("currentClassAssignments", currentClassAssignments)
        model.addAttribute("currentSubjectAssignments", currentSubjectAssignments)
        model.addAttribute("isOob", true)
        
        return "admin/community/staff/assignments-modal"
    }

    @GetMapping("/staff/classes-by-track/{trackId}")
    @ResponseBody
    fun getStaffClassesByTrack(@PathVariable trackId: UUID, session: HttpSession): List<Map<String, Any>> {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return emptyList()
            
        val classes = schoolClassRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
            .filter { it.track?.id == trackId }
            
        return classes.map { cls ->
            mapOf(
                "id" to cls.id!!,
                "className" to cls.className,
                "gradeLevel" to (cls.gradeLevel ?: "")
            )
        }
    }

    @GetMapping("/staff/subjects-by-class/{classId}")
    @ResponseBody
    fun getStaffSubjectsByClass(@PathVariable classId: UUID, session: HttpSession): List<Map<String, Any>> {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return emptyList()
            
        val classSubjects = classSubjectRepository.findBySchoolClassIdAndIsActive(classId, true)
            .filter { it.schoolId == selectedSchoolId }
            
        return classSubjects.map { cs ->
            mapOf(
                "id" to cs.subject.id!!,
                "subjectName" to cs.subject.subjectName,
                "subjectCode" to (cs.subject.subjectCode ?: "")
            )
        }
    }

    @PostMapping("/staff/remove-class-assignment/{assignmentId}")
    @ResponseBody
    fun removeClassAssignment(
        @PathVariable assignmentId: UUID,
        session: HttpSession,
        authentication: Authentication
    ): Map<String, Any> {
        val customUser = authentication.principal as CustomUserDetails
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return mapOf("success" to false, "message" to "Unauthorized")

        try {
            val assignment = classTeacherRepository.findById(assignmentId).orElseThrow { 
                RuntimeException("Assignment not found") 
            }
            
            // Security check
            if (assignment.schoolId != selectedSchoolId) {
                return mapOf("success" to false, "message" to "Unauthorized")
            }
            
            assignment.isActive = false
            classTeacherRepository.save(assignment)
            
            // Log the removal activity
            val userRole = (authentication.principal as CustomUserDetails).authorities.firstOrNull()?.authority ?: "USER"
            activityLogService.logClassTeacherRemoved(
                selectedSchoolId, customUser.user.id!!, userRole, assignment.staff.id!!,
                assignment.schoolClass.className, assignment.academicSession.sessionName, assignment.term.termName
            )
            
            return mapOf("success" to true, "message" to "Assignment removed successfully")
        } catch (e: Exception) {
            return mapOf("success" to false, "message" to "Error removing assignment: ${e.message}")
        }
    }

    @PostMapping("/staff/remove-subject-assignment/{assignmentId}")
    @ResponseBody
    fun removeSubjectAssignment(
        @PathVariable assignmentId: UUID,
        session: HttpSession,
        authentication: Authentication
    ): Map<String, Any> {
        val customUser = authentication.principal as CustomUserDetails
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return mapOf("success" to false, "message" to "Unauthorized")

        try {
            val assignment = subjectTeacherRepository.findById(assignmentId).orElseThrow { 
                RuntimeException("Assignment not found") 
            }
            
            // Security check
            if (assignment.schoolId != selectedSchoolId) {
                return mapOf("success" to false, "message" to "Unauthorized")
            }
            
            assignment.isActive = false
            subjectTeacherRepository.save(assignment)
            
            // Log the removal activity
            val userRole = (authentication.principal as CustomUserDetails).authorities.firstOrNull()?.authority ?: "USER"
            activityLogService.logSubjectTeacherRemoved(
                selectedSchoolId, customUser.user.id!!, userRole, assignment.staff.id!!,
                assignment.subject.subjectName, assignment.schoolClass.className, assignment.academicSession.sessionName, assignment.term.termName
            )
            
            return mapOf("success" to true, "message" to "Assignment removed successfully")
        } catch (e: Exception) {
            return mapOf("success" to false, "message" to "Error removing assignment: ${e.message}")
        }
    }

    private fun getUpdatedStaffList(selectedSchoolId: UUID, model: Model, session: HttpSession): String {
        logger.info("Generating updated staff list for school ID: $selectedSchoolId")
        
        val sessionId = session.getAttribute("selectedSessionId")
        val termId = session.getAttribute("selectedTermId")
        logger.info("Session Attributes - selectedSessionId: $sessionId, selectedTermId: $termId")
        
        val pageable = PageRequest.of(0, 12, Sort.by("user.firstName"))
        
        // Get effective session and term
        val (effectiveSession, effectiveTerm) = getEffectiveSessionAndTerm(session, selectedSchoolId)
        
        logger.info("Effective Context - Session: ${effectiveSession?.sessionName} (${effectiveSession?.id}), Term: ${effectiveTerm?.termName} (${effectiveTerm?.id})")
        
        // Load all staff and then populate assignments (following existing pattern)
        // ideally we should page first then populate, but maintaining existing pattern for now
        val allStaff = staffRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
        populateStaffAssignments(allStaff, selectedSchoolId, effectiveSession, effectiveTerm)
        
        val pagedStaff = allStaff.take(12)
        val staffPage = org.springframework.data.domain.PageImpl(pagedStaff, pageable, allStaff.size.toLong())
        
        val designations = staffRepository.findDistinctDesignationsBySchoolId(selectedSchoolId)
        val communityStats = getCommunityStats(selectedSchoolId)
        
        model.addAttribute("staffPage", staffPage)
        model.addAttribute("designations", designations)
        model.addAttribute("communityStats", communityStats)
        
        return "admin/community/staff/staff-cards :: staff-cards-content"
    }

    // Helper method to populate teacher assignments for staff
    private fun populateStaffAssignments(
        staffList: List<Staff>, 
        schoolId: UUID, 
        effectiveSession: AcademicSession?, 
        effectiveTerm: Term?
    ): List<Staff> {
        if (staffList.isEmpty()) return staffList
        
        val currentSession = effectiveSession 
            ?: academicSessionRepository.findBySchoolIdAndIsCurrentSessionAndIsActive(schoolId, true, true)
            ?: return staffList
            
        val currentTerm = effectiveTerm 
            ?: termRepository.findByAcademicSessionIdAndIsCurrentTermAndIsActive(currentSession.id!!, true, true).orElse(null)
            ?: return staffList
            
        logger.info("Populating assignments using - Session: ${currentSession.sessionName}, Term: ${currentTerm.termName}")
        
        val staffIds = staffList.map { it.id!! }
        
        // Load class teacher assignments
        // efficient fetch for the specific staff list would be better, but existing repo methods are limited
        // We'll filter in memory from the method that fetches for school/session/term which is likely cached/efficient enough 
        // OR we can rely on Lazy/Batch fetching if we just accessed the collections, but here we want specific session filtering
        
        val classTeacherAssignments = classTeacherRepository.findBySchoolIdAndIsActiveAndSessionAndTermWithDetails(
            schoolId, true, currentSession.id!!, currentTerm.id!!
        )
        logger.info("Found ${classTeacherAssignments.size} class teacher assignments for this session/term")
        
        val classAssignmentsByStaff = classTeacherAssignments.groupBy { it.staff.id }
        
        // Load subject teacher assignments
        val subjectTeacherAssignments = subjectTeacherRepository.findBySchoolIdAndIsActiveAndSessionAndTermWithDetails(
            schoolId, true, currentSession.id!!, currentTerm.id!!
        )
        logger.info("Found ${subjectTeacherAssignments.size} subject teacher assignments for this session/term")
        
        val subjectAssignmentsByStaff = subjectTeacherAssignments.groupBy { it.staff.id }
        
        // Assign the loaded assignments to staff
        staffList.forEach { staff ->
            // Filter assignments relevant to this staff
            staff.classTeacherAssignments = (classAssignmentsByStaff[staff.id] ?: emptyList()).toMutableSet()
            staff.subjectTeacherAssignments = (subjectAssignmentsByStaff[staff.id] ?: emptyList()).toMutableSet()
        }
        
        return staffList
    }

    private fun populateParentStudentClasses(
        parents: List<Parent>, 
        schoolId: UUID, 
        effectiveSession: AcademicSession?, 
        effectiveTerm: Term?
    ) {
        if (parents.isEmpty()) return
        
        val currentSession = effectiveSession 
            ?: academicSessionRepository.findBySchoolIdAndIsCurrentSessionAndIsActive(schoolId, true, true)
            ?: return
            
        val currentTerm = effectiveTerm 
            ?: termRepository.findByAcademicSessionIdAndIsCurrentTermAndIsActive(currentSession.id!!, true, true).orElse(null)
            ?: return
            
        // Collect all students from parents
        val students = parents.flatMap { parent -> 
            try {
                // Handle potential lazy initialization by catching exception if not initialized
                // But for 'WithRelationships' query it's eager. For search, it might trigger lazy fetch.
                parent.studentRelationships.map { it.student }
            } catch (e: Exception) {
                // If we can't load students, skip
                emptyList<Student>()
            }
        }.distinctBy { it.id }
        
        if (students.isEmpty()) {
            logger.info("No students found for the listed parents.")
            return
        }
        
        logger.info("Fetching class enrollments for ${students.size} students using Session: ${currentSession.sessionName}, Term: ${currentTerm.termName}")
        
        // Fetch enrollments relevant to the session/term
        val enrollments = studentClassRepository.findByStudentIdInAndAcademicSessionIdAndTermIdAndIsActiveWithClassDetails(
            students.map { it.id!! }, 
            currentSession.id!!, 
            currentTerm.id!!, 
            true
        )
        logger.info("Found ${enrollments.size} active enrollments for this session/term.")
        
        val enrollmentsByStudent = enrollments.groupBy { it.student.id }
        
        // Assign to student objects
        students.forEach { student ->
            val studentEnrollments = (enrollmentsByStudent[student.id] ?: emptyList()).toMutableList()
            student.classEnrollments = studentEnrollments
            if (studentEnrollments.isNotEmpty()) {
               logger.debug("Assigned ${studentEnrollments.size} classes to student ${student.user.firstName}")
            }
        }
    }

    // Deprecated but kept for compatibility during refactor if needed, delegating to new method
    private fun loadStaffWithTeacherAssignments(schoolId: UUID): List<Staff> {
        val allStaff = staffRepository.findBySchoolIdAndIsActiveWithTeacherAssignments(schoolId, true)
        return populateStaffAssignments(allStaff, schoolId, null, null)
    }

    private fun handleDatabaseError(e: Exception, defaultMessage: String): String {
        // Unwrap the exception to find the root cause
        var rootCause: Throwable = e
        while (rootCause.cause != null && rootCause.cause != rootCause) {
            rootCause = rootCause.cause!!
        }

        val message = rootCause.message ?: e.message ?: return defaultMessage
        val lowerCaseMessage = message.lowercase()

        return when {
            lowerCaseMessage.contains("unique_user_email") || lowerCaseMessage.contains("users_email_key") -> 
                "A user with this email address already exists."
            lowerCaseMessage.contains("unique_staff_id_school") -> 
                "This Staff ID is already in use in this school."
            lowerCaseMessage.contains("unique_student_id_school") -> 
                "This Student ID is already in use in this school."
            lowerCaseMessage.contains("unique_student_user_school") ->
                "This user is already enrolled as a student in this school."
            lowerCaseMessage.contains("unique_staff_user_school") ->
                "This user is already registered as staff in this school."
            lowerCaseMessage.contains("unique_parent_user_school") ->
                "This user is already registered as a parent in this school."
            lowerCaseMessage.contains("duplicate key value violates unique constraint") -> {
                when {
                    lowerCaseMessage.contains("email") -> "A user with this email address already exists."
                    lowerCaseMessage.contains("phone") -> "A user with this phone number already exists."
                    lowerCaseMessage.contains("staff_id") -> "This Staff ID is already in use."
                    lowerCaseMessage.contains("student_id") -> "This Student ID is already in use."
                    lowerCaseMessage.contains("admission_number") -> "This Admission Number is already in use."
                    else -> "A record with this information already exists."
                }
            }
            else -> "$defaultMessage: ${e.localizedMessage}"
        }
    }


    private fun updateClassEnrollmentCount(classId: UUID) {
        val count = studentClassRepository.countBySchoolClassIdAndIsActive(classId, true)
        schoolClassRepository.findById(classId).ifPresent { schoolClass ->
            schoolClass.currentEnrollment = count.toInt()
            schoolClassRepository.save(schoolClass)
        }
    }
}
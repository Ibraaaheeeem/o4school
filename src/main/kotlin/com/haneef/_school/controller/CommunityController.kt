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
    private val parentWalletService: com.haneef._school.service.ParentWalletService,
    private val parentWalletRepository: ParentWalletRepository,
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

    // Staff Management
    @GetMapping("/staff")
    fun staffList(
        model: Model,
        authentication: Authentication,
        session: HttpSession,
        @RequestParam(defaultValue = "0") page: Int = 0,
        @RequestParam(defaultValue = "12") size: Int = 12,
        @RequestParam(required = false) search: String? = null,
        @RequestParam(required = false) designation: String? = null,
        @RequestHeader(value = "HX-Request", required = false) hxRequest: Boolean? = null
    ): String {
        val customUser = authentication.principal as CustomUserDetails
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return if (hxRequest == true) "fragments/error :: error-message" else "redirect:/select-school"

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
                val allStaff = loadStaffWithTeacherAssignments(selectedSchoolId)
                val startIndex = (page * size).coerceAtMost(allStaff.size)
                val endIndex = ((page + 1) * size).coerceAtMost(allStaff.size)
                val pagedStaff = if (startIndex < allStaff.size) allStaff.subList(startIndex, endIndex) else emptyList()
                org.springframework.data.domain.PageImpl(pagedStaff, pageable, allStaff.size.toLong())
            }
        }

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

        return if (hxRequest == true) {
            "admin/community/staff/staff-cards :: staff-cards-content"
        } else {
            "admin/community/staff/list"
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
        val phoneNumber = staff.user.phoneNumber
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
        @RequestParam phoneNumber: String,
        session: HttpSession,
        redirectAttributes: RedirectAttributes
    ): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/select-school"

        try {
            // Combine country code and phone number
            val fullPhoneNumber = countryCode + phoneNumber
            
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
                // Check if user already exists by email
                val existingUser = if (!userDto.email.isNullOrBlank()) userRepository.findByEmail(userDto.email!!).orElse(null) else null
                val savedUser = if (existingUser != null) {
                    // Update existing user's phone if it was provided
                    if (fullPhoneNumber.isNotBlank()) {
                        existingUser.phoneNumber = fullPhoneNumber
                    }
                    userRepository.save(existingUser)
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
        @RequestParam phoneNumber: String,
        session: HttpSession,
        model: Model
    ): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "fragments/error :: error-message"

        try {
            // Combine country code and phone number
            val fullPhoneNumber = countryCode + phoneNumber
            
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
            } else {
                // Check if user already exists by email
                val existingUser = if (!userDto.email.isNullOrBlank()) userRepository.findByEmail(userDto.email!!).orElse(null) else null
                val savedUser = if (existingUser != null) {
                    // Update existing user's phone if it was provided
                    if (fullPhoneNumber.isNotBlank()) {
                        existingUser.phoneNumber = fullPhoneNumber
                    }
                    userRepository.save(existingUser)
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

            // Return updated staff list
            val pageable = PageRequest.of(0, 12, Sort.by("user.firstName"))
            val allStaff = loadStaffWithTeacherAssignments(selectedSchoolId)
            val pagedStaff = allStaff.take(12)
            val staffPage = org.springframework.data.domain.PageImpl(pagedStaff, pageable, allStaff.size.toLong())
            val designations = staffRepository.findDistinctDesignationsBySchoolId(selectedSchoolId)
            val communityStats = getCommunityStats(selectedSchoolId)
            
            model.addAttribute("staffPage", staffPage)
            model.addAttribute("designations", designations)
            model.addAttribute("communityStats", communityStats)
            
            return "admin/community/staff/staff-cards :: staff-cards-content"
        } catch (e: Exception) {
            model.addAttribute("error", handleDatabaseError(e, "Error saving staff"))
            return "fragments/error :: error-message"
        }
    }

    // Student Management

    @GetMapping("/students")
    fun studentList(
        model: Model,
        authentication: Authentication,
        session: HttpSession,
        @RequestParam(defaultValue = "0") page: Int = 0,
        @RequestParam(defaultValue = "12") size: Int = 12,
        @RequestParam(required = false) search: String? = null,
        @RequestParam(required = false, name = "classId") classIds: List<UUID>? = null,
        @RequestHeader(value = "HX-Request", required = false) hxRequest: Boolean? = null
    ): String {
        val customUser = authentication.principal as CustomUserDetails
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return if (hxRequest == true) "fragments/error :: error-message" else "redirect:/select-school"

        val pageable = PageRequest.of(page, size, Sort.by("user.firstName"))
        
        // Filter out null or empty classIds if any
        val validClassIds = classIds?.filterNotNull()?.filter { it.toString().isNotBlank() }
        
        // Apply filtering based on multiple class IDs
        val studentPage = when {
            !search.isNullOrBlank() && !validClassIds.isNullOrEmpty() -> {
                studentRepository.findBySchoolIdAndIsActiveAndClassIdInAndSearch(
                    selectedSchoolId, true, validClassIds, search, pageable)
            }
            !search.isNullOrBlank() -> {
                studentRepository.findBySchoolIdAndIsActiveAndUserFullNameContaining(
                    selectedSchoolId, true, search, pageable)
            }
            !validClassIds.isNullOrEmpty() -> {
                studentRepository.findBySchoolIdAndIsActiveAndClassIdIn(
                    selectedSchoolId, true, validClassIds, pageable)
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

        return if (hxRequest == true) {
            "admin/community/students/student-cards :: student-cards-content"
        } else {
            "admin/community/students/list"
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
                    this.phoneNumber = phoneNumber ?: ""
                    this.dateOfBirth = userDto.dateOfBirth
                    this.gender = userDto.gender
                }
                userRepository.save(existingUser)
                
                // Update student details
                existingStudent.apply {
                    this.admissionNumber = if (studentDto.admissionNumber.isNullOrBlank()) generateAdmissionNumber(selectedSchoolId) else studentDto.admissionNumber
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
            } else {
                // Check if user already exists by email
                val existingUser = if (!userDto.email.isNullOrBlank()) userRepository.findByEmail(userDto.email!!).orElse(null) else null
                val savedUser = if (existingUser != null) {
                    if (!phoneNumber.isNullOrBlank()) {
                        existingUser.phoneNumber = phoneNumber!!
                    }
                    userRepository.save(existingUser)
                } else {
                    val newUser = User(phoneNumber = if (phoneNumber.isNullOrBlank()) generateDummyPhoneNumber() else phoneNumber).apply {
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
                    val newStudent = Student(
                        user = savedUser,
                        studentId = studentId,
                        admissionDate = LocalDate.now()
                    ).apply {
                        this.schoolId = selectedSchoolId
                        this.admissionNumber = if (studentDto.admissionNumber.isNullOrBlank()) generateAdmissionNumber(selectedSchoolId) else studentDto.admissionNumber
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

            // Return updated student list
            val pageable = PageRequest.of(0, 12, Sort.by("user.firstName"))
            val studentPage = studentRepository.findBySchoolIdAndIsActiveWithEnrollments(selectedSchoolId, true, pageable)
            val tracks = educationTrackRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
            val classes = schoolClassRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
            val communityStats = getCommunityStats(selectedSchoolId)
            
            model.addAttribute("studentPage", studentPage)
            model.addAttribute("tracks", tracks)
            model.addAttribute("classes", classes)
            model.addAttribute("communityStats", communityStats)
            
            return "admin/community/students/student-cards :: student-cards-content"
        } catch (e: Exception) {
            model.addAttribute("error", handleDatabaseError(e, "Error saving student"))
            return "fragments/error :: error-message"
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
        @RequestParam(defaultValue = "0") page: Int = 0,
        @RequestParam(defaultValue = "12") size: Int = 12,
        @RequestParam(required = false) search: String? = null,
        @RequestHeader(value = "HX-Request", required = false) hxRequest: Boolean? = null
    ): String {
        val customUser = authentication.principal as CustomUserDetails
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return if (hxRequest == true) "fragments/error :: error-message" else "redirect:/select-school"

        val pageable = PageRequest.of(page, size, Sort.by("user.firstName"))
        
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

        return if (hxRequest == true) {
            "admin/community/parents/parent-cards :: parent-cards-content"
        } else {
            "admin/community/parents/list"
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
    fun deleteStaff(@PathVariable id: UUID, session: HttpSession, redirectAttributes: RedirectAttributes): String {
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
            redirectAttributes.addFlashAttribute("success", "Staff deleted successfully!")
        } catch (e: Exception) {
            redirectAttributes.addFlashAttribute("error", "Error deleting staff: ${e.message}")
        }
        return "redirect:/admin/community/staff"
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
                    phoneNumber = userDto.phoneNumber ?: ""
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
                    val newUser = User(phoneNumber = if (userDto.phoneNumber.isNullOrBlank()) generateDummyPhoneNumber() else userDto.phoneNumber!!).apply {
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
    fun deleteStudent(@PathVariable id: UUID, session: HttpSession, redirectAttributes: RedirectAttributes): String {
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
            redirectAttributes.addFlashAttribute("success", "Student deleted successfully!")
        } catch (e: Exception) {
            redirectAttributes.addFlashAttribute("error", "Error deleting student: ${e.message}")
        }
        return "redirect:/admin/community/students"
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
                    phoneNumber = userDto.phoneNumber ?: ""
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
                    val newUser = User(phoneNumber = userDto.phoneNumber ?: "").apply {
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
        model: Model
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
                    this.phoneNumber = userDto.phoneNumber ?: ""
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
            } else {
                // Check if user already exists by email
                val existingUser = if (!userDto.email.isNullOrBlank()) userRepository.findByEmail(userDto.email!!).orElse(null) else null
                val savedUser = if (existingUser != null) {
                    if (!userDto.phoneNumber.isNullOrBlank()) {
                        existingUser.phoneNumber = userDto.phoneNumber!!
                    }
                    userRepository.save(existingUser)
                } else {
                    val newUser = User(phoneNumber = userDto.phoneNumber ?: "").apply {
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
            }

            // Return updated parent list
            val pageable = PageRequest.of(0, 12, Sort.by("user.firstName"))
            val allParents = parentRepository.findBySchoolIdAndIsActiveWithRelationships(selectedSchoolId, true)
            val pagedParents = allParents.take(12)
            val parentPage = org.springframework.data.domain.PageImpl(pagedParents, pageable, allParents.size.toLong())
            val communityStats = getCommunityStats(selectedSchoolId)
            
            model.addAttribute("parentPage", parentPage)
            model.addAttribute("communityStats", communityStats)
            
            return "admin/community/parents/parent-cards :: parent-cards-content"
        } catch (e: Exception) {
            model.addAttribute("error", handleDatabaseError(e, "Error saving parent"))
            return "fragments/error :: error-message"
        }
    }



    @PostMapping("/parents/{id}/delete")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SYSTEM_ADMIN')")
    fun deleteParent(@PathVariable id: UUID, session: HttpSession, redirectAttributes: RedirectAttributes): String {
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
            redirectAttributes.addFlashAttribute("success", "Parent deleted successfully!")
        } catch (e: Exception) {
            redirectAttributes.addFlashAttribute("error", "Error deleting parent: ${e.message}")
        }
        return "redirect:/admin/community/parents"
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

    private fun generateDummyPhoneNumber(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val randomPart = (1..8).map { chars.random() }.joinToString("")
        return "STU-$randomPart"
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

            // Get current academic session and term
            val currentSession = academicSessionRepository.findBySchoolIdAndIsCurrentSessionAndIsActive(selectedSchoolId, true, true)
                ?: throw RuntimeException("No current academic session found")
            val currentTerm = termRepository.findByAcademicSessionIdAndIsCurrentTermAndIsActive(currentSession.id!!, true, true)
                .orElseThrow { RuntimeException("No current term found") }

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
            
            // Return updated student list
            val pageable = PageRequest.of(0, 20, Sort.by("user.firstName"))
            val studentPage = studentRepository.findBySchoolIdAndIsActiveWithEnrollments(selectedSchoolId, true, pageable)
            val tracks = educationTrackRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
            val classes = schoolClassRepository.findBySchoolIdAndIsActiveWithTrack(selectedSchoolId, true)
            val classesByTrack = classes.groupBy { it.track?.id }
            val communityStats = getCommunityStats(selectedSchoolId)
            
            model.addAttribute("studentPage", studentPage)
            model.addAttribute("tracks", tracks)
            model.addAttribute("classesByTrack", classesByTrack)
            model.addAttribute("communityStats", communityStats)
            model.addAttribute("selectedClassIds", emptyList<UUID>())
            
            return "admin/community/students/student-cards :: student-cards-content"
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
                    studentRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
                        .filter { it.id !in assignedStudentIds }
                        .take(10) // Limit for modal display
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
        model: Model
    ): String {
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
                model.addAttribute("success", "Parent successfully assigned to student")
            }
            
            // Return updated parent list
            val pageable = PageRequest.of(0, 20, Sort.by("user.firstName"))
            val allParents = parentRepository.findBySchoolIdAndIsActiveWithRelationships(selectedSchoolId, true)
            val pagedParents = allParents.take(20)
            val parentPage = org.springframework.data.domain.PageImpl(pagedParents, pageable, allParents.size.toLong())
            val communityStats = getCommunityStats(selectedSchoolId)
            
            model.addAttribute("parentPage", parentPage)
            model.addAttribute("communityStats", communityStats)
            
            return "admin/community/parents/parent-cards :: parent-cards-content"
        } catch (e: Exception) {
            model.addAttribute("error", "Error assigning parent to student: ${e.message}")
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
                "gradeLevel" to (schoolClass.gradeLevel ?: ""),
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

            // Get current academic session and term
            val currentSession = academicSessionRepository.findBySchoolIdAndIsCurrentSessionAndIsActive(selectedSchoolId, true, true)
                ?: throw RuntimeException("No current academic session found")
            val currentTerm = termRepository.findByAcademicSessionIdAndIsCurrentTermAndIsActive(currentSession.id!!, true, true)
                .orElseThrow { RuntimeException("No current term found") }

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
            
            // Return updated student list
            val pageable = PageRequest.of(0, 12, Sort.by("user.firstName"))
            val studentPage = studentRepository.findBySchoolIdAndIsActive(selectedSchoolId, true, pageable)
            val tracks = educationTrackRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
            val classes = schoolClassRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
            val communityStats = getCommunityStats(selectedSchoolId)
            
            model.addAttribute("studentPage", studentPage)
            model.addAttribute("tracks", tracks)
            model.addAttribute("classes", classes)
            model.addAttribute("communityStats", communityStats)
            
            return "admin/community/students/student-cards :: student-cards-content"
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
            
            // Return updated parent list
            val pageable = PageRequest.of(0, 12, Sort.by("user.firstName"))
            val allParents = parentRepository.findBySchoolIdAndIsActiveWithRelationships(selectedSchoolId, true)
            val pagedParents = allParents.take(12)
            val parentPage = org.springframework.data.domain.PageImpl(pagedParents, pageable, allParents.size.toLong())
            val communityStats = getCommunityStats(selectedSchoolId)
            
            model.addAttribute("parentPage", parentPage)
            model.addAttribute("communityStats", communityStats)
            
            return "admin/community/parents/parent-cards :: parent-cards-content"
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
        session: HttpSession,
        model: Model,
        authentication: Authentication,
        redirectAttributes: RedirectAttributes
    ): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/select-school"

        try {
            val parent = parentRepository.findById(parentId).orElseThrow()
            val result = parentWalletService.createWalletForParent(parent)
            
            if (result.isSuccess) {
                model.addAttribute("success", "Wallet created successfully for ${parent.user.fullName}")
            } else {
                model.addAttribute("error", "Error creating wallet: ${result.exceptionOrNull()?.message}")
            }
        } catch (e: Exception) {
            model.addAttribute("error", "Error: ${e.message}")
        }

        // Return the updated parent cards fragment
        return parentList(model, authentication, session, page, 12, search, null)
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
            ?: return "fragments/success :: success-message"

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
                // Check if user already exists by email
                val existingUser = if (!email.isNullOrBlank()) userRepository.findByEmail(email).orElse(null) else null
                val savedUser = if (existingUser != null) {
                    if (fullPhoneNumber.isNotBlank()) {
                        existingUser.phoneNumber = fullPhoneNumber
                    }
                    userRepository.save(existingUser)
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
            ?: return "fragments/success :: success-message"

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
                }
                userRepository.save(existingUser)
                
                existingStudent.apply {
                    this.studentId = studentId
                    this.dateOfBirth = if (dateOfBirth.isNullOrBlank()) null else LocalDate.parse(dateOfBirth)
                    this.gender = gender?.let { com.haneef._school.entity.Gender.valueOf(it.uppercase()) }
                }
                studentRepository.save(existingStudent)
                
                model.addAttribute("message", "Student updated successfully!")
            } else {
                // Check if user already exists by email
                val existingUser = if (!email.isNullOrBlank()) userRepository.findByEmail(email).orElse(null) else null
                val savedUser = if (existingUser != null) {
                    if (!phoneNumber.isNullOrBlank()) {
                        existingUser.phoneNumber = phoneNumber!!
                    }
                    userRepository.save(existingUser)
                } else {
                    // Create new student - use provided phone number or generate unique placeholder
                    val finalPhoneNumber = if (!phoneNumber.isNullOrBlank()) {
                        phoneNumber!!
                    } else {
                        generateDummyPhoneNumber()
                    }
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
                    val newStudent = Student(
                        user = savedUser,
                        studentId = studentId,
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
            ?: return "fragments/success :: success-message"

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
                // Check if user already exists by email
                val existingUser = if (!email.isNullOrBlank()) userRepository.findByEmail(email).orElse(null) else null
                val savedUser = if (existingUser != null) {
                    if (fullPhoneNumber.isNotBlank()) {
                        existingUser.phoneNumber = fullPhoneNumber
                    }
                    userRepository.save(existingUser)
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
        logger.info("=== Starting Class Teacher Assignment ===")
        logger.info("Request Parameters - staffId: $staffId, assignedClassId: $assignedClassId")
        logger.info("Authentication: ${authentication.name}")
        
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
        logger.info("Selected School ID from session: $selectedSchoolId")
        
        if (selectedSchoolId == null) {
            logger.error("No selected school ID found in session")
            return "fragments/error :: error-message"
        }

        try {
            logger.info("Fetching entities from database...")
            
            // Fetch staff
            val staff = staffRepository.findById(staffId).orElseThrow { 
                logger.error("Staff not found with ID: $staffId")
                RuntimeException("Staff not found") 
            }
            logger.info("Staff found: ${staff.user.firstName} ${staff.user.lastName} (ID: ${staff.id}, School: ${staff.schoolId})")
            
            // Fetch class
            val schoolClass = schoolClassRepository.findById(assignedClassId).orElseThrow { 
                logger.error("Class not found with ID: $assignedClassId")
                RuntimeException("Class not found") 
            }
            logger.info("Class found: ${schoolClass.className} (ID: ${schoolClass.id}, School: ${schoolClass.schoolId})")
            
            // Security checks
            logger.info("Performing security checks...")
            if (staff.schoolId != selectedSchoolId) {
                logger.error("Security violation: Staff school ID (${staff.schoolId}) does not match selected school ID ($selectedSchoolId)")
                return "fragments/error :: error-message"
            }
            if (schoolClass.schoolId != selectedSchoolId) {
                logger.error("Security violation: Class school ID (${schoolClass.schoolId}) does not match selected school ID ($selectedSchoolId)")
                return "fragments/error :: error-message"
            }
            logger.info("Security checks passed")
            
            // Get current academic session and term
            logger.info("Fetching current academic session and term...")
            val currentSession = academicSessionRepository.findBySchoolIdAndIsCurrentSessionAndIsActive(selectedSchoolId, true, true)
            if (currentSession == null) {
                logger.error("No current academic session found for school ID: $selectedSchoolId")
                throw RuntimeException("No current academic session found")
            }
            logger.info("Current session found: ${currentSession.sessionName} (ID: ${currentSession.id})")
            
            val currentTerm = termRepository.findByAcademicSessionIdAndIsCurrentTermAndIsActive(currentSession.id!!, true, true)
            if (!currentTerm.isPresent) {
                logger.error("No current term found for session ID: ${currentSession.id}")
                throw RuntimeException("No current term found")
            }
            val term = currentTerm.get()
            logger.info("Current term found: ${term.termName} (ID: ${term.id})")
            
            // Check if assignment already exists
            logger.info("Checking for existing assignment...")
            // Check if assignment already exists (active or inactive)
            logger.info("Checking for existing assignment...")
            val existingAssignment = classTeacherRepository.findByStaffIdAndSchoolClassIdAndAcademicSessionIdAndTermIdAndSchoolId(
                staffId, assignedClassId, currentSession.id!!, term.id!!, selectedSchoolId)
            
            if (existingAssignment != null) {
                if (existingAssignment.isActive) {
                    logger.warn("Assignment already exists and is active for staff: $staffId, class: $assignedClassId")
                    model.addAttribute("error", "Staff is already assigned as class teacher for this class")
                } else {
                    logger.info("Reactivating existing class teacher assignment...")
                    existingAssignment.isActive = true
                    classTeacherRepository.save(existingAssignment)
                    model.addAttribute("success", "Class teacher assignment reactivated successfully!")
                }
            } else {
                logger.info("Creating new class teacher assignment...")
                val classTeacher = ClassTeacher(
                    staff = staff,
                    schoolClass = schoolClass,
                    academicSession = currentSession,
                    term = term
                ).apply {
                    this.schoolId = selectedSchoolId
                    this.isActive = true
                }
                
                logger.info("Saving class teacher assignment to database...")
                val savedAssignment = classTeacherRepository.save(classTeacher)
                logger.info("Class teacher assignment saved successfully with ID: ${savedAssignment.id}")
                
                model.addAttribute("success", "Class teacher assignment created successfully!")
                logger.info("Success message added to model")
            }
        } catch (e: Exception) {
            logger.error("Error creating class teacher assignment", e)
            logger.error("Exception details: ${e.javaClass.simpleName} - ${e.message}")
            e.printStackTrace()
            model.addAttribute("error", "Error creating assignment: ${e.message}")
        }

        logger.info("Returning updated staff list...")
        // Return updated staff list
        return getUpdatedStaffList(selectedSchoolId, model)
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
        logger.info("=== Starting Subject Teacher Assignment ===")
        logger.info("Request Parameters - staffId: $staffId, assignedClassId: $assignedClassId, subjectId: $subjectId")
        logger.info("Authentication: ${authentication.name}")
        
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
        logger.info("Selected School ID from session: $selectedSchoolId")
        
        if (selectedSchoolId == null) {
            logger.error("No selected school ID found in session")
            return "fragments/error :: error-message"
        }

        try {
            logger.info("Fetching entities from database...")
            
            // Fetch staff
            val staff = staffRepository.findById(staffId).orElseThrow { 
                logger.error("Staff not found with ID: $staffId")
                RuntimeException("Staff not found") 
            }
            logger.info("Staff found: ${staff.user.firstName} ${staff.user.lastName} (ID: ${staff.id}, School: ${staff.schoolId})")
            
            // Fetch class
            val schoolClass = schoolClassRepository.findById(assignedClassId).orElseThrow { 
                logger.error("Class not found with ID: $assignedClassId")
                RuntimeException("Class not found") 
            }
            logger.info("Class found: ${schoolClass.className} (ID: ${schoolClass.id}, School: ${schoolClass.schoolId})")
            
            // Fetch subject
            val subject = subjectRepository.findById(subjectId).orElseThrow { 
                logger.error("Subject not found with ID: $subjectId")
                RuntimeException("Subject not found") 
            }
            logger.info("Subject found: ${subject.subjectName} (ID: ${subject.id}, School: ${subject.schoolId})")
            
            // Security checks
            logger.info("Performing security checks...")
            if (staff.schoolId != selectedSchoolId) {
                logger.error("Security violation: Staff school ID (${staff.schoolId}) does not match selected school ID ($selectedSchoolId)")
                return "fragments/error :: error-message"
            }
            if (schoolClass.schoolId != selectedSchoolId) {
                logger.error("Security violation: Class school ID (${schoolClass.schoolId}) does not match selected school ID ($selectedSchoolId)")
                return "fragments/error :: error-message"
            }
            if (subject.schoolId != selectedSchoolId) {
                logger.error("Security violation: Subject school ID (${subject.schoolId}) does not match selected school ID ($selectedSchoolId)")
                return "fragments/error :: error-message"
            }
            logger.info("Security checks passed")
            
            // Get current academic session and term
            logger.info("Fetching current academic session and term...")
            val currentSession = academicSessionRepository.findBySchoolIdAndIsCurrentSessionAndIsActive(selectedSchoolId, true, true)
            if (currentSession == null) {
                logger.error("No current academic session found for school ID: $selectedSchoolId")
                throw RuntimeException("No current academic session found")
            }
            logger.info("Current session found: ${currentSession.sessionName} (ID: ${currentSession.id})")
            
            val currentTerm = termRepository.findByAcademicSessionIdAndIsCurrentTermAndIsActive(currentSession.id!!, true, true)
            if (!currentTerm.isPresent) {
                logger.error("No current term found for session ID: ${currentSession.id}")
                throw RuntimeException("No current term found")
            }
            val term = currentTerm.get()
            logger.info("Current term found: ${term.termName} (ID: ${term.id})")
            
            // Check if assignment already exists
            logger.info("Checking for existing assignment...")
            // Check if assignment already exists (active or inactive)
            logger.info("Checking for existing assignment...")
            val existingAssignment = subjectTeacherRepository.findByStaffIdAndSubjectIdAndSchoolClassIdAndAcademicSessionIdAndTermIdAndSchoolId(
                staffId, subjectId, assignedClassId, currentSession.id!!, term.id!!, selectedSchoolId)
            
            if (existingAssignment != null) {
                if (existingAssignment.isActive) {
                    logger.warn("Assignment already exists and is active for staff: $staffId, subject: $subjectId, class: $assignedClassId")
                    model.addAttribute("error", "Staff is already assigned as subject teacher for this subject in this class")
                } else {
                    logger.info("Reactivating existing subject teacher assignment...")
                    existingAssignment.isActive = true
                    subjectTeacherRepository.save(existingAssignment)
                    model.addAttribute("success", "Subject teacher assignment reactivated successfully!")
                }
            } else {
                logger.info("Creating new subject teacher assignment...")
                val subjectTeacher = SubjectTeacher(
                    staff = staff,
                    subject = subject,
                    schoolClass = schoolClass,
                    academicSession = currentSession,
                    term = term
                ).apply {
                    this.schoolId = selectedSchoolId
                    this.isActive = true
                }
                
                logger.info("Saving subject teacher assignment to database...")
                val savedAssignment = subjectTeacherRepository.save(subjectTeacher)
                logger.info("Subject teacher assignment saved successfully with ID: ${savedAssignment.id}")
                
                model.addAttribute("success", "Subject teacher assignment created successfully!")
                logger.info("Success message added to model")
            }
        } catch (e: Exception) {
            logger.error("Error creating subject teacher assignment", e)
            logger.error("Exception details: ${e.javaClass.simpleName} - ${e.message}")
            e.printStackTrace()
            model.addAttribute("error", "Error creating assignment: ${e.message}")
        }
        
        logger.info("Returning updated staff list...")
        // Return updated staff list
        return getUpdatedStaffList(selectedSchoolId, model)
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
            
            return mapOf("success" to true, "message" to "Assignment removed successfully")
        } catch (e: Exception) {
            return mapOf("success" to false, "message" to "Error removing assignment: ${e.message}")
        }
    }

    private fun getUpdatedStaffList(selectedSchoolId: UUID, model: Model): String {
        logger.info("Generating updated staff list for school ID: $selectedSchoolId")
        
        val pageable = PageRequest.of(0, 12, Sort.by("user.firstName"))
        val allStaff = loadStaffWithTeacherAssignments(selectedSchoolId)
        logger.info("Loaded ${allStaff.size} staff members with teacher assignments")
        
        val pagedStaff = allStaff.take(12)
        val staffPage = org.springframework.data.domain.PageImpl(pagedStaff, pageable, allStaff.size.toLong())
        logger.info("Created staff page with ${pagedStaff.size} staff members (page 1 of ${if (allStaff.size <= 12) 1 else (allStaff.size + 11) / 12})")
        
        val designations = staffRepository.findDistinctDesignationsBySchoolId(selectedSchoolId)
        logger.info("Found ${designations.size} distinct designations")
        
        val communityStats = getCommunityStats(selectedSchoolId)
        logger.info("Generated community stats")
        
        model.addAttribute("staffPage", staffPage)
        model.addAttribute("designations", designations)
        model.addAttribute("communityStats", communityStats)
        
        logger.info("Returning staff cards template fragment")
        return "admin/community/staff/staff-cards :: staff-cards-content"
    }

    // Helper method to load staff with teacher assignments avoiding MultipleBagFetchException
    private fun loadStaffWithTeacherAssignments(schoolId: UUID): List<Staff> {
        // Get all staff without eager loading the assignments
        val allStaff = staffRepository.findBySchoolIdAndIsActiveWithTeacherAssignments(schoolId, true)
        
        // Get current academic session and term
        val currentSession = academicSessionRepository.findBySchoolIdAndIsCurrentSessionAndIsActive(schoolId, true, true)
            ?: return allStaff // Return staff without assignments if no current session
        val currentTerm = termRepository.findByAcademicSessionIdAndIsCurrentTermAndIsActive(currentSession.id!!, true, true)
            .orElse(null) ?: return allStaff // Return staff without assignments if no current term
        
        // Load class teacher assignments separately
        val classTeacherAssignments = classTeacherRepository.findBySchoolIdAndIsActiveAndSessionAndTermWithDetails(
            schoolId, true, currentSession.id!!, currentTerm.id!!
        )
        val classAssignmentsByStaff = classTeacherAssignments.groupBy { it.staff.id }
        
        // Load subject teacher assignments separately
        val subjectTeacherAssignments = subjectTeacherRepository.findBySchoolIdAndIsActiveAndSessionAndTermWithDetails(
            schoolId, true, currentSession.id!!, currentTerm.id!!
        )
        val subjectAssignmentsByStaff = subjectTeacherAssignments.groupBy { it.staff.id }
        
        // Assign the loaded assignments to staff
        allStaff.forEach { staff ->
            staff.classTeacherAssignments = (classAssignmentsByStaff[staff.id] ?: emptyList()).toMutableSet()
            staff.subjectTeacherAssignments = (subjectAssignmentsByStaff[staff.id] ?: emptyList()).toMutableSet()
        }
        
        return allStaff
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
package com.haneef._school.controller
import com.haneef._school.dto.*
import com.haneef._school.entity.*
import com.haneef._school.repository.*
import com.haneef._school.service.CustomUserDetails
import com.haneef._school.service.SchoolStructureService
import com.haneef._school.service.BankService
import com.haneef._school.service.PaystackRecipientService
import com.haneef._school.service.SchoolContentService
import jakarta.servlet.http.HttpSession
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.util.UUID

@Controller
@RequestMapping("/admin/school-setup")
@PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'SCHOOL_ADMIN', 'TEACHER', 'STAFF')")
class SchoolSetupController(
    private val schoolRepository: SchoolRepository,
    private val educationTrackRepository: EducationTrackRepository,
    private val departmentRepository: DepartmentRepository,
    private val schoolClassRepository: SchoolClassRepository,
    private val subjectRepository: SubjectRepository,
    private val classSubjectRepository: ClassSubjectRepository,
    private val schoolStructureService: SchoolStructureService,
    private val schoolBankAccountRepository: SchoolBankAccountRepository,
    private val bankService: BankService,
    private val paystackRecipientService: PaystackRecipientService,
    private val authorizationService: com.haneef._school.service.AuthorizationService,
    private val schoolContentService: SchoolContentService
) {

    @GetMapping("/academic-structure")
    fun academicStructure(model: Model, authentication: Authentication, session: HttpSession): String {
        val customUser = authentication.principal as CustomUserDetails
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/admin/school-setup/school-details"
        
        // Get school information
        val school = schoolRepository.findById(selectedSchoolId).orElseThrow { 
            RuntimeException("School not found") 
        }
        
        val tracks = educationTrackRepository.findAllWithHierarchy(selectedSchoolId)
        
        model.addAttribute("user", customUser.user)
        model.addAttribute("userRole", "School Administrator")
        model.addAttribute("school", school)
        model.addAttribute("tracks", tracks)
        model.addAttribute("newTrack", EducationTrack())
        model.addAttribute("newDepartment", Department())
        model.addAttribute("newClass", SchoolClass())
        
        return "admin/school-setup/academic-structure"
    }

    // --- Track Actions ---
    @PostMapping("/academic-structure/track/save")
    fun saveTrack(
        @ModelAttribute trackDto: EducationTrackDto,
        @RequestParam(required = false) id: UUID?,
        session: HttpSession,
        model: Model
    ): String {
        val selectedSchoolId = authorizationService.validateSchoolAccess(
            session.getAttribute("selectedSchoolId") as? UUID
        )
            
        try {
            if (id != null) {
                // Use secure validation
                val existing = authorizationService.validateAndGetEducationTrack(id, selectedSchoolId)
                
                existing.name = trackDto.name ?: ""
                existing.description = trackDto.description
                educationTrackRepository.save(existing)
            } else {
                val track = EducationTrack(
                    name = trackDto.name ?: "",
                    description = trackDto.description
                ).apply {
                    schoolId = selectedSchoolId
                    isActive = true
                }
                educationTrackRepository.save(track)
            }
            model.addAttribute("success", "Track saved successfully")
        } catch (e: Exception) {
            model.addAttribute("error", "Error saving track: ${e.message}")
        }
        
        val tracks = educationTrackRepository.findAllWithHierarchy(selectedSchoolId)
        model.addAttribute("tracks", tracks)
        
        return "admin/school-setup/fragments/structure-tree :: structure-tree-fragment"
    }

    @PostMapping("/academic-structure/track/delete")
    fun deleteTrack(@RequestParam id: UUID, session: HttpSession, model: Model): String {
        val selectedSchoolId = authorizationService.validateSchoolAccess(
            session.getAttribute("selectedSchoolId") as? UUID
        )

        try {
            // Use secure validation
            val track = authorizationService.validateAndGetEducationTrack(id, selectedSchoolId)
            
            track.isActive = false
            educationTrackRepository.save(track)
            model.addAttribute("success", "Track deleted successfully")
        } catch (e: Exception) {
            model.addAttribute("error", "Error deleting track")
        }
        
        val tracks = educationTrackRepository.findAllWithHierarchy(selectedSchoolId)
        model.addAttribute("tracks", tracks)

        return "admin/school-setup/fragments/structure-tree :: structure-tree-fragment"
    }

    // --- Department Actions ---
    @PostMapping("/academic-structure/department/save")
    fun saveDepartment(
        @ModelAttribute departmentDto: DepartmentDto,
        @RequestParam trackId: UUID,
        @RequestParam(required = false) id: UUID?,
        session: HttpSession,
        model: Model
    ): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/admin/school-setup/school-details"

        try {
            if (id != null) {
                val existing = departmentRepository.findById(id).orElseThrow()
                
                if (existing.schoolId != selectedSchoolId) {
                    model.addAttribute("error", "Unauthorized access to department")
                    return "admin/school-setup/fragments/structure-tree :: structure-tree-fragment"
                }
                
                existing.name = departmentDto.name ?: ""
                existing.description = departmentDto.description
                departmentRepository.save(existing)
            } else {
                val track = educationTrackRepository.findById(trackId).orElseThrow()
                
                if (track.schoolId != selectedSchoolId) {
                    model.addAttribute("error", "Unauthorized access to track")
                    return "admin/school-setup/fragments/structure-tree :: structure-tree-fragment"
                }
                
                val department = Department(
                    name = departmentDto.name ?: "",
                    description = departmentDto.description
                ).apply {
                    schoolId = selectedSchoolId
                    isActive = true
                    this.track = track
                }
                departmentRepository.save(department)
            }
            model.addAttribute("success", "Department saved successfully")
        } catch (e: Exception) {
            model.addAttribute("error", "Error saving department: ${e.message}")
        }
        
        val tracks = educationTrackRepository.findAllWithHierarchy(selectedSchoolId)
        model.addAttribute("tracks", tracks)

        return "admin/school-setup/fragments/structure-tree :: structure-tree-fragment"
    }

    @PostMapping("/academic-structure/department/delete")
    fun deleteDepartment(@RequestParam id: UUID, session: HttpSession, model: Model): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/admin/school-setup/school-details"

        try {
            val dept = departmentRepository.findById(id).orElseThrow()
            
            if (dept.schoolId != selectedSchoolId) {
                model.addAttribute("error", "Unauthorized access to department")
                return "admin/school-setup/fragments/structure-tree :: structure-tree-fragment"
            }
            
            dept.isActive = false
            departmentRepository.save(dept)
            model.addAttribute("success", "Department deleted successfully")
        } catch (e: Exception) {
            model.addAttribute("error", "Error deleting department")
        }
        
        val tracks = educationTrackRepository.findAllWithHierarchy(selectedSchoolId)
        model.addAttribute("tracks", tracks)

        return "admin/school-setup/fragments/structure-tree :: structure-tree-fragment"
    }

    // --- Class Actions ---
    @PostMapping("/academic-structure/class/save")
    fun saveClass(
        @ModelAttribute schoolClassDto: SchoolClassDto,
        @RequestParam departmentId: UUID,
        @RequestParam(required = false) id: UUID?,
        session: HttpSession,
        model: Model
    ): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/admin/school-setup/school-details"

        try {
            if (id != null) {
                val existing = schoolClassRepository.findById(id).orElseThrow()
                
                if (existing.schoolId != selectedSchoolId) {
                    model.addAttribute("error", "Unauthorized access to class")
                    return "admin/school-setup/fragments/structure-tree :: structure-tree-fragment"
                }
                
                existing.className = schoolClassDto.className ?: ""
                existing.maxCapacity = schoolClassDto.maxCapacity ?: 30
                schoolClassRepository.save(existing)
            } else {
                val dept = departmentRepository.findById(departmentId).orElseThrow()
                
                if (dept.schoolId != selectedSchoolId) {
                    model.addAttribute("error", "Unauthorized access to department")
                    return "admin/school-setup/fragments/structure-tree :: structure-tree-fragment"
                }
                
                val schoolClass = SchoolClass(
                    className = schoolClassDto.className ?: "",
                    maxCapacity = schoolClassDto.maxCapacity ?: 30
                ).apply {
                    schoolId = selectedSchoolId
                    isActive = true
                    this.department = dept
                    this.track = dept.track // Inherit track from department
                    this.classCode = (schoolClassDto.className ?: "").replace(" ", "").uppercase()
                    this.gradeLevel = schoolClassDto.className ?: ""
                }
                schoolClassRepository.save(schoolClass)
            }
            model.addAttribute("success", "Class saved successfully")
        } catch (e: Exception) {
            model.addAttribute("error", "Error saving class: ${e.message}")
        }
        
        val tracks = educationTrackRepository.findAllWithHierarchy(selectedSchoolId)
        model.addAttribute("tracks", tracks)

        return "admin/school-setup/fragments/structure-tree :: structure-tree-fragment"
    }

    @PostMapping("/academic-structure/class/delete")
    fun deleteClass(@RequestParam id: UUID, session: HttpSession, model: Model): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/admin/school-setup/school-details"

        try {
            val cls = schoolClassRepository.findById(id).orElseThrow()
            
            if (cls.schoolId != selectedSchoolId) {
                model.addAttribute("error", "Unauthorized access to class")
                return "admin/school-setup/fragments/structure-tree :: structure-tree-fragment"
            }
            
            cls.isActive = false
            schoolClassRepository.save(cls)
            model.addAttribute("success", "Class deleted successfully")
        } catch (e: Exception) {
            model.addAttribute("error", "Error deleting class")
        }
        
        val tracks = educationTrackRepository.findAllWithHierarchy(selectedSchoolId)
        model.addAttribute("tracks", tracks)

        return "admin/school-setup/fragments/structure-tree :: structure-tree-fragment"
    }

    @PostMapping("/academic-structure/generate-default")
    fun generateDefaultStructure(
        session: HttpSession,
        model: Model
    ): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/admin/school-setup/school-details"
        
        try {
            val result = schoolStructureService.generateDefaultStructure(selectedSchoolId)
            model.addAttribute("success", result["message"])
        } catch (e: Exception) {
            model.addAttribute("error", e.message)
        }
        
        val tracks = educationTrackRepository.findAllWithHierarchy(selectedSchoolId)
        model.addAttribute("tracks", tracks)
        
        return "admin/school-setup/fragments/structure-tree :: structure-tree-fragment"
    }

    @GetMapping
    fun schoolSetupHome(model: Model, authentication: Authentication, session: HttpSession): String {
        val customUser = authentication.principal as CustomUserDetails
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
        
        if (selectedSchoolId == null) {
            return "redirect:/select-school"
        }
        
        val school = schoolRepository.findById(selectedSchoolId).orElse(null)
        val setupProgress = calculateSetupProgress(selectedSchoolId)
        
        model.addAttribute("user", customUser.user)
        model.addAttribute("school", school)
        model.addAttribute("setupProgress", setupProgress)
        
        return "admin/school-setup/home"
    }

    // Step 1: School Details
    @GetMapping("/school-details")
    fun schoolDetails(model: Model, authentication: Authentication, session: HttpSession): String {
        val customUser = authentication.principal as CustomUserDetails
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
        
        val school = if (selectedSchoolId != null) {
            schoolRepository.findById(selectedSchoolId).orElse(null)
        } else null
        
        val bankAccount = if (selectedSchoolId != null) {
            schoolBankAccountRepository.findBySchoolId(selectedSchoolId)
        } else null
        
        val schoolDto = if (school != null) {
            SchoolDto(
                name = school.name,
                email = school.email,
                phone = school.phone,
                website = school.website,
                addressLine1 = school.addressLine1,
                addressLine2 = school.addressLine2,
                city = school.city,
                state = school.state,
                postalCode = school.postalCode,
                country = school.country,
                adminName = school.adminName,
                adminEmail = school.adminEmail,
                adminPhone = school.adminPhone,
                slug = school.slug,
                admissionPrefix = school.admissionPrefix
            )
        } else SchoolDto()
        
        model.addAttribute("user", customUser.user)
        model.addAttribute("schoolDto", schoolDto)
        model.addAttribute("bankAccount", bankAccount ?: SchoolBankAccount())
        model.addAttribute("banks", bankService.getAllBanks())
        model.addAttribute("isEdit", school != null)
        
        return "admin/school-setup/school-details"
    }

    @PostMapping("/school-details")
    fun saveSchoolDetails(
        @ModelAttribute schoolDto: SchoolDto,
        @RequestParam(required = false) bankCode: String?,
        @RequestParam(required = false) accountNumber: String?,
        @RequestParam(required = false) accountName: String?,
        authentication: Authentication,
        session: HttpSession,
        redirectAttributes: RedirectAttributes
    ): String {
        val customUser = authentication.principal as CustomUserDetails
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
        
        val savedSchool = if (selectedSchoolId != null) {
            // Update existing school
            val existingSchool = schoolRepository.findById(selectedSchoolId).orElse(null)
            if (existingSchool != null) {
                existingSchool.apply {
                    name = schoolDto.name
                    email = schoolDto.email
                    phone = schoolDto.phone
                    website = schoolDto.website
                    addressLine1 = schoolDto.addressLine1
                    addressLine2 = schoolDto.addressLine2
                    city = schoolDto.city
                    state = schoolDto.state
                    postalCode = schoolDto.postalCode
                    country = schoolDto.country ?: "Nigeria"
                    adminName = schoolDto.adminName
                    adminEmail = schoolDto.adminEmail
                    adminPhone = schoolDto.adminPhone
                    slug = schoolDto.slug
                    admissionPrefix = schoolDto.admissionPrefix
                }
                schoolRepository.save(existingSchool)
            } else throw RuntimeException("School not found")
        } else {
            // Create new school
            val newSchool = School().apply {
                name = schoolDto.name
                email = schoolDto.email
                phone = schoolDto.phone
                website = schoolDto.website
                addressLine1 = schoolDto.addressLine1
                addressLine2 = schoolDto.addressLine2
                city = schoolDto.city
                state = schoolDto.state
                postalCode = schoolDto.postalCode
                country = schoolDto.country ?: "Nigeria"
                adminName = schoolDto.adminName
                adminEmail = schoolDto.adminEmail
                adminPhone = schoolDto.adminPhone
                slug = if (schoolDto.slug.isNullOrBlank()) generateSlug(schoolDto.name ?: "") else schoolDto.slug
                admissionPrefix = schoolDto.admissionPrefix
            }
            schoolRepository.save(newSchool).also {
                session.setAttribute("selectedSchoolId", it.id)
            }
        }
        
        // Save bank details
        if (!bankCode.isNullOrBlank() && !accountNumber.isNullOrBlank() && !accountName.isNullOrBlank()) {
            val bankAccount = schoolBankAccountRepository.findBySchoolId(savedSchool.id!!) 
                ?: SchoolBankAccount(school = savedSchool, bankName = "", accountNumber = "", accountName = "")
            
            val bankInfo = bankService.getBankByCode(bankCode)
            bankAccount.apply {
                this.bankName = bankInfo?.name ?: "Unknown Bank"
                this.bankCode = bankCode
                this.accountNumber = accountNumber
                this.accountName = accountName
            }
            schoolBankAccountRepository.save(bankAccount)
        }
        
        redirectAttributes.addFlashAttribute("success", "School details saved successfully!")
        return "redirect:/admin/school-setup/school-details"
    }

    // Step 2: Education Tracks
    @GetMapping("/education-tracks")
    fun educationTracks(model: Model, authentication: Authentication, session: HttpSession): String {
        val customUser = authentication.principal as CustomUserDetails
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/admin/school-setup/school-details"
        
        val tracks = educationTrackRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
        
        model.addAttribute("user", customUser.user)
        model.addAttribute("tracks", tracks)
        model.addAttribute("newTrack", EducationTrack())
        
        return "admin/school-setup/education-tracks"
    }

    @PostMapping("/education-tracks")
    fun saveEducationTrack(
        @ModelAttribute trackDto: EducationTrackDto,
        session: HttpSession,
        redirectAttributes: RedirectAttributes
    ): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/admin/school-setup/school-details"
        
        val track = EducationTrack(
            name = trackDto.name ?: "",
            description = trackDto.description
        ).apply {
            schoolId = selectedSchoolId
            isActive = true
        }
        educationTrackRepository.save(track)
        
        redirectAttributes.addFlashAttribute("success", "Education track added successfully!")
        return "redirect:/admin/school-setup/education-tracks"
    }

    // Step 3: Departments
    @GetMapping("/departments")
    fun departments(model: Model, authentication: Authentication, session: HttpSession): String {
        val customUser = authentication.principal as CustomUserDetails
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/admin/school-setup/school-details"
        
        val departments = departmentRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
        
        model.addAttribute("user", customUser.user)
        model.addAttribute("departments", departments)
        model.addAttribute("newDepartment", Department())
        
        return "admin/school-setup/departments"
    }

    @PostMapping("/departments")
    fun saveDepartment(
        @ModelAttribute departmentDto: DepartmentDto,
        session: HttpSession,
        redirectAttributes: RedirectAttributes
    ): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/admin/school-setup/school-details"
        
        val department = Department(
            name = departmentDto.name ?: "",
            description = departmentDto.description
        ).apply {
            schoolId = selectedSchoolId
            isActive = true
            // Note: In this simple form, track might not be selected, or we might need to add track selection
            if (departmentDto.trackId != null) {
                val track = educationTrackRepository.findById(departmentDto.trackId!!).orElse(null)
                if (track != null && track.schoolId == selectedSchoolId) {
                    this.track = track
                }
            }
        }
        departmentRepository.save(department)
        
        redirectAttributes.addFlashAttribute("success", "Department added successfully!")
        return "redirect:/admin/school-setup/departments"
    }

    // Step 4: Classes
    @GetMapping("/classes")
    fun classes(model: Model, authentication: Authentication, session: HttpSession): String {
        val customUser = authentication.principal as CustomUserDetails
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/admin/school-setup/school-details"
        
        val classes = schoolClassRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
        val tracks = educationTrackRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
        val departments = departmentRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
        
        model.addAttribute("user", customUser.user)
        model.addAttribute("classes", classes)
        model.addAttribute("tracks", tracks)
        model.addAttribute("departments", departments)
        model.addAttribute("newClass", SchoolClass())
        
        return "admin/school-setup/classes"
    }

    @PostMapping("/classes")
    fun saveClass(
        @ModelAttribute schoolClassDto: SchoolClassDto,
        session: HttpSession,
        redirectAttributes: RedirectAttributes
    ): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/admin/school-setup/school-details"
        
        val schoolClass = SchoolClass(
            className = schoolClassDto.className ?: "",
            maxCapacity = schoolClassDto.maxCapacity ?: 30
        ).apply {
            schoolId = selectedSchoolId
            isActive = true
            classCode = (schoolClassDto.className ?: "").replace(" ", "").uppercase()
            gradeLevel = schoolClassDto.className ?: ""
            
            if (schoolClassDto.departmentId != null) {
                val dept = departmentRepository.findById(schoolClassDto.departmentId!!).orElse(null)
                if (dept != null && dept.schoolId == selectedSchoolId) {
                    this.department = dept
                    this.track = dept.track
                }
            }
        }
        schoolClassRepository.save(schoolClass)
        
        redirectAttributes.addFlashAttribute("success", "Class added successfully!")
        return "redirect:/admin/school-setup/classes"
    }

    // Step 5: Subjects
    @GetMapping("/subjects")
    fun subjects(
        model: Model, 
        authentication: Authentication, 
        session: HttpSession,
        @RequestParam(required = false) trackId: UUID?,
        @RequestParam(required = false) classId: UUID?
    ): String {
        val customUser = authentication.principal as CustomUserDetails
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/admin/school-setup/school-details"
        
        // Get all data for filtering
        val tracks = educationTrackRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
        val allClasses = schoolClassRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
        val departments = departmentRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
        
        // Filter classes by track if specified
        val filteredClasses = if (trackId != null) {
            allClasses.filter { it.track?.id == trackId }
        } else {
            allClasses
        }
        
        // Get subjects with their class assignments
        
        // Get subjects with their class assignments
        val allSubjects = subjectRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
        val subjectsWithClasses = allSubjects.map { subject ->
            val classAssignments = classSubjectRepository.findBySubjectIdAndIsActive(subject.id!!, true)
            val assignedClasses = classAssignments.mapNotNull { it.schoolClass }
            
            // Filter assigned classes based on track and class filters
            val relevantClasses = assignedClasses.filter { assignedClass ->
                val matchesTrack = trackId == null || assignedClass.track?.id == trackId
                val matchesClass = classId == null || assignedClass.id == classId
                matchesTrack && matchesClass
            }
            
            SubjectWithClasses(subject, relevantClasses)
        }.filter { subjectWithClasses ->
            // Show subjects that have relevant class assignments or show all if no filters applied
            when {
                trackId != null || classId != null -> subjectWithClasses.assignedClasses.isNotEmpty()
                else -> true // Show all subjects when no filters are applied
            }
        }
        
        model.addAttribute("user", customUser.user)
        model.addAttribute("subjectsWithClasses", subjectsWithClasses)
        model.addAttribute("tracks", tracks)
        model.addAttribute("allClasses", allClasses)
        model.addAttribute("filteredClasses", filteredClasses)
        model.addAttribute("departments", departments)
        model.addAttribute("selectedTrackId", trackId)
        model.addAttribute("selectedClassId", classId)
        model.addAttribute("newSubject", Subject())
        
        return "admin/school-setup/subjects"
    }

    @PostMapping("/subjects")
    fun saveSubject(
        @ModelAttribute subjectDto: SubjectDto,
        @RequestParam(required = false) id: UUID?,
        session: HttpSession,
        redirectAttributes: RedirectAttributes
    ): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/admin/school-setup/school-details"
        
        try {
            val savedSubject = if (id != null) {
                // Update existing subject
                val existingSubject = subjectRepository.findById(id).orElseThrow()
                
                if (existingSubject.schoolId != selectedSchoolId) {
                    redirectAttributes.addFlashAttribute("error", "Unauthorized access to subject")
                    return "redirect:/admin/school-setup/subjects"
                }
                
                existingSubject.apply {
                    subjectName = subjectDto.subjectName ?: ""
                    subjectCode = subjectDto.subjectCode
                    isCoreSubject = subjectDto.isCoreSubject
                    description = subjectDto.description
                }
                subjectRepository.save(existingSubject)
            } else {
                // Create new subject
                val subject = Subject(
                    subjectName = subjectDto.subjectName ?: "",
                    subjectCode = subjectDto.subjectCode,
                    isCoreSubject = subjectDto.isCoreSubject,
                    description = subjectDto.description
                ).apply {
                    schoolId = selectedSchoolId
                    isActive = true
                }
                subjectRepository.save(subject)
            }
            
            // Update class assignments
            updateSubjectClassAssignments(savedSubject.id!!, subjectDto.assignedClassIds.filterNotNull(), selectedSchoolId)
            
            val message = if (id != null) "Subject updated successfully!" else "Subject added successfully!"
            redirectAttributes.addFlashAttribute("success", message)
        } catch (e: Exception) {
            redirectAttributes.addFlashAttribute("error", "Error saving subject: ${e.message}")
        }
        
        return "redirect:/admin/school-setup/subjects"
    }

    // Step 6: Landing Page Content
    @GetMapping("/landing-page")
    fun landingPage(model: Model, authentication: Authentication, session: HttpSession): String {
        val customUser = authentication.principal as CustomUserDetails
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/admin/school-setup/school-details"
        
        val school = schoolRepository.findById(selectedSchoolId).orElse(null)
            ?: return "redirect:/admin/school-setup/school-details"
        
        val sections = schoolContentService.getCustomizableSections()
        val sectionContents = mutableMapOf<String, String>()
        val hasCustomContent = mutableMapOf<String, Boolean>()

        sections.forEach { section ->
            sectionContents[section] = schoolContentService.getSchoolContent(school, section)
            hasCustomContent[section] = schoolContentService.hasCustomContent(selectedSchoolId, section)
        }
        
        model.addAttribute("user", customUser.user)
        model.addAttribute("school", school)
        model.addAttribute("sections", sections)
        model.addAttribute("sectionContents", sectionContents)
        model.addAttribute("hasCustomContent", hasCustomContent)
        
        return "admin/school-setup/landing-page"
    }
    
    private fun updateSubjectClassAssignments(subjectId: UUID, assignedClassIds: List<UUID>, schoolId: UUID) {
        // Get all existing assignments (both active and inactive) for this subject
        val allExistingAssignments = classSubjectRepository.findBySubjectId(subjectId)
        
        // Deactivate all currently active assignments
        val currentlyActiveAssignments = allExistingAssignments.filter { it.isActive }
        currentlyActiveAssignments.forEach { assignment ->
            assignment.isActive = false
            classSubjectRepository.save(assignment)
        }
        
        // Process new assignments
        assignedClassIds.forEach { classId ->
            val schoolClass = schoolClassRepository.findById(classId).orElse(null)
            val subject = subjectRepository.findById(subjectId).orElse(null)
            
            if (schoolClass != null && subject != null) {
                // Check if an assignment already exists (active or inactive)
                val existingAssignment = allExistingAssignments.find { 
                    it.schoolClass?.id == classId && it.subject?.id == subjectId 
                }
                
                if (existingAssignment != null) {
                    // Reactivate existing assignment
                    existingAssignment.isActive = true
                    classSubjectRepository.save(existingAssignment)
                } else {
                    // Create new assignment
                    val classSubject = ClassSubject(
                        schoolClass = schoolClass,
                        subject = subject
                    ).apply {
                        isActive = true
                        this.schoolId = schoolId
                    }
                    classSubjectRepository.save(classSubject)
                }
            }
        }
    }
    
    @GetMapping("/classes-by-track/{trackId}")
    @ResponseBody
    fun getClassesByTrack(@PathVariable trackId: UUID, session: HttpSession): List<ClassOption> {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID ?: return emptyList()
        
        val classes = schoolClassRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
            .filter { it.track?.id == trackId }
        
        return classes.map { schoolClass ->
            ClassOption(
                id = schoolClass.id!!,
                className = schoolClass.className,
                departmentName = schoolClass.department?.name,
                trackId = schoolClass.track?.id ?: UUID.randomUUID(),
                trackName = schoolClass.track?.name
            )
        }
    }

    @GetMapping("/subjects/{id}/edit")
    @ResponseBody
    fun getSubjectForEdit(@PathVariable id: UUID, session: HttpSession): SubjectEditDto? {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID ?: return null
        val subject = subjectRepository.findById(id).orElse(null) ?: return null
        
        if (subject.schoolId != selectedSchoolId) return null
        
        // Get assigned classes
        val classAssignments = classSubjectRepository.findBySubjectIdAndIsActive(subject.id!!, true)
        val assignedClassIds = classAssignments.mapNotNull { it.schoolClass?.id }
        
        // Get all available tracks for this school
        val allTracks = educationTrackRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
        val availableTracks = allTracks.map { track ->
            TrackOption(
                id = track.id!!,
                name = track.name
            )
        }
        
        // Get all available classes for this school
        val allClasses = schoolClassRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
        val availableClasses = allClasses.map { schoolClass ->
            ClassOption(
                id = schoolClass.id!!,
                className = schoolClass.className,
                departmentName = schoolClass.department?.name,
                trackId = schoolClass.track?.id ?: UUID.randomUUID(),
                trackName = schoolClass.track?.name
            )
        }
        
        return SubjectEditDto(
            id = subject.id!!,
            subjectName = subject.subjectName,
            subjectCode = subject.subjectCode,
            isCoreSubject = subject.isCoreSubject ?: false,
            description = subject.description,
            assignedClassIds = assignedClassIds,
            availableTracks = availableTracks,
            availableClasses = availableClasses
        )
    }

    @PostMapping("/academic-structure/subjects/generate-default")
    fun generateDefaultSubjects(
        session: HttpSession,
        model: Model
    ): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/admin/school-setup/school-details"
        
        try {
            val result = schoolStructureService.generateDefaultSubjects(selectedSchoolId)
            model.addAttribute("success", result["message"])
        } catch (e: Exception) {
            model.addAttribute("error", e.message)
        }
        
        val subjects = subjectRepository.findBySchoolIdAndIsActive(selectedSchoolId, true)
        model.addAttribute("subjects", subjects)
        
        return "admin/school-setup/subjects :: subjects-list-fragment"
    }

    // Assign subjects to classes
    @PostMapping("/assign-subject")
    fun assignSubjectToClass(
        @RequestParam subjectId: UUID,
        @RequestParam classId: UUID,
        session: HttpSession,
        redirectAttributes: RedirectAttributes
    ): String {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
            ?: return "redirect:/admin/school-setup/school-details"
        
        // Get the actual entities
        val schoolClass = schoolClassRepository.findById(classId).orElse(null)
        val subject = subjectRepository.findById(subjectId).orElse(null)
        
        if (schoolClass != null && subject != null) {
            if (schoolClass.schoolId != selectedSchoolId || subject.schoolId != selectedSchoolId) {
                redirectAttributes.addFlashAttribute("error", "Unauthorized access to class or subject")
                return "redirect:/admin/school-setup/subjects"
            }
            
            // Check if assignment already exists
            val existing = classSubjectRepository.findBySchoolClassIdAndSubjectIdAndIsActive(classId, subjectId, true)
            if (existing == null) {
                val classSubject = ClassSubject(
                    schoolClass = schoolClass,
                    subject = subject
                ).apply { 
                    isActive = true
                    schoolId = selectedSchoolId
                }
                
                classSubjectRepository.save(classSubject)
                redirectAttributes.addFlashAttribute("success", "Subject assigned to class successfully!")
            } else {
                redirectAttributes.addFlashAttribute("error", "Subject is already assigned to this class!")
            }
        } else {
            redirectAttributes.addFlashAttribute("error", "Invalid class or subject selected!")
        }
        
        return "redirect:/admin/school-setup/subjects"
    }

    private fun calculateSetupProgress(schoolId: UUID): Map<String, Any> {
        val school = schoolRepository.findById(schoolId).orElse(null)
        val tracksCount = educationTrackRepository.countBySchoolIdAndIsActive(schoolId, true)
        val departmentsCount = departmentRepository.countBySchoolIdAndIsActive(schoolId, true)
        val classesCount = schoolClassRepository.countBySchoolIdAndIsActive(schoolId, true)
        val subjectsCount = subjectRepository.countBySchoolIdAndIsActive(schoolId, true)
        
        val steps = mutableMapOf<String, Boolean>()
        steps["schoolDetails"] = school != null && school.name?.isNotBlank() == true
        steps["educationTracks"] = tracksCount > 0L
        steps["departments"] = departmentsCount > 0L
        steps["classes"] = classesCount > 0L
        steps["subjects"] = subjectsCount > 0L
        
        val hasCustomContent = schoolContentService.getCustomizableSections().any { section ->
            schoolContentService.hasCustomContent(schoolId, section)
        }
        steps["landingPage"] = hasCustomContent
        
        val completedSteps = steps.values.count { it }
        val totalSteps = steps.size
        val progressPercentage = (completedSteps * 100) / totalSteps
        
        return mapOf(
            "steps" to steps,
            "completedSteps" to completedSteps,
            "totalSteps" to totalSteps,
            "progressPercentage" to progressPercentage
        )
    }

    private fun generateSlug(name: String): String {
        return name.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), "")
            .replace(Regex("\\s+"), "-")
            .trim('-')
    }
    
    @GetMapping("/resolve-bank-account")
    @ResponseBody
    fun resolveBankAccount(
        @RequestParam accountNumber: String,
        @RequestParam bankCode: String
    ): Map<String, Any> {
        val result = paystackRecipientService.verifyBankAccount(accountNumber, bankCode)
        return if (result.isSuccess) {
            mapOf("status" to true, "accountName" to result.getOrNull()!!)
        } else {
            mapOf("status" to false, "message" to (result.exceptionOrNull()?.message ?: "Verification failed"))
        }
    }
    
    @GetMapping("/check-slug")
    @ResponseBody
    fun checkSlugAvailability(@RequestParam slug: String, session: HttpSession): Map<String, Any> {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
        val school = schoolRepository.findBySlug(slug).orElse(null)
        val isAvailable = school == null || school.id == selectedSchoolId
        return mapOf("available" to isAvailable)
    }

    @GetMapping("/check-prefix")
    @ResponseBody
    fun checkPrefixAvailability(@RequestParam prefix: String, session: HttpSession): Map<String, Any> {
        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
        val school = schoolRepository.findByAdmissionPrefix(prefix).orElse(null)
        val isAvailable = school == null || school.id == selectedSchoolId
        return mapOf("available" to isAvailable)
    }

    data class SubjectWithClasses(
        val subject: Subject,
        val assignedClasses: List<SchoolClass>
    )
    
    data class SubjectEditDto(
        val id: UUID,
        val subjectName: String?,
        val subjectCode: String?,
        val isCoreSubject: Boolean?,
        val description: String?,
        val assignedClassIds: List<UUID>,
        val availableTracks: List<TrackOption>,
        val availableClasses: List<ClassOption>
    )
    
    data class TrackOption(
        val id: UUID,
        val name: String
    )
    
    data class ClassOption(
        val id: UUID,
        val className: String,
        val departmentName: String?,
        val trackId: UUID,
        val trackName: String?
    )
}
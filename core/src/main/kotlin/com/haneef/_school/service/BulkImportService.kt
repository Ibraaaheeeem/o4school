package com.haneef._school.service

import com.haneef._school.dto.*
import com.haneef._school.entity.*
import com.haneef._school.repository.*
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.UUID

@Service
class BulkImportService(
    private val userRepository: UserRepository,
    private val studentRepository: StudentRepository,
    private val parentRepository: ParentRepository,
    private val staffRepository: StaffRepository,
    private val roleRepository: RoleRepository,
    private val userSchoolRoleRepository: UserSchoolRoleRepository,
    private val passwordEncoder: PasswordEncoder,
    private val transactionTemplate: TransactionTemplate,
    private val schoolRepository: SchoolRepository
) {

    private val dateFormatter = DateTimeFormatter.ofPattern(DATE_PATTERN)

    companion object {
        private const val ROLE_PARENT = "PARENT"
        private const val ROLE_STAFF = "STAFF"
        private const val ROLE_STUDENT = "STUDENT"
        
        private const val PASSWORD_PARENT = "parent123"
        private const val PASSWORD_STAFF = "staff123"
        private const val PASSWORD_STUDENT = "student123"
        
        private const val DATE_PATTERN = "dd/MM/yyyy"
        private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern(DATE_PATTERN)
    }

    /**
     * Validates the imported data and returns a preview of what will be imported
     */
    fun validateAndPreview(data: BulkImportDataDTO, schoolId: UUID): BulkImportPreviewDTO {
        val studentsPreview = validateStudents(data.students, schoolId)
        val parentsPreview = validateParents(data.parents, schoolId)
        val staffPreview = validateStaff(data.staff, schoolId)

        val hasErrors = studentsPreview.errors.any { it.severity == ErrorSeverity.ERROR } ||
                       parentsPreview.errors.any { it.severity == ErrorSeverity.ERROR } ||
                       staffPreview.errors.any { it.severity == ErrorSeverity.ERROR }

        return BulkImportPreviewDTO(
            students = studentsPreview,
            parents = parentsPreview,
            staff = staffPreview,
            hasErrors = hasErrors
        )
    }

    private fun validateStudents(students: List<StudentImportData>, schoolId: UUID): ImportCategoryPreview {
        val errors = mutableListOf<ImportError>()
        val validData = mutableListOf<Map<String, String>>()
        var duplicates = 0

        students.forEachIndexed { index, student ->
            val rowNumber = index + 2 // +2 because index starts at 0 and row 1 is header
            
            try {
                // Validate required fields
                if (student.firstName.isBlank()) {
                    errors.add(ImportError(rowNumber, "FirstName", "First name is required", ErrorSeverity.ERROR))
                    return@forEachIndexed
                }
                if (student.lastName.isBlank()) {
                    errors.add(ImportError(rowNumber, "LastName", "Last name is required", ErrorSeverity.ERROR))
                    return@forEachIndexed
                }
                if (student.gender.isBlank()) {
                    errors.add(ImportError(rowNumber, "Gender", "Gender is required", ErrorSeverity.ERROR))
                    return@forEachIndexed
                }
                if (student.dateOfBirth.isBlank()) {
                    errors.add(ImportError(rowNumber, "DateOfBirth", "Date of birth is required", ErrorSeverity.ERROR))
                    return@forEachIndexed
                }
                
                if (student.admissionNumber.isNullOrBlank()) {
                    errors.add(ImportError(rowNumber, "AdmissionNumber", "Admission number is required", ErrorSeverity.ERROR))
                    return@forEachIndexed
                }

                // Validate gender
                if (student.gender !in listOf("M", "F")) {
                    errors.add(ImportError(rowNumber, "Gender", "Gender must be 'M' or 'F'", ErrorSeverity.ERROR))
                    return@forEachIndexed
                }

                // Validate date format
                try {
                    LocalDate.parse(student.dateOfBirth, DATE_FORMATTER)
                } catch (e: DateTimeParseException) {
                    errors.add(ImportError(rowNumber, "DateOfBirth", 
                        "Invalid date format. Expected $DATE_PATTERN", ErrorSeverity.ERROR))
                    return@forEachIndexed
                }

                // Check for duplicate admission number IN THIS SCHOOL
                val exists = studentRepository.findByAdmissionNumberAndSchoolId(student.admissionNumber, schoolId) != null
                if (exists) {
                    errors.add(ImportError(rowNumber, "AdmissionNumber", 
                        "Student with admission number '${student.admissionNumber}' already exists in this school", ErrorSeverity.WARNING))
                    duplicates++
                    return@forEachIndexed
                }

                // Add to valid data
                validData.add(mapOf(
                    "FirstName" to student.firstName,
                    "LastName" to student.lastName,
                    "MiddleName" to (student.middleName ?: ""),
                    "Gender" to student.gender,
                    "DateOfBirth" to student.dateOfBirth,
                    "AdmissionNumber" to student.admissionNumber,
                    "Address" to (student.address ?: "")
                ))
            } catch (e: Exception) {
                errors.add(ImportError(rowNumber, null, "Error processing row: ${e.message}", ErrorSeverity.ERROR))
            }
        }

        return ImportCategoryPreview(
            totalRows = students.size,
            validEntries = validData.size,
            duplicates = duplicates,
            errors = errors,
            validData = validData
        )
    }

    private fun validateParents(parents: List<ParentImportData>, schoolId: UUID): ImportCategoryPreview {
        val errors = mutableListOf<ImportError>()
        val validData = mutableListOf<Map<String, String>>()
        var duplicates = 0

        // Fetch ROLE_PARENT once
        val parentRole = roleRepository.findByName(ROLE_PARENT).orElseThrow()

        parents.forEachIndexed { index, parent ->
            val rowNumber = index + 2
            
            try {
                // Validate required fields
                if (parent.firstName.isBlank()) {
                    errors.add(ImportError(rowNumber, "FirstName", "First name is required", ErrorSeverity.ERROR))
                    return@forEachIndexed
                }
                if (parent.lastName.isBlank()) {
                    errors.add(ImportError(rowNumber, "LastName", "Last name is required", ErrorSeverity.ERROR))
                    return@forEachIndexed
                }
                if (parent.email.isBlank()) {
                    errors.add(ImportError(rowNumber, "Email", "Email is required", ErrorSeverity.ERROR))
                    return@forEachIndexed
                }
                if (parent.phoneNumber.isBlank()) {
                    errors.add(ImportError(rowNumber, "PhoneNumber", "Phone number is required", ErrorSeverity.ERROR))
                    return@forEachIndexed
                }

                // Validate email format
                if (!parent.email.matches(EMAIL_REGEX)) {
                    errors.add(ImportError(rowNumber, "Email", "Invalid email format", ErrorSeverity.ERROR))
                    return@forEachIndexed
                }

                // Check if user exists
                val existingUser = userRepository.findByEmail(parent.email).orElse(null)
                
                if (existingUser != null) {
                    // Check if user has PARENT role IN THIS SCHOOL
                    val roleExists = userSchoolRoleRepository.existsByUserIdAndSchoolIdAndRoleId(existingUser.id!!, schoolId, parentRole.id!!)
                    
                    if (roleExists) {
                        errors.add(ImportError(rowNumber, "Email", 
                            "Parent with this Email already exists in this school", ErrorSeverity.WARNING))
                        duplicates++
                        return@forEachIndexed
                    }
                }

                validData.add(mapOf(
                    "FirstName" to parent.firstName,
                    "LastName" to parent.lastName,
                    "Email" to parent.email,
                    "PhoneNumber" to parent.phoneNumber,
                    "Address" to (parent.address ?: "")
                ))
            } catch (e: Exception) {
                errors.add(ImportError(rowNumber, null, "Error processing row: ${e.message}", ErrorSeverity.ERROR))
            }
        }

        return ImportCategoryPreview(
            totalRows = parents.size,
            validEntries = validData.size,
            duplicates = duplicates,
            errors = errors,
            validData = validData
        )
    }

    private fun validateStaff(staff: List<StaffImportData>, schoolId: UUID): ImportCategoryPreview {
        val errors = mutableListOf<ImportError>()
        val validData = mutableListOf<Map<String, String>>()
        var duplicates = 0

        // Fetch ROLE_STAFF once
        val staffRole = roleRepository.findByName(ROLE_STAFF).orElseThrow()

        staff.forEachIndexed { index, staffMember ->
            val rowNumber = index + 2
            
            try {
                // Validate required fields
                if (staffMember.firstName.isBlank()) {
                    errors.add(ImportError(rowNumber, "FirstName", "First name is required", ErrorSeverity.ERROR))
                    return@forEachIndexed
                }
                if (staffMember.lastName.isBlank()) {
                    errors.add(ImportError(rowNumber, "LastName", "Last name is required", ErrorSeverity.ERROR))
                    return@forEachIndexed
                }
                if (staffMember.email.isBlank()) {
                    errors.add(ImportError(rowNumber, "Email", "Email is required", ErrorSeverity.ERROR))
                    return@forEachIndexed
                }
                if (staffMember.phoneNumber.isBlank()) {
                    errors.add(ImportError(rowNumber, "PhoneNumber", "Phone number is required", ErrorSeverity.ERROR))
                    return@forEachIndexed
                }
                if (staffMember.designation.isBlank()) {
                    errors.add(ImportError(rowNumber, "Designation", "Designation is required", ErrorSeverity.ERROR))
                    return@forEachIndexed
                }

                // Validate email format
                if (!staffMember.email.matches(EMAIL_REGEX)) {
                    errors.add(ImportError(rowNumber, "Email", "Invalid email format", ErrorSeverity.ERROR))
                    return@forEachIndexed
                }

                // Validate date if present
                if (!staffMember.dateOfHire.isNullOrBlank()) {
                    try {
                        LocalDate.parse(staffMember.dateOfHire, DATE_FORMATTER)
                    } catch (e: DateTimeParseException) {
                        errors.add(ImportError(rowNumber, "DateOfHire", 
                            "Invalid date format. Expected $DATE_PATTERN", ErrorSeverity.ERROR))
                        return@forEachIndexed
                    }
                }

                // Check if user exists
                val existingUser = userRepository.findByEmail(staffMember.email).orElse(null)
                
                if (existingUser != null) {
                    // Check if user has STAFF role IN THIS SCHOOL
                    val roleExists = userSchoolRoleRepository.existsByUserIdAndSchoolIdAndRoleId(existingUser.id!!, schoolId, staffRole.id!!)
                    
                    if (roleExists) {
                        errors.add(ImportError(rowNumber, "Email", 
                            "Staff with this Email already exists in this school", ErrorSeverity.WARNING))
                        duplicates++
                        return@forEachIndexed
                    }
                }

                validData.add(mapOf(
                    "FirstName" to staffMember.firstName,
                    "LastName" to staffMember.lastName,
                    "Email" to staffMember.email,
                    "PhoneNumber" to staffMember.phoneNumber,
                    "Designation" to staffMember.designation,
                    "DateOfHire" to (staffMember.dateOfHire ?: "")
                ))
            } catch (e: Exception) {
                errors.add(ImportError(rowNumber, null, "Error processing row: ${e.message}", ErrorSeverity.ERROR))
            }
        }

        return ImportCategoryPreview(
            totalRows = staff.size,
            validEntries = validData.size,
            duplicates = duplicates,
            errors = errors,
            validData = validData
        )
    }

    /**
     * Performs the actual import after user confirmation
     */
    fun performImport(preview: BulkImportPreviewDTO, schoolId: UUID): BulkImportResultDTO {
        var studentsImported = 0
        var parentsImported = 0
        var staffImported = 0
        val errors = mutableListOf<String>()

        try {
            // Import parents first
            preview.parents.validData.forEach { data ->
                try {
                    transactionTemplate.execute {
                        createParent(data, schoolId)
                    }
                    parentsImported++
                } catch (e: Exception) {
                    errors.add("Failed to import parent ${data["Email"]}: ${e.message}")
                }
            }

            // Import staff
            preview.staff.validData.forEach { data ->
                try {
                    transactionTemplate.execute {
                        createStaff(data, schoolId)
                    }
                    staffImported++
                } catch (e: Exception) {
                    errors.add("Failed to import staff ${data["Email"]}: ${e.message}")
                }
            }

            // Import students
            preview.students.validData.forEach { data ->
                try {
                    transactionTemplate.execute {
                        createStudent(data, schoolId)
                    }
                    studentsImported++
                } catch (e: Exception) {
                    errors.add("Failed to import student ${data["AdmissionNumber"]}: ${e.message}")
                }
            }

        } catch (e: Exception) {
            errors.add("Import failed: ${e.message}")
        }

        return BulkImportResultDTO(
            studentsImported = studentsImported,
            parentsImported = parentsImported,
            staffImported = staffImported,
            studentsSkipped = preview.students.errors.size,
            parentsSkipped = preview.parents.errors.size,
            staffSkipped = preview.staff.errors.size,
            errors = errors,
            success = errors.isEmpty()
        )
    }

    private fun createStudent(data: Map<String, String>, schoolId: UUID): Student {
        var admissionNumber = data["AdmissionNumber"]
        
        // Strip admission prefix if it exists
        if (!admissionNumber.isNullOrBlank()) {
            val school = schoolRepository.findById(schoolId).orElse(null)
            if (school != null && !school.admissionPrefix.isNullOrBlank()) {
                val prefix = school.admissionPrefix!!
                if (admissionNumber!!.startsWith(prefix)) {
                    admissionNumber = admissionNumber!!.substring(prefix.length)
                }
            }
        }

        val phoneNumber = admissionNumber?.takeIf { it.isNotBlank() }

        // For students, we always create a new user for now as we generate email
        val user = User(
            phoneNumber = phoneNumber,
            email = generateStudentEmail(data["FirstName"]!!, data["LastName"]!!),
            firstName = data["FirstName"]!!,
            lastName = data["LastName"]!!,
            middleName = data["MiddleName"],
            passwordHash = passwordEncoder.encode(PASSWORD_STUDENT)
        )
        user.emailVerified = false
        user.status = UserStatus.ACTIVE
        user.addressLine1 = data["Address"]?.takeIf { it.isNotBlank() }
        val savedUser = userRepository.save(user)

        // Assign STUDENT role
        assignRole(savedUser, schoolId, ROLE_STUDENT)

        val student = Student(
            user = savedUser,
            studentId = UUID.randomUUID().toString(),
            admissionNumber = admissionNumber?.takeIf { it.isNotBlank() },
            admissionDate = LocalDate.now(),
            dateOfBirth = LocalDate.parse(data["DateOfBirth"], DATE_FORMATTER),
            gender = if (data["Gender"] == "M") Gender.MALE else Gender.FEMALE
        )
        student.schoolId = schoolId

        return studentRepository.save(student)
    }

    private fun createParent(data: Map<String, String>, schoolId: UUID): Parent {
        val user = getOrCreateUser(data, PASSWORD_PARENT)
        assignRole(user, schoolId, ROLE_PARENT)

        val existingParent = parentRepository.findByUserIdAndSchoolId(user.id!!, schoolId)
        if (existingParent != null) return existingParent

        val parent = Parent(user = user)
        parent.schoolId = schoolId
        return parentRepository.save(parent)
    }

    private fun createStaff(data: Map<String, String>, schoolId: UUID): Staff {
        val user = getOrCreateUser(data, PASSWORD_STAFF)
        assignRole(user, schoolId, ROLE_STAFF)

        val existingStaff = staffRepository.findByUserIdAndSchoolId(user.id!!, schoolId)
        if (existingStaff != null) return existingStaff

        val staff = Staff(
            user = user,
            staffId = UUID.randomUUID().toString(),
            designation = data["Designation"]!!,
            hireDate = data["DateOfHire"]?.takeIf { it.isNotBlank() }
                ?.let { LocalDate.parse(it, DATE_FORMATTER) }
                ?: LocalDate.now()
        )
        staff.schoolId = schoolId
        return staffRepository.save(staff)
    }

    private fun getOrCreateUser(data: Map<String, String>, defaultPassword: String): User {
        val email = data["Email"]!!
        return userRepository.findByEmail(email).orElseGet {
            val user = User(
                phoneNumber = data["PhoneNumber"]!!,
                email = email,
                firstName = data["FirstName"]!!,
                lastName = data["LastName"]!!,
                passwordHash = passwordEncoder.encode(defaultPassword)
            )
            user.emailVerified = false
            user.status = UserStatus.ACTIVE
            user.addressLine1 = data["Address"]?.takeIf { it.isNotBlank() }
            userRepository.save(user)
        }
    }

    private fun assignRole(user: User, schoolId: UUID, roleName: String) {
        val role = roleRepository.findByName(roleName).orElseThrow {
            RuntimeException("$roleName role not found")
        }

        if (!userSchoolRoleRepository.existsByUserIdAndSchoolIdAndRoleId(user.id!!, schoolId, role.id!!)) {
            val userSchoolRole = UserSchoolRole(
                user = user,
                schoolId = schoolId,
                role = role,
                isPrimary = true
            )
            userSchoolRole.isActive = true
            userSchoolRoleRepository.save(userSchoolRole)
        }
    }

    private fun generateStudentEmail(firstName: String, lastName: String): String {
        val base = "${firstName.lowercase()}.${lastName.lowercase()}"
        var email = "$base@student.4school.com"
        var counter = 1
        
        while (userRepository.existsByEmail(email)) {
            email = "$base$counter@student.4school.com"
            counter++
        }
        
        return email
    }



}

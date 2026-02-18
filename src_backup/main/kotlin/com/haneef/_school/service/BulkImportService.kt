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

    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

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
                // Admission number is now mandatory
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
                    LocalDate.parse(student.dateOfBirth, dateFormatter)
                } catch (e: DateTimeParseException) {
                    errors.add(ImportError(rowNumber, "DateOfBirth", 
                        "Invalid date format. Expected DD/MM/YYYY", ErrorSeverity.ERROR))
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
                if (!parent.email.matches(Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"))) {
                    errors.add(ImportError(rowNumber, "Email", "Invalid email format", ErrorSeverity.ERROR))
                    return@forEachIndexed
                }

                // Check if user exists
                val existingUser = userRepository.findByEmail(parent.email).orElse(null)
                
                if (existingUser != null) {
                    // Check if user has PARENT role IN THIS SCHOOL
                    val parentRole = roleRepository.findByName("PARENT").orElseThrow()
                    val hasRole = userSchoolRoleRepository.existsByUserIdAndSchoolIdAndRoleId(existingUser.id!!, schoolId, parentRole.id!!)
                    
                    if (hasRole) {
                        errors.add(ImportError(rowNumber, "Email", 
                            "Parent with this Email already exists in this school", ErrorSeverity.WARNING))
                        duplicates++
                        return@forEachIndexed
                    }
                    // If user exists but doesn't have role in this school, it's valid (we will link them)
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
                if (!staffMember.email.matches(Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"))) {
                    errors.add(ImportError(rowNumber, "Email", "Invalid email format", ErrorSeverity.ERROR))
                    return@forEachIndexed
                }

                // Validate date if present
                if (!staffMember.dateOfHire.isNullOrBlank()) {
                    try {
                        LocalDate.parse(staffMember.dateOfHire, dateFormatter)
                    } catch (e: DateTimeParseException) {
                        errors.add(ImportError(rowNumber, "DateOfHire", 
                            "Invalid date format. Expected DD/MM/YYYY", ErrorSeverity.ERROR))
                        return@forEachIndexed
                    }
                }

                // Check if user exists
                val existingUser = userRepository.findByEmail(staffMember.email).orElse(null)
                
                if (existingUser != null) {
                    // Check if user has STAFF role IN THIS SCHOOL
                    val staffRole = roleRepository.findByName("STAFF").orElseThrow()
                    val hasRole = userSchoolRoleRepository.existsByUserIdAndSchoolIdAndRoleId(existingUser.id!!, schoolId, staffRole.id!!)
                    
                    if (hasRole) {
                        errors.add(ImportError(rowNumber, "Email", 
                            "Staff with this Email already exists in this school", ErrorSeverity.WARNING))
                        duplicates++
                        return@forEachIndexed
                    }
                    // If user exists but doesn't have role in this school, it's valid
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

        val phoneNumber = if (!admissionNumber.isNullOrBlank()) admissionNumber else null

        // For students, we always create a new user for now as we generate email
        // If we wanted to support existing students, we'd need a way to match them (e.g. admission number + school, but that's what we are creating)
        
        val user = User(
            phoneNumber = phoneNumber,
            email = generateStudentEmail(data["FirstName"]!!, data["LastName"]!!),
            firstName = data["FirstName"]!!,
            lastName = data["LastName"]!!,
            middleName = data["MiddleName"],
            passwordHash = passwordEncoder.encode("student123")
        )
        user.emailVerified = false
        user.status = UserStatus.ACTIVE
        // Set address if provided
        if (!data["Address"].isNullOrBlank()) {
            user.addressLine1 = data["Address"]
        }
        val savedUser = userRepository.save(user)

        // Assign STUDENT role
        val studentRole = roleRepository.findByName("STUDENT").orElseThrow { 
            RuntimeException("STUDENT role not found") 
        }
        val userSchoolRole = UserSchoolRole(
            user = savedUser,
            schoolId = schoolId,
            role = studentRole,
            isPrimary = true
        )
        userSchoolRole.isActive = true
        userSchoolRoleRepository.save(userSchoolRole)

        val student = Student(
            user = savedUser,
            studentId = UUID.randomUUID().toString(),
            admissionNumber = admissionNumber?.takeIf { it.isNotBlank() },
            admissionDate = LocalDate.now(),
            dateOfBirth = LocalDate.parse(data["DateOfBirth"], dateFormatter),
            gender = if (data["Gender"] == "M") Gender.MALE else Gender.FEMALE
        )
        student.schoolId = schoolId

        return studentRepository.save(student)
    }

    private fun createParent(data: Map<String, String>, schoolId: UUID): Parent {
        val email = data["Email"]!!
        
        // Check if user exists
        var user = userRepository.findByEmail(email).orElse(null)
        
        if (user == null) {
            // Create new user
            user = User(
                phoneNumber = data["PhoneNumber"]!!,
                email = email,
                firstName = data["FirstName"]!!,
                lastName = data["LastName"]!!,
                passwordHash = passwordEncoder.encode("parent123")
            )
            user.emailVerified = false
            user.status = UserStatus.ACTIVE
            // Set address if provided
            if (!data["Address"].isNullOrBlank()) {
                user.addressLine1 = data["Address"]
            }
            user = userRepository.save(user)
        }

        // Assign PARENT role
        val parentRole = roleRepository.findByName("PARENT").orElseThrow { 
            RuntimeException("PARENT role not found") 
        }
        
        // Check if role exists (it shouldn't if validation passed, but good to be safe)
        if (!userSchoolRoleRepository.existsByUserIdAndSchoolIdAndRoleId(user.id!!, schoolId, parentRole.id!!)) {
            val userSchoolRole = UserSchoolRole(
                user = user,
                schoolId = schoolId,
                role = parentRole,
                isPrimary = true
            )
            userSchoolRole.isActive = true
            userSchoolRoleRepository.save(userSchoolRole)
        }

        // Create Parent entity for this school
        // Check if parent entity already exists for this school (shouldn't if role didn't exist, but possible if data inconsistency)
        // Actually, Parent entity is unique per user? No, Parent entity has schoolId.
        // Let's check if Parent entity exists for this user and school
        val existingParent = parentRepository.findByUserIdAndSchoolId(user.id!!, schoolId)
        
        return if (existingParent != null) {
            existingParent
        } else {
            val parent = Parent(
                user = user
            )
            parent.schoolId = schoolId
            parentRepository.save(parent)
        }
    }

    private fun createStaff(data: Map<String, String>, schoolId: UUID): Staff {
        val email = data["Email"]!!
        
        // Check if user exists
        var user = userRepository.findByEmail(email).orElse(null)
        
        if (user == null) {
            // Create new user
            user = User(
                phoneNumber = data["PhoneNumber"]!!,
                email = email,
                firstName = data["FirstName"]!!,
                lastName = data["LastName"]!!,
                passwordHash = passwordEncoder.encode("staff123")
            )
            user.emailVerified = false
            user.status = UserStatus.ACTIVE
            user = userRepository.save(user)
        }

        // Assign STAFF role
        val staffRole = roleRepository.findByName("STAFF").orElseThrow { 
            RuntimeException("STAFF role not found") 
        }
        
        if (!userSchoolRoleRepository.existsByUserIdAndSchoolIdAndRoleId(user.id!!, schoolId, staffRole.id!!)) {
            val userSchoolRole = UserSchoolRole(
                user = user,
                schoolId = schoolId,
                role = staffRole,
                isPrimary = true
            )
            userSchoolRole.isActive = true
            userSchoolRoleRepository.save(userSchoolRole)
        }

        // Create Staff entity
        val existingStaff = staffRepository.findByUserIdAndSchoolId(user.id!!, schoolId)
        
        return if (existingStaff != null) {
            existingStaff
        } else {
            val staff = Staff(
                user = user,
                staffId = UUID.randomUUID().toString(),
                designation = data["Designation"]!!,
                hireDate = data["DateOfHire"]?.takeIf { it.isNotBlank() }
                    ?.let { LocalDate.parse(it, dateFormatter) }
                    ?: LocalDate.now()
            )
            staff.schoolId = schoolId
            staffRepository.save(staff)
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

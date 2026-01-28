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

                // Check for duplicate admission number
                if (!student.admissionNumber.isNullOrBlank()) {
                    val exists = studentRepository.findByAdmissionNumber(student.admissionNumber) != null
                    if (exists) {
                        errors.add(ImportError(rowNumber, "AdmissionNumber", 
                            "Student with admission number '${student.admissionNumber}' already exists", ErrorSeverity.WARNING))
                        duplicates++
                        return@forEachIndexed
                    }
                }

                // Add to valid data
                validData.add(mapOf(
                    "FirstName" to student.firstName,
                    "LastName" to student.lastName,
                    "MiddleName" to (student.middleName ?: ""),
                    "Gender" to student.gender,
                    "DateOfBirth" to student.dateOfBirth,
                    "AdmissionNumber" to (student.admissionNumber ?: ""),
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

                // Check for duplicate email
                val emailExists = userRepository.existsByEmail(parent.email)
                
                if (emailExists) {
                    errors.add(ImportError(rowNumber, "Email", 
                        "Parent with this Email already exists", ErrorSeverity.WARNING))
                    duplicates++
                    return@forEachIndexed
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

                // Check for duplicate email
                val emailExists = userRepository.existsByEmail(staffMember.email)
                
                if (emailExists) {
                    errors.add(ImportError(rowNumber, "Email", 
                        "Staff with this Email already exists", ErrorSeverity.WARNING))
                    duplicates++
                    return@forEachIndexed
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
            studentsSkipped = preview.students.duplicates,
            parentsSkipped = preview.parents.duplicates,
            staffSkipped = preview.staff.duplicates,
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
        val user = User(
            phoneNumber = data["PhoneNumber"]!!,
            email = data["Email"]!!,
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
        val savedUser = userRepository.save(user)

        // Assign PARENT role
        val parentRole = roleRepository.findByName("PARENT").orElseThrow { 
            RuntimeException("PARENT role not found") 
        }
        val userSchoolRole = UserSchoolRole(
            user = savedUser,
            schoolId = schoolId,
            role = parentRole,
            isPrimary = true
        )
        userSchoolRole.isActive = true
        userSchoolRoleRepository.save(userSchoolRole)

        val parent = Parent(
            user = savedUser
        )
        parent.schoolId = schoolId

        return parentRepository.save(parent)
    }

    private fun createStaff(data: Map<String, String>, schoolId: UUID): Staff {
        val user = User(
            phoneNumber = data["PhoneNumber"]!!,
            email = data["Email"]!!,
            firstName = data["FirstName"]!!,
            lastName = data["LastName"]!!,
            passwordHash = passwordEncoder.encode("staff123")
        )
        user.emailVerified = false
        user.status = UserStatus.ACTIVE
        val savedUser = userRepository.save(user)

        // Assign STAFF role
        val staffRole = roleRepository.findByName("STAFF").orElseThrow { 
            RuntimeException("STAFF role not found") 
        }
        val userSchoolRole = UserSchoolRole(
            user = savedUser,
            schoolId = schoolId,
            role = staffRole,
            isPrimary = true
        )
        userSchoolRole.isActive = true
        userSchoolRoleRepository.save(userSchoolRole)

        val staff = Staff(
            user = savedUser,
            staffId = UUID.randomUUID().toString(),
            designation = data["Designation"]!!,
            hireDate = data["DateOfHire"]?.takeIf { it.isNotBlank() }
                ?.let { LocalDate.parse(it, dateFormatter) }
                ?: LocalDate.now()
        )
        staff.schoolId = schoolId

        return staffRepository.save(staff)
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

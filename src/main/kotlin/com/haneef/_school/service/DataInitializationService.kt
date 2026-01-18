package com.haneef._school.service

import com.haneef._school.entity.*
import com.haneef._school.repository.*
import org.springframework.boot.CommandLineRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@Profile("!prod")
@Service
class DataInitializationService(
    private val schoolRepository: SchoolRepository,
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val userSchoolRoleRepository: UserSchoolRoleRepository,
    private val passwordEncoder: PasswordEncoder,
    private val educationTrackRepository: EducationTrackRepository,
    private val schoolClassRepository: SchoolClassRepository,
    private val subjectRepository: SubjectRepository,
    private val classSubjectRepository: ClassSubjectRepository,
    private val staffRepository: StaffRepository,
    private val studentRepository: StudentRepository,
    private val parentRepository: ParentRepository,
    private val studentClassRepository: StudentClassRepository,
    private val parentStudentRepository: ParentStudentRepository,
    private val classTeacherRepository: ClassTeacherRepository,
    private val subjectTeacherRepository: SubjectTeacherRepository,
    private val departmentRepository: DepartmentRepository,
    private val academicSessionRepository: AcademicSessionRepository,
    private val termRepository: TermRepository,
    private val examinationRepository: ExaminationRepository,
    private val questionRepository: QuestionRepository,
    private val feeItemRepository: FeeItemRepository,
    private val classFeeItemRepository: ClassFeeItemRepository,
    private val userGlobalRoleRepository: UserGlobalRoleRepository,
    private val settlementRepository: SettlementRepository
) : CommandLineRunner {

    @Transactional
    override fun run(vararg args: String) {
        initializeRoles()
        initializeSchool()
        initializeSchoolStructure()
        initializeSubjects()
        initializeStaff()
        initializeStudentsAndParents()
        initializeAcademicSessions()
        initializeClassAssignments()
        initializeTeacherAssignments()
        initializeExaminations()
        initializeFeeItems()
        initializeSystemAdmin()
        migrateSettlements()
    }

    private fun initializeRoles() {
        if (roleRepository.count() == 0L) {
            val roles = listOf(
                Role("SCHOOL_ADMIN", RoleType.SCHOOL_ADMIN, "School Administrator - Full school management access"),
                Role("ADMIN", RoleType.ADMIN, "Functional Administrator - Specific task access"),
                Role("SYSTEM_ADMIN", RoleType.ADMIN, "Platform System Administrator"),
                Role("PRINCIPAL", RoleType.ADMIN, "School Principal"),
                Role("TEACHER", RoleType.STAFF, "Teacher"),
                Role("STUDENT", RoleType.STUDENT, "Student"),
                Role("PARENT", RoleType.PARENT, "Parent"),
                Role("STAFF", RoleType.STAFF, "Staff Member")
            )
            roleRepository.saveAll(roles)
        }
    }

    private fun initializeSchool() {
        if (schoolRepository.count() == 0L) {
            val school = School(
                name = "Demo High School",
                slug = "demo-high-school",
                email = "info@demohighschool.edu",
                phone = "+1-555-0123",
                website = "https://demohighschool.edu",
                addressLine1 = "123 Education Street",
                city = "Learning City",
                state = "LC",
                postalCode = "12345",
                adminName = "Dr. Jane Smith",
                adminEmail = "admin@demohighschool.edu",
                adminPhone = "+1-555-0100"
            )
            school.schoolMotto = "Excellence in Education"
            schoolRepository.save(school)

            // Create admin user
            val adminRole = roleRepository.findByName("SCHOOL_ADMIN").orElseThrow()
            val adminUser = User(
                phoneNumber = "+1-555-0100",
                passwordHash = passwordEncoder.encode("admin123"),
                email = "admin@demohighschool.edu",
                firstName = "System",
                lastName = "Administrator"
            )
            adminUser.emailVerified = true
            adminUser.isVerified = true
            adminUser.status = UserStatus.ACTIVE
            val savedAdminUser = userRepository.save(adminUser)

            // Assign school admin role
            val userSchoolRole = UserSchoolRole(
                user = savedAdminUser,
                schoolId = school.id!!,
                role = adminRole,
                isPrimary = true
            )
            userSchoolRoleRepository.save(userSchoolRole)
        }
    }

    private fun initializeSchoolStructure() {
        val demoSchool = schoolRepository.findBySlug("demo-high-school").orElse(null) ?: return
        
        // Create Education Tracks first
        if (educationTrackRepository.count() == 0L) {
            val tracks = listOf(
                EducationTrack("Primary", "Primary education track for grades 1-6"),
                EducationTrack("Junior Secondary", "Junior secondary education for grades 7-9"),
                EducationTrack("Senior Secondary", "Senior secondary education for grades 10-12")
            )
            
            tracks.forEach { track ->
                track.schoolId = demoSchool.id!!
                track.isActive = true
                educationTrackRepository.save(track)
            }
        }

        // Create Departments and link them to tracks
        if (departmentRepository.count() == 0L) {
            val primaryTrack = educationTrackRepository.findBySchoolIdAndIsActive(demoSchool.id!!, true)
                .find { it.name == "Primary" }
            val jssTrack = educationTrackRepository.findBySchoolIdAndIsActive(demoSchool.id!!, true)
                .find { it.name == "Junior Secondary" }
            val sssTrack = educationTrackRepository.findBySchoolIdAndIsActive(demoSchool.id!!, true)
                .find { it.name == "Senior Secondary" }
            
            val departments = listOf(
                // Primary departments
                Department("Primary General", primaryTrack, "General primary education department"),
                Department("Primary Sciences", primaryTrack, "Primary science and mathematics"),
                
                // JSS departments
                Department("JSS Sciences", jssTrack, "Junior secondary sciences"),
                Department("JSS Arts", jssTrack, "Junior secondary arts and humanities"),
                Department("JSS Commercial", jssTrack, "Junior secondary commercial subjects"),
                
                // SSS departments
                Department("Science", sssTrack, "Senior secondary science department"),
                Department("Arts", sssTrack, "Senior secondary arts and humanities"),
                Department("Commercial", sssTrack, "Senior secondary commercial department"),
                Department("Technical", sssTrack, "Senior secondary technical education")
            )
            
            departments.forEach { dept ->
                dept.schoolId = demoSchool.id!!
                dept.isActive = true
                departmentRepository.save(dept)
            }
        }

        // Create Classes and link them to departments
        if (schoolClassRepository.count() == 0L) {
            val primaryTrack = educationTrackRepository.findBySchoolIdAndIsActive(demoSchool.id!!, true)
                .find { it.name == "Primary" }
            val jssTrack = educationTrackRepository.findBySchoolIdAndIsActive(demoSchool.id!!, true)
                .find { it.name == "Junior Secondary" }
            val sssTrack = educationTrackRepository.findBySchoolIdAndIsActive(demoSchool.id!!, true)
                .find { it.name == "Senior Secondary" }

            // Get departments
            val primaryGeneralDept = departmentRepository.findByTrackIdAndIsActive(primaryTrack?.id ?: UUID.randomUUID(), true)
                .find { it.name == "Primary General" }
            val primarySciencesDept = departmentRepository.findByTrackIdAndIsActive(primaryTrack?.id ?: UUID.randomUUID(), true)
                .find { it.name == "Primary Sciences" }
            val jssSciencesDept = departmentRepository.findByTrackIdAndIsActive(jssTrack?.id ?: UUID.randomUUID(), true)
                .find { it.name == "JSS Sciences" }
            val jssArtsDept = departmentRepository.findByTrackIdAndIsActive(jssTrack?.id ?: UUID.randomUUID(), true)
                .find { it.name == "JSS Arts" }
            val jssCommercialDept = departmentRepository.findByTrackIdAndIsActive(jssTrack?.id ?: UUID.randomUUID(), true)
                .find { it.name == "JSS Commercial" }
            val sssScienceDept = departmentRepository.findByTrackIdAndIsActive(sssTrack?.id ?: UUID.randomUUID(), true)
                .find { it.name == "Science" }
            val sssArtsDept = departmentRepository.findByTrackIdAndIsActive(sssTrack?.id ?: UUID.randomUUID(), true)
                .find { it.name == "Arts" }
            val sssCommercialDept = departmentRepository.findByTrackIdAndIsActive(sssTrack?.id ?: UUID.randomUUID(), true)
                .find { it.name == "Commercial" }

            val classes = mutableListOf<SchoolClass>()
            
            // Primary Classes
            primaryTrack?.let { track ->
                for (grade in 1..6) {
                    listOf("A", "B").forEach { section ->
                        val className = "Primary $grade$section"
                        val department = if (grade <= 3) primaryGeneralDept else primarySciencesDept
                        val schoolClass = SchoolClass(
                            className = className,
                            classCode = "P$grade$section",
                            gradeLevel = "Grade $grade",
                            track = track,
                            department = department,
                            maxCapacity = 30
                        )
                        schoolClass.schoolId = demoSchool.id!!
                        schoolClass.isActive = true
                        classes.add(schoolClass)
                    }
                }
            }

            // JSS Classes
            jssTrack?.let { track ->
                for (grade in 1..3) {
                    listOf("A", "B", "C").forEach { section ->
                        val className = "JSS $grade$section"
                        val department = when (section) {
                            "A" -> jssSciencesDept
                            "B" -> jssArtsDept
                            "C" -> jssCommercialDept
                            else -> jssSciencesDept
                        }
                        val schoolClass = SchoolClass(
                            className = className,
                            classCode = "JSS$grade$section",
                            gradeLevel = "JSS $grade",
                            track = track,
                            department = department,
                            maxCapacity = 35
                        )
                        schoolClass.schoolId = demoSchool.id!!
                        schoolClass.isActive = true
                        classes.add(schoolClass)
                    }
                }
            }

            // SSS Classes
            sssTrack?.let { track ->
                for (grade in 1..3) {
                    listOf("Science A", "Science B", "Arts A", "Arts B", "Commercial A").forEach { section ->
                        val className = "SSS $grade $section"
                        val department = when {
                            section.startsWith("Science") -> sssScienceDept
                            section.startsWith("Arts") -> sssArtsDept
                            section.startsWith("Commercial") -> sssCommercialDept
                            else -> sssScienceDept
                        }
                        val schoolClass = SchoolClass(
                            className = className,
                            classCode = "SSS$grade${section.replace(" ", "")}",
                            gradeLevel = "SSS $grade",
                            track = track,
                            department = department,
                            maxCapacity = 40
                        )
                        schoolClass.schoolId = demoSchool.id!!
                        schoolClass.isActive = true
                        classes.add(schoolClass)
                    }
                }
            }

            schoolClassRepository.saveAll(classes)
        }
    }

    private fun initializeSubjects() {
        val demoSchool = schoolRepository.findBySlug("demo-high-school").orElse(null) ?: return
        
        if (subjectRepository.count() == 0L) {
            val subjects = listOf(
                // Core subjects
                Subject("Mathematics", "MATH", "Core mathematics curriculum"),
                Subject("English Language", "ENG", "English language and literature"),
                Subject("Science", "SCI", "General science"),
                Subject("Social Studies", "SS", "Social studies and civics"),
                
                // Secondary subjects
                Subject("Physics", "PHY", "Physics"),
                Subject("Chemistry", "CHEM", "Chemistry"),
                Subject("Biology", "BIO", "Biology"),
                Subject("Geography", "GEO", "Geography"),
                Subject("History", "HIST", "History"),
                Subject("Economics", "ECON", "Economics"),
                Subject("Government", "GOV", "Government"),
                Subject("Literature", "LIT", "Literature in English"),
                
                // Other subjects
                Subject("Physical Education", "PE", "Physical education and sports"),
                Subject("Art", "ART", "Creative arts"),
                Subject("Music", "MUS", "Music"),
                Subject("Computer Science", "CS", "Computer science and ICT")
            )
            
            subjects.forEach { subject ->
                subject.schoolId = demoSchool.id!!
                subject.isActive = true
                subjectRepository.save(subject)
            }
        }
    }

    private fun initializeStaff() {
        val demoSchool = schoolRepository.findBySlug("demo-high-school").orElse(null) ?: return
        val teacherRole = roleRepository.findByName("TEACHER").orElse(null) ?: return
        
        if (staffRepository.count() == 0L) {
            val staffData = listOf(
                Triple("John", "Smith", "Mathematics"),
                Triple("Sarah", "Johnson", "English Language"),
                Triple("Michael", "Brown", "Science"),
                Triple("Emily", "Davis", "Social Studies"),
                Triple("David", "Wilson", "Physics"),
                Triple("Lisa", "Anderson", "Chemistry"),
                Triple("Robert", "Taylor", "Biology"),
                Triple("Jennifer", "Thomas", "Geography"),
                Triple("William", "Jackson", "History"),
                Triple("Jessica", "White", "Economics")
            )
            
            staffData.forEachIndexed { index, (firstName, lastName, subjectName) ->
                val email = "${firstName.lowercase()}.${lastName.lowercase()}@demohighschool.edu"
                val phoneNumber = "+234${9000000000L + index + 1}"
                val gender = if (index % 2 == 0) "Male" else "Female"
                
                // Create user
                val user = User(
                    phoneNumber = phoneNumber,
                    passwordHash = passwordEncoder.encode("teacher123"),
                    email = email,
                    firstName = firstName,
                    lastName = lastName
                )
                user.emailVerified = true
                user.isVerified = true
                user.status = UserStatus.ACTIVE
                val savedUser = userRepository.save(user)
                
                // Create staff
                val staff = Staff(
                    user = savedUser,
                    staffId = "EMP${String.format("%03d", index + 1)}",
                    hireDate = LocalDate.now().minusYears((1..5).random().toLong())
                )
                staff.department = subjectName
                staff.designation = "Teacher"
                staff.highestDegree = "B.Ed"
                staff.yearsOfExperience = (2..15).random()
                staff.isSubjectTeacher = true
                staff.schoolId = demoSchool.id!!
                staffRepository.save(staff)
                
                // Assign teacher role
                val userSchoolRole = UserSchoolRole(
                    user = savedUser,
                    schoolId = demoSchool.id!!,
                    role = teacherRole,
                    isPrimary = true
                )
                userSchoolRoleRepository.save(userSchoolRole)
            }
        }
    }

    private fun initializeStudentsAndParents() {
        val demoSchool = schoolRepository.findBySlug("demo-high-school").orElse(null) ?: return
        val studentRole = roleRepository.findByName("STUDENT").orElse(null) ?: return
        val parentRole = roleRepository.findByName("PARENT").orElse(null) ?: return
        
        if (studentRepository.count() == 0L) {
            val studentData = listOf(
                Triple("Alice", "Johnson", "Female"),
                Triple("Bob", "Smith", "Male"),
                Triple("Carol", "Brown", "Female"),
                Triple("David", "Wilson", "Male"),
                Triple("Emma", "Davis", "Female"),
                Triple("Frank", "Miller", "Male"),
                Triple("Grace", "Taylor", "Female"),
                Triple("Henry", "Anderson", "Male"),
                Triple("Ivy", "Thomas", "Female"),
                Triple("Jack", "Jackson", "Male")
            )
            
            val parentData = listOf(
                Triple("James", "Johnson", "Father"),
                Triple("Mary", "Johnson", "Mother"),
                Triple("Robert", "Smith", "Father"),
                Triple("Linda", "Smith", "Mother"),
                Triple("William", "Brown", "Father"),
                Triple("Patricia", "Brown", "Mother"),
                Triple("Richard", "Wilson", "Father"),
                Triple("Jennifer", "Wilson", "Mother"),
                Triple("Charles", "Davis", "Father"),
                Triple("Elizabeth", "Davis", "Mother")
            )
            
            studentData.forEachIndexed { index, (firstName, lastName, gender) ->
                val email = "${firstName.lowercase()}.${lastName.lowercase()}@student.demohighschool.edu"
                val phoneNumber = "+234${8000000000L + index + 1}"
                
                // Create user for student
                val user = User(
                    phoneNumber = phoneNumber,
                    passwordHash = passwordEncoder.encode("student123"),
                    email = email,
                    firstName = firstName,
                    lastName = lastName
                )
                user.dateOfBirth = LocalDate.now().minusYears((6..18).random().toLong())
                user.gender = gender
                user.addressLine1 = "${index + 1} Student Street, Learning City"
                user.emailVerified = true
                user.isVerified = true
                user.status = UserStatus.ACTIVE
                val savedUser = userRepository.save(user)
                
                // Create student
                val student = Student(
                    user = savedUser,
                    studentId = "STU${String.format("%04d", index + 1)}",
                    admissionDate = LocalDate.now().minusYears((1..3).random().toLong())
                )
                student.admissionNumber = "ADM${2024}${String.format("%04d", index + 1)}"
                student.academicStatus = AcademicStatus.ENROLLED
                student.currentGradeLevel = "Grade ${(1..12).random()}"
                student.schoolId = demoSchool.id!!
                studentRepository.save(student)
                
                // Assign student role
                val userSchoolRole = UserSchoolRole(
                    user = savedUser,
                    schoolId = demoSchool.id!!,
                    role = studentRole,
                    isPrimary = true
                )
                userSchoolRoleRepository.save(userSchoolRole)
                
                // Create parents (2 parents per student)
                val parentIndex1 = index * 2
                val parentIndex2 = index * 2 + 1
                
                if (parentIndex1 < parentData.size && parentIndex2 < parentData.size) {
                    val (parentFirstName1, parentLastName1, relationshipType1) = parentData[parentIndex1]
                    val (parentFirstName2, parentLastName2, relationshipType2) = parentData[parentIndex2]
                    
                    // Create first parent
                    val parentEmail1 = "${parentFirstName1.lowercase()}.${parentLastName1.lowercase()}@parent.demohighschool.edu"
                    val parentUser1 = User(
                        phoneNumber = "+234${7000000000L + parentIndex1 + 1}",
                        passwordHash = passwordEncoder.encode("parent123"),
                        email = parentEmail1,
                        firstName = parentFirstName1,
                        lastName = parentLastName1
                    )
                    parentUser1.emailVerified = true
                    parentUser1.isVerified = true
                    parentUser1.status = UserStatus.ACTIVE
                    val savedParentUser1 = userRepository.save(parentUser1)
                    
                    val parent1 = Parent(
                        user = savedParentUser1
                    )
                    parent1.isPrimaryContact = relationshipType1 == "Father"
                    parent1.schoolId = demoSchool.id!!
                    parentRepository.save(parent1)
                    
                    // Create parent-student relationship
                    val parentStudent1 = ParentStudent(
                        parent = parent1,
                        student = student,
                        relationshipType = relationshipType1
                    )
                    parentStudent1.schoolId = demoSchool.id!!
                    parentStudentRepository.save(parentStudent1)
                    
                    // Assign parent role
                    val parentUserSchoolRole1 = UserSchoolRole(
                        user = savedParentUser1,
                        schoolId = demoSchool.id!!,
                        role = parentRole,
                        isPrimary = true
                    )
                    userSchoolRoleRepository.save(parentUserSchoolRole1)
                    
                    // Create second parent
                    val parentEmail2 = "${parentFirstName2.lowercase()}.${parentLastName2.lowercase()}@parent.demohighschool.edu"
                    val parentUser2 = User(
                        phoneNumber = "+234${7000000000L + parentIndex2 + 1}",
                        passwordHash = passwordEncoder.encode("parent123"),
                        email = parentEmail2,
                        firstName = parentFirstName2,
                        lastName = parentLastName2
                    )
                    parentUser2.emailVerified = true
                    parentUser2.isVerified = true
                    parentUser2.status = UserStatus.ACTIVE
                    val savedParentUser2 = userRepository.save(parentUser2)
                    
                    val parent2 = Parent(
                        user = savedParentUser2
                    )
                    parent2.isPrimaryContact = relationshipType2 == "Mother"
                    parent2.schoolId = demoSchool.id!!
                    parentRepository.save(parent2)
                    
                    // Create parent-student relationship
                    val parentStudent2 = ParentStudent(
                        parent = parent2,
                        student = student,
                        relationshipType = relationshipType2
                    )
                    parentStudent2.schoolId = demoSchool.id!!
                    parentStudentRepository.save(parentStudent2)
                    
                    // Assign parent role
                    val parentUserSchoolRole2 = UserSchoolRole(
                        user = savedParentUser2,
                        schoolId = demoSchool.id!!,
                        role = parentRole,
                        isPrimary = true
                    )
                    userSchoolRoleRepository.save(parentUserSchoolRole2)
                }
            }
        }
    }

    private fun initializeClassAssignments() {
        val demoSchool = schoolRepository.findBySlug("demo-high-school").orElse(null) ?: return
        val currentSession = academicSessionRepository.findBySchoolIdAndIsCurrentSessionAndIsActive(demoSchool.id!!, true, true)
        val currentTerm = currentSession?.let { termRepository.findByAcademicSessionIdAndIsCurrentTermAndIsActive(it.id!!, true, true).orElse(null) }
        
        if (studentClassRepository.count() == 0L && currentSession != null && currentTerm != null) {
            val students = studentRepository.findBySchoolIdAndIsActive(demoSchool.id!!, true)
            val classes = schoolClassRepository.findBySchoolIdAndIsActive(demoSchool.id!!, true)
            
            if (classes.isEmpty()) {
                println("No classes found for school ${demoSchool.name}. Skipping class assignments.")
                return
            }
            
            students.forEachIndexed { index, student ->
                val assignedClass = classes[index % classes.size]
                
                val studentClass = StudentClass(
                    student = student,
                    schoolClass = assignedClass,
                    academicSession = currentSession,
                    term = currentTerm
                )
                studentClass.enrollmentDate = LocalDate.now().minusMonths((1..6).random().toLong())
                studentClass.schoolId = demoSchool.id!!
                studentClassRepository.save(studentClass)
                
                // Update class enrollment count
                assignedClass.currentEnrollment = assignedClass.studentEnrollments.count { it.isActive }
                schoolClassRepository.save(assignedClass)
            }
        }
    }

    private fun initializeTeacherAssignments() {
        val demoSchool = schoolRepository.findBySlug("demo-high-school").orElse(null) ?: return
        val currentSession = academicSessionRepository.findBySchoolIdAndIsCurrentSessionAndIsActive(demoSchool.id!!, true, true)
        val currentTerm = currentSession?.let { termRepository.findByAcademicSessionIdAndIsCurrentTermAndIsActive(it.id!!, true, true).orElse(null) }
        
        if (classTeacherRepository.count() == 0L && currentSession != null && currentTerm != null) {
            val staff = staffRepository.findBySchoolIdAndIsActive(demoSchool.id!!, true)
            val classes = schoolClassRepository.findBySchoolIdAndIsActive(demoSchool.id!!, true)
            
            if (staff.isEmpty() || classes.isEmpty()) {
                println("No staff (${staff.size}) or classes (${classes.size}) found for school ${demoSchool.name}. Skipping teacher assignments.")
                return
            }
            
            classes.forEachIndexed { index, schoolClass ->
                val assignedStaff = staff[index % staff.size]
                
                val classTeacher = ClassTeacher(
                    staff = assignedStaff,
                    schoolClass = schoolClass,
                    academicSession = currentSession,
                    term = currentTerm
                )
                classTeacher.schoolId = demoSchool.id!!
                classTeacherRepository.save(classTeacher)
            }
        }
    }

    private fun initializeAcademicSessions() {
        val demoSchool = schoolRepository.findBySlug("demo-high-school").orElse(null) ?: return
        
        if (academicSessionRepository.count() == 0L) {
            val academicSessions = listOf(
                AcademicSession().apply {
                    sessionName = "2024/2025 Academic Session"
                    sessionYear = "2024-2025"
                    startDate = LocalDate.of(2024, 9, 1)
                    endDate = LocalDate.of(2025, 7, 31)
                    isCurrentSession = true
                    schoolId = demoSchool.id!!
                    isActive = true
                },
                AcademicSession().apply {
                    sessionName = "2025/2026 Academic Session"
                    sessionYear = "2025-2026"
                    startDate = LocalDate.of(2025, 9, 1)
                    endDate = LocalDate.of(2026, 7, 31)
                    isCurrentSession = false
                    schoolId = demoSchool.id!!
                    isActive = true
                }
            )
            
            academicSessionRepository.saveAll(academicSessions)
        }
        
        // Initialize Terms for Academic Sessions
        if (termRepository.count() == 0L) {
            val currentSession = academicSessionRepository.findBySchoolIdAndIsActive(demoSchool.id!!, true)
                .find { it.isCurrentSession }
            val nextSession = academicSessionRepository.findBySchoolIdAndIsActive(demoSchool.id!!, true)
                .find { !it.isCurrentSession }
            
            val terms = mutableListOf<Term>()
            
            // Terms for current session (2024/2025)
            currentSession?.let { session ->
                val term1 = Term(
                    academicSession = session,
                    termName = "First Term",
                    startDate = LocalDate.of(2024, 9, 1),
                    endDate = LocalDate.of(2024, 12, 15)
                ).apply {
                    isCurrentTerm = true
                    status = "active"
                    schoolId = demoSchool.id!!
                    isActive = true
                }
                
                val term2 = Term(
                    academicSession = session,
                    termName = "Second Term",
                    startDate = LocalDate.of(2025, 1, 8),
                    endDate = LocalDate.of(2025, 4, 10)
                ).apply {
                    isCurrentTerm = false
                    status = "planned"
                    schoolId = demoSchool.id!!
                    isActive = true
                }
                
                val term3 = Term(
                    academicSession = session,
                    termName = "Third Term",
                    startDate = LocalDate.of(2025, 4, 28),
                    endDate = LocalDate.of(2025, 7, 31)
                ).apply {
                    isCurrentTerm = false
                    status = "planned"
                    schoolId = demoSchool.id!!
                    isActive = true
                }
                
                terms.addAll(listOf(term1, term2, term3))
            }
            
            // Terms for next session (2025/2026)
            nextSession?.let { session ->
                val term1 = Term(
                    academicSession = session,
                    termName = "First Term",
                    startDate = LocalDate.of(2025, 9, 1),
                    endDate = LocalDate.of(2025, 12, 15)
                ).apply {
                    isCurrentTerm = false
                    status = "planned"
                    schoolId = demoSchool.id!!
                    isActive = true
                }
                
                val term2 = Term(
                    academicSession = session,
                    termName = "Second Term",
                    startDate = LocalDate.of(2026, 1, 8),
                    endDate = LocalDate.of(2026, 4, 10)
                ).apply {
                    isCurrentTerm = false
                    status = "planned"
                    schoolId = demoSchool.id!!
                    isActive = true
                }
                
                val term3 = Term(
                    academicSession = session,
                    termName = "Third Term",
                    startDate = LocalDate.of(2026, 4, 28),
                    endDate = LocalDate.of(2026, 7, 31)
                ).apply {
                    isCurrentTerm = false
                    status = "planned"
                    schoolId = demoSchool.id!!
                    isActive = true
                }
                
                terms.addAll(listOf(term1, term2, term3))
            }
            
            termRepository.saveAll(terms)
        }
    }



    private fun initializeExaminations() {
        val demoSchool = schoolRepository.findBySlug("demo-high-school").orElse(null) ?: return
        
        if (examinationRepository.count() == 0L) {
            val subjects = subjectRepository.findBySchoolIdAndIsActive(demoSchool.id!!, true)
            val classes = schoolClassRepository.findBySchoolIdAndIsActive(demoSchool.id!!, true)
            val currentSession = academicSessionRepository.findBySchoolIdAndIsActive(demoSchool.id!!, true)
                .find { it.isCurrentSession }
            
            if (subjects.isNotEmpty() && classes.isNotEmpty() && currentSession != null) {
                val examinations = mutableListOf<Examination>()
                
                // Create sample examinations for different subjects and classes
                val examTypes = listOf("Assignment", "Continuous Assessment", "Mid-Term Test", "End-of-Term Examination")
                val terms = listOf("First Term", "Second Term", "Third Term")
                
                // Create examinations for first 5 subjects and first 3 classes
                subjects.take(5).forEach { subject ->
                    classes.take(3).forEach { schoolClass ->
                        terms.take(2).forEach { term -> // Only first two terms
                            examTypes.take(2).forEach { examType -> // Only first two exam types
                                val examination = Examination(
                                    title = "$term $examType - ${subject.subjectName} (${schoolClass.className})",
                                    examType = examType,
                                    subject = subject,
                                    schoolClass = schoolClass,
                                    term = term,
                                    session = currentSession.sessionYear,
                                    createdBy = userRepository.findByEmail("admin@demohighschool.edu").map { it.id!! }.orElse(UUID.randomUUID())
                                )
                                
                                examination.schoolId = demoSchool.id!!
                                examination.isActive = true
                                examination.isPublished = (1..10).random() > 3 // 70% chance of being published
                                examination.durationMinutes = when (examType) {
                                    "Assignment" -> 120
                                    "Continuous Assessment" -> 60
                                    "Mid-Term Test" -> 90
                                    "End-of-Term Examination" -> 180
                                    else -> 60
                                }
                                examination.totalMarks = when (examType) {
                                    "Assignment" -> 20
                                    "Continuous Assessment" -> 30
                                    "Mid-Term Test" -> 50
                                    "End-of-Term Examination" -> 100
                                    else -> 50
                                }
                                
                                examinations.add(examination)
                            }
                        }
                    }
                }
                
                examinationRepository.saveAll(examinations)
                println("Created ${examinations.size} sample examinations")
            }
        }
    }

    private fun initializeFeeItems() {
        val demoSchool = schoolRepository.findBySlug("demo-high-school").orElse(null) ?: return
        
        val currentSession = academicSessionRepository.findBySchoolIdAndIsActive(demoSchool.id!!, true)
            .find { it.isCurrentSession }
        val currentTerm = currentSession?.let { 
            termRepository.findByAcademicSessionIdAndIsCurrentTermAndIsActive(it.id!!, true, true).orElse(null) 
        }

        if (feeItemRepository.count() == 0L) {
            val feeItems = listOf(
                FeeItem("Tuition Fee", 50000.toBigDecimal(), FeeCategory.TUITION, "Annual tuition fee", true, true, RecurrenceType.TERMLY, academicSession = currentSession, term = currentTerm),
                FeeItem("Registration Fee", 5000.toBigDecimal(), FeeCategory.REGISTRATION, "One-time registration fee", true, true, RecurrenceType.ONE_TIME, academicSession = currentSession, term = currentTerm),
                FeeItem("Examination Fee", 3000.toBigDecimal(), FeeCategory.EXAMINATION, "Examination fee per term", true, true, RecurrenceType.TERMLY, academicSession = currentSession, term = currentTerm),
                FeeItem("Library Fee", 2000.toBigDecimal(), FeeCategory.LIBRARY, "Library access and maintenance", false, true, RecurrenceType.ANNUALLY, academicSession = currentSession, term = currentTerm),
                FeeItem("Laboratory Fee", 4000.toBigDecimal(), FeeCategory.LABORATORY, "Science laboratory usage", false, true, RecurrenceType.TERMLY, academicSession = currentSession, term = currentTerm),
                FeeItem("Sports Fee", 1500.toBigDecimal(), FeeCategory.SPORTS, "Sports activities and equipment", false, true, RecurrenceType.OPTIONAL, academicSession = currentSession, term = currentTerm),
                FeeItem("Transport Fee", 8000.toBigDecimal(), FeeCategory.TRANSPORT, "School bus transportation", false, true, RecurrenceType.TERMLY, academicSession = currentSession, term = currentTerm),
                FeeItem("Uniform Fee", 6000.toBigDecimal(), FeeCategory.UNIFORM, "School uniform and accessories", false, true, RecurrenceType.ONE_TIME, academicSession = currentSession, term = currentTerm),
                FeeItem("Books Fee", 12000.toBigDecimal(), FeeCategory.BOOKS, "Textbooks and learning materials", true, true, RecurrenceType.ANNUALLY, academicSession = currentSession, term = currentTerm),
                FeeItem("Technology Fee", 3500.toBigDecimal(), FeeCategory.TECHNOLOGY, "Computer lab and internet access", false, true, RecurrenceType.TERMLY, academicSession = currentSession, term = currentTerm)
            )
            
            feeItems.forEach { feeItem ->
                feeItem.schoolId = demoSchool.id!!
                feeItem.isActive = true
                feeItemRepository.save(feeItem)
            }
        }
        
        // Initialize some class fee assignments
        if (classFeeItemRepository.count() == 0L) {
            val classes = schoolClassRepository.findBySchoolIdAndIsActive(demoSchool.id!!, true).take(5)
            val feeItems = feeItemRepository.findBySchoolIdAndIsActiveOrderByFeeCategoryAscNameAsc(demoSchool.id!!, true)
            
            classes.forEach { schoolClass ->
                val mandatoryFees = feeItems.filter { it.isMandatory }
                mandatoryFees.forEach { feeItem: FeeItem ->
                    val classFeeItem = ClassFeeItem(
                        schoolClass = schoolClass,
                        feeItem = feeItem
                    )
                    classFeeItem.schoolId = demoSchool.id!!
                    classFeeItem.academicSession = feeItem.academicSession
                    classFeeItem.termId = feeItem.term
                    classFeeItem.isActive = true
                    classFeeItemRepository.save(classFeeItem)
                }
                
                // Assign some optional fees randomly
                val optionalFees = feeItems.filter { !it.isMandatory }.shuffled().take(2)


                optionalFees.forEach { feeItem ->
                    val classFeeItem = ClassFeeItem(
                        schoolClass = schoolClass,
                        feeItem = feeItem
                    )
                    classFeeItem.schoolId = demoSchool.id!!
                    classFeeItem.academicSession = feeItem.academicSession
                    classFeeItem.termId = feeItem.term
                    classFeeItem.isActive = true
                    classFeeItemRepository.save(classFeeItem)
                }
            }
        }
    }
    private fun initializeSystemAdmin() {
        val email = "admin@4school.app"
        
        val systemAdminRole = roleRepository.findByName("SYSTEM_ADMIN").orElseGet {
            roleRepository.save(Role("SYSTEM_ADMIN", RoleType.ADMIN, "Platform System Administrator"))
        }

        val adminUser = userRepository.findByEmail(email).orElseGet {
            userRepository.save(User(
                phoneNumber = "+2340000000000",
                passwordHash = passwordEncoder.encode("siteadmin123"),
                email = email,
                firstName = "Platform",
                lastName = "Admin"
            ).apply {
                emailVerified = true
                isVerified = true
                status = UserStatus.ACTIVE
            })
        }
        
        // Ensure global SYSTEM_ADMIN role is assigned
        if (!userGlobalRoleRepository.existsByUserIdAndRoleId(adminUser.id!!, systemAdminRole.id!!)) {
            userGlobalRoleRepository.save(UserGlobalRole(
                user = adminUser,
                role = systemAdminRole
            ))
        }
    }

    private fun migrateSettlements() {
        val settlements = settlementRepository.findAll()
        var updated = false
        settlements.forEach { settlement ->
            if (settlement.settlementType == null) {
                settlement.settlementType = SettlementType.AUTO
                settlementRepository.save(settlement)
                updated = true
            }
        }
        if (updated) {
            println("Migrated existing settlements to AUTO type")
        }
    }
}
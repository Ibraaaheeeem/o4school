package com.haneef._school.integration

import com.haneef._school.entity.*
import com.haneef._school.repository.*
import com.haneef._school.security.WithMockCustomUser
import jakarta.servlet.http.HttpSession
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.time.LocalDate
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.transaction.annotation.Transactional
class FeatureIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var schoolRepository: SchoolRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var roleRepository: RoleRepository

    @Autowired
    private lateinit var staffRepository: StaffRepository

    @Autowired
    private lateinit var parentRepository: ParentRepository

    @Autowired
    private lateinit var studentRepository: StudentRepository

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    private lateinit var session: MockHttpSession
    private lateinit var testSchoolId: UUID
    private lateinit var adminUser: User
    private lateinit var staffUser: User
    private lateinit var parentUser: User
    private lateinit var studentUser: User

    private val logger = org.slf4j.LoggerFactory.getLogger(FeatureIntegrationTest::class.java)

    @BeforeEach
    fun setup() {
        try {
            // Clean up
            staffRepository.deleteAll()
            parentRepository.deleteAll()
            studentRepository.deleteAll()
            userRepository.deleteAll()
            schoolRepository.deleteAll()
            roleRepository.deleteAll()
            
            // Create School
            val school = School()
            school.name = "Test School"
            school.slug = "test-school"
            school.email = "school@test.com"
            school.phone = "1234567890"
            school.isActive = true
            school.addressLine1 = "123 Test St"
            school.city = "Test City"
            school.state = "Test State"
            school.postalCode = "12345"
            school.adminName = "Admin"
            school.adminEmail = "admin@test.com"
            school.adminPhone = "0987654321"
            testSchoolId = schoolRepository.save(school).id!!
            schoolRepository.flush()
            logger.info("School Created with ID: $testSchoolId")
            
            session = MockHttpSession()
            session.setAttribute("selectedSchoolId", testSchoolId)

            // Create Roles
            val roles = listOf("SCHOOL_ADMIN", "STAFF", "PARENT", "STUDENT", "TEACHER")
            roles.forEach { roleName ->
                if (roleRepository.findByName(roleName).isEmpty) {
                    val type = if (roleName == "TEACHER") RoleType.STAFF else RoleType.valueOf(roleName)
                    roleRepository.save(Role(name = roleName, roleType = type, description = roleName))
                }
            }
            roleRepository.flush()

            logger.info("Creating Admin...")
            adminUser = createUser("admin@test.com", "SCHOOL_ADMIN")
            logger.info("Admin Created.")

            // Create Staff User and Entity
            logger.info("Creating Staff...")
            // Manually create user to avoid createUser side effects
            val staffEmail = "${UUID.randomUUID()}_staff@test.com"
            val sUser = User(
                email = staffEmail,
                firstName = "Staff",
                lastName = "User",
                phoneNumber = "080${System.nanoTime() % 1000000000}",
                passwordHash = passwordEncoder.encode("password")
            ).apply {
                status = UserStatus.ACTIVE
                isVerified = true
                emailVerified = true
            }
            staffUser = userRepository.save(sUser)
            userRepository.flush()
            
            // Add role to staff user
            val staffRole = roleRepository.findByName("STAFF").get()
            val staffUserSchoolRole = UserSchoolRole(
                user = staffUser,
                role = staffRole,
                schoolId = testSchoolId
            )
            staffUser.schoolRoles.add(staffUserSchoolRole)
            staffUser = userRepository.save(staffUser)
            userRepository.flush()

            val staff = Staff().apply {
                this.user = staffUser
                this.staffId = "STF-${UUID.randomUUID()}"
                this.hireDate = LocalDate.now()
                this.schoolId = testSchoolId
                this.isActive = true
            }
            logger.info("Saving Staff with School ID: ${staff.schoolId}")
            staffRepository.save(staff)
            staffRepository.flush()
            logger.info("Staff Created.")

            // Create Parent User and Entity
            logger.info("Creating Parent...")
            parentUser = createUser("parent@test.com", "PARENT")
            val parent = Parent().apply {
                this.user = parentUser
                this.schoolId = testSchoolId
                this.isActive = true
            }
            parentRepository.save(parent)
            parentRepository.flush()
            logger.info("Parent Created.")

            // Create Student User and Entity
            logger.info("Creating Student...")
            studentUser = createUser("student@test.com", "STUDENT")
            val student = Student().apply {
                this.user = studentUser
                this.studentId = "STD-${UUID.randomUUID()}"
                this.admissionDate = LocalDate.now()
                this.schoolId = testSchoolId
                this.isActive = true
            }
            studentRepository.save(student)
            studentRepository.flush()
            logger.info("Student Created.")
            
        } catch (e: Exception) {
            logger.error("SETUP FAILED: ${e.message}", e)
            var cause = e.cause
            while (cause != null) {
                logger.error("Caused by: ${cause.message}")
                cause = cause.cause
            }
            throw e
        }
    }

    private fun createUser(email: String, roleName: String): User {
        val uniqueEmail = "${UUID.randomUUID()}_$email"
        val user = User(
            email = uniqueEmail,
            firstName = "Test",
            lastName = roleName,
            phoneNumber = "080${System.nanoTime() % 1000000000}", // Unique phone
            passwordHash = passwordEncoder.encode("password")
        ).apply {
            status = UserStatus.ACTIVE
            isVerified = true
            emailVerified = true
        }
        val savedUser = userRepository.save(user)
        userRepository.flush()
        
        val role = roleRepository.findByName(roleName).get()
        val userSchoolRole = UserSchoolRole(
            user = savedUser,
            role = role,
            schoolId = testSchoolId
        )
        savedUser.schoolRoles.add(userSchoolRole)
        val finalUser = userRepository.save(savedUser)
        userRepository.flush()
        return finalUser
    }

    private fun authenticateUser(email: String) {
        val user = userRepository.findByEmail(email).orElseThrow { RuntimeException("User not found: $email") }
        val principal = com.haneef._school.service.CustomUserDetails(user)
        val auth = org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
            principal, "password", principal.authorities
        )
        org.springframework.security.core.context.SecurityContextHolder.getContext().authentication = auth
    }

    @Test
    fun `should load public landing page`() {
        mockMvc.perform(get("/"))
            .andExpect(status().isOk)
            .andExpect(view().name("public/platform-home"))
    }

    @Test
    fun `should load login page`() {
        mockMvc.perform(get("/auth/login"))
            .andExpect(status().isOk)
            .andExpect(view().name("auth/login"))
    }

    @Test
    fun `should load registration page`() {
        mockMvc.perform(get("/auth/register"))
            .andExpect(status().isOk)
            .andExpect(view().name("auth/register"))
    }

    @Test
    fun `should redirect unauthenticated user to login when accessing admin dashboard`() {
        mockMvc.perform(get("/admin/dashboard"))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrlPattern("**/login"))
    }

    @Test
    fun `should allow admin to access admin dashboard`() {
        authenticateUser(adminUser.email!!)
        mockMvc.perform(get("/admin/dashboard").session(session))
            .andExpect(status().isOk)
            .andExpect(view().name("dashboard/admin-dashboard"))
    }

    @Test
    fun `should allow staff to access staff dashboard`() {
        authenticateUser(staffUser.email!!)
        mockMvc.perform(get("/staff/dashboard").session(session))
            .andExpect(status().isOk)
            .andExpect(view().name("dashboard/staff-dashboard"))
    }

    @Test
    fun `should allow parent to access parent dashboard`() {
        authenticateUser(parentUser.email!!)
        mockMvc.perform(get("/parent/dashboard").session(session))
            .andExpect(status().isOk)
            .andExpect(view().name("dashboard/parent-dashboard"))
    }

    @Test
    fun `should allow student to access student dashboard`() {
        authenticateUser(studentUser.email!!)
        mockMvc.perform(get("/student/dashboard").session(session))
            .andExpect(status().isOk)
            .andExpect(view().name("dashboard/student-dashboard"))
    }
    
    @Test
    fun `should load school setup page for admin`() {
        authenticateUser(adminUser.email!!)
        mockMvc.perform(get("/admin/school-setup").session(session))
            .andExpect(status().isOk)
            .andExpect(view().name("admin/school-setup/home"))
    }

    @Test
    fun `should load financial dashboard for admin`() {
        authenticateUser(adminUser.email!!)
        mockMvc.perform(get("/admin/financial").session(session))
            .andExpect(status().isOk)
            .andExpect(view().name("admin/financial/home"))
    }
}

package com.haneef._school.integration

import com.haneef._school.entity.*
import com.haneef._school.repository.*
import com.haneef._school.security.WithMockCustomUser
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
class StaffPageTest {

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
    private lateinit var staffUser: User

    @BeforeEach
    fun setup() {
        staffRepository.deleteAll()
        parentRepository.deleteAll()
        studentRepository.deleteAll()
        userRepository.deleteAll()
        schoolRepository.deleteAll()
        roleRepository.deleteAll()
        
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
        
        session = MockHttpSession()
        session.setAttribute("selectedSchoolId", testSchoolId)

        // Create Roles
        if (roleRepository.findByName("STAFF").isEmpty) {
            roleRepository.save(Role(name = "STAFF", roleType = RoleType.valueOf("STAFF"), description = "STAFF"))
        }
        roleRepository.flush()

        // Create Staff User and Entity
        staffUser = createUser("staff@test.com", "STAFF")
        val staff = Staff().apply {
            this.user = staffUser
            this.staffId = "STF-${UUID.randomUUID()}"
            this.hireDate = LocalDate.now()
            this.schoolId = testSchoolId
            this.isActive = true
        }
        staffRepository.save(staff)
        staffRepository.flush()
    }

    private fun createUser(email: String, roleName: String): User {
        val uniqueEmail = "${UUID.randomUUID()}_$email"
        val user = User(
            email = uniqueEmail,
            firstName = "Test",
            lastName = roleName,
            phoneNumber = "080${System.nanoTime() % 1000000000}",
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
    fun `should load staff classes page`() {
        authenticateUser(staffUser.email!!)
        mockMvc.perform(get("/staff/classes").session(session))
            .andExpect(status().isOk)
            .andExpect(view().name("staff/classes"))
    }

    @Test
    fun `should load staff dashboard`() {
        authenticateUser(staffUser.email!!)
        mockMvc.perform(get("/staff/dashboard").session(session))
            .andExpect(status().isOk)
            .andExpect(view().name("dashboard/staff-dashboard"))
    }
}

package com.haneef._school.integration

import com.haneef._school.entity.*
import com.haneef._school.repository.*
import com.haneef._school.service.CustomUserDetails
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.transaction.annotation.Transactional
class FinancialAnalyticsTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var schoolRepository: SchoolRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var roleRepository: RoleRepository

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    private lateinit var session: MockHttpSession
    private lateinit var testSchoolId: UUID
    private lateinit var adminUser: User

    @BeforeEach
    fun setup() {
        // Clean up
        userRepository.deleteAll()
        schoolRepository.deleteAll()
        roleRepository.deleteAll()

        // Create School
        val school = School()
        school.name = "Test School"
        school.slug = "test-school-${UUID.randomUUID()}"
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
        
        session = MockHttpSession()
        session.setAttribute("selectedSchoolId", testSchoolId)

        // Create Roles
        if (roleRepository.findByName("SCHOOL_ADMIN").isEmpty) {
            roleRepository.save(Role(name = "SCHOOL_ADMIN", roleType = RoleType.SCHOOL_ADMIN, description = "School Admin"))
        }

        // Create Admin User
        val uniqueEmail = "admin-${UUID.randomUUID()}@test.com"
        val user = User(
            email = uniqueEmail,
            firstName = "Test",
            lastName = "Admin",
            phoneNumber = "080${System.nanoTime() % 1000000000}",
            passwordHash = passwordEncoder.encode("password")
        ).apply {
            status = UserStatus.ACTIVE
            isVerified = true
            emailVerified = true
        }
        val savedUser = userRepository.save(user)
        
        val role = roleRepository.findByName("SCHOOL_ADMIN").get()
        val userSchoolRole = UserSchoolRole(
            user = savedUser,
            role = role,
            schoolId = testSchoolId
        )
        savedUser.schoolRoles.add(userSchoolRole)
        adminUser = userRepository.save(savedUser)
    }

    private fun authenticateUser(email: String) {
        val user = userRepository.findByEmail(email).orElseThrow { RuntimeException("User not found: $email") }
        val principal = CustomUserDetails(user)
        val auth = UsernamePasswordAuthenticationToken(
            principal, "password", principal.authorities
        )
        SecurityContextHolder.getContext().authentication = auth
    }

    @Test
    fun `should load payment analytics page`() {
        authenticateUser(adminUser.email!!)
        mockMvc.perform(get("/admin/financial/analytics").session(session))
            .andExpect(status().isOk)
            .andExpect(view().name("admin/financial/payment-analytics"))
            .andExpect(model().attributeExists("analytics"))
    }
}

package com.haneef._school.service

import com.haneef._school.entity.*
import com.haneef._school.exception.BadRequestException
import com.haneef._school.exception.NotFoundException
import com.haneef._school.exception.TooManyRequestsException
import com.haneef._school.repository.*
import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.LocalDateTime
import java.util.*

class UserServiceTest {

    private val userRepo = mockk<UserRepository>(relaxed = true)
    private val roleRepo = mockk<RoleRepository>(relaxed = true)
    private val schoolRepo = mockk<SchoolRepository>(relaxed = true)
    private val userSchoolRoleRepo = mockk<UserSchoolRoleRepository>(relaxed = true)
    private val passwordEncoder = mockk<PasswordEncoder>(relaxed = true)
    private val emailService = mockk<EmailService>(relaxed = true)

    private lateinit var service: UserService

    @BeforeEach
    fun setup() {
        service = UserService(userRepo, roleRepo, schoolRepo, userSchoolRoleRepo, passwordEncoder, emailService)
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `requestOtp - non-existing email returns success and does not call email`() {
        every { userRepo.findByEmail("no@example.com") } returns Optional.empty()

        val res = service.requestOtp("no@example.com", "login")

        assertEquals("OTP sent successfully", res)
        verify(exactly = 0) { emailService.sendOtpEmail(any(), any()) }
    }

    @Test
    fun `requestOtp - existing user sends otp and saves`() {
        val user = User(email = "u@example.com")
        every { userRepo.findByEmail("u@example.com") } returns Optional.of(user)
        every { userRepo.save(any()) } returns user
        every { emailService.sendOtpEmail("u@example.com", any()) } returns Pair(true, "ok")

        val res = service.requestOtp("u@example.com", "login")

        assertEquals("OTP sent successfully", res)
        verify { emailService.sendOtpEmail("u@example.com", any()) }
        verify { userRepo.save(any()) }
    }

    @Test
    fun `requestOtp - rate limited`() {
        val user = User(email = "u@example.com")
        user.lastOtpSent = LocalDateTime.now()
        every { userRepo.findByEmail("u@example.com") } returns Optional.of(user)

        assertThrows(TooManyRequestsException::class.java) {
            service.requestOtp("u@example.com", "login")
        }
    }

    @Test
    fun `verifyOtp - success returns user`() {
        val user = User(email = "u@example.com")
        user.otpCode = UserService.hashOtp("123456")
        user.otpExpires = LocalDateTime.now().plusMinutes(10)
        every { userRepo.findByEmail("u@example.com") } returns Optional.of(user)
        every { userRepo.save(any()) } returns user

        val res = service.verifyOtp("u@example.com", "123456", "login")
        assertNotNull(res)
        verify { userRepo.save(any()) }
    }

    @Test
    fun `verifyOtp - wrong code increments attempts and fails`() {
        val user = User(email = "u@example.com")
        user.otpCode = UserService.hashOtp("123456")
        user.otpExpires = LocalDateTime.now().plusMinutes(10)
        user.otpAttempts = 0
        every { userRepo.findByEmail("u@example.com") } returns Optional.of(user)
        every { userRepo.save(any()) } returns user

        assertThrows(BadRequestException::class.java) {
            service.verifyOtp("u@example.com", "000000", "login")
        }
        assertEquals(1, user.otpAttempts)
    }

    @Test
    fun `resetPassword - invalid user throws`() {
        every { userRepo.findByEmail("no@example.com") } returns Optional.empty()
        assertThrows(NotFoundException::class.java) {
            service.resetPassword("no@example.com", "x", "pass")
        }
    }

    @Test
    fun `resetPassword - invalid otp throws`() {
        val user = User(email = "u@example.com")
        user.otpCode = "123456"
        user.otpExpires = LocalDateTime.now().plusMinutes(10)
        every { userRepo.findByEmail("u@example.com") } returns Optional.of(user)

        assertThrows(BadRequestException::class.java) {
            service.resetPassword("u@example.com", "000000", "pass")
        }
    }

    @Test
    fun `resetPassword - success encodes password and activates user`() {
        val user = User(email = "u@example.com")
        user.otpCode = UserService.hashOtp("123456")
        user.otpExpires = LocalDateTime.now().plusMinutes(10)
        user.isVerified = false
        every { userRepo.findByEmail("u@example.com") } returns Optional.of(user)
        every { passwordEncoder.encode("pass") } returns "hashed"
        every { userRepo.save(any()) } returns user
        // role lookup not needed for this branch (intendedRole null)

        service.resetPassword("u@example.com", "123456", "pass")

        assertEquals("hashed", user.passwordHash)
        assertTrue(user.isVerified)
        verify { userRepo.save(any()) }
    }

    @Test
    fun `resetPassword - school admin activation creates school and role`() {
        val user = User(email = "admin@example.com", firstName = "Alice")
        user.intendedRole = "SCHOOL_ADMIN"
        user.otpCode = UserService.hashOtp("123456")
        user.otpExpires = LocalDateTime.now().plusMinutes(10)
        user.isVerified = false

        val savedSchool = School(name = "Alice's School", slug = "alice-1234", email = "admin@example.com", phone = "", adminName = "Alice", adminEmail = "admin@example.com", adminPhone = "")
        savedSchool.id = UUID.randomUUID()

        every { userRepo.findByEmail("admin@example.com") } returns Optional.of(user)
        every { passwordEncoder.encode("pass") } returns "hashed"
        every { roleRepo.findByName("SCHOOL_ADMIN") } returns Optional.of(Role("SCHOOL_ADMIN", RoleType.SCHOOL_ADMIN))
        every { userRepo.save(any()) } returns user
        every { schoolRepo.save(any()) } returns savedSchool
        every { userSchoolRoleRepo.findByUser(user) } returns emptyList()
        every { userSchoolRoleRepo.save(any()) } returns mockk(relaxed = true)

        service.resetPassword("admin@example.com", "123456", "pass")

        assertTrue(user.isVerified)
        verify { schoolRepo.save(any()) }
        verify { userSchoolRoleRepo.save(any()) }
    }

    @Test
    fun `resetPassword - teacher activation assigns pending role`() {
        val user = User(email = "t@example.com")
        user.intendedRole = "TEACHER"
        user.intendedSchoolSlug = "myslug"
        user.otpCode = UserService.hashOtp("123456")
        user.otpExpires = LocalDateTime.now().plusMinutes(10)
        user.isVerified = false

        val school = School(name = "My", slug = "myslug", email = "", phone = "", adminName = "", adminEmail = "", adminPhone = "")
        school.id = UUID.randomUUID()

        every { userRepo.findByEmail("t@example.com") } returns Optional.of(user)
        every { passwordEncoder.encode("pass") } returns "hashed"
        every { roleRepo.findByName("TEACHER") } returns Optional.of(Role("TEACHER", RoleType.STAFF))
        every { schoolRepo.findBySlug("myslug") } returns Optional.of(school)
        every { userSchoolRoleRepo.findByUserAndSchoolId(user, school.id!!) } returns emptyList()
        every { userSchoolRoleRepo.save(any()) } returns mockk(relaxed = true)
        every { userRepo.save(any()) } returns user

        service.resetPassword("t@example.com", "123456", "pass")

        assertEquals(UserStatus.PENDING, user.status)
        assertEquals("pending", user.approvalStatus)
        verify { userSchoolRoleRepo.save(any()) }
    }
}

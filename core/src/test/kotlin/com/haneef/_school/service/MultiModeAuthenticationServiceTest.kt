package com.haneef._school.service

import com.haneef._school.dto.LoginMethod
import com.haneef._school.dto.LoginRequest
import com.haneef._school.entity.Student
import com.haneef._school.entity.User
import com.haneef._school.repository.StudentRepository
import com.haneef._school.repository.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UsernameNotFoundException

class MultiModeAuthenticationServiceTest {

    private val userRepository = mockk<UserRepository>(relaxed = true)
    private val studentRepository = mockk<StudentRepository>(relaxed = true)
    private val phoneNumberService = mockk<PhoneNumberService>(relaxed = true)
    private val customUserDetailsService = mockk<CustomUserDetailsService>(relaxed = true)

    private val service = MultiModeAuthenticationService(
        userRepository = userRepository,
        studentRepository = studentRepository,
        phoneNumberService = phoneNumberService,
        customUserDetailsService = customUserDetailsService
    )

    @Test
    fun `authenticateUser trims and lowercases email before lookup`() {
        val request = LoginRequest(
            loginMethod = LoginMethod.EMAIL,
            identifier = "  USER@EXAMPLE.COM  ",
            password = "secret"
        )

        val user = User().apply { isActive = true }
        val details = mockk<UserDetails>()

        every { userRepository.findByEmailIgnoreCase("user@example.com") } returns user
        every { customUserDetailsService.createUserDetails(user) } returns details

        val result = service.authenticateUser(request)

        assertEquals(details, result)
        verify(exactly = 1) { userRepository.findByEmailIgnoreCase("user@example.com") }
    }

    @Test
    fun `authenticateUser throws generic not found message for missing email`() {
        val request = LoginRequest(
            loginMethod = LoginMethod.EMAIL,
            identifier = "missing@example.com",
            password = "secret"
        )

        every { userRepository.findByEmailIgnoreCase("missing@example.com") } returns null

        val ex = assertThrows(UsernameNotFoundException::class.java) {
            service.authenticateUser(request)
        }

        assertEquals("Invalid credentials", ex.message)
    }

    @Test
    fun `authenticateUser throws generic not found message for inactive phone user`() {
        val request = LoginRequest(
            loginMethod = LoginMethod.PHONE,
            identifier = "08012345678",
            countryCode = "NG",
            password = "secret"
        )

        every { phoneNumberService.parseAndFormatPhoneNumber("08012345678", "NG") } returns "+2348012345678"
        every { userRepository.findByPhoneNumberForAuth("+2348012345678") } returns User().apply { isActive = false }

        val ex = assertThrows(UsernameNotFoundException::class.java) {
            service.authenticateUser(request)
        }

        assertEquals("Invalid credentials", ex.message)
    }

    @Test
    fun `authenticateUser trims and uppercases student identifier before lookup`() {
        val request = LoginRequest(
            loginMethod = LoginMethod.STUDENT,
            identifier = "  ad-001  ",
            password = "secret"
        )

        val student = Student()
        val details = mockk<UserDetails>()

        every { studentRepository.findByAdmissionNumber("AD-001") } returns listOf(student)
        every { customUserDetailsService.createStudentUserDetails(student) } returns details

        val result = service.authenticateUser(request)

        assertEquals(details, result)
        verify(exactly = 1) { studentRepository.findByAdmissionNumber("AD-001") }
    }
}

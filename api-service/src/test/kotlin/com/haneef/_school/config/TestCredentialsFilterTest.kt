package com.haneef._school.config

import com.haneef._school.entity.User
import com.haneef._school.service.CustomUserDetails
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder

class TestCredentialsFilterTest {

    private lateinit var filter: TestCredentialsFilter
    private lateinit var filterChain: FilterChain

    @BeforeEach
    fun setUp() {
        filter = TestCredentialsFilter()
        filterChain = mock(FilterChain::class.java)
        SecurityContextHolder.clearContext()
    }

    private fun setAuthentication(email: String) {
        val user = User()
        user.email = email
        user.firstName = "Test"
        user.lastName = "User"
        val userDetails = CustomUserDetails(user)
        val auth = UsernamePasswordAuthenticationToken(userDetails, null, userDetails.authorities)
        SecurityContextHolder.getContext().authentication = auth
    }

    @Test
    fun `should allow GET requests for test credentials`() {
        setAuthentication("test_admin@4school.app")
        val request = MockHttpServletRequest()
        request.method = "GET"
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, filterChain)

        verify(filterChain).doFilter(request, response)
        assertEquals(200, response.status)
    }

    @Test
    fun `should block POST requests for test credentials`() {
        setAuthentication("test_admin@4school.app")
        val request = MockHttpServletRequest()
        request.method = "POST"
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, filterChain)

        verify(filterChain, never()).doFilter(any(), any())
        assertEquals(HttpServletResponse.SC_FORBIDDEN, response.status)
        assertEquals("Test credentials are not allowed to modify data", response.errorMessage)
    }
    
    @Test
    fun `should allow POST requests for normal users`() {
        setAuthentication("normal.user@example.com")
        val request = MockHttpServletRequest()
        request.method = "POST"
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, filterChain)

        verify(filterChain).doFilter(request, response)
        assertEquals(200, response.status)
    }

    @Test
    fun `should block PUT requests for parent test credentials`() {
        setAuthentication("test_parent@4school.app")
        val request = MockHttpServletRequest()
        request.method = "PUT"
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, filterChain)

        verify(filterChain, never()).doFilter(any(), any())
        assertEquals(HttpServletResponse.SC_FORBIDDEN, response.status)
        assertEquals("Test credentials are not allowed to modify data", response.errorMessage)
    }
}

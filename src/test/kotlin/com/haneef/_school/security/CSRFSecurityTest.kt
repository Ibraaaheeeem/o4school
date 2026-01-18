package com.haneef._school.security

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureTestMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@SpringBootTest
@AutoConfigureTestMvc
@ActiveProfiles("test")
class CSRFSecurityTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `should reject POST requests without CSRF token`() {
        mockMvc.perform(
            post("/contact")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("name", "Test User")
                .param("email", "test@example.com")
                .param("message", "Test message")
        )
        .andExpect(status().isForbidden)
    }

    @Test
    fun `should accept POST requests with valid CSRF token`() {
        mockMvc.perform(
            post("/contact")
                .with(csrf())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("name", "Test User")
                .param("email", "test@example.com")
                .param("message", "Test message")
        )
        .andExpect(status().is3xxRedirection)
    }

    @Test
    @WithMockUser(roles = ["SCHOOL_ADMIN"])
    fun `should reject authenticated POST requests without CSRF token`() {
        mockMvc.perform(
            post("/admin/community/students/save")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("firstName", "Test")
                .param("lastName", "Student")
        )
        .andExpect(status().isForbidden)
    }

    @Test
    @WithMockUser(roles = ["SCHOOL_ADMIN"])
    fun `should accept authenticated POST requests with valid CSRF token`() {
        mockMvc.perform(
            post("/admin/community/students/save")
                .with(csrf())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("firstName", "Test")
                .param("lastName", "Student")
        )
        .andExpect(status().isOk)
    }

    @Test
    fun `should provide CSRF token endpoint`() {
        mockMvc.perform(get("/csrf-token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.token").exists())
            .andExpect(jsonPath("$.headerName").exists())
            .andExpect(jsonPath("$.parameterName").exists())
    }

    @Test
    fun `should allow GET requests without CSRF token`() {
        mockMvc.perform(get("/"))
            .andExpect(status().isOk)
    }

    @Test
    fun `should reject PUT requests without CSRF token`() {
        mockMvc.perform(
            put("/admin/test-endpoint")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"test\": \"data\"}")
        )
        .andExpect(status().isForbidden)
    }

    @Test
    fun `should reject DELETE requests without CSRF token`() {
        mockMvc.perform(delete("/admin/test-endpoint/1"))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `should allow whitelisted endpoints without CSRF`() {
        // Test webhook endpoint (should be excluded from CSRF)
        mockMvc.perform(
            post("/paystack/webhooks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"event\": \"test\"}")
        )
        .andExpect(status().isNotFound) // 404 because endpoint doesn't exist, but not 403 (CSRF)
    }
}
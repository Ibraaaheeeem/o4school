package com.haneef._school.integration

import com.haneef._school.entity.School
import com.haneef._school.repository.SchoolRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SchoolRegistrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var schoolRepository: SchoolRepository

    @BeforeEach
    fun setup() {
        schoolRepository.deleteAll()
        
        val school = School()
        school.name = "Test School"
        school.slug = "test-school"
        school.isActive = true
        school.addressLine1 = "123 Test St"
        school.city = "Test City"
        school.state = "Test State"
        school.postalCode = "12345"
        schoolRepository.save(school)
    }

    @Test
    fun `should validate existing school slug`() {
        mockMvc.perform(get("/api/public/validate-school-slug")
            .param("slug", "test-school")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.valid").value(true))
            .andExpect(jsonPath("$.name").value("Test School"))
            .andExpect(jsonPath("$.address").value("123 Test St, Test City, Test State"))
    }

    @Test
    fun `should validate existing school slug case insensitive`() {
        mockMvc.perform(get("/api/public/validate-school-slug")
            .param("slug", "TEST-SCHOOL")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.valid").value(true))
            .andExpect(jsonPath("$.name").value("Test School"))
            .andExpect(jsonPath("$.address").value("123 Test St, Test City, Test State"))
    }

    @Test
    fun `should return invalid for non-existent school slug`() {
        mockMvc.perform(get("/api/public/validate-school-slug")
            .param("slug", "non-existent-school")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.valid").value(false))
    }
}

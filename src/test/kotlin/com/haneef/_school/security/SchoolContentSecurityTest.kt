package com.haneef._school.security

import com.haneef._school.controller.SchoolContentController
import com.haneef._school.entity.School
import com.haneef._school.repository.SchoolRepository
import com.haneef._school.service.SchoolContentService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.*

class SchoolContentSecurityTest {

    private lateinit var mockMvc: MockMvc
    private val schoolContentService = mock(SchoolContentService::class.java)
    private val schoolRepository = mock(SchoolRepository::class.java)
    private val schoolId = UUID.randomUUID()

    @BeforeEach
    fun setup() {
        val controller = SchoolContentController(schoolContentService, schoolRepository)
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build()
        
        `when`(schoolContentService.getCustomizableSections()).thenReturn(
            listOf("hero-content", "about-content", "features-content", "contact-info", "additional-sections")
        )
    }

    @Test
    fun `saveSection should accept valid section name`() {
        `when`(schoolContentService.saveSchoolContent(schoolId, "hero-content", "New Content")).thenReturn(true)

        mockMvc.perform(
            post("/admin/school-content/save/hero-content")
                .param("content", "New Content")
                .sessionAttr("selectedSchoolId", schoolId)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Content saved successfully!"))
    }

    @Test
    fun `saveSection should reject invalid section name with path traversal attempt`() {
        mockMvc.perform(
            post("/admin/school-content/save/..%2f..%2f..%2fetc%2fpasswd")
                .param("content", "Malicious Content")
                .sessionAttr("selectedSchoolId", schoolId)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("Invalid section name"))
    }

    @Test
    fun `resetSection should accept valid section name`() {
        `when`(schoolContentService.saveSchoolContent(schoolId, "about-content", "")).thenReturn(true)

        mockMvc.perform(
            post("/admin/school-content/reset/about-content")
                .sessionAttr("selectedSchoolId", schoolId)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Content reset to default!"))
    }

    @Test
    fun `resetSection should reject invalid section name`() {
        mockMvc.perform(
            post("/admin/school-content/reset/invalid-section")
                .sessionAttr("selectedSchoolId", schoolId)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("Invalid section name"))
    }
}

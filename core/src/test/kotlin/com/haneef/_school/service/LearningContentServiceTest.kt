package com.haneef._school.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.util.UUID

class LearningContentServiceTest {

    private lateinit var jdbcTemplate: NamedParameterJdbcTemplate
    private lateinit var service: LearningContentService

    @BeforeEach
    fun setup() {
        // Instantiate with valid (non-blank) credentials. @PostConstruct is NOT called by the
        // test runner, so no real HikariCP pool is created. We inject a mock jdbcTemplate directly.
        service = LearningContentService(
            dbUrl = "jdbc:postgresql://localhost/test",
            dbUser = "testuser",
            dbPass = "testpass",
            maxPoolSize = 2
        )
        jdbcTemplate = mockk(relaxed = true)
        val field = LearningContentService::class.java.getDeclaredField("jdbcTemplate")
        field.isAccessible = true
        field.set(service, jdbcTemplate)
    }

    // ---- init() guard tests ----------------------------------------------------------------

    @Test
    fun `init throws IllegalArgumentException when dbUrl is blank`() {
        assertThrows<IllegalArgumentException> {
            LearningContentService(
                dbUrl = "   ",
                dbUser = "user",
                dbPass = "pass",
                maxPoolSize = 5
            ).init()
        }
    }

    @Test
    fun `init throws IllegalArgumentException when dbUser is blank`() {
        assertThrows<IllegalArgumentException> {
            LearningContentService(
                dbUrl = "jdbc:postgresql://localhost/test",
                dbUser = "",
                dbPass = "pass",
                maxPoolSize = 5
            ).init()
        }
    }

    // ---- getContentForWeek -----------------------------------------------------------------

    @Test
    fun `getContentForWeek returns empty list immediately for empty subject IDs`() {
        val result = service.getContentForWeek(emptyList(), week = 1, term = 1)

        assertTrue(result.isEmpty())
        // jdbcTemplate must never be called
        verify(exactly = 0) { jdbcTemplate.query(any<String>(), any<Map<String, *>>(), any<RowMapper<*>>()) }
    }

    @Test
    fun `getContentForWeek returns empty list and does not rethrow on DB error`() {
        every {
            jdbcTemplate.query(any<String>(), any<Map<String, *>>(), any<RowMapper<Triple<*, *, *>>>())
        } throws RuntimeException("simulated DB failure")

        val result = service.getContentForWeek(listOf(UUID.randomUUID()), week = 1, term = 1)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getContentForWeek maps base rows into ClassroomContent correctly`() {
        val subjectId = UUID.randomUUID()
        val subjectName = "Mathematics"
        val weekTheme = "Fractions"

        // Stub the outer base-row query to return one subject row (Triple)
        every {
            jdbcTemplate.query(
                match<String> { it.contains("FROM subjects s") },
                any<Map<String, *>>(),
                any<RowMapper<*>>()
            )
        } returns listOf(Triple(subjectId, subjectName, weekTheme))

        // Stub topics query (fetchTopicsWithSubtopics) — returns no topics
        every {
            jdbcTemplate.query(
                match<String> { it.contains("FROM topics") },
                any<Map<String, *>>(),
                any<RowMapper<*>>()
            )
        } returns emptyList<Any>()

        // Stub lessons query — returns one truly unassigned lesson
        every {
            jdbcTemplate.query(
                match<String> { it.contains("FROM lessons") },
                any<Map<String, *>>(),
                any<RowMapper<*>>()
            )
        } returns listOf(
            LessonDto(id = 1, title = "Intro", topicName = null, subtheme = null, durationMinutes = 30, lessonType = "video")
        )

        val result = service.getContentForWeek(listOf(subjectId), week = 2, term = 1)

        assertEquals(1, result.size)
        val content = result.first()
        assertEquals(subjectName, content.subjectName)
        assertEquals(2, content.weekNumber)
        assertEquals(weekTheme, content.weekTheme)
        assertTrue(content.topics.isEmpty())
        assertEquals(1, content.unassignedLessons.size)
        assertEquals("Intro", content.unassignedLessons.first().title)
    }

    // ---- getMenuHierarchy ------------------------------------------------------------------

    @Test
    fun `getMenuHierarchy returns empty list immediately for empty subject IDs`() {
        val result = service.getMenuHierarchy(emptyList(), term = 1)
        assertTrue(result.isEmpty())
        verify(exactly = 0) { jdbcTemplate.query(any<String>(), any<Map<String, *>>(), any<RowMapper<*>>()) }
    }

    // ---- getLessonDetails ------------------------------------------------------------------

    @Test
    fun `getLessonDetails returns null when lesson does not exist`() {
        every {
            jdbcTemplate.queryForMap(any<String>(), any<Map<String, *>>())
        } throws org.springframework.dao.EmptyResultDataAccessException(1)

        val result = service.getLessonDetails(9999)

        assertNull(result)
    }

    @Test
    fun `getLessonDetails returns the row map when found`() {
        val row: Map<String, Any?> = mapOf("id" to 1, "title" to "Intro to Algebra")
        every { jdbcTemplate.queryForMap(any<String>(), any<Map<String, *>>()) } returns row

        val result = service.getLessonDetails(1)

        assertEquals("Intro to Algebra", result?.get("title"))
    }

    // ---- listAllSubjects -------------------------------------------------------------------

    @Test
    fun `listAllSubjects returns empty list and does not rethrow on DB error`() {
        every {
            jdbcTemplate.query(any<String>(), any<Map<String, *>>(), any<RowMapper<ElearnerSubjectDto>>())
        } throws RuntimeException("simulated DB failure")

        val result = service.listAllSubjects()

        assertTrue(result.isEmpty())
    }
}

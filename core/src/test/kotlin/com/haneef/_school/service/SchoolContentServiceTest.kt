package com.haneef._school.service

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

class SchoolContentServiceTest {
    private val sanitizer = mockk<HtmlSanitizerService>()
    private val service = SchoolContentService(sanitizer)

    private val section = "test-section"
    private val schoolId: UUID = UUID.randomUUID()
    private val contentDir: Path = Paths.get("src/main/resources/templates/school/$schoolId/contents")
    private val contentFile: Path = contentDir.resolve("$section.html")

    @AfterEach
    fun cleanup() {
        try {
            if (Files.exists(contentFile)) Files.deleteIfExists(contentFile)
            var dir = contentDir
            if (Files.exists(dir)) {
                Files.deleteIfExists(dir)
                dir = dir.parent
                // attempt remove parent school dir if empty
                if (dir != null && Files.exists(dir)) Files.deleteIfExists(dir)
            }
        } catch (_: Exception) {
        }
    }

    @Test
    fun `getCustomizableSections returns expected sections`() {
        val sections = service.getCustomizableSections()
        assertTrue(sections.contains("hero-content"))
        assertTrue(sections.contains("about-content"))
    }

    @Test
    fun `saveSchoolContent writes sanitized content and hasCustomContent is true`() {
        val raw = "<script>alert(1)</script><p>Hello</p>"
        val sanitized = "<p>Hello</p>"
        every { sanitizer.sanitize(raw) } returns sanitized

        val saved = service.saveSchoolContent(schoolId, section, raw)
        assertTrue(saved)
        assertTrue(service.hasCustomContent(schoolId, section))

        val fileContents = Files.readString(contentFile)
        assertEquals(sanitized, fileContents)
    }

    @Test
    fun `saveSchoolContent returns false when sanitizer throws`() {
        val raw = "<b>bad</b>"
        every { sanitizer.sanitize(raw) } throws RuntimeException("boom")

        val saved = service.saveSchoolContent(schoolId, section, raw)
        assertFalse(saved)
        assertFalse(service.hasCustomContent(schoolId, section))
    }

    @Test
    fun `getSchoolContent returns custom content when present`() {
        val raw = "<p>Custom</p>"
        every { sanitizer.sanitize(raw) } returns raw

        val ok = service.saveSchoolContent(schoolId, section, raw)
        assertTrue(ok)

        val school = com.haneef._school.entity.School().apply { id = schoolId }
        val result = service.getSchoolContent(school, section)
        assertTrue(result.contains("Custom"))
    }

    @Test
    fun `getSchoolContent returns built in default when no custom exists`() {
        val school = com.haneef._school.entity.School().apply { id = schoolId }
        val result = service.getSchoolContent(school, "hero-content")
        assertTrue(result.contains("Welcome to Our School") || result.contains("default-content"))
    }
}

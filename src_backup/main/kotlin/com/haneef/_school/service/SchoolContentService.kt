package com.haneef._school.service

import com.haneef._school.entity.School
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

@Service
class SchoolContentService(
    private val htmlSanitizerService: HtmlSanitizerService
) {

    private val contentBasePath = "school"

    /**
     * Get custom content for a school section, fallback to default if not found
     */
    fun getSchoolContent(school: School, section: String): String {
        return try {
            val customContent = getCustomContent(school.id!!, section)
            if (customContent.isNotBlank()) {
                customContent
            } else {
                getDefaultContent(section)
            }
        } catch (e: Exception) {
            getDefaultContent(section)
        }
    }

    /**
     * Get custom content from school's content directory
     */
    private fun getCustomContent(schoolId: UUID, section: String): String {
        return try {
            val contentPath = Paths.get("src/main/resources/templates/$contentBasePath/$schoolId/contents/$section.html")
            if (Files.exists(contentPath)) {
                Files.readString(contentPath)
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Get default content from templates
     */
    private fun getDefaultContent(section: String): String {
        return try {
            val resource = ClassPathResource("templates/public/defaults/$section.html")
            if (resource.exists()) {
                resource.inputStream.bufferedReader().use { it.readText() }
            } else {
                getBuiltInDefault(section)
            }
        } catch (e: Exception) {
            getBuiltInDefault(section)
        }
    }

    /**
     * Save custom content for a school section
     */
    fun saveSchoolContent(schoolId: UUID, section: String, content: String): Boolean {
        return try {
            val contentDir = Paths.get("src/main/resources/templates/$contentBasePath/$schoolId/contents")
            Files.createDirectories(contentDir)
            
            val contentFile = contentDir.resolve("$section.html")
            
            // Sanitize content before saving to prevent XSS
            val sanitizedContent = htmlSanitizerService.sanitize(content)
            
            Files.writeString(contentFile, sanitizedContent)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get list of customizable sections
     */
    fun getCustomizableSections(): List<String> {
        return listOf(
            "hero-content",
            "about-content", 
            "features-content",
            "contact-info",
            "additional-sections"
        )
    }

    /**
     * Check if school has custom content for a section
     */
    fun hasCustomContent(schoolId: UUID, section: String): Boolean {
        val contentPath = Paths.get("src/main/resources/templates/$contentBasePath/$schoolId/contents/$section.html")
        return Files.exists(contentPath)
    }

    /**
     * Built-in default content when no template file exists
     */
    private fun getBuiltInDefault(section: String): String {
        return when (section) {
            "hero-content" -> """
                <div class="hero-content">
                    <h1 class="hero-title">Welcome to Our School</h1>
                    <p class="hero-subtitle">Excellence in Education, Innovation in Learning</p>
                    <div class="hero-buttons">
                        <a href="/register" class="btn btn-white btn-large">
                            <i class="fas fa-user-plus"></i> Get Started
                        </a>
                        <a href="#about" class="btn btn-outline btn-large" style="border-color: white; color: white;">
                            <i class="fas fa-info-circle"></i> Learn More
                        </a>
                    </div>
                </div>
            """.trimIndent()
            
            "about-content" -> """
                <div class="about-text">
                    <h2>About Our School</h2>
                    <p>
                        We are committed to providing excellence in education through innovative teaching methods 
                        and comprehensive student support. Our school management system ensures seamless 
                        communication between students, parents, and faculty.
                    </p>
                    <p>
                        With state-of-the-art technology and experienced educators, we create an environment 
                        where every student can thrive and reach their full potential.
                    </p>
                </div>
            """.trimIndent()
            
            "features-content" -> """
                <div class="features-grid">
                    <div class="feature-card">
                        <div class="feature-icon"><i class="fas fa-users"></i></div>
                        <h3 class="feature-title">Student Management</h3>
                        <p class="feature-description">Comprehensive student records and academic progress monitoring.</p>
                    </div>
                    <div class="feature-card">
                        <div class="feature-icon"><i class="fas fa-chalkboard-teacher"></i></div>
                        <h3 class="feature-title">Teacher Portal</h3>
                        <p class="feature-description">Powerful tools for teachers to manage classes and assessments.</p>
                    </div>
                    <div class="feature-card">
                        <div class="feature-icon"><i class="fas fa-chart-line"></i></div>
                        <h3 class="feature-title">Academic Analytics</h3>
                        <p class="feature-description">Data-driven insights into student performance.</p>
                    </div>
                </div>
            """.trimIndent()
            
            "contact-info" -> """
                <div class="contact-info">
                    <h3>Contact Information</h3>
                    
                    <div class="contact-item">
                        <i class="fas fa-map-marker-alt contact-icon"></i>
                        <div>
                            <div th:text="${'$'}{school.addressLine1}">Address Line 1</div>
                            <div th:if="${'$'}{school.addressLine2}" th:text="${'$'}{school.addressLine2}">Address Line 2</div>
                            <div>
                                <span th:text="${'$'}{school.city}">City</span>, 
                                <span th:text="${'$'}{school.state}">State</span> 
                                <span th:text="${'$'}{school.postalCode}">Postal Code</span>
                            </div>
                        </div>
                    </div>

                    <div class="contact-item">
                        <i class="fas fa-phone contact-icon"></i>
                        <div>
                            <a th:href="'tel:' + ${'$'}{school.phone}" th:text="${'$'}{school.phone}">Phone Number</a>
                        </div>
                    </div>

                    <div class="contact-item">
                        <i class="fas fa-envelope contact-icon"></i>
                        <div>
                            <a th:href="'mailto:' + ${'$'}{school.email}" th:text="${'$'}{school.email}">Email Address</a>
                        </div>
                    </div>

                    <div th:if="${'$'}{school.website}" class="contact-item">
                        <i class="fas fa-globe contact-icon"></i>
                        <div>
                            <a th:href="${'$'}{school.website}" target="_blank" th:text="${'$'}{school.website}">Website</a>
                        </div>
                    </div>
                </div>
            """.trimIndent()
            
            else -> "<div class=\"default-content\">Content not available</div>"
        }
    }
}
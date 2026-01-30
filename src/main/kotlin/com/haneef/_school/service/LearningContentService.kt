package com.haneef._school.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import jakarta.annotation.PostConstruct
import javax.sql.DataSource

data class ClassroomContent(
    val subjectName: String,
    val weekNumber: Int,
    val weekTheme: String?,
    val topics: List<TopicDto>,
    val lessons: List<LessonDto>
)

data class TopicDto(
    val id: Int,
    val name: String,
    val description: String?,
    val subtopics: List<SubtopicDto> = emptyList()
)

data class SubtopicDto(
    val id: Int,
    val name: String,
    val description: String?
)

data class LessonDto(
    val id: Int,
    val title: String,
    val topicName: String?
)

@Service
class LearningContentService(
    @Value("\${elearner.datasource.url}") private val dbUrl: String,
    @Value("\${elearner.datasource.username}") private val dbUser: String,
    @Value("\${elearner.datasource.password}") private val dbPass: String
) {

    private lateinit var jdbcTemplate: JdbcTemplate

    @PostConstruct
    fun init() {
        val dataSource = DriverManagerDataSource().apply {
            setDriverClassName("org.postgresql.Driver")
            url = dbUrl
            username = dbUser
            password = dbPass
        }
        jdbcTemplate = JdbcTemplate(dataSource)
    }

    fun getContentForWeek(subjectNames: List<String>, week: Int, term: Int): List<ClassroomContent> {
        if (subjectNames.isEmpty()) return emptyList()

        val placeholders = subjectNames.joinToString(",") { "?" }
        // We first find the subjects in the elearner DB that match our names
        // Then for each subject, we fetch the week info, topics, and lessons
        
        // This query finds subjects and their week info
        val sqlValue = """
            SELECT s.id as subject_id, s.name as subject_name, w.week, w.name as week_name, w.theme
            FROM subjects s
            LEFT JOIN weeks w ON s.id = w.subject_id AND w.week = ? AND w.term = ?
            WHERE s.name IN ($placeholders)
        """
        
        val args = mutableListOf<Any>(week, term)
        args.addAll(subjectNames)

        val results = mutableListOf<ClassroomContent>()

        try {
            jdbcTemplate.query(sqlValue, { rs, _ ->
                val subjectId = rs.getInt("subject_id")
                val subjectName = rs.getString("subject_name")
                val weekTheme = rs.getString("theme")
                
                // Now fetch topics for this subject/week/term
                val topics = fetchTopics(subjectId, week, term)
                // Fetch lessons
                val lessons = fetchLessons(subjectId, week, term)
                
                ClassroomContent(
                    subjectName = subjectName,
                    weekNumber = week,
                    weekTheme = weekTheme,
                    topics = topics,
                    lessons = lessons
                )
            }, *args.toTypedArray()).forEach { results.add(it) }
        } catch (e: Exception) {
            e.printStackTrace()
            // Return empty if connection fails
        }

        return results
    }
    
    fun getLessonDetails(lessonId: Int): Map<String, Any?>? {
         try {
            val sql = "SELECT * FROM lessons WHERE id = ?"
            return jdbcTemplate.queryForMap(sql, lessonId)
        } catch (e: Exception) {
           return null
        }
    }

    private fun fetchTopics(subjectId: Int, week: Int, term: Int): List<TopicDto> {
        val sql = "SELECT id, name, description FROM topics WHERE subject_id = ? AND week = ? AND term = ?"
        return jdbcTemplate.query(sql, { rs, _ ->
            val topicId = rs.getInt("id")
            val subtopics = fetchSubtopics(topicId)
            TopicDto(
                id = topicId,
                name = rs.getString("name"),
                description = rs.getString("description"),
                subtopics = subtopics
            )
        }, subjectId, week, term)
    }

    private fun fetchSubtopics(topicId: Int): List<SubtopicDto> {
        val sql = "SELECT id, name, description FROM subtopics WHERE topic_id = ?"
        return jdbcTemplate.query(sql, { rs, _ ->
            SubtopicDto(
                id = rs.getInt("id"),
                name = rs.getString("name"),
                description = rs.getString("description")
            )
        }, topicId)
    }

    private fun fetchLessons(subjectId: Int, week: Int, term: Int): List<LessonDto> {
        val sql = "SELECT id, title, topic FROM lessons WHERE subject_id = ? AND week = ? AND term = ?"
        return jdbcTemplate.query(sql, { rs, _ ->
            LessonDto(
                id = rs.getInt("id"),
                title = rs.getString("title"),
                topicName = rs.getString("topic")
            )
        }, subjectId, week, term)
    }
}

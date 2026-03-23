package com.haneef._school.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import com.zaxxer.hikari.HikariDataSource
import jakarta.annotation.PostConstruct
import java.util.UUID

data class ClassroomContent(
    val subjectName: String,
    val weekNumber: Int,
    val weekTheme: String?,
    val topics: List<TopicContentDto>,
    val unassignedLessons: List<LessonDto>
)

data class ElearnerSubjectDto(
    val id: UUID,
    val name: String,
    val code: String?,
    val gradeLevel: Int? = null
)

// Internal DTO for raw fetching
data class TopicDto(
    val id: Int,
    val name: String,
    val description: String?,
    val subtopics: List<SubtopicDto> = emptyList()
)

// Rich DTO for display
data class TopicContentDto(
    val id: Int,
    val name: String,
    val description: String?,
    val subtopics: List<SubtopicDto>,
    val lessons: List<LessonDto>
)

data class SubtopicDto(
    val id: Int,
    val name: String,
    val description: String?
)

data class LessonDto(
    val id: Int,
    val title: String,
    val topicName: String?,
    val subtheme: String?,
    val durationMinutes: Int?,
    val lessonType: String?
)

data class WeekMenuDto(
    val week: Int,
    val subjects: List<SubjectMenuDto>
)

data class SubjectMenuDto(
    val id: UUID,
    val name: String,
    val topics: List<TopicMenuDto>,
    val subThemes: List<SubThemeMenuDto> = emptyList(),
    val unassignedLessons: List<LessonDto> = emptyList()
)

data class SubThemeMenuDto(
    val name: String,
    val lessons: List<LessonDto>
)

data class TopicMenuDto(
    val id: Int,
    val name: String,
    val description: String? = null,
    val lessons: List<LessonDto> = emptyList()
)

@Service
class LearningContentService(
    @Value("\${elearner.datasource.url}") private val dbUrl: String,
    @Value("\${elearner.datasource.username}") private val dbUser: String,
    @Value("\${elearner.datasource.password}") private val dbPass: String,
    @Value("\${elearner.datasource.max-pool-size:10}") private val maxPoolSize: Int
) {

    companion object {
        private val logger = LoggerFactory.getLogger(LearningContentService::class.java)
        private const val DEFAULT_MAX_WEEK = 12
    }

    private lateinit var jdbcTemplate: NamedParameterJdbcTemplate

    @PostConstruct
    fun init() {
        require(dbUrl.isNotBlank()) { "elearner.datasource.url must not be blank" }
        require(dbUser.isNotBlank()) { "elearner.datasource.username must not be blank" }
        val dataSource = HikariDataSource().apply {
            poolName = "elearner-pool"
            connectionTimeout = 30_000L
            driverClassName = "org.postgresql.Driver"
            jdbcUrl = dbUrl
            username = dbUser
            password = dbPass
            maximumPoolSize = maxPoolSize
        }
        jdbcTemplate = NamedParameterJdbcTemplate(dataSource)
        logger.info("LearningContentService initialized with pool size {}", maxPoolSize)
    }

    fun getContentForWeek(elearnerSubjectIds: List<UUID>, week: Int, term: Int): List<ClassroomContent> {
        if (elearnerSubjectIds.isEmpty()) return emptyList()
        logger.debug("getContentForWeek: subjects={}, week={}, term={}", elearnerSubjectIds.size, week, term)

        val sql = """
            SELECT s.id as subject_id, s.name as subject_name, w.week, w.name as week_name, w.theme
            FROM subjects s
            LEFT JOIN weeks w ON s.id = w.subject_id AND w.week = :week AND w.term = :term
            WHERE s.id IN (:ids)
        """

        return try {
            val params = mapOf("week" to week, "term" to term, "ids" to elearnerSubjectIds)

            // Fetch base subject rows first — DB calls must NOT be nested inside the row mapper
            // while the outer ResultSet is still open (risks connection pool exhaustion).
            val baseRows = jdbcTemplate.query(sql, params) { rs, _ ->
                Triple(
                    rs.getObject("subject_id", UUID::class.java),
                    rs.getString("subject_name"),
                    rs.getString("theme")
                )
            }

            baseRows.map { (subjectId, subjectName, weekTheme) ->
                logger.debug("Processing subject: {} ({})", subjectName, subjectId)

                val rawTopics = fetchTopicsWithSubtopics(subjectId, week, term)
                val allLessons = fetchLessons(subjectId, week, term)
                logger.debug("Subject {}: {} topics, {} lessons", subjectName, rawTopics.size, allLessons.size)

                var topicContentList = rawTopics.map { topic ->
                    val topicLessons = allLessons.filter {
                        it.topicName?.trim()?.equals(topic.name.trim(), ignoreCase = true) == true
                    }
                    TopicContentDto(
                        id = topic.id,
                        name = topic.name,
                        description = topic.description,
                        subtopics = topic.subtopics,
                        lessons = topicLessons
                    )
                }

                val assignedLessonIds = topicContentList.flatMap { it.lessons }.map { it.id }.toSet()
                var remainingLessons = allLessons.filter { it.id !in assignedLessonIds }

                if (remainingLessons.isNotEmpty()) {
                    val virtualTopics = remainingLessons.groupBy { it.subtheme }
                        .mapNotNull { (subthemeName, lessons) ->
                            subthemeName ?: return@mapNotNull null
                            TopicContentDto(
                                id = lessons.first().id * -1,
                                name = subthemeName,
                                description = null,
                                subtopics = emptyList(),
                                lessons = lessons
                            )
                        }
                    topicContentList = topicContentList + virtualTopics
                    val finalAssignedIds = topicContentList.flatMap { it.lessons }.map { it.id }.toSet()
                    remainingLessons = allLessons.filter { it.id !in finalAssignedIds }
                }

                logger.debug(
                    "Subject {} — assigned: {}, unassigned: {}",
                    subjectName, topicContentList.flatMap { it.lessons }.size, remainingLessons.size
                )

                ClassroomContent(
                    subjectName = subjectName,
                    weekNumber = week,
                    weekTheme = weekTheme,
                    topics = topicContentList,
                    unassignedLessons = remainingLessons
                )
            }.also { logger.debug("getContentForWeek returning {} result(s)", it.size) }
        } catch (e: Exception) {
            logger.error("Error in getContentForWeek: subjects={}, week={}, term={}", elearnerSubjectIds.size, week, term, e)
            emptyList()
        }
    }

    fun getMenuHierarchy(elearnerSubjectIds: List<UUID>, term: Int): List<WeekMenuDto> {
        if (elearnerSubjectIds.isEmpty()) return emptyList()

        // 1. Fetch Subject Names
        val subjectsSql = "SELECT id, name FROM subjects WHERE id IN (:ids)"
        val subjectsList = jdbcTemplate.query(subjectsSql, mapOf("ids" to elearnerSubjectIds)) { rs, _ ->
            rs.getObject("id", UUID::class.java) to rs.getString("name")
        }.toMap()

        // 2. Fetch All Topics for the Term (needed for tabs)
        val topicsSql = """
            SELECT id, name, description, subject_id, week 
            FROM topics 
            WHERE subject_id IN (:ids) AND term = :term 
            ORDER BY week ASC, "order" ASC
        """
        val allTopics = jdbcTemplate.query(topicsSql, mapOf("ids" to elearnerSubjectIds, "term" to term)) { rs, _ ->
            mapOf(
                "id" to rs.getInt("id"),
                "name" to rs.getString("name"),
                "description" to rs.getString("description"),
                "subjectId" to rs.getObject("subject_id", UUID::class.java),
                "week" to rs.getInt("week")
            )
        }

        // 3. Fetch All Lessons for the Term (including subtheme)
        val lessonsSql = """
            SELECT id, title, topic, subtheme, duration_minutes, lesson_type, subject_id, week 
            FROM lessons 
            WHERE subject_id IN (:ids) AND term = :term 
            ORDER BY "order" ASC
        """
        val allLessons = jdbcTemplate.query(lessonsSql, mapOf("ids" to elearnerSubjectIds, "term" to term)) { rs, _ ->
            mapOf(
                "id" to rs.getInt("id"),
                "title" to rs.getString("title"),
                "topicName" to rs.getString("topic"),
                "subtheme" to rs.getString("subtheme"),
                "durationMinutes" to rs.getObject("duration_minutes", Int::class.javaObjectType),
                "lessonType" to rs.getString("lesson_type"),
                "subjectId" to rs.getObject("subject_id", UUID::class.java),
                "week" to rs.getInt("week")
            )
        }

        // 4. Group by Week and Subject
        val maxWeek = (allTopics.map { it["week"] as Int } + allLessons.map { it["week"] as Int } + listOf(DEFAULT_MAX_WEEK)).maxOrNull() ?: DEFAULT_MAX_WEEK
        
        return (1..maxWeek).map { weekNum ->
            WeekMenuDto(
                week = weekNum,
                subjects = elearnerSubjectIds.map { subjId ->
                    val subjName = subjectsList[subjId] ?: "Unknown"
                    
                    val weekSubjTopics = allTopics.filter { it["week"] == weekNum && it["subjectId"] == subjId }
                    val weekSubjLessons = allLessons.filter { it["week"] == weekNum && it["subjectId"] == subjId }
                    
                    // Group by Topics (for tabs)
                    var topicDtos = weekSubjTopics.map { t ->
                        val tId = t["id"] as Int
                        val tName = t["name"] as String
                        val tLessons = weekSubjLessons.filter {
                            it["topicName"]?.toString()?.trim()?.equals(tName.trim(), ignoreCase = true) == true
                        }.map { it.toLessonDto() }

                        TopicMenuDto(
                            id = tId,
                            name = tName,
                            description = t["description"]?.toString(),
                            lessons = tLessons
                        )
                    }

                    // Unassigned lessons (relative to topics)
                    val assignedLessonIds = topicDtos.flatMap { it.lessons }.map { it.id }.toSet()
                    val remainingLessons = weekSubjLessons.filter { (it["id"] as Int) !in assignedLessonIds }

                    // FALLBACK: If we have unassigned lessons with subthemes, promote them to virtual topics
                    if (remainingLessons.isNotEmpty()) {
                        val virtualTopics = remainingLessons.groupBy { it["subtheme"]?.toString() }
                            .mapNotNull { (name, lessons) ->
                                name ?: return@mapNotNull null
                                TopicMenuDto(
                                    id = (lessons.first()["id"] as Int) * -1,
                                    name = name,
                                    description = null,
                                    lessons = lessons.map { it.toLessonDto() }
                                )
                            }
                        topicDtos = topicDtos + virtualTopics
                    }

                    // Group by Subthemes (for sidebar hierarchy)
                    val subThemeGroups = weekSubjLessons.groupBy {
                        it["subtheme"]?.toString() ?: "General"
                    }.map { (name, lessons) ->
                        SubThemeMenuDto(name = name, lessons = lessons.map { it.toLessonDto() })
                    }

                    // Final unassigned (truly no topic and no subtheme)
                    val finalAssignedIds = topicDtos.flatMap { it.lessons }.map { it.id }.toSet()
                    val unassignedLessons = weekSubjLessons
                        .filter { (it["id"] as Int) !in finalAssignedIds }
                        .map { it.toLessonDto() }

                    SubjectMenuDto(
                        id = subjId,
                        name = subjName,
                        topics = topicDtos,
                        subThemes = subThemeGroups,
                        unassignedLessons = unassignedLessons
                    )
                }.sortedBy { it.name }
            )
        }
    }
    
    fun getLessonDetails(lessonId: Int): Map<String, Any?>? {
        return try {
            val sql = "SELECT * FROM lessons WHERE id = :id"
            jdbcTemplate.queryForMap(sql, mapOf("id" to lessonId))
        } catch (e: Exception) {
            logger.warn("Lesson not found or error fetching id={}: {}", lessonId, e.message)
            null
        }
    }

    // Fetches topics and all their subtopics in two queries (batch) instead of N+1
    private fun fetchTopicsWithSubtopics(subjectId: UUID, week: Int, term: Int): List<TopicDto> {
        val sql = "SELECT id, name, description FROM topics WHERE subject_id = :subjectId AND week = :week AND term = :term ORDER BY \"order\" ASC"
        val params = mapOf("subjectId" to subjectId, "week" to week, "term" to term)
        val topics = jdbcTemplate.query(sql, params) { rs, _ ->
            Triple(rs.getInt("id"), rs.getString("name"), rs.getString("description"))
        }
        if (topics.isEmpty()) return emptyList()
        val subtopicsByTopic = fetchSubtopicsBatch(topics.map { it.first })
        return topics.map { (id, name, description) ->
            TopicDto(id = id, name = name, description = description, subtopics = subtopicsByTopic[id] ?: emptyList())
        }
    }

    // Batch-loads subtopics for multiple topic IDs in a single query instead of one query per topic
    private fun fetchSubtopicsBatch(topicIds: List<Int>): Map<Int, List<SubtopicDto>> {
        if (topicIds.isEmpty()) return emptyMap()
        val sql = "SELECT id, name, description, topic_id FROM subtopics WHERE topic_id IN (:topicIds)"
        return jdbcTemplate.query(sql, mapOf("topicIds" to topicIds)) { rs, _ ->
            rs.getInt("topic_id") to SubtopicDto(
                id = rs.getInt("id"),
                name = rs.getString("name"),
                description = rs.getString("description")
            )
        }.groupBy({ it.first }, { it.second })
    }

    private fun fetchLessons(subjectId: UUID, week: Int, term: Int): List<LessonDto> {
        val sql = "SELECT id, title, topic, subtheme, duration_minutes, lesson_type FROM lessons WHERE subject_id = :subjectId AND week = :week AND term = :term ORDER BY \"order\" ASC"
        val params = mapOf(
            "subjectId" to subjectId,
            "week" to week,
            "term" to term
        )
        return jdbcTemplate.query(sql, params) { rs, _ ->
            LessonDto(
                id = rs.getInt("id"),
                title = rs.getString("title"),
                topicName = rs.getString("topic"),
                subtheme = rs.getString("subtheme"),
                durationMinutes = rs.getObject("duration_minutes", Int::class.javaObjectType),
                lessonType = rs.getString("lesson_type")
            )
        }
    }

    fun listAllSubjects(): List<ElearnerSubjectDto> {
        val sql = "SELECT id, name, code, grade_level FROM subjects ORDER BY grade_level, name"
        return try {
            jdbcTemplate.query(sql, emptyMap<String, Any>()) { rs, _ ->
                ElearnerSubjectDto(
                    id = rs.getObject("id", UUID::class.java),
                    name = rs.getString("name"),
                    code = rs.getString("code"),
                    gradeLevel = rs.getObject("grade_level", Int::class.javaObjectType)
                )
            }
        } catch (e: Exception) {
            logger.error("Failed to list subjects", e)
            emptyList()
        }
    }

    /** Converts a raw JDBC row map (from [getMenuHierarchy] queries) to a typed [LessonDto]. */
    private fun Map<String, Any?>.toLessonDto() = LessonDto(
        id = this["id"] as Int,
        title = this["title"] as String,
        topicName = this["topicName"]?.toString(),
        subtheme = this["subtheme"]?.toString(),
        durationMinutes = this["durationMinutes"] as? Int,
        lessonType = this["lessonType"]?.toString()
    )
}

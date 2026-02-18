package com.haneef._school.service

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

    private lateinit var jdbcTemplate: NamedParameterJdbcTemplate

    @PostConstruct
    fun init() {
        val dataSource = HikariDataSource().apply {
            driverClassName = "org.postgresql.Driver"
            jdbcUrl = dbUrl
            username = dbUser
            password = dbPass
            maximumPoolSize = maxPoolSize
        }
        jdbcTemplate = NamedParameterJdbcTemplate(dataSource)
    }

    fun getContentForWeek(elearnerSubjectIds: List<UUID>, week: Int, term: Int): List<ClassroomContent> {
        val logFile = java.io.File("/tmp/elearner_debug.txt")
        logFile.writeText("Service call: ids=$elearnerSubjectIds, week=$week, term=$term\n")
        
        if (elearnerSubjectIds.isEmpty()) {
            logFile.appendText("Empty subject list, returning.\n")
            return emptyList()
        }

        val sqlValue = """
            SELECT s.id as subject_id, s.name as subject_name, w.week, w.name as week_name, w.theme
            FROM subjects s
            LEFT JOIN weeks w ON s.id = w.subject_id AND w.week = :week AND w.term = :term
            WHERE s.id IN (:ids)
        """
        
        try {
            val params = mapOf(
                "week" to week,
                "term" to term,
                "ids" to elearnerSubjectIds
            )
            
            val results = jdbcTemplate.query(sqlValue, params) { rs, _ ->
                val subjectId = rs.getObject("subject_id", UUID::class.java)
                val subjectName = rs.getString("subject_name")
                val weekTheme = rs.getString("theme")
                
                logFile.appendText("Processing subject: $subjectName ($subjectId)\n")
                
                // Fetch basic entities
                val rawTopics = fetchTopics(subjectId, week, term)
                val allLessons = fetchLessons(subjectId, week, term)
                
                logFile.appendText("Found ${rawTopics.size} topics and ${allLessons.size} lessons.\n")
                
                // Group lessons by topic name
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
                
                // Identifying unassigned lessons with subthemes for promotion to virtual topics
                val assignedLessonIds = topicContentList.flatMap { it.lessons }.map { it.id }.toSet()
                var remainingLessons = allLessons.filter { !assignedLessonIds.contains(it.id) }
                
                if (remainingLessons.isNotEmpty()) {
                    val subthemeGroups = remainingLessons.groupBy { it.subtheme }
                    
                    val virtualTopics = subthemeGroups.mapNotNull { (subthemeName, lessons) ->
                        if (subthemeName == null) return@mapNotNull null
                        TopicContentDto(
                            id = lessons.first().id * -1,
                            name = subthemeName,
                            description = null,
                            subtopics = emptyList(),
                            lessons = lessons
                        )
                    }
                    
                    // Add virtual topics to the main list
                    topicContentList = topicContentList + virtualTopics
                    
                    // Final remaining lessons are those truly without a topic OR a subtheme
                    val finalAssignedIds = topicContentList.flatMap { it.lessons }.map { it.id }.toSet()
                    remainingLessons = allLessons.filter { !finalAssignedIds.contains(it.id) }
                }
                
                logFile.appendText("Assigned lessons (including virtual): ${topicContentList.flatMap { it.lessons }.size}, Unassigned: ${remainingLessons.size}\n")
                
                ClassroomContent(
                    subjectName = subjectName,
                    weekNumber = week,
                    weekTheme = weekTheme,
                    topics = topicContentList,
                    unassignedLessons = remainingLessons
                )
            }
            
            logFile.appendText("Returning ${results.size} result(s).\n")
            return results
        } catch (e: Exception) {
            logFile.appendText("Error in getContentForWeek: ${e.message}\n")
            logFile.appendText(e.stackTraceToString() + "\n")
            e.printStackTrace()
            return emptyList()
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
        val maxWeek = (allTopics.map { it["week"] as Int } + allLessons.map { it["week"] as Int } + listOf(12)).maxOrNull() ?: 12
        
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
                        }.map { l ->
                            LessonDto(
                                id = l["id"] as Int,
                                title = l["title"] as String,
                                topicName = l["topicName"]?.toString(),
                                subtheme = l["subtheme"]?.toString(),
                                durationMinutes = l["durationMinutes"] as? Int,
                                lessonType = l["lessonType"]?.toString()
                            )
                        }
                        
                        TopicMenuDto(
                            id = tId,
                            name = tName,
                            description = t["description"]?.toString(),
                            lessons = tLessons
                        )
                    }
                    
                    // Unassigned lessons (relative to topics)
                    val assignedLessonIds = topicDtos.flatMap { it.lessons }.map { it.id }.toSet()
                    val remainingLessons = weekSubjLessons.filter { !assignedLessonIds.contains(it["id"] as Int) }
                    
                    // FALLBACK: If we have unassigned lessons with subthemes, promote them to virtual topics
                    if (remainingLessons.isNotEmpty()) {
                        val subthemeGroups = remainingLessons.groupBy { it["subtheme"]?.toString() }
                        
                        val virtualTopics = subthemeGroups.mapNotNull { (name, lessons) ->
                            if (name == null) return@mapNotNull null
                            TopicMenuDto(
                                id = (lessons.first()["id"] as Int) * -1,
                                name = name,
                                description = null,
                                lessons = lessons.map { l ->
                                    LessonDto(
                                        id = l["id"] as Int,
                                        title = l["title"] as String,
                                        topicName = l["topicName"]?.toString(),
                                        subtheme = l["subtheme"]?.toString(),
                                        durationMinutes = l["durationMinutes"] as? Int,
                                        lessonType = l["lessonType"]?.toString()
                                    )
                                }
                            )
                        }
                        
                        topicDtos = topicDtos + virtualTopics
                    }

                    // Group by Subthemes (for sidebar hierarchy - this remains largely the same but uses all lessons)
                    val subThemeGroups = weekSubjLessons.groupBy { 
                        it["subtheme"]?.toString() ?: "General" 
                    }.map { (name, lessons) ->
                        SubThemeMenuDto(
                            name = name,
                            lessons = lessons.map { l ->
                                LessonDto(
                                    id = l["id"] as Int,
                                    title = l["title"] as String,
                                    topicName = l["topicName"]?.toString(),
                                    subtheme = l["subtheme"]?.toString(),
                                    durationMinutes = l["durationMinutes"] as? Int,
                                    lessonType = l["lessonType"]?.toString()
                                )
                            }
                        )
                    }
                    
                    // Final unassigned (truly no topic and no subtheme)
                    val finalAssignedIds = topicDtos.flatMap { it.lessons }.map { it.id }.toSet()
                    val unassignedLessons = weekSubjLessons.filter { !finalAssignedIds.contains(it["id"] as Int) }.map { l ->
                        LessonDto(
                            id = l["id"] as Int,
                            title = l["title"] as String,
                            topicName = l["topicName"]?.toString(),
                            subtheme = l["subtheme"]?.toString(),
                            durationMinutes = l["durationMinutes"] as? Int,
                            lessonType = l["lessonType"]?.toString()
                        )
                    }

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
         try {
            val sql = "SELECT * FROM lessons WHERE id = :id"
            return jdbcTemplate.queryForMap(sql, mapOf("id" to lessonId))
        } catch (e: Exception) {
           return null
        }
    }

    // Returns intermediate DTO
    private fun fetchTopics(subjectId: UUID, week: Int, term: Int): List<TopicDto> {
        val sql = "SELECT id, name, description FROM topics WHERE subject_id = :subjectId AND week = :week AND term = :term ORDER BY \"order\" ASC"
        val params = mapOf(
            "subjectId" to subjectId,
            "week" to week,
            "term" to term
        )
        return jdbcTemplate.query(sql, params) { rs, _ ->
            val topicId = rs.getInt("id")
            val subtopics = fetchSubtopics(topicId)
            TopicDto(
                id = topicId,
                name = rs.getString("name"),
                description = rs.getString("description"),
                subtopics = subtopics
            )
        }
    }

    private fun fetchSubtopics(topicId: Int): List<SubtopicDto> {
        val sql = "SELECT id, name, description FROM subtopics WHERE topic_id = :topicId"
        return jdbcTemplate.query(sql, mapOf("topicId" to topicId)) { rs, _ ->
            SubtopicDto(
                id = rs.getInt("id"),
                name = rs.getString("name"),
                description = rs.getString("description")
            )
        }
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
            emptyList()
        }
    }
}

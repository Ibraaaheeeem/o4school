package com.haneef._school.controller

import com.haneef._school.service.SchoolDataTools
import org.springframework.ai.chat.client.ChatClient
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/admin/query")
class NaturalLanguageQueryController(
    private val chatClient: ChatClient,
    private val schoolDataTools: SchoolDataTools
) {

    @PostMapping("/parents")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'SCHOOL_ADMIN', 'ADMIN')")
    fun queryParents(@RequestBody request: QueryRequest, session: jakarta.servlet.http.HttpSession): List<SchoolDataTools.RecipientInfo> {
        val schoolId = session.getAttribute("selectedSchoolId") as? java.util.UUID 
            ?: throw IllegalStateException("No school selected")

        val response = chatClient.prompt()
            .system("""
                You are an administrative assistant for a school with ID: $schoolId.
                Your task is to filter recipients (parents, staff, or students) based on the user's criteria.
                
                CAPABILITIES:
                - Use 'queryParents' for parents. You can now filter by debt (e.g. 'owing > 50k'), paid amount, children's class, age, gender, or new/old status.
                - Use 'queryStaff' for staff. You can filter by track, classes/subjects taught, role (Class/Subject teacher), or recruitment year (e.g. 'joined after 2023').
                - Use 'queryStudents' for students.
                
                ALWAYS provide the schoolId ($schoolId) to the tool.
                Return only the list of recipients.
            """.trimIndent())
            .user(request.query)
            .tools(schoolDataTools)
            .call()
            .entity(RecipientListResponse::class.java)

        return response?.recipients ?: emptyList()
    }

    data class QueryRequest(val query: String)
    data class RecipientListResponse(val recipients: List<SchoolDataTools.RecipientInfo>)
}

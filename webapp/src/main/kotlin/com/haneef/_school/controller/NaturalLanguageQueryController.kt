package com.haneef._school.controller

import com.haneef._school.config.NativeDto
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
                Your task is to filter recipients for broadcast messages.
                
                STRICT LIMITATIONS:
                - You ONLY support filtering for 'parents' and 'staff'.
                - Use 'queryParents' for parent-related queries.
                - Use 'queryStaff' for staff-related queries.
                - DO NOT attempt to query students, school info, or general questions here.
                - If the user query does not target parents or staff, respond with an empty recipient list or abort.
                
                ALWAYS provide the schoolId ($schoolId) to the tool.
                Return only the list of recipients.
            """.trimIndent())
            .user(request.query)
            .tools(schoolDataTools)
            .call()
            .entity(RecipientListResponse::class.java)

        return response?.recipients ?: emptyList()
    }

    @NativeDto
    data class QueryRequest(val query: String)
    @NativeDto
    data class RecipientListResponse(val recipients: List<SchoolDataTools.RecipientInfo>)
}

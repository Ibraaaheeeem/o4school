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
    fun queryParents(@RequestBody request: QueryRequest, session: jakarta.servlet.http.HttpSession): List<SchoolDataTools.ParentInfo> {
        val schoolId = session.getAttribute("selectedSchoolId") as? java.util.UUID 
            ?: throw IllegalStateException("No school selected")

        val response = chatClient.prompt()
            .system("""
                You are an administrative assistant for a school with ID: $schoolId.
                Your task is to filter recipients (parents or staff) based on the user's criteria.
                - Use the 'queryParents' tool if the user is asking for parents (e.g. 'parents who are owing', 'all parents').
                - Use the 'queryStaff' tool if the user is asking for staff members (e.g. 'all staff', 'staff in department X').
                
                ALWAYS provide the schoolId ($schoolId) to the tool.
                Return only the list of recipients.
            """.trimIndent())
            .user(request.query)
            .tools(schoolDataTools)
            .call()
            .entity(ParentListResponse::class.java)

        return response?.parents ?: emptyList()
    }

    data class QueryRequest(val query: String)
    data class ParentListResponse(val parents: List<SchoolDataTools.ParentInfo>)
}

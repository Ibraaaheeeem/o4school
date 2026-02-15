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
    fun queryParents(@RequestBody request: QueryRequest): List<SchoolDataTools.ParentInfo> {
        val response = chatClient.prompt()
            .system("You are an administrative assistant for a school. Your task is to filter parents based on the user's criteria. Use the 'queryParents' tool to get the results. Return only the list of parents.")
            .user(request.query)
            .functions("queryParents")
            .call()
            .entity(ParentListResponse::class.java)

        return response?.parents ?: emptyList()
    }

    data class QueryRequest(val query: String)
    data class ParentListResponse(val parents: List<SchoolDataTools.ParentInfo>)
}

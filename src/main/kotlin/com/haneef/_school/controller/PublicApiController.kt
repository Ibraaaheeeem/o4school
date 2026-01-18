package com.haneef._school.controller

import com.haneef._school.repository.SchoolRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/public")
class PublicApiController(
    private val schoolRepository: SchoolRepository
) {

    @GetMapping("/validate-school-slug")
    fun validateSchoolSlug(@RequestParam slug: String): ResponseEntity<Map<String, Any?>> {
        val schoolOpt = schoolRepository.findBySlugIgnoreCase(slug)
        
        return if (schoolOpt.isPresent) {
            val school = schoolOpt.get()
            ResponseEntity.ok(mapOf(
                "valid" to true,
                "name" to school.name,
                "address" to listOfNotNull(school.addressLine1, school.city, school.state).joinToString(", ")
            ))
        } else {
            ResponseEntity.ok(mapOf(
                "valid" to false,
                "message" to "Invalid school code"
            ))
        }
    }
}

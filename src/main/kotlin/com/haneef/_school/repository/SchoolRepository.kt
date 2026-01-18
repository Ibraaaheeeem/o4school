package com.haneef._school.repository

import com.haneef._school.entity.School
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface SchoolRepository : JpaRepository<School, UUID> {
    fun findBySlug(slug: String): Optional<School>
    fun findBySlugIgnoreCase(slug: String): Optional<School>
    fun findBySlugAndIsActive(slug: String, isActive: Boolean): Optional<School>
    fun existsBySlug(slug: String): Boolean
    fun existsByAdmissionPrefix(prefix: String): Boolean
    fun findByAdmissionPrefix(prefix: String): Optional<School>
}
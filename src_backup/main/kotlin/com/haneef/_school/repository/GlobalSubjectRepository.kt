package com.haneef._school.repository

import com.haneef._school.entity.GlobalSubject
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface GlobalSubjectRepository : JpaRepository<GlobalSubject, UUID> {
    fun findByIsActiveTrue(): List<GlobalSubject>
    fun findByIsCoreTrueAndIsActiveTrue(): List<GlobalSubject>
    fun findByNameIgnoreCase(name: String): GlobalSubject?
}

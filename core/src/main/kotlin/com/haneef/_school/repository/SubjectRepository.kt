package com.haneef._school.repository

import java.util.UUID
import com.haneef._school.entity.Subject
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository

@Repository
interface SubjectRepository : JpaRepository<Subject, UUID> {
    // Global subject queries (no school filtering)
    fun findByIsActive(isActive: Boolean): List<Subject>
    fun findByIsActive(isActive: Boolean, pageable: Pageable): Page<Subject>
    
    @Query("SELECT s FROM Subject s WHERE s.isActive = :isActive AND " +
           "(LOWER(s.subjectName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(s.subjectCode) LIKE LOWER(CONCAT('%', :query, '%')))")
    fun searchSubjects(@Param("query") query: String, 
                       @Param("isActive") isActive: Boolean, 
                       pageable: Pageable): Page<Subject>

    // Check for duplicate subject names globally
    fun findBySubjectNameIgnoreCaseAndIsActive(subjectName: String, isActive: Boolean): Subject?
}
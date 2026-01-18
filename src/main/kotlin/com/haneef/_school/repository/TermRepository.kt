package com.haneef._school.repository

import java.util.UUID

import com.haneef._school.entity.Term
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface TermRepository : JpaRepository<Term, UUID>, SecureTermRepository {
    
    fun findBySchoolIdAndIsActive(schoolId: UUID, isActive: Boolean): List<Term>
    
    fun findByAcademicSessionIdAndIsActiveOrderByStartDate(academicSessionId: UUID, isActive: Boolean): List<Term>
    
    fun findBySchoolIdAndIsCurrentTermAndIsActive(schoolId: UUID, isCurrentTerm: Boolean, isActive: Boolean): Optional<Term>
    
    fun findByAcademicSessionIdAndIsCurrentTermAndIsActive(academicSessionId: UUID, isCurrentTerm: Boolean, isActive: Boolean): Optional<Term>
    
    @Query("SELECT t FROM Term t WHERE t.academicSession.id = :sessionId AND t.termName = :termName AND t.isActive = :isActive")
    fun findBySessionIdAndTermNameAndIsActive(
        @Param("sessionId") sessionId: UUID,
        @Param("termName") termName: String,
        @Param("isActive") isActive: Boolean
    ): Optional<Term>
    
    fun findByAcademicSessionIdAndTermNameAndIsActive(academicSessionId: UUID, termName: String, isActive: Boolean): Optional<Term>

    fun findByAcademicSessionIdAndTermName(academicSessionId: UUID, termName: String): Optional<Term>
    
    @Query("SELECT COUNT(t) FROM Term t WHERE t.academicSession.id = :sessionId AND t.isActive = :isActive")
    fun countBySessionIdAndIsActive(
        @Param("sessionId") sessionId: UUID,
        @Param("isActive") isActive: Boolean
    ): Long
    
    fun findBySchoolIdAndIsActiveOrderByStartDateAsc(schoolId: UUID, isActive: Boolean): List<Term>
    
    @Query("SELECT t FROM Term t WHERE t.schoolId = :schoolId AND t.isActive = :isActive ORDER BY t.startDate ASC")
    fun findBySchoolIdAndIsActiveOrderByStartDate(@Param("schoolId") schoolId: UUID, @Param("isActive") isActive: Boolean): List<Term>
}
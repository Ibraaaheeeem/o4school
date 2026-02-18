package com.haneef._school.repository

import java.util.UUID

import com.haneef._school.entity.AcademicSession
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface AcademicSessionRepository : JpaRepository<AcademicSession, UUID>, SecureAcademicSessionRepository {
    
    fun findBySchoolIdAndIsActive(schoolId: UUID, isActive: Boolean): List<AcademicSession>
    
    fun findBySchoolIdAndIsCurrentSessionAndIsActive(schoolId: UUID, isCurrentSession: Boolean, isActive: Boolean): AcademicSession?
    
    @Query("SELECT a FROM AcademicSession a WHERE a.schoolId = :schoolId AND a.isActive = :isActive ORDER BY a.sessionYear DESC")
    fun findBySchoolIdAndIsActiveOrderByYearDesc(@Param("schoolId") schoolId: UUID, @Param("isActive") isActive: Boolean): List<AcademicSession>
    
    fun findBySchoolIdAndSessionYearAndIsActive(schoolId: UUID, sessionYear: String, isActive: Boolean): AcademicSession?
    
    fun existsBySchoolIdAndSessionYearAndIsActive(schoolId: UUID, sessionYear: String, isActive: Boolean): Boolean
}
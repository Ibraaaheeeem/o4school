package com.haneef._school.repository

import java.util.UUID

import com.haneef._school.entity.EducationTrack
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface EducationTrackRepository : JpaRepository<EducationTrack, UUID>, SecureEducationTrackRepository {
    
    fun findBySchoolIdAndIsActive(schoolId: UUID, isActive: Boolean): List<EducationTrack>
    
    fun countBySchoolIdAndIsActive(schoolId: UUID, isActive: Boolean): Long
    
    fun findBySchoolIdAndName(schoolId: UUID, name: String): EducationTrack?
    
    fun findByNameAndSchoolIdAndIsActive(name: String, schoolId: UUID, isActive: Boolean): EducationTrack?

    @Query("SELECT t FROM EducationTrack t WHERE t.schoolId = :schoolId AND t.isActive = true ORDER BY t.name")
    fun findAllWithHierarchy(@Param("schoolId") schoolId: UUID): List<EducationTrack>
}
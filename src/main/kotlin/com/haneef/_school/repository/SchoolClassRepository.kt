package com.haneef._school.repository

import java.util.UUID

import com.haneef._school.entity.SchoolClass
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface SchoolClassRepository : JpaRepository<SchoolClass, UUID>, SecureSchoolClassRepository {
    
    fun findBySchoolIdAndIsActive(schoolId: UUID, isActive: Boolean): List<SchoolClass>
    
    fun countBySchoolIdAndIsActive(schoolId: UUID, isActive: Boolean): Long
    
    fun findBySchoolIdAndClassNameAndDepartmentId(schoolId: UUID, className: String, departmentId: UUID): SchoolClass?
    
    fun findByClassNameAndSchoolIdAndIsActive(className: String, schoolId: UUID, isActive: Boolean): SchoolClass?
    
    @Query("SELECT sc FROM SchoolClass sc WHERE sc.track.id = :trackId AND sc.isActive = true")
    fun findByEducationTrackId(@Param("trackId") trackId: UUID): List<SchoolClass>
    
    @Query("SELECT sc FROM SchoolClass sc WHERE sc.department.id = :departmentId AND sc.isActive = true")
    fun findByDepartmentId(@Param("departmentId") departmentId: UUID): List<SchoolClass>
    
    fun findByDepartmentIdAndIsActive(departmentId: UUID, isActive: Boolean): List<SchoolClass>
    
    fun findBySchoolIdAndTrackIdAndIsActive(schoolId: UUID, trackId: UUID, isActive: Boolean): List<SchoolClass>
    
    @Query("SELECT sc FROM SchoolClass sc LEFT JOIN FETCH sc.track WHERE sc.schoolId = :schoolId AND sc.isActive = :isActive")
    fun findBySchoolIdAndIsActiveWithTrack(@Param("schoolId") schoolId: UUID, @Param("isActive") isActive: Boolean): List<SchoolClass>
}
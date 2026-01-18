package com.haneef._school.repository

import java.util.UUID

import com.haneef._school.entity.Department
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface DepartmentRepository : JpaRepository<Department, UUID>, SecureDepartmentRepository {
    
    fun findBySchoolIdAndIsActive(schoolId: UUID, isActive: Boolean): List<Department>
    
    fun countBySchoolIdAndIsActive(schoolId: UUID, isActive: Boolean): Long
    
    fun findBySchoolIdAndNameAndTrackId(schoolId: UUID, name: String, trackId: UUID): Department?
    
    fun findByNameAndSchoolIdAndIsActive(name: String, schoolId: UUID, isActive: Boolean): Department?
    
    fun findByTrackIdAndIsActive(trackId: UUID, isActive: Boolean): List<Department>
}
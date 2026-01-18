package com.haneef._school.repository

import java.util.UUID

import com.haneef._school.entity.Designation
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface DesignationRepository : JpaRepository<Designation, UUID> {
    
    fun findBySchoolIdAndIsActive(schoolId: UUID, isActive: Boolean): List<Designation>
    
    fun findBySchoolId(schoolId: UUID): List<Designation>
    
    @Query("SELECT d FROM Designation d JOIN FETCH d.permissions dp JOIN FETCH dp.permission WHERE d.id = :id")
    fun findByIdWithPermissions(id: UUID): Designation?
    
    fun existsBySchoolIdAndName(schoolId: UUID, name: String): Boolean
}
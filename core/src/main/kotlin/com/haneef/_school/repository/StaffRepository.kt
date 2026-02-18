package com.haneef._school.repository

import java.util.UUID

import com.haneef._school.entity.Staff
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface StaffRepository : JpaRepository<Staff, UUID>, SecureStaffRepository {
    
    fun findBySchoolIdAndIsActive(schoolId: UUID, isActive: Boolean): List<Staff>
    
    fun findBySchoolIdAndIsActive(schoolId: UUID, isActive: Boolean, pageable: Pageable): Page<Staff>
    
    fun countBySchoolIdAndIsActive(schoolId: UUID, isActive: Boolean): Long
    
    @Query("SELECT DISTINCT s.designation FROM Staff s WHERE s.schoolId = :schoolId AND s.isActive = true")
    fun findDistinctDesignationsBySchoolId(@Param("schoolId") schoolId: UUID): List<String>
    
    @Query(value = "SELECT s FROM Staff s WHERE s.schoolId = :schoolId AND s.isActive = :isActive AND " +
           "(CAST(s.user.firstName AS string) ILIKE CONCAT('%', :search, '%') OR " +
           "CAST(s.user.lastName AS string) ILIKE CONCAT('%', :search, '%') OR " +
           "CAST(s.staffId AS string) ILIKE CONCAT('%', :search, '%'))")
    fun findBySchoolIdAndIsActiveAndSearch(
        @Param("schoolId") schoolId: UUID,
        @Param("isActive") isActive: Boolean,
        @Param("search") search: String,
        pageable: Pageable
    ): Page<Staff>
    
    @Query("SELECT s FROM Staff s WHERE s.schoolId = :schoolId AND s.isActive = :isActive AND s.designation = :designation")
    fun findBySchoolIdAndIsActiveAndDesignation(
        @Param("schoolId") schoolId: UUID,
        @Param("isActive") isActive: Boolean,
        @Param("designation") designation: String,
        pageable: Pageable
    ): Page<Staff>
    
    fun findByUserIdAndSchoolId(userId: UUID, schoolId: UUID): Staff?
    
    fun findByStaffIdAndSchoolId(staffId: String, schoolId: UUID): Staff?
    
    @Query("SELECT s FROM Staff s WHERE s.schoolId = :schoolId AND s.isActive = :isActive AND " +
           "(CAST(s.user.firstName AS string) ILIKE CONCAT('%', :search, '%') OR " +
           "CAST(s.user.lastName AS string) ILIKE CONCAT('%', :search, '%') OR " +
           "CAST(s.staffId AS string) ILIKE CONCAT('%', :search, '%'))")
    fun findBySchoolIdAndIsActiveAndUserFullNameContaining(
        @Param("schoolId") schoolId: UUID,
        @Param("isActive") isActive: Boolean,
        @Param("search") search: String,
        pageable: Pageable
    ): Page<Staff>
    
    @Query("SELECT s FROM Staff s WHERE s.schoolId = :schoolId AND s.isActive = :isActive AND " +
           "s.designation = :designation AND " +
           "(CAST(s.user.firstName AS string) ILIKE CONCAT('%', :search, '%') OR " +
           "CAST(s.user.lastName AS string) ILIKE CONCAT('%', :search, '%') OR " +
           "CAST(s.staffId AS string) ILIKE CONCAT('%', :search, '%'))")
    fun findBySchoolIdAndIsActiveAndDesignationAndUserFullNameContaining(
        @Param("schoolId") schoolId: UUID,
        @Param("isActive") isActive: Boolean,
        @Param("designation") designation: String,
        @Param("search") search: String,
        pageable: Pageable
    ): Page<Staff>
    
    @Query("SELECT DISTINCT s FROM Staff s LEFT JOIN FETCH s.user WHERE s.schoolId = :schoolId AND s.isActive = :isActive ORDER BY s.user.firstName")
    fun findBySchoolIdAndIsActiveWithTeacherAssignments(
        @Param("schoolId") schoolId: UUID,
        @Param("isActive") isActive: Boolean
    ): List<Staff>
}
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

    @Query(value = "SELECT s FROM Staff s WHERE s.schoolId = :schoolId AND s.isActive = :isActive AND " +
           "(CAST(s.user.firstName AS string) ILIKE CONCAT('%', :search, '%') OR " +
           "CAST(s.user.lastName AS string) ILIKE CONCAT('%', :search, '%') OR " +
           "CAST(s.staffId AS string) ILIKE CONCAT('%', :search, '%'))")
    fun findBySchoolIdAndIsActiveAndSearch(
        @Param("schoolId") schoolId: UUID,
        @Param("isActive") isActive: Boolean,
        @Param("search") search: String
    ): List<Staff>
    
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
    
    @Query("SELECT DISTINCT s FROM Staff s LEFT JOIN FETCH s.user " +
           "LEFT JOIN s.classTeacherAssignments cta " +
           "LEFT JOIN s.subjectTeacherAssignments sta " +
           "WHERE s.schoolId = :schoolId AND s.isActive = :isActive AND (" +
           "(:hasTrackFilter = false OR cta.schoolClass.track.id IN :trackIds OR sta.schoolClass.track.id IN :trackIds) AND " +
           "(:hasDeptFilter = false OR s.department IN :deptNames OR cta.schoolClass.department.name IN :deptNames OR sta.schoolClass.department.name IN :deptNames) AND " +
           "(:hasClassFilter = false OR cta.schoolClass.id IN :classIds OR sta.schoolClass.id IN :classIds))")
    fun findByFilter(
        @Param("schoolId") schoolId: UUID,
        @Param("isActive") isActive: Boolean,
        @Param("hasTrackFilter") hasTrackFilter: Boolean,
        @Param("trackIds") trackIds: List<UUID>?,
        @Param("hasDeptFilter") hasDeptFilter: Boolean,
        @Param("deptNames") deptNames: List<String>?,
        @Param("hasClassFilter") hasClassFilter: Boolean,
        @Param("classIds") classIds: List<UUID>?
    ): List<Staff>

    @Query("SELECT DISTINCT s FROM Staff s LEFT JOIN FETCH s.user WHERE s.schoolId = :schoolId AND s.isActive = :isActive ORDER BY s.user.firstName")
    fun findBySchoolIdAndIsActiveWithTeacherAssignments(
        @Param("schoolId") schoolId: UUID,
        @Param("isActive") isActive: Boolean
    ): List<Staff>
}
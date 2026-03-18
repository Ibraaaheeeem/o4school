package com.haneef._school.repository

import java.util.UUID

import com.haneef._school.entity.Parent
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ParentRepository : JpaRepository<Parent, UUID>, SecureParentRepository {
    
    fun findBySchoolIdAndIsActive(schoolId: UUID, isActive: Boolean): List<Parent>
    
    fun findBySchoolIdAndIsActive(schoolId: UUID, isActive: Boolean, pageable: Pageable): Page<Parent>
    
    fun countBySchoolIdAndIsActive(schoolId: UUID, isActive: Boolean): Long
    
    @Query("SELECT DISTINCT p FROM Parent p " +
           "LEFT JOIN p.studentRelationships ps " +
           "LEFT JOIN ps.student s " +
           "WHERE p.schoolId = :schoolId AND p.isActive = :isActive AND (" +
           "CAST(p.user.firstName AS string) ILIKE CONCAT('%', :search, '%') OR " +
           "CAST(p.user.lastName AS string) ILIKE CONCAT('%', :search, '%') OR " +
           "CAST(p.user.phoneNumber AS string) ILIKE CONCAT('%', :search, '%') OR " +
           "(ps.isActive = true AND s.isActive = true AND (" +
           "CAST(s.user.firstName AS string) ILIKE CONCAT('%', :search, '%') OR " +
           "CAST(s.user.lastName AS string) ILIKE CONCAT('%', :search, '%') OR " +
           "CAST(s.studentId AS string) ILIKE CONCAT('%', :search, '%') OR " +
           "CAST(s.admissionNumber AS string) ILIKE CONCAT('%', :search, '%'))))")
    fun findBySchoolIdAndIsActiveAndSearch(
        @Param("schoolId") schoolId: UUID,
        @Param("isActive") isActive: Boolean,
        @Param("search") search: String,
        pageable: Pageable
    ): Page<Parent>

    @Query("SELECT DISTINCT p FROM Parent p " +
           "LEFT JOIN p.studentRelationships ps " +
           "LEFT JOIN ps.student s " +
           "WHERE p.schoolId = :schoolId AND p.isActive = :isActive AND (" +
           "CAST(p.user.firstName AS string) ILIKE CONCAT('%', :search, '%') OR " +
           "CAST(p.user.lastName AS string) ILIKE CONCAT('%', :search, '%') OR " +
           "CAST(p.user.phoneNumber AS string) ILIKE CONCAT('%', :search, '%') OR " +
           "(ps.isActive = true AND s.isActive = true AND (" +
           "CAST(s.user.firstName AS string) ILIKE CONCAT('%', :search, '%') OR " +
           "CAST(s.user.lastName AS string) ILIKE CONCAT('%', :search, '%') OR " +
           "CAST(s.studentId AS string) ILIKE CONCAT('%', :search, '%') OR " +
           "CAST(s.admissionNumber AS string) ILIKE CONCAT('%', :search, '%'))))")
    fun findBySchoolIdAndIsActiveAndSearch(
        @Param("schoolId") schoolId: UUID,
        @Param("isActive") isActive: Boolean,
        @Param("search") search: String
    ): List<Parent>
    
    fun findByUserIdAndSchoolId(userId: UUID, schoolId: UUID): Parent?

    fun findByUserId(userId: UUID): List<Parent>
    
    @Query("SELECT p FROM Parent p LEFT JOIN FETCH p.paystackWallet LEFT JOIN FETCH p.squadWallet WHERE p.user.id = :userId")
    fun findByUserIdWithWallet(@Param("userId") userId: UUID): List<Parent>
    
    @Query("SELECT p FROM Parent p LEFT JOIN FETCH p.paystackWallet LEFT JOIN FETCH p.squadWallet LEFT JOIN FETCH p.studentRelationships sr LEFT JOIN FETCH sr.student s LEFT JOIN FETCH s.user WHERE p.schoolId = :schoolId AND p.isActive = :isActive ORDER BY p.user.firstName")
    fun findBySchoolIdAndIsActiveWithRelationships(
        @Param("schoolId") schoolId: UUID,
        @Param("isActive") isActive: Boolean
    ): List<Parent>
    
    @Query("SELECT p FROM Parent p WHERE p.schoolId = :schoolId AND p.isActive = :isActive AND " +
           "(CAST(p.user.firstName AS string) ILIKE CONCAT('%', :search, '%') OR " +
           "CAST(p.user.lastName AS string) ILIKE CONCAT('%', :search, '%') OR " +
           "CAST(p.user.phoneNumber AS string) ILIKE CONCAT('%', :search, '%'))")
    fun findBySchoolIdAndIsActiveAndUserFullNameContaining(
        @Param("schoolId") schoolId: UUID,
        @Param("isActive") isActive: Boolean,
        @Param("search") search: String,
        pageable: Pageable
    ): Page<Parent>

    @Query("SELECT DISTINCT p FROM Parent p LEFT JOIN FETCH p.user " +
           "LEFT JOIN FETCH p.studentRelationships sr " +
           "LEFT JOIN FETCH sr.student s " +
           "LEFT JOIN FETCH s.user " +
           "WHERE p.schoolId = :schoolId AND p.isActive = :isActive AND (" +
           "(:hasClassFilter = false OR EXISTS (SELECT 1 FROM StudentClass sc WHERE sc.student = s AND sc.schoolClass.id IN :classIds AND sc.isActive = true)) AND " +
           "(:hasTrackFilter = false OR EXISTS (SELECT 1 FROM StudentClass sc WHERE sc.student = s AND sc.schoolClass.track.id IN :trackIds AND sc.isActive = true)) AND " +
           "(:hasDeptFilter = false OR EXISTS (SELECT 1 FROM StudentClass sc WHERE sc.student = s AND sc.schoolClass.department.name IN :deptNames AND sc.isActive = true)) AND " +
           "(:studentGender = 'ANY' OR s.user.gender = :studentGender) AND " +
           "(:studentStatus = 'ANY' OR s._isNew = :isNew))")
    fun findByFilter(
        @Param("schoolId") schoolId: UUID,
        @Param("isActive") isActive: Boolean,
        @Param("hasClassFilter") hasClassFilter: Boolean,
        @Param("classIds") classIds: List<UUID>?,
        @Param("hasTrackFilter") hasTrackFilter: Boolean,
        @Param("trackIds") trackIds: List<UUID>?,
        @Param("deptNames") deptNames: List<String>?,
        @Param("hasDeptFilter") hasDeptFilter: Boolean,
        @Param("studentGender") studentGender: String?,
        @Param("studentStatus") studentStatus: String?,
        @Param("isNew") isNew: Boolean
    ): List<Parent>

    fun findByUserEmail(email: String): java.util.Optional<Parent>
}
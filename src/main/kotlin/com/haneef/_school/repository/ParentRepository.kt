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
    
    @Query("SELECT p FROM Parent p WHERE p.schoolId = :schoolId AND p.isActive = :isActive AND " +
           "(CAST(p.user.firstName AS string) ILIKE CONCAT('%', :search, '%') OR " +
           "CAST(p.user.lastName AS string) ILIKE CONCAT('%', :search, '%') OR " +
           "CAST(p.user.phoneNumber AS string) ILIKE CONCAT('%', :search, '%'))")
    fun findBySchoolIdAndIsActiveAndSearch(
        @Param("schoolId") schoolId: UUID,
        @Param("isActive") isActive: Boolean,
        @Param("search") search: String,
        pageable: Pageable
    ): Page<Parent>
    
    fun findByUserIdAndSchoolId(userId: UUID, schoolId: UUID): Parent?

    fun findByUserId(userId: UUID): List<Parent>
    
    @Query("SELECT p FROM Parent p LEFT JOIN FETCH p.wallet WHERE p.user.id = :userId")
    fun findByUserIdWithWallet(@Param("userId") userId: UUID): List<Parent>
    
    @Query("SELECT p FROM Parent p LEFT JOIN FETCH p.wallet LEFT JOIN FETCH p.studentRelationships sr LEFT JOIN FETCH sr.student s LEFT JOIN FETCH s.user WHERE p.schoolId = :schoolId AND p.isActive = :isActive ORDER BY p.user.firstName")
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
}
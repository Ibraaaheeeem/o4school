package com.haneef._school.repository

import java.util.UUID

import com.haneef._school.entity.User
import com.haneef._school.entity.UserSchoolRole
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

@Repository
interface UserSchoolRoleRepository : JpaRepository<UserSchoolRole, UUID> {
    
    @Query("SELECT usr FROM UserSchoolRole usr JOIN FETCH usr.role JOIN FETCH usr.user WHERE usr.user = :user")
    fun findByUser(@Param("user") user: User): List<UserSchoolRole>
    
    @Query("SELECT usr FROM UserSchoolRole usr JOIN FETCH usr.role JOIN FETCH usr.user WHERE usr.user = :user AND usr.schoolId = :schoolId")
    fun findByUserAndSchoolId(@Param("user") user: User, @Param("schoolId") schoolId: UUID): List<UserSchoolRole>

    @Query("SELECT usr FROM UserSchoolRole usr JOIN FETCH usr.role JOIN FETCH usr.user WHERE usr.user = :user AND usr.isActive = :isActive")
    fun findByUserAndIsActive(@Param("user") user: User, @Param("isActive") isActive: Boolean): List<UserSchoolRole>
    
    @Query("SELECT usr FROM UserSchoolRole usr JOIN FETCH usr.role JOIN FETCH usr.user WHERE usr.schoolId = :schoolId AND usr.isActive = :isActive")
    fun findBySchoolIdAndIsActive(@Param("schoolId") schoolId: UUID, @Param("isActive") isActive: Boolean): List<UserSchoolRole>
    
    @Query("SELECT usr FROM UserSchoolRole usr JOIN FETCH usr.role JOIN FETCH usr.user WHERE usr.user = :user AND usr.schoolId = :schoolId AND usr.isActive = :isActive")
    fun findByUserAndSchoolIdAndIsActive(@Param("user") user: User, @Param("schoolId") schoolId: UUID, @Param("isActive") isActive: Boolean): UserSchoolRole?
    
    @Query("SELECT usr FROM UserSchoolRole usr JOIN FETCH usr.role JOIN FETCH usr.user WHERE usr.user.id = :userId AND usr.isActive = true")
    fun findActiveRolesByUserId(@Param("userId") userId: UUID): List<UserSchoolRole>

    fun existsByUserIdAndRoleId(userId: UUID, roleId: UUID): Boolean

    fun existsByUserIdAndSchoolIdAndRoleId(userId: UUID, schoolId: UUID, roleId: UUID): Boolean

    @Query("SELECT usr FROM UserSchoolRole usr JOIN FETCH usr.role JOIN FETCH usr.user WHERE usr.schoolId = :schoolId")
    fun findBySchoolId(@Param("schoolId") schoolId: UUID): List<UserSchoolRole>

    @Query("SELECT usr FROM UserSchoolRole usr JOIN FETCH usr.role JOIN FETCH usr.user WHERE usr.schoolId = :schoolId",
           countQuery = "SELECT count(usr) FROM UserSchoolRole usr WHERE usr.schoolId = :schoolId")
    fun findBySchoolId(@Param("schoolId") schoolId: UUID, pageable: Pageable): Page<UserSchoolRole>

    @Query("SELECT usr FROM UserSchoolRole usr JOIN FETCH usr.role JOIN FETCH usr.user WHERE usr.schoolId = :schoolId AND usr.role.name IN :roleNames",
           countQuery = "SELECT count(usr) FROM UserSchoolRole usr JOIN usr.role r WHERE usr.schoolId = :schoolId AND r.name IN :roleNames")
    fun findBySchoolIdAndRoleNameIn(@Param("schoolId") schoolId: UUID, @Param("roleNames") roleNames: List<String>, pageable: Pageable): Page<UserSchoolRole>

    @Query("SELECT usr FROM UserSchoolRole usr JOIN FETCH usr.role JOIN FETCH usr.user WHERE usr.schoolId = :schoolId AND usr.role.name IN :roleNames")
    fun findBySchoolIdAndRoleNameIn(@Param("schoolId") schoolId: UUID, @Param("roleNames") roleNames: List<String>): List<UserSchoolRole>
}
package com.haneef._school.repository

import java.util.UUID

import com.haneef._school.entity.User
import com.haneef._school.entity.UserStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface UserRepository : JpaRepository<User, UUID> {
    
    fun findByEmail(email: String): Optional<User>
    
    fun findByEmailIgnoreCase(email: String): User?
    
    fun findByPhoneNumber(phoneNumber: String): Optional<User>
    
    @Query("SELECT u FROM User u WHERE u.phoneNumber = :phoneNumber")
    fun findByPhoneNumberForAuth(@Param("phoneNumber") phoneNumber: String): User?
    
    fun findByEmailAndIsActive(email: String, isActive: Boolean): Optional<User>
    
    fun findByPhoneNumberAndIsActive(phoneNumber: String, isActive: Boolean): Optional<User>
    
    fun findByStatus(status: UserStatus): List<User>
    
    fun findByIsActiveAndStatus(isActive: Boolean, status: UserStatus): List<User>
    
    fun existsByEmail(email: String): Boolean
    
    fun existsByPhoneNumber(phoneNumber: String): Boolean
    
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.schoolRoles sr LEFT JOIN FETCH sr.role LEFT JOIN FETCH sr.user LEFT JOIN FETCH u.globalRoles gr LEFT JOIN FETCH gr.role LEFT JOIN FETCH gr.user WHERE u.email = :email")
    fun findByEmailWithRoles(email: String): Optional<User>
    
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.schoolRoles sr LEFT JOIN FETCH sr.role LEFT JOIN FETCH sr.user LEFT JOIN FETCH u.globalRoles gr LEFT JOIN FETCH gr.role LEFT JOIN FETCH gr.user WHERE u.phoneNumber = :phoneNumber")
    fun findByPhoneNumberWithRoles(phoneNumber: String): Optional<User>
}
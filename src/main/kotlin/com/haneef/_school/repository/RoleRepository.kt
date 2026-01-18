package com.haneef._school.repository

import java.util.UUID

import com.haneef._school.entity.Role
import com.haneef._school.entity.RoleType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface RoleRepository : JpaRepository<Role, UUID> {
    
    fun findByName(name: String): Optional<Role>
    
    fun findByRoleType(roleType: RoleType): List<Role>
    
    fun findByIsSystemRole(isSystemRole: Boolean): List<Role>
    
    fun existsByName(name: String): Boolean
}
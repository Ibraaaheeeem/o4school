package com.haneef._school.repository

import java.util.UUID

import com.haneef._school.entity.Permission
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface PermissionRepository : JpaRepository<Permission, UUID> {
    
    fun findByName(name: String): Optional<Permission>
    
    fun findByModule(module: String): List<Permission>
    
    fun findByIsActive(isActive: Boolean): List<Permission>
    
    fun existsByName(name: String): Boolean
}
package com.haneef._school.repository

import java.util.UUID

import com.haneef._school.entity.DesignationPermission
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface DesignationPermissionRepository : JpaRepository<DesignationPermission, UUID> {
    
    fun findByDesignationId(designationId: UUID): List<DesignationPermission>
    
    fun findByDesignationIdAndPermissionName(designationId: UUID, permissionName: String): DesignationPermission?
}
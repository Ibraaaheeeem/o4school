package com.haneef._school.repository

import com.haneef._school.entity.UserGlobalRole
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface UserGlobalRoleRepository : JpaRepository<UserGlobalRole, UUID> {
    fun findByUserId(userId: UUID): List<UserGlobalRole>
    fun existsByUserIdAndRoleId(userId: UUID, roleId: UUID): Boolean
}

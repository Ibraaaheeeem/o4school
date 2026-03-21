package com.haneef._school.service

import java.util.UUID

import com.haneef._school.entity.School
import com.haneef._school.entity.UserSchoolRole
import com.haneef._school.repository.SchoolRepository
import com.haneef._school.repository.UserSchoolRoleRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserSchoolRoleService(
    private val userSchoolRoleRepository: UserSchoolRoleRepository,
    private val schoolRepository: SchoolRepository
) {

    @Transactional(readOnly = true)
    fun getActiveRolesByUserId(userId: UUID): List<UserSchoolRole> {
        return userSchoolRoleRepository.findActiveRolesByUserId(userId)
    }

    @Transactional(readOnly = true)
    fun getActiveRolesByUserIdAndSchoolId(userId: UUID, schoolId: UUID): List<UserSchoolRole> {
        return getActiveRolesByUserId(userId)
            .asSequence()
            .filter { it.schoolId == schoolId }
            .toList()
    }

    @Transactional(readOnly = true)
    fun getUserSchools(userId: UUID): List<UUID> {
        return getActiveRolesByUserId(userId)
            .asSequence()
            .mapNotNull { it.schoolId }
            .distinct()
            .toList()
    }
    
    @Transactional(readOnly = true)
    fun getUserSchoolsWithDetails(userId: UUID): List<School> {
        val schoolIds = getUserSchools(userId).filterNotNull()
        return schoolRepository.findAllById(schoolIds)
    }

    @Transactional(readOnly = true)
    fun hasMultipleSchools(userId: UUID): Boolean {
        val seen = HashSet<UUID>()
        for (role in getActiveRolesByUserId(userId)) {
            val sid = role.schoolId ?: continue
            seen.add(sid)
            if (seen.size > 1) return true
        }
        return false
    }

    @Transactional(readOnly = true)
    fun hasMultipleRolesInSchool(userId: UUID, schoolId: UUID): Boolean {
        var found = 0
        for (role in getActiveRolesByUserId(userId)) {
            if (role.schoolId == schoolId) {
                found++
                if (found > 1) return true
            }
        }
        return false
    }

    @Transactional(readOnly = true)
    fun getUserSchoolRolesGroupedBySchool(userId: UUID): Map<UUID?, List<UserSchoolRole>> {
        return getActiveRolesByUserId(userId).groupBy { it.schoolId }
    }
}
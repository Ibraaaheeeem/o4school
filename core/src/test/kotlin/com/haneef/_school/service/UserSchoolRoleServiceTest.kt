package com.haneef._school.service

import com.haneef._school.entity.Role
import com.haneef._school.entity.School
import com.haneef._school.entity.User
import com.haneef._school.entity.UserSchoolRole
import com.haneef._school.entity.RoleType
import com.haneef._school.repository.SchoolRepository
import com.haneef._school.repository.UserSchoolRoleRepository
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.*

@ExtendWith(MockKExtension::class)
class UserSchoolRoleServiceTest {

    private val repo: UserSchoolRoleRepository = mockk()
    private val schoolRepo: SchoolRepository = mockk()
    private val svc = UserSchoolRoleService(repo, schoolRepo)

    private fun makeUserSchoolRole(schoolId: UUID?): UserSchoolRole {
        return UserSchoolRole(
            user = User(),
            schoolId = schoolId,
            role = Role(name = "r", roleType = RoleType.STAFF)
        )
    }

    @Test
    fun `getActiveRolesByUserId delegates to repository`() {
        val uid = UUID.randomUUID()
        val roles = listOf(makeUserSchoolRole(UUID.randomUUID()))
        every { repo.findActiveRolesByUserId(uid) } returns roles

        val res = svc.getActiveRolesByUserId(uid)
        assertEquals(roles, res)
    }

    @Test
    fun `getActiveRolesByUserIdAndSchoolId filters by school id`() {
        val uid = UUID.randomUUID()
        val sid1 = UUID.randomUUID()
        val sid2 = UUID.randomUUID()
        val r1 = makeUserSchoolRole(sid1)
        val r2 = makeUserSchoolRole(sid2)
        val r3 = makeUserSchoolRole(sid1)
        every { repo.findActiveRolesByUserId(uid) } returns listOf(r1, r2, r3)

        val res = svc.getActiveRolesByUserIdAndSchoolId(uid, sid1)
        assertEquals(2, res.size)
        assertTrue(res.all { it.schoolId == sid1 })
    }

    @Test
    fun `getUserSchools returns distinct non-null ids`() {
        val uid = UUID.randomUUID()
        val sid1 = UUID.randomUUID()
        val r1 = makeUserSchoolRole(sid1)
        val r2 = makeUserSchoolRole(null)
        val r3 = makeUserSchoolRole(sid1)
        every { repo.findActiveRolesByUserId(uid) } returns listOf(r1, r2, r3)

        val res = svc.getUserSchools(uid)
        assertEquals(1, res.size)
        assertEquals(sid1, res.first())
    }

    @Test
    fun `getUserSchoolsWithDetails fetches schools for non-null ids`() {
        val uid = UUID.randomUUID()
        val sid1 = UUID.randomUUID()
        val r1 = makeUserSchoolRole(sid1)
        every { repo.findActiveRolesByUserId(uid) } returns listOf(r1)

        val school = School(name = "S", slug = "s1", email = "x@e", phone = null)
        every { schoolRepo.findAllById(listOf(sid1)) } returns listOf(school)

        val res = svc.getUserSchoolsWithDetails(uid)
        assertEquals(1, res.size)
        assertEquals(school, res.first())
    }

    @Test
    fun `hasMultipleSchools returns true when user has roles in multiple schools`() {
        val uid = UUID.randomUUID()
        val sid1 = UUID.randomUUID()
        val sid2 = UUID.randomUUID()
        val roles = listOf(makeUserSchoolRole(sid1), makeUserSchoolRole(sid2))
        every { repo.findActiveRolesByUserId(uid) } returns roles

        assertTrue(svc.hasMultipleSchools(uid))
    }

    @Test
    fun `hasMultipleSchools returns false for single school`() {
        val uid = UUID.randomUUID()
        val sid1 = UUID.randomUUID()
        every { repo.findActiveRolesByUserId(uid) } returns listOf(makeUserSchoolRole(sid1))

        assertFalse(svc.hasMultipleSchools(uid))
    }

    @Test
    fun `hasMultipleRolesInSchool returns true when more than one role exists for school`() {
        val uid = UUID.randomUUID()
        val sid = UUID.randomUUID()
        every { repo.findActiveRolesByUserId(uid) } returns listOf(makeUserSchoolRole(sid), makeUserSchoolRole(sid))

        assertTrue(svc.hasMultipleRolesInSchool(uid, sid))
    }

    @Test
    fun `hasMultipleRolesInSchool returns false when single or none`() {
        val uid = UUID.randomUUID()
        val sid = UUID.randomUUID()
        every { repo.findActiveRolesByUserId(uid) } returns listOf(makeUserSchoolRole(sid))

        assertFalse(svc.hasMultipleRolesInSchool(uid, sid))
    }

    @Test
    fun `getUserSchoolRolesGroupedBySchool groups including null key`() {
        val uid = UUID.randomUUID()
        val sid1 = UUID.randomUUID()
        val r1 = makeUserSchoolRole(sid1)
        val r2 = makeUserSchoolRole(null)
        every { repo.findActiveRolesByUserId(uid) } returns listOf(r1, r2)

        val grouped = svc.getUserSchoolRolesGroupedBySchool(uid)
        assertTrue(grouped.containsKey(sid1))
        assertTrue(grouped.containsKey(null))
        assertEquals(1, grouped[sid1]?.size)
        assertEquals(1, grouped[null]?.size)
    }
}

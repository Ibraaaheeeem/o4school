package com.haneef._school.security

import com.haneef._school.entity.Role
import com.haneef._school.entity.RoleType
import com.haneef._school.entity.User
import com.haneef._school.entity.UserSchoolRole
import com.haneef._school.entity.UserStatus
import com.haneef._school.service.CustomUserDetails
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.test.context.support.WithSecurityContextFactory
import java.util.UUID

class WithMockCustomUserSecurityContextFactory : WithSecurityContextFactory<WithMockCustomUser> {
    override fun createSecurityContext(annotation: WithMockCustomUser): SecurityContext {
        val context = SecurityContextHolder.createEmptyContext()

        val user = User(
            email = annotation.username,
            firstName = annotation.name.split(" ").firstOrNull() ?: "Test",
            lastName = annotation.name.split(" ").lastOrNull() ?: "User",
            phoneNumber = "1234567890",
            passwordHash = "password"
        )
        user.id = UUID.randomUUID()
        user.status = UserStatus.ACTIVE
        user.isVerified = true
        user.emailVerified = true

        val schoolId = if (annotation.schoolId.isNotEmpty()) UUID.fromString(annotation.schoolId) else UUID.randomUUID()

        val schoolRoles = annotation.roles.map { roleName ->
            val role = Role(name = roleName, roleType = RoleType.valueOf(roleName), description = roleName)
            UserSchoolRole(
                user = user,
                role = role,
                schoolId = schoolId
            )
        }.toMutableSet()
        
        user.schoolRoles = schoolRoles

        val principal = CustomUserDetails(user)
        val auth = UsernamePasswordAuthenticationToken(principal, "password", principal.authorities)
        context.authentication = auth
        return context
    }
}

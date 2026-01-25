package com.haneef._school.security

import org.springframework.security.test.context.support.WithSecurityContext
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

@Retention(RetentionPolicy.RUNTIME)
@WithSecurityContext(factory = WithMockCustomUserSecurityContextFactory::class)
annotation class WithMockCustomUser(
    val username: String = "user",
    val name: String = "Test User",
    val roles: Array<String> = ["USER"],
    val schoolId: String = ""
)

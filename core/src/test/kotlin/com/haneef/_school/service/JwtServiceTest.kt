package com.haneef._school.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.core.userdetails.User
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Date

class JwtServiceTest {

    private lateinit var jwtService: JwtService

    @BeforeEach
    fun setUp() {
        jwtService = JwtService()
        setField("secret", "12345678901234567890123456789012")
        setField("clock", Clock.fixed(Instant.parse("2026-03-23T10:15:30Z"), ZoneOffset.UTC))
        jwtService.validateConfiguration()
    }

    @Test
    fun `validateConfiguration throws when secret is blank`() {
        val service = JwtService()
        setField(service, "secret", "   ")

        assertThrows(IllegalArgumentException::class.java) {
            service.validateConfiguration()
        }
    }

    @Test
    fun `validateConfiguration throws when secret is shorter than 32 bytes`() {
        val service = JwtService()
        setField(service, "secret", "short-secret")

        assertThrows(IllegalArgumentException::class.java) {
            service.validateConfiguration()
        }
    }

    @Test
    fun `generateToken uses standard expiration for non-admin users`() {
        val user = User.withUsername("student@example.com")
            .password("password")
            .roles("STUDENT")
            .build()

        val token = jwtService.generateToken(user)
        val issuedAt = jwtService.extractClaim(token, io.jsonwebtoken.Claims::getIssuedAt)
        val expiration = jwtService.extractClaim(token, io.jsonwebtoken.Claims::getExpiration)

        assertEquals(Date.from(Instant.parse("2026-03-23T10:15:30Z")), issuedAt)
        assertEquals(259_200_000L, expiration.time - issuedAt.time)
    }

    @Test
    fun `generateToken uses admin expiration for admin users`() {
        val user = User.withUsername("admin@example.com")
            .password("password")
            .roles("ADMIN")
            .build()

        val token = jwtService.generateToken(user)
        val issuedAt = jwtService.extractClaim(token, io.jsonwebtoken.Claims::getIssuedAt)
        val expiration = jwtService.extractClaim(token, io.jsonwebtoken.Claims::getExpiration)

        assertEquals(86_400_000L, expiration.time - issuedAt.time)
    }

    @Test
    fun `isTokenValid returns true for matching user and generated token`() {
        val user = User.withUsername("teacher@example.com")
            .password("password")
            .roles("TEACHER")
            .build()

        val token = jwtService.generateToken(user)

        assertTrue(jwtService.isTokenValid(token, user))
    }

    @Test
    fun `isTokenValid returns false for malformed token instead of throwing`() {
        val user = User.withUsername("teacher@example.com")
            .password("password")
            .roles("TEACHER")
            .build()

        assertFalse(jwtService.isTokenValid("not-a-jwt", user))
    }

    @Test
    fun `extractUsername accepts token with surrounding whitespace`() {
        val user = User.withUsername("parent@example.com")
            .password("password")
            .roles("PARENT")
            .build()

        val token = jwtService.generateToken(user)

        assertEquals("parent@example.com", jwtService.extractUsername("  $token  \n"))
    }

    private fun setField(name: String, value: Any) {
        setField(jwtService, name, value)
    }

    private fun setField(target: Any, name: String, value: Any) {
        val field = target.javaClass.getDeclaredField(name)
        field.isAccessible = true
        field.set(target, value)
    }
}

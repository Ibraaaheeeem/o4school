@file:Suppress("DEPRECATION")
package com.haneef._school.service

import io.jsonwebtoken.Claims
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Service
import java.time.Clock
import java.util.*
import javax.crypto.SecretKey

@Service
class JwtService {

    companion object {
        private val logger = LoggerFactory.getLogger(JwtService::class.java)
        private const val ADMIN_TOKEN_EXPIRATION_MILLIS = 86_400_000L
        private const val STANDARD_TOKEN_EXPIRATION_MILLIS = 259_200_000L
        private val PRIVILEGED_ROLES = setOf("ROLE_ADMIN", "ROLE_SYSTEM_ADMIN", "ROLE_SCHOOL_ADMIN")
    }

    @Value("\${jwt.secret}")
    private lateinit var secret: String

    private var clock: Clock = Clock.systemUTC()

    @PostConstruct
    fun validateConfiguration() {
        require(secret.isNotBlank()) { "jwt.secret must not be blank" }
        require(secret.toByteArray(Charsets.UTF_8).size >= 32) {
            "jwt.secret must be at least 32 bytes for HS256"
        }
    }


    private fun getSigningKey(): SecretKey {
        val keyBytes = secret.toByteArray(Charsets.UTF_8)
        return Keys.hmacShaKeyFor(keyBytes)
    }

    fun extractUsername(token: String): String {
        return extractClaim(token, Claims::getSubject)
    }

    fun <T> extractClaim(token: String, claimsResolver: (Claims) -> T): T {
        val claims = extractAllClaims(token)
        return claimsResolver(claims)
    }

    fun generateToken(userDetails: UserDetails): String {
        return generateToken(HashMap(), userDetails)
    }

    fun generateToken(
        extraClaims: Map<String, Any>,
        userDetails: UserDetails
    ): String {
        val expiration = calculateExpiration(userDetails)
        return buildToken(extraClaims, userDetails, expiration)
    }

    private fun calculateExpiration(userDetails: UserDetails): Long {
        val isAdmin = userDetails.authorities.any {
            it.authority in PRIVILEGED_ROLES
        }

        return if (isAdmin) {
            ADMIN_TOKEN_EXPIRATION_MILLIS
        } else {
            STANDARD_TOKEN_EXPIRATION_MILLIS
        }
    }

    private fun buildToken(
        extraClaims: Map<String, Any>,
        userDetails: UserDetails,
        expiration: Long
    ): String {
        val issuedAt = now()
        return Jwts
            .builder()
            .claims(extraClaims)
            .subject(userDetails.username)
            .issuedAt(issuedAt)
            .expiration(Date(issuedAt.time + expiration))
            .signWith(getSigningKey())
            .compact()
    }

    fun isTokenValid(token: String, userDetails: UserDetails): Boolean {
        return try {
            val username = extractUsername(token)
            (username == userDetails.username) && !isTokenExpired(token)
        } catch (e: JwtException) {
            logger.debug("JWT validation failed for user {}", userDetails.username, e)
            false
        } catch (e: IllegalArgumentException) {
            logger.debug("JWT validation failed for user {}", userDetails.username, e)
            false
        }
    }

    private fun isTokenExpired(token: String): Boolean {
        return extractExpiration(token).before(Date())
    }

    private fun extractExpiration(token: String): Date {
        return extractClaim(token, Claims::getExpiration)
    }

    private fun extractAllClaims(token: String): Claims {
        val normalizedToken = token.trim()
        require(normalizedToken.isNotEmpty()) { "JWT token must not be blank" }

        return Jwts
            .parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(normalizedToken)
            .payload
    }

    private fun now(): Date = Date.from(clock.instant())
}
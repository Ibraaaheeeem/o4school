package com.haneef._school.service

import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

@Service
class RateLimitingService {

    private data class BucketEntry(
        val bucket: Bucket,
        @Volatile var lastAccessEpochMillis: Long
    )

    private val buckets = ConcurrentHashMap<String, BucketEntry>()
    private val maxBuckets = 10_000
    private val bucketTtlMillis = Duration.ofHours(6).toMillis()

    fun resolveBucket(key: String): Bucket {
        val now = System.currentTimeMillis()
        cleanupIfNeeded(now)
        val entry = buckets.computeIfAbsent(key) { _ ->
            BucketEntry(newBucket(), now)
        }
        entry.lastAccessEpochMillis = now
        return entry.bucket
    }

    private fun newBucket(): Bucket {
        val limit = Bandwidth.builder()
            .capacity(10)
            .refillGreedy(10, Duration.ofMinutes(1))
            .build()
        return Bucket.builder()
            .addLimit(limit)
            .build()
    }
    
    fun resolveLoginBucket(key: String): Bucket {
        val now = System.currentTimeMillis()
        cleanupIfNeeded(now)
        val entry = buckets.computeIfAbsent("login_$key") { _ ->
            val limit = Bandwidth.builder()
                .capacity(5)
                .refillGreedy(5, Duration.ofMinutes(1))
                .build()
            BucketEntry(
                Bucket.builder()
                    .addLimit(limit)
                    .build(),
                now
            )
        }
        entry.lastAccessEpochMillis = now
        return entry.bucket
    }
    
    fun resolveRegistrationBucket(key: String): Bucket {
        val now = System.currentTimeMillis()
        cleanupIfNeeded(now)
        val entry = buckets.computeIfAbsent("register_$key") { _ ->
            val limit = Bandwidth.builder()
                .capacity(3)
                .refillGreedy(3, Duration.ofHours(1))
                .build()
            BucketEntry(
                Bucket.builder()
                    .addLimit(limit)
                    .build(),
                now
            )
        }
        entry.lastAccessEpochMillis = now
        return entry.bucket
    }

    fun resolveClientKey(request: HttpServletRequest): String {
        val forwardedFor = request.getHeader("X-Forwarded-For")
        if (!forwardedFor.isNullOrBlank()) {
            val first = forwardedFor.split(',').firstOrNull()?.trim()
            if (!first.isNullOrBlank()) return first.lowercase()
        }

        val realIp = request.getHeader("X-Real-IP")?.trim()
        if (!realIp.isNullOrBlank()) return realIp.lowercase()

        return (request.remoteAddr ?: "unknown").trim().lowercase()
    }

    fun getFormattedWaitTime(bucket: Bucket): String {
        val probe = bucket.estimateAbilityToConsume(1)
        if (probe.canBeConsumed()) return "0 seconds"

        val nanos = probe.nanosToWaitForRefill
        val seconds = ((nanos + 999_999_999L) / 1_000_000_000L).coerceAtLeast(1L)
        return if (seconds >= 60) {
            val minutes = seconds / 60
            val remainingSeconds = seconds % 60
            if (remainingSeconds > 0) "$minutes minutes and $remainingSeconds seconds" else "$minutes minutes"
        } else {
            "$seconds seconds"
        }
    }

    private fun cleanupIfNeeded(now: Long) {
        if (buckets.size <= maxBuckets) {
            return
        }

        val expiryCutoff = now - bucketTtlMillis
        buckets.entries.removeIf { it.value.lastAccessEpochMillis < expiryCutoff }

        if (buckets.size > maxBuckets) {
            val overflow = buckets.size - maxBuckets
            val oldestKeys = buckets.entries
                .asSequence()
                .sortedBy { it.value.lastAccessEpochMillis }
                .take(overflow)
                .map { it.key }
                .toList()
            oldestKeys.forEach { buckets.remove(it) }
        }
    }
}

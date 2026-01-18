package com.haneef._school.service

import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import io.github.bucket4j.Refill
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

@Service
class RateLimitingService {

    private val buckets = ConcurrentHashMap<String, Bucket>()

    fun resolveBucket(key: String): Bucket {
        return buckets.computeIfAbsent(key) { _ ->
            newBucket()
        }
    }

    private fun newBucket(): Bucket {
        // Allow 10 requests per minute
        val limit = Bandwidth.classic(10, Refill.greedy(10, Duration.ofMinutes(1)))
        return Bucket.builder()
            .addLimit(limit)
            .build()
    }
    
    fun resolveLoginBucket(key: String): Bucket {
         return buckets.computeIfAbsent("login_$key") { _ ->
            // Allow 5 login attempts per minute
            val limit = Bandwidth.classic(5, Refill.greedy(5, Duration.ofMinutes(1)))
            Bucket.builder()
                .addLimit(limit)
                .build()
        }
    }
    
    fun resolveRegistrationBucket(key: String): Bucket {
         return buckets.computeIfAbsent("register_$key") { _ ->
            // Allow 3 registration attempts per hour per IP
            val limit = Bandwidth.classic(3, Refill.greedy(3, Duration.ofHours(1)))
            Bucket.builder()
                .addLimit(limit)
                .build()
        }
    }

    fun getFormattedWaitTime(bucket: Bucket): String {
        val probe = bucket.estimateAbilityToConsume(1)
        if (probe.canBeConsumed()) return "0 seconds"
        
        val seconds = java.util.concurrent.TimeUnit.NANOSECONDS.toSeconds(probe.nanosToWaitForRefill)
        return if (seconds >= 60) {
            val minutes = seconds / 60
            val remainingSeconds = seconds % 60
            if (remainingSeconds > 0) "$minutes minutes and $remainingSeconds seconds" else "$minutes minutes"
        } else {
            "$seconds seconds"
        }
    }
}

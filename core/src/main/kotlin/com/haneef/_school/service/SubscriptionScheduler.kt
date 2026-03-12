package com.haneef._school.service

import com.haneef._school.entity.SubscriptionStatus
import com.haneef._school.repository.SchoolSubscriptionRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class SubscriptionScheduler(
    private val subscriptionRepository: SchoolSubscriptionRepository
) {
    private val log = LoggerFactory.getLogger(SubscriptionScheduler::class.java)

    /**
     * Daily job that checks for expired subscriptions and updates their status.
     * Runs at 1:00 AM server time every day.
     */
    @Scheduled(cron = "0 0 1 * * ?")
    @Transactional
    fun checkAndExpireSubscriptions() {
        log.info("Starting daily subscription expiration check...")
        val now = LocalDateTime.now()
        
        // Find all active subscriptions where the valid_until date has passed
        val expiredSubscriptions = subscriptionRepository.findAll().filter {
            it.subscriptionStatus == SubscriptionStatus.ACTIVE &&
            it.validUntil != null &&
            it.validUntil!!.isBefore(now)
        }
        
        var count = 0
        for (sub in expiredSubscriptions) {
            sub.subscriptionStatus = SubscriptionStatus.EXPIRED
            sub.lastUpdated = now
            subscriptionRepository.save(sub)
            count++
            // Ideally notify the school admin via email/SMS here...
        }
        
        log.info("Subscription expiration check complete. Expired $count subscriptions.")
    }
}

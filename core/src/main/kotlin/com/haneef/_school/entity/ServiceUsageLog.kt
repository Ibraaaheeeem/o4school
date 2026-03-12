package com.haneef._school.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "service_usage_logs",
    indexes = [
        Index(columnList = "school_id", name = "idx_usage_log_school"),
        Index(columnList = "user_id", name = "idx_usage_log_user"),
        Index(columnList = "service_type", name = "idx_usage_log_service")
    ]
)
class ServiceUsageLog(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_id", nullable = false)
    var school: School,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @Enumerated(EnumType.STRING)
    @Column(name = "service_type", nullable = false)
    var serviceType: ServiceFeature,

    @Column(nullable = false)
    var amount: Int = 1,

    @Column(columnDefinition = "TEXT")
    var description: String? = null,

    @Column(nullable = false)
    var timestamp: LocalDateTime = LocalDateTime.now()
) : BaseEntity() {

    constructor() : this(
        school = School(),
        user = User(),
        serviceType = ServiceFeature.AI_TOKENS
    )
}

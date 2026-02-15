package com.haneef._school.entity

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "whatsapp_templates")
class WhatsAppTemplate(
    @Column(name = "template_id", nullable = false, unique = true)
    var templateId: String,

    @Column(name = "template_name", nullable = false)
    var templateName: String,

    @Column(nullable = false)
    var language: String,

    @Column(nullable = false)
    var category: String,

    @Column(name = "parameter_count", nullable = false)
    var parameterCount: Int = 0,

    @Column(name = "parameter_mapping", columnDefinition = "TEXT")
    var parameterMapping: String? = null,

    @Column(name = "components_json", columnDefinition = "TEXT")
    var componentsJson: String? = null,

    @Column(nullable = false)
    var status: String = "PENDING",

    @Column(name = "last_synced_at")
    var lastSyncedAt: LocalDateTime = LocalDateTime.now()
) : TenantAwareEntity()

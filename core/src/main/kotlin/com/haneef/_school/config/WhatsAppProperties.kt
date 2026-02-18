package com.haneef._school.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "whatsapp.meta")
class WhatsAppProperties {
    var accessToken: String? = null
    
    var phoneNumberId: String? = null
    var businessAccountId: String? = null
    var apiVersion: String = "v17.0"
    var verifyToken: String? = null
}

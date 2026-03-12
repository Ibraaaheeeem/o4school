package com.haneef._school.entity

enum class ServiceFeature(val description: String) {
    FEE_COLLECTION("Fee Collection Modules"),
    WHATSAPP_MESSAGING("WhatsApp Messaging"),
    SMS_MESSAGING("SMS Messaging"),
    AI_TOKENS("AI Assistant Tokens");
    
    companion object {
        fun fromString(value: String): ServiceFeature? {
            return entries.find { it.name.equals(value, ignoreCase = true) }
        }
    }
}

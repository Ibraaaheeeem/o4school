package com.haneef._school.config

import jakarta.annotation.PostConstruct
import org.springframework.context.annotation.Configuration

/**
 * Configuration to set Tomcat system properties for file uploads
 * This must be set before Tomcat initializes
 */
@Configuration
class TomcatPropertiesConfig {

    @PostConstruct
    fun configureTomcatProperties() {
        // Set Tomcat properties for multipart handling
        System.setProperty("org.apache.tomcat.util.http.fileupload.impl.FileCountMax", "100")
        System.setProperty("org.apache.catalina.connector.MAX_PARAMETER_COUNT", "10000")
        
        println("✓ Tomcat multipart properties configured")
        println("  - FileCountMax: 100")
        println("  - MaxParameterCount: 10000")
    }
}

package com.haneef._school.config

import org.springframework.boot.SpringApplication
import org.springframework.boot.env.EnvironmentPostProcessor
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource
import org.slf4j.LoggerFactory

/**
 * Load environment variables into Spring Boot configuration at startup.
 * Ensures environment variables like DEEPSEEK_API_KEY are properly resolved
 * for property replacement in application.properties files.
 */
class EnvironmentVariablePostProcessor : EnvironmentPostProcessor {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun postProcessEnvironment(
        environment: ConfigurableEnvironment,
        application: SpringApplication
    ) {
        val envProperties = mutableMapOf<String, Any>()
        
        // Read all system environment variables
        System.getenv().forEach { (key, value) ->
            envProperties[key] = value
        }
        
        // Add all env vars as a property source with high priority
        if (envProperties.isNotEmpty()) {
            val propertySource = MapPropertySource("systemEnvironment", envProperties)
            // Add at the beginning so env vars take precedence
            environment.propertySources.addFirst(propertySource)
            logger.info("Loaded {} environment variables into Spring configuration", envProperties.size)
        }
    }
}

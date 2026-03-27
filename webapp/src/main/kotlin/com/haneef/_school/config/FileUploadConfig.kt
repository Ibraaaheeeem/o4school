package com.haneef._school.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.nio.file.Paths

@Configuration
class FileUploadConfig : WebMvcConfigurer {

    @Value("\${app.upload.dir:uploads}")
    private lateinit var uploadDir: String

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        val normalizedUploadPath = Paths.get(uploadDir).toAbsolutePath().normalize().toString()
        registry.addResourceHandler("/uploads/**")
            .addResourceLocations("file:$normalizedUploadPath/")
            .setCachePeriod(3600) // Cache for 1 hour
    }
}
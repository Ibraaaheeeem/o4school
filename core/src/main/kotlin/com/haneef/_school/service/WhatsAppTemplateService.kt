package com.haneef._school.service

import com.haneef._school.config.WhatsAppProperties
import com.haneef._school.entity.WhatsAppTemplate
import com.haneef._school.repository.WhatsAppTemplateRepository
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.time.LocalDateTime
import java.util.*

@Service
class WhatsAppTemplateService(
    private val templateRepository: WhatsAppTemplateRepository,
    private val properties: WhatsAppProperties
) {
    private val logger = LoggerFactory.getLogger(WhatsAppTemplateService::class.java)
    private val restTemplate = RestTemplate()
    private val objectMapper = jacksonObjectMapper()
    
    fun syncTemplates(schoolId: UUID): Boolean {
        val businessAccountId = properties.businessAccountId
        val accessToken = properties.accessToken

        if (businessAccountId.isNullOrBlank() || accessToken.isNullOrBlank()) {
            logger.error("WhatsApp Meta properties missing for sync")
            return false
        }

        val url = "https://graph.facebook.com/${properties.apiVersion}/$businessAccountId/message_templates"
        
        val headers = HttpHeaders()
        val tokenPrefix = if (accessToken.length > 10) accessToken.substring(0, 10) else "***"
        logger.info("Syncing WhatsApp templates using token: $tokenPrefix... (length: ${accessToken.length})")
        headers.setBearerAuth(accessToken)
        headers.contentType = MediaType.APPLICATION_JSON
        
        val entity = HttpEntity<Unit>(headers)

        return try {
            val response = restTemplate.exchange(url, HttpMethod.GET, entity, Map::class.java)
            val body = response.body as? Map<*, *>
            val data = body?.get("data") as? List<*> ?: emptyList<Any>()

            data.forEach { item ->
                val templateMap = item as? Map<*, *> ?: return@forEach
                updateLocalTemplate(templateMap, schoolId)
            }
            true
        } catch (e: Exception) {
            logger.error("Failed to sync WhatsApp templates: ${e.message}")
            false
        }
    }

    private fun updateLocalTemplate(metaTemplate: Map<*, *>, schoolId: UUID) {
        val metaId = metaTemplate["id"] as? String ?: return
        val name = metaTemplate["name"] as? String ?: return
        val language = metaTemplate["language"] as? String ?: "en_US"
        val category = metaTemplate["category"] as? String ?: "UTILITY"
        val status = metaTemplate["status"] as? String ?: "PENDING"
        
        // Store components as JSON
        val components = metaTemplate["components"] as? List<*> ?: emptyList<Any>()
        val componentsJson = objectMapper.writeValueAsString(components)
        
        var parameterCount = 0
        val uniquePlaceholders = mutableSetOf<String>()
        
        components.forEach { comp ->
            val compMap = comp as? Map<String, Any> ?: return@forEach
            val text = compMap["text"] as? String ?: ""
            val regex = Regex("\\{\\{([a-zA-Z0-9_]+)}}")
            val matches = regex.findAll(text)
            
            matches.forEach { uniquePlaceholders.add(it.groupValues[1]) }
            
            // For numbered system, keep track of max digit found
            val maxParam = matches.mapNotNull { 
                it.groupValues[1].filter { char -> char.isDigit() }.toIntOrNull() 
            }.maxOrNull() ?: 0
            
            if (maxParam > parameterCount) {
                parameterCount = maxParam
            }
        }
        
        // If no numbered placeholders were found but we have unique named ones, use the set size
        if (parameterCount == 0 && uniquePlaceholders.isNotEmpty()) {
            parameterCount = uniquePlaceholders.size
        }

        val existing = templateRepository.findByTemplateId(metaId)
        if (existing.isPresent) {
            val template = existing.get()
            template.templateName = name
            template.language = language
            template.category = category
            template.status = status
            template.parameterCount = parameterCount
            template.componentsJson = componentsJson
            template.lastSyncedAt = LocalDateTime.now()
            templateRepository.save(template)
        } else {
            val newTemplate = WhatsAppTemplate(
                templateId = metaId,
                templateName = name,
                language = language,
                category = category,
                parameterCount = parameterCount,
                componentsJson = componentsJson,
                status = status
            )
            newTemplate.schoolId = schoolId
            templateRepository.save(newTemplate)
        }
    }

    fun createMetaTemplate(
        schoolId: UUID,
        name: String,
        category: String,
        language: String,
        components: List<Map<String, Any>>,
        mapping: String? = null
    ): Boolean {
        val businessAccountId = properties.businessAccountId
        val accessToken = properties.accessToken

        if (businessAccountId.isNullOrBlank() || accessToken.isNullOrBlank()) return false

        val url = "https://graph.facebook.com/${properties.apiVersion}/$businessAccountId/message_templates"
        
        val headers = HttpHeaders()
        headers.setBearerAuth(accessToken)
        headers.contentType = MediaType.APPLICATION_JSON
        
        val body = mapOf(
            "name" to name.lowercase().replace(" ", "_"),
            "category" to category,
            "language" to language,
            "components" to components
        )

        val entity = HttpEntity(body, headers)

        return try {
            val response = restTemplate.postForEntity(url, entity, Map::class.java)
            if (response.statusCode.is2xxSuccessful) {
                syncTemplates(schoolId)
                
                // If mapping provided, find the new template and apply it
                if (!mapping.isNullOrBlank()) {
                    val bodyMap = response.body as? Map<*, *>
                    val metaId = bodyMap?.get("id") as? String
                    if (metaId != null) {
                        templateRepository.findByTemplateId(metaId).ifPresent {
                            it.parameterMapping = mapping
                            templateRepository.save(it)
                        }
                    }
                }
                true
            } else {
                logger.error("Failed to create WhatsApp template: ${response.body}")
                false
            }
        } catch (e: Exception) {
            logger.error("Error creating WhatsApp template: ${e.message}")
            false
        }
    }

    fun deleteMetaTemplate(schoolId: UUID, templateId: UUID): Boolean {
        val template = templateRepository.findById(templateId).orElseThrow { RuntimeException("Template not found") }
        val businessAccountId = properties.businessAccountId
        val accessToken = properties.accessToken

        if (businessAccountId.isNullOrBlank() || accessToken.isNullOrBlank()) return false

        // Meta requires template name for deletion
        val url = "https://graph.facebook.com/${properties.apiVersion}/$businessAccountId/message_templates?name=${template.templateName}"
        
        val headers = HttpHeaders()
        headers.setBearerAuth(accessToken)
        
        val entity = HttpEntity<Unit>(headers)

        return try {
            val response = restTemplate.exchange(url, HttpMethod.DELETE, entity, Map::class.java)
            if (response.statusCode.is2xxSuccessful) {
                templateRepository.delete(template)
                true
            } else {
                logger.error("Failed to delete WhatsApp template: ${response.body}")
                false
            }
        } catch (e: Exception) {
            logger.error("Error deleting WhatsApp template: ${e.message}")
            false
        }
    }

    fun getAllTemplates(schoolId: UUID): List<WhatsAppTemplate> {
        return templateRepository.findBySchoolId(schoolId)
    }

    fun updateParameterMapping(templateId: UUID, mapping: String) {
        val template = templateRepository.findById(templateId).orElseThrow { RuntimeException("Template not found") }
        template.parameterMapping = mapping
        templateRepository.save(template)
    }

    fun getBroadcastTemplates(schoolId: UUID): List<WhatsAppTemplate> {
        return templateRepository.findBySchoolIdAndIsForBroadcast(schoolId, true)
    }

    fun markSelectedTemplates(schoolId: UUID, names: List<String>) {
        val templates = templateRepository.findBySchoolId(schoolId)
        templates.forEach { template ->
            template.isForBroadcast = names.contains(template.templateName)
            templateRepository.save(template)
        }
    }

    fun getTemplate(id: UUID): WhatsAppTemplate {
        return templateRepository.findById(id).orElseThrow { RuntimeException("Template not found") }
    }

    fun markTemplatesForBroadcast(schoolId: UUID, templateNames: List<String>) {
        val allTemplates = templateRepository.findBySchoolId(schoolId)
        allTemplates.forEach { template ->
            val shouldBeForBroadcast = templateNames.contains(template.templateName)
            if (template.isForBroadcast != shouldBeForBroadcast) {
                template.isForBroadcast = shouldBeForBroadcast
                templateRepository.save(template)
            }
        }
    }
}

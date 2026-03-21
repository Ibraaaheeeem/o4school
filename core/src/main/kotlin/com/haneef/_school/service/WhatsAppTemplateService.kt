package com.haneef._school.service

import com.haneef._school.config.WhatsAppProperties
import com.haneef._school.entity.WhatsAppTemplate
import com.haneef._school.repository.WhatsAppTemplateRepository
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.util.*

@Service
class WhatsAppTemplateService(
    private val templateRepository: WhatsAppTemplateRepository,
    private val properties: WhatsAppProperties,
    private val restTemplate: RestTemplate = RestTemplate(),
    private val objectMapper: com.fasterxml.jackson.databind.ObjectMapper = jacksonObjectMapper()
) {
    private val logger = LoggerFactory.getLogger(WhatsAppTemplateService::class.java)
    
    fun syncTemplates(): Boolean {
        logger.warn("WhatsApp template sync has been disabled")
        return false
    }

    // not currently used, but implemented for future use if needed
    fun createMetaTemplate(
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
                val bodyMap = response.body as? Map<*, *>
                val metaId = bodyMap?.get("id") as? String

                if (metaId.isNullOrBlank()) {
                    logger.error("Template created on Meta but response did not include template id")
                    return false
                }

                val parameterCount = calculateParameterCount(components)
                val componentsJson = objectMapper.writeValueAsString(components)

                val template = templateRepository.findByTemplateId(metaId).orElse(
                    WhatsAppTemplate(
                        templateId = metaId,
                        templateName = name.lowercase().replace(" ", "_"),
                        language = language,
                        category = category,
                        parameterCount = parameterCount,
                        componentsJson = componentsJson,
                        status = "PENDING"
                    )
                )

                template.templateName = name.lowercase().replace(" ", "_")
                template.language = language
                template.category = category
                template.parameterCount = parameterCount
                template.componentsJson = componentsJson
                template.status = "PENDING"
                
                // If mapping provided, find the new template and apply it
                if (!mapping.isNullOrBlank()) {
                    template.parameterMapping = mapping
                }
                templateRepository.save(template)
                true
            } else {
                logger.error("Failed to create WhatsApp template: ${response.body}")
                false
            }
        } catch (e: Exception) {
            logger.error("Error creating WhatsApp template", e)
            false
        }
    }

    // not currently used, but implemented for future use if needed
    fun deleteMetaTemplate(templateId: UUID): Boolean {
        logger.warn("WhatsApp template deletion has been disabled for template id: {}", templateId)
        return false
    }

    fun getAllTemplates(): List<WhatsAppTemplate> {
        return templateRepository.findAll()
    }

    fun updateParameterMapping(templateId: UUID, mapping: String) {
        val template = templateRepository.findById(templateId).orElseThrow { RuntimeException("Template not found") }
        template.parameterMapping = mapping
        templateRepository.save(template)
    }

    fun getBroadcastTemplates(recipientType: String? = null, channel: String? = null): List<WhatsAppTemplate> {
        var templates = templateRepository.findByIsForBroadcast(true)
        
        // Filter by recipientType
        if (recipientType != null) {
            if (recipientType == "ALL") {
                templates = templates.filter { it.targetRole == "GENERAL" }
            } else {
                val mappedRole = if (recipientType == "PARENTS") "PARENT" else recipientType
                templates = templates.filter {
                    it.targetRole == "GENERAL" || it.targetRole == mappedRole
                }
            }
        }

        // Filter by channel prefix
        return when (channel?.uppercase()) {
            "SMS" -> templates.filter { it.templateName?.startsWith("sms_") == true }
            "WHATSAPP" -> templates.filter { it.templateName?.startsWith("sms_") != true }
            else -> templates // INTERNAL or null = all templates
        }
    }

    fun markSelectedTemplates(names: List<String>) {
        val templates = templateRepository.findAll()
        templates.forEach { template ->
            val shouldBeForBroadcast = names.contains(template.templateName)
            if (template.isForBroadcast != shouldBeForBroadcast) {
                template.isForBroadcast = shouldBeForBroadcast
                templateRepository.save(template)
            }
        }
    }

    private fun calculateParameterCount(components: List<Map<String, Any>>): Int {
        var parameterCount = 0
        val uniquePlaceholders = mutableSetOf<String>()

        components.forEach { compMap ->
            val text = compMap["text"] as? String ?: ""
            val regex = Regex("\\{\\{([a-zA-Z0-9_]+)}}")
            val matches = regex.findAll(text)

            matches.forEach { uniquePlaceholders.add(it.groupValues[1]) }

            val maxParam = matches.mapNotNull {
                it.groupValues[1].filter { char -> char.isDigit() }.toIntOrNull()
            }.maxOrNull() ?: 0

            if (maxParam > parameterCount) {
                parameterCount = maxParam
            }
        }

        if (parameterCount == 0 && uniquePlaceholders.isNotEmpty()) {
            parameterCount = uniquePlaceholders.size
        }

        return parameterCount
    }

    fun getTemplate(id: UUID): WhatsAppTemplate {
        return templateRepository.findById(id).orElseThrow { RuntimeException("Template not found") }
    }
}

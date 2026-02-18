package com.haneef._school.repository

import com.haneef._school.entity.WhatsAppTemplate
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID
import java.util.Optional

@Repository
interface WhatsAppTemplateRepository : JpaRepository<WhatsAppTemplate, UUID> {
    fun findBySchoolId(schoolId: UUID): List<WhatsAppTemplate>
    fun findByTemplateId(templateId: String): Optional<WhatsAppTemplate>
    fun findByTemplateNameAndSchoolId(templateName: String, schoolId: UUID): Optional<WhatsAppTemplate>
    fun findBySchoolIdAndIsForBroadcast(schoolId: UUID, isForBroadcast: Boolean): List<WhatsAppTemplate>
}

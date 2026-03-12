package com.haneef._school.repository

import com.haneef._school.entity.ServiceUsageLog
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ServiceUsageLogRepository : JpaRepository<ServiceUsageLog, UUID> {
    fun findBySchoolIdOrderByTimestampDesc(schoolId: UUID, pageable: Pageable): Page<ServiceUsageLog>
    fun findBySchoolIdAndUserIdOrderByTimestampDesc(schoolId: UUID, userId: UUID, pageable: Pageable): Page<ServiceUsageLog>
}

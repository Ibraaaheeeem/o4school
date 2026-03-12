package com.haneef._school.repository

import com.haneef._school.entity.SchoolSubscription
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface SchoolSubscriptionRepository : JpaRepository<SchoolSubscription, UUID> {
    fun findBySchoolId(schoolId: UUID): SchoolSubscription?
}

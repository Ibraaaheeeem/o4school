package com.haneef._school.repository

import com.haneef._school.entity.ActivityLog
import com.haneef._school.entity.ActivityType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.UUID

@Repository
interface ActivityLogRepository : JpaRepository<ActivityLog, UUID> {
    
    // Find activities for a specific school
    fun findBySchoolIdOrderByCreatedAtDesc(schoolId: UUID, pageable: Pageable): Page<ActivityLog>
    
    // Find activities for a specific user (what they did)
    fun findBySchoolIdAndUserIdOrderByCreatedAtDesc(
        schoolId: UUID, 
        userId: UUID, 
        pageable: Pageable
    ): Page<ActivityLog>
    
    // Find activities about a specific user (what happened to them)
    fun findBySchoolIdAndTargetUserIdOrderByCreatedAtDesc(
        schoolId: UUID, 
        targetUserId: UUID, 
        pageable: Pageable
    ): Page<ActivityLog>
    
    // Find activities by type
    fun findBySchoolIdAndActivityTypeOrderByCreatedAtDesc(
        schoolId: UUID, 
        activityType: ActivityType, 
        pageable: Pageable
    ): Page<ActivityLog>
    
    // Find activities by user role
    fun findBySchoolIdAndUserRoleOrderByCreatedAtDesc(
        schoolId: UUID, 
        userRole: String, 
        pageable: Pageable
    ): Page<ActivityLog>
    
    // Find recent activities for dashboard
    @Query("""
        SELECT a FROM ActivityLog a 
        WHERE a.schoolId = :schoolId 
        AND a.createdAt >= :since 
        ORDER BY a.createdAt DESC
    """)
    fun findRecentActivities(
        @Param("schoolId") schoolId: UUID,
        @Param("since") since: LocalDateTime,
        pageable: Pageable
    ): Page<ActivityLog>
    
    // Find activities related to a specific user (either as actor or target)
    @Query("""
        SELECT a FROM ActivityLog a 
        WHERE a.schoolId = :schoolId 
        AND (a.userId = :userId OR a.targetUserId = :userId)
        ORDER BY a.createdAt DESC
    """)
    fun findActivitiesRelatedToUser(
        @Param("schoolId") schoolId: UUID,
        @Param("userId") userId: UUID,
        pageable: Pageable
    ): Page<ActivityLog>
    
    // Find activities by date range
    @Query("""
        SELECT a FROM ActivityLog a 
        WHERE a.schoolId = :schoolId 
        AND a.createdAt BETWEEN :startDate AND :endDate 
        ORDER BY a.createdAt DESC
    """)
    fun findByDateRange(
        @Param("schoolId") schoolId: UUID,
        @Param("startDate") startDate: LocalDateTime,
        @Param("endDate") endDate: LocalDateTime,
        pageable: Pageable
    ): Page<ActivityLog>
    
    // Count activities by type for analytics
    @Query("""
        SELECT a.activityType, COUNT(a) 
        FROM ActivityLog a 
        WHERE a.schoolId = :schoolId 
        AND a.createdAt >= :since 
        GROUP BY a.activityType
    """)
    fun countActivitiesByType(
        @Param("schoolId") schoolId: UUID,
        @Param("since") since: LocalDateTime
    ): List<Array<Any>>
}
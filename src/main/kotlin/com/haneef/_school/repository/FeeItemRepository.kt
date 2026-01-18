package com.haneef._school.repository

import java.util.UUID

import com.haneef._school.entity.FeeCategory
import com.haneef._school.entity.FeeItem
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface FeeItemRepository : JpaRepository<FeeItem, UUID>, SecureFeeItemRepository {
    
    fun findBySchoolIdAndIsActiveOrderByFeeCategoryAscNameAsc(schoolId: UUID, isActive: Boolean): List<FeeItem>
    
    fun findBySchoolIdAndFeeCategoryAndIsActive(schoolId: UUID, feeCategory: FeeCategory, isActive: Boolean): List<FeeItem>
    
    fun findBySchoolIdAndNameAndIsActive(schoolId: UUID, name: String, isActive: Boolean): Optional<FeeItem>
    
    @Query(value = "SELECT DISTINCT f.* FROM fee_items f " +
           "LEFT JOIN class_fee_items cfi ON f.id = cfi.fee_item_id " +
           "LEFT JOIN classes sc ON cfi.class_id = sc.id " +
           "WHERE f.school_id = :schoolId AND f.is_active = :isActive AND " +
           "(CAST(:#{#category?.name} AS text) IS NULL OR f.fee_category = CAST(:#{#category?.name} AS text)) AND " +
           "(CAST(:search AS text) IS NULL OR " +
           "LOWER(f.name) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%')) OR " +
           "LOWER(f.description) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%')))",
           nativeQuery = true)
    fun findBySchoolIdAndFilters(
        @Param("schoolId") schoolId: UUID,
        @Param("isActive") isActive: Boolean,
        @Param("category") category: FeeCategory?,
        @Param("search") search: String?
    ): List<FeeItem>
    
    fun countBySchoolIdAndIsActive(schoolId: UUID, isActive: Boolean): Long
}
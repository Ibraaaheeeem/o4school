package com.haneef._school.repository

import java.util.UUID

import com.haneef._school.entity.ClassFeeItem
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface ClassFeeItemRepository : JpaRepository<ClassFeeItem, UUID> {
    
    fun findBySchoolIdAndIsActiveOrderBySchoolClassAscFeeItemAsc(schoolId: UUID, isActive: Boolean): List<ClassFeeItem>
    
    fun findBySchoolClassIdAndAcademicSessionIdAndIsActive(classId: UUID, sessionId: UUID, isActive: Boolean): List<ClassFeeItem>
    

    
    @Query("SELECT cfi FROM ClassFeeItem cfi LEFT JOIN FETCH cfi.schoolClass LEFT JOIN FETCH cfi.academicSession WHERE cfi.feeItem.id = :feeItemId AND cfi.isActive = :isActive")
    fun findByFeeItemIdAndIsActive(@Param("feeItemId") feeItemId: UUID, @Param("isActive") isActive: Boolean): List<ClassFeeItem>
    

    fun findBySchoolClassIdAndFeeItemIdAndAcademicSessionIdAndTermIdAndIsActive(
        classId: UUID, 
        feeItemId: UUID, 
        academicSessionId: UUID, 
        term: com.haneef._school.entity.Term?, 
        isActive: Boolean
    ): Optional<ClassFeeItem>

    fun findBySchoolClassIdAndFeeItemIdAndAcademicSessionIdAndTermId(
        classId: UUID, 
        feeItemId: UUID, 
        academicSessionId: UUID, 
        term: com.haneef._school.entity.Term?
    ): Optional<ClassFeeItem>
    
    @Query("SELECT cfi FROM ClassFeeItem cfi WHERE cfi.schoolClass.id = :classId AND cfi.academicSession.id = :sessionId AND (cfi.termId IS NULL OR cfi.termId.id = :termId) AND cfi.isActive = :isActive")
    fun findBySchoolClassIdAndAcademicSessionIdAndTermIdFilters(
        @Param("classId") classId: UUID,
        @Param("sessionId") sessionId: UUID,
        @Param("termId") termId: UUID?,
        @Param("isActive") isActive: Boolean
    ): List<ClassFeeItem>

    @Query("SELECT cfi FROM ClassFeeItem cfi JOIN cfi.feeItem fi WHERE cfi.schoolId = :schoolId AND cfi.academicSession.id = :sessionId AND (:termId IS NULL OR cfi.termId.id = :termId) AND fi.isMandatory = false AND cfi.isActive = true")
    fun findOptionalFees(
        @Param("schoolId") schoolId: UUID,
        @Param("sessionId") sessionId: UUID,
        @Param("termId") termId: UUID?
    ): List<ClassFeeItem>

    fun countBySchoolIdAndIsActive(schoolId: UUID, isActive: Boolean): Long
}
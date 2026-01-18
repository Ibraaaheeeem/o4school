package com.haneef._school.repository

import com.haneef._school.entity.StudentOptionalFee
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface StudentOptionalFeeRepository : JpaRepository<StudentOptionalFee, UUID> {
    fun findByStudentIdAndClassFeeItemId(studentId: UUID, classFeeItemId: UUID): StudentOptionalFee?
    fun findByStudentIdAndIsActive(studentId: UUID, isActive: Boolean): List<StudentOptionalFee>
    fun existsByStudentIdAndClassFeeItemIdAndIsActive(studentId: UUID, classFeeItemId: UUID, isActive: Boolean): Boolean
    
    @Query("""
        SELECT sof FROM StudentOptionalFee sof 
        JOIN FETCH sof.student s 
        JOIN FETCH s.user u
        JOIN FETCH sof.classFeeItem cfi 
        JOIN FETCH cfi.feeItem fi 
        JOIN FETCH cfi.schoolClass sc 
        LEFT JOIN FETCH sof.academicSession acs 
        LEFT JOIN FETCH sof.term t 
        WHERE sof.classFeeItem.schoolId = :schoolId 
        AND (:sessionId IS NULL OR sof.academicSession.id = :sessionId) 
        AND (:termId IS NULL OR sof.term.id = :termId OR sof.term IS NULL) 
        AND sof.isActive = true 
        ORDER BY u.firstName, u.lastName, fi.name
    """)
    fun findActiveOptionalFeesBySchool(
        @Param("schoolId") schoolId: UUID,
        @Param("sessionId") sessionId: UUID?,
        @Param("termId") termId: UUID?
    ): List<StudentOptionalFee>
    
    // Fallback method without term filtering for debugging
    @Query("""
        SELECT sof FROM StudentOptionalFee sof 
        JOIN FETCH sof.student s 
        JOIN FETCH s.user u
        JOIN FETCH sof.classFeeItem cfi 
        JOIN FETCH cfi.feeItem fi 
        JOIN FETCH cfi.schoolClass sc 
        LEFT JOIN FETCH sof.academicSession acs 
        LEFT JOIN FETCH sof.term t 
        WHERE sof.classFeeItem.schoolId = :schoolId 
        AND (:sessionId IS NULL OR sof.academicSession.id = :sessionId) 
        AND sof.isActive = true 
        ORDER BY u.firstName, u.lastName, fi.name
    """)
    fun findActiveOptionalFeesBySchoolNoTermFilter(
        @Param("schoolId") schoolId: UUID,
        @Param("sessionId") sessionId: UUID?
    ): List<StudentOptionalFee>
}

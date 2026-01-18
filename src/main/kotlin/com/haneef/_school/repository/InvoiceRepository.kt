package com.haneef._school.repository

import com.haneef._school.entity.Invoice
import com.haneef._school.entity.InvoiceStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface InvoiceRepository : JpaRepository<Invoice, UUID> {
    
    fun findBySchoolIdAndIsActive(schoolId: UUID, isActive: Boolean): List<Invoice>
    
    fun findByStudentIdAndSchoolIdAndIsActive(studentId: UUID, schoolId: UUID, isActive: Boolean): List<Invoice>
    
    fun findByStudentIdAndAcademicSessionIdAndIsActive(studentId: UUID, academicSessionId: UUID, isActive: Boolean): List<Invoice>
    
    fun findByStudentIdAndAcademicSessionIdAndTermAndIsActive(
        studentId: UUID, 
        academicSessionId: UUID, 
        term: String?, 
        isActive: Boolean
    ): List<Invoice>
    
    @Query("SELECT i FROM Invoice i WHERE i.student.id = :studentId AND i.schoolId = :schoolId AND i.isActive = :isActive AND i.status IN :statuses")
    fun findByStudentIdAndSchoolIdAndStatusIn(
        @Param("studentId") studentId: UUID,
        @Param("schoolId") schoolId: UUID,
        @Param("isActive") isActive: Boolean,
        @Param("statuses") statuses: List<InvoiceStatus>
    ): List<Invoice>

    fun findByStudentIdInAndSchoolIdAndIsActive(studentIds: List<UUID>, schoolId: UUID, isActive: Boolean): List<Invoice>

    fun findByStudentIdInAndAcademicSessionIdAndIsActive(studentIds: List<UUID>, academicSessionId: UUID, isActive: Boolean): List<Invoice>

    fun findByStudentIdInAndAcademicSessionIdAndTermAndIsActive(
        studentIds: List<UUID>, 
        academicSessionId: UUID, 
        term: String?, 
        isActive: Boolean
    ): List<Invoice>
}

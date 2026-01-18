package com.haneef._school.repository

import java.util.UUID

import com.haneef._school.entity.ClassTeacher
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ClassTeacherRepository : JpaRepository<ClassTeacher, UUID> {
    
    fun findBySchoolIdAndIsActive(schoolId: UUID, isActive: Boolean): List<ClassTeacher>
    
    fun findByStaffIdAndIsActive(staffId: UUID, isActive: Boolean): List<ClassTeacher>
    
    fun findBySchoolClassIdAndIsActive(classId: UUID, isActive: Boolean): List<ClassTeacher>
    
    @Query("SELECT ct FROM ClassTeacher ct JOIN FETCH ct.staff s JOIN FETCH s.user JOIN FETCH ct.schoolClass sc LEFT JOIN FETCH sc.track JOIN FETCH ct.academicSession JOIN FETCH ct.term WHERE ct.schoolId = :schoolId AND ct.isActive = :isActive AND ct.academicSession.id = :sessionId AND ct.term.id = :termId")
    fun findBySchoolIdAndIsActiveAndSessionAndTermWithDetails(
        @Param("schoolId") schoolId: UUID,
        @Param("isActive") isActive: Boolean,
        @Param("sessionId") sessionId: UUID,
        @Param("termId") termId: UUID
    ): List<ClassTeacher>
    
    fun existsByStaffIdAndSchoolClassIdAndAcademicSessionIdAndTermIdAndSchoolIdAndIsActive(
        staffId: UUID,
        classId: UUID,
        sessionId: UUID,
        termId: UUID,
        schoolId: UUID,
        isActive: Boolean
    ): Boolean

    fun findByStaffIdAndSchoolClassIdAndAcademicSessionIdAndTermIdAndSchoolId(
        staffId: UUID,
        classId: UUID,
        sessionId: UUID,
        termId: UUID,
        schoolId: UUID
    ): ClassTeacher?
    
    fun findByStaffIdAndAcademicSessionIdAndTermIdAndIsActive(
        staffId: UUID,
        sessionId: UUID,
        termId: UUID,
        isActive: Boolean
    ): List<ClassTeacher>
    
    fun findByStaffIdAndAcademicSessionIdAndIsActive(
        staffId: UUID,
        sessionId: UUID,
        isActive: Boolean
    ): List<ClassTeacher>
    
    @Query("SELECT ct FROM ClassTeacher ct JOIN FETCH ct.staff s JOIN FETCH s.user JOIN FETCH ct.schoolClass sc LEFT JOIN FETCH sc.track WHERE ct.schoolId = :schoolId AND ct.isActive = :isActive AND ct.academicSession.id = :sessionId")
    fun findBySchoolIdAndIsActiveAndSessionWithDetails(
        @Param("schoolId") schoolId: UUID,
        @Param("isActive") isActive: Boolean,
        @Param("sessionId") sessionId: UUID
    ): List<ClassTeacher>
}
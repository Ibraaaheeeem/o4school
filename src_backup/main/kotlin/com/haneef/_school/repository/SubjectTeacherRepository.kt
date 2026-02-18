package com.haneef._school.repository

import java.util.UUID

import com.haneef._school.entity.SubjectTeacher
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface SubjectTeacherRepository : JpaRepository<SubjectTeacher, UUID> {
    
    fun findBySchoolIdAndIsActive(schoolId: UUID, isActive: Boolean): List<SubjectTeacher>
    
    fun findByStaffIdAndIsActive(staffId: UUID, isActive: Boolean): List<SubjectTeacher>
    
    fun findBySchoolClassIdAndIsActive(classId: UUID, isActive: Boolean): List<SubjectTeacher>
    
    fun findBySubjectIdAndIsActive(subjectId: UUID, isActive: Boolean): List<SubjectTeacher>
    
    @Query("SELECT st FROM SubjectTeacher st JOIN FETCH st.staff s JOIN FETCH s.user JOIN FETCH st.subject sub JOIN FETCH st.schoolClass sc LEFT JOIN FETCH sc.track JOIN FETCH st.academicSession JOIN FETCH st.term WHERE st.schoolId = :schoolId AND st.isActive = :isActive AND st.academicSession.id = :sessionId AND st.term.id = :termId")
    fun findBySchoolIdAndIsActiveAndSessionAndTermWithDetails(
        @Param("schoolId") schoolId: UUID,
        @Param("isActive") isActive: Boolean,
        @Param("sessionId") sessionId: UUID,
        @Param("termId") termId: UUID
    ): List<SubjectTeacher>
    
    fun existsByStaffIdAndSubjectIdAndSchoolClassIdAndAcademicSessionIdAndTermIdAndSchoolIdAndIsActive(
        staffId: UUID,
        subjectId: UUID,
        classId: UUID,
        sessionId: UUID,
        termId: UUID,
        schoolId: UUID,
        isActive: Boolean
    ): Boolean

    fun findByStaffIdAndSubjectIdAndSchoolClassIdAndAcademicSessionIdAndTermIdAndSchoolId(
        staffId: UUID,
        subjectId: UUID,
        classId: UUID,
        sessionId: UUID,
        termId: UUID,
        schoolId: UUID
    ): SubjectTeacher?
    
    fun findByStaffIdAndAcademicSessionIdAndTermIdAndIsActive(
        staffId: UUID,
        sessionId: UUID,
        termId: UUID,
        isActive: Boolean
    ): List<SubjectTeacher>
    
    fun findByStaffIdAndAcademicSessionIdAndIsActive(
        staffId: UUID,
        sessionId: UUID,
        isActive: Boolean
    ): List<SubjectTeacher>
    
    @Query("SELECT st FROM SubjectTeacher st JOIN FETCH st.staff s JOIN FETCH s.user JOIN FETCH st.subject sub JOIN FETCH st.schoolClass sc LEFT JOIN FETCH sc.track WHERE st.schoolId = :schoolId AND st.isActive = :isActive AND st.academicSession.id = :sessionId")
    fun findBySchoolIdAndIsActiveAndSessionWithDetails(
        @Param("schoolId") schoolId: UUID,
        @Param("isActive") isActive: Boolean,
        @Param("sessionId") sessionId: UUID
    ): List<SubjectTeacher>
}
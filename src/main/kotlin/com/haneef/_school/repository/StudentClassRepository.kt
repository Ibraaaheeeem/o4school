package com.haneef._school.repository

import java.util.UUID

import com.haneef._school.entity.StudentClass
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface StudentClassRepository : JpaRepository<StudentClass, UUID> {
    
    fun existsByStudentIdAndSchoolClassIdAndIsActive(studentId: UUID, schoolClassId: UUID, isActive: Boolean): Boolean
    
    fun findBySchoolIdAndIsActive(schoolId: UUID, isActive: Boolean): List<StudentClass>
    
    fun findByStudentIdAndIsActive(studentId: UUID, isActive: Boolean): List<StudentClass>
    
    fun findBySchoolClassIdAndIsActive(classId: UUID, isActive: Boolean): List<StudentClass>
    
    fun findBySchoolClassIdAndIsActive(classId: UUID, isActive: Boolean, pageable: org.springframework.data.domain.Pageable): org.springframework.data.domain.Page<StudentClass>
    
    fun countBySchoolClassIdAndIsActive(classId: UUID, isActive: Boolean): Long
    
    // New methods using foreign key relationships
    fun findByAcademicSessionIdAndIsActive(academicSessionId: UUID, isActive: Boolean): List<StudentClass>
    
    fun findByTermIdAndIsActive(termId: UUID, isActive: Boolean): List<StudentClass>
    
    fun findByAcademicSessionIdAndTermIdAndIsActive(academicSessionId: UUID, termId: UUID, isActive: Boolean): List<StudentClass>
    
    fun findBySchoolClassIdAndAcademicSessionIdAndIsActive(classId: UUID, academicSessionId: UUID, isActive: Boolean): List<StudentClass>
    
    fun findBySchoolClassIdAndAcademicSessionIdAndTermIdAndIsActive(classId: UUID, academicSessionId: UUID, termId: UUID, isActive: Boolean): List<StudentClass>
    
    fun findByStudentIdAndAcademicSessionIdAndTermId(studentId: UUID, academicSessionId: UUID, termId: UUID): List<StudentClass>
    
    fun findByStudentIdAndAcademicSessionIdAndIsActive(studentId: UUID, academicSessionId: UUID, isActive: Boolean): List<StudentClass>
    
    fun findByStudentIdAndAcademicSessionIdAndTermIdAndIsActive(studentId: UUID, academicSessionId: UUID, termId: UUID, isActive: Boolean): List<StudentClass>
    
    fun findByStudentIdAndSchoolClassTrackIdAndAcademicSessionIdAndTermId(
        studentId: UUID, trackId: UUID, academicSessionId: UUID, termId: UUID
    ): List<StudentClass>
    
    @Query("SELECT sc FROM StudentClass sc JOIN FETCH sc.student s JOIN FETCH s.user WHERE sc.schoolId = :schoolId AND sc.isActive = true")
    fun findBySchoolIdWithStudentDetails(@Param("schoolId") schoolId: UUID): List<StudentClass>
    
    @Query("SELECT sc FROM StudentClass sc JOIN FETCH sc.schoolClass c LEFT JOIN FETCH c.track tr LEFT JOIN FETCH c.department d LEFT JOIN FETCH d.track t WHERE sc.student.id = :studentId AND sc.isActive = true")
    fun findByStudentIdWithClassAndTrack(@Param("studentId") studentId: UUID): List<StudentClass>
    
    @Query("SELECT sc FROM StudentClass sc JOIN FETCH sc.academicSession JOIN FETCH sc.term WHERE sc.schoolId = :schoolId AND sc.isActive = true")
    fun findBySchoolIdWithSessionAndTerm(@Param("schoolId") schoolId: UUID): List<StudentClass>

    @Query("SELECT sc FROM StudentClass sc JOIN FETCH sc.schoolClass c LEFT JOIN FETCH c.track WHERE sc.student.id IN :studentIds AND sc.academicSession.id = :sessionId AND sc.term.id = :termId AND sc.isActive = :isActive")
    fun findByStudentIdInAndAcademicSessionIdAndTermIdAndIsActiveWithClassDetails(
        @Param("studentIds") studentIds: List<UUID>,
        @Param("sessionId") sessionId: UUID,
        @Param("termId") termId: UUID,
        @Param("isActive") isActive: Boolean
    ): List<StudentClass>
}
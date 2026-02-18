package com.haneef._school.repository

import java.util.UUID

import com.haneef._school.entity.ParentStudent
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ParentStudentRepository : JpaRepository<ParentStudent, UUID> {
    
    fun findBySchoolIdAndIsActive(schoolId: UUID, isActive: Boolean): List<ParentStudent>
    
    fun findByParentIdAndIsActive(parentId: UUID, isActive: Boolean): List<ParentStudent>
    
    fun findByStudentIdAndIsActive(studentId: UUID, isActive: Boolean): List<ParentStudent>
    
    @Query("SELECT ps FROM ParentStudent ps JOIN FETCH ps.parent p JOIN FETCH p.user WHERE ps.student.id = :studentId AND ps.isActive = true")
    fun findByStudentIdWithParentDetails(@Param("studentId") studentId: UUID): List<ParentStudent>
    
    @Query("SELECT ps FROM ParentStudent ps JOIN FETCH ps.student s JOIN FETCH s.user WHERE ps.parent.id = :parentId AND ps.isActive = true")
    fun findByParentIdWithStudentDetails(@Param("parentId") parentId: UUID): List<ParentStudent>
    
    @Query("SELECT ps FROM ParentStudent ps JOIN FETCH ps.parent p JOIN FETCH p.user JOIN FETCH ps.student s JOIN FETCH s.user WHERE ps.schoolId = :schoolId AND ps.isActive = true")
    fun findBySchoolIdWithDetails(@Param("schoolId") schoolId: UUID): List<ParentStudent>
    
    fun existsByParentIdAndStudentIdAndSchoolIdAndIsActive(
        parentId: UUID, 
        studentId: UUID, 
        schoolId: UUID, 
        isActive: Boolean
    ): Boolean

    fun findByParentIdAndStudentIdAndSchoolId(
        parentId: UUID, 
        studentId: UUID, 
        schoolId: UUID
    ): ParentStudent?
}
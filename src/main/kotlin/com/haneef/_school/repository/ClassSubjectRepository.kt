package com.haneef._school.repository

import java.util.UUID

import com.haneef._school.entity.ClassSubject
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ClassSubjectRepository : JpaRepository<ClassSubject, UUID> {
    
    @Query("SELECT cs FROM ClassSubject cs WHERE cs.schoolClass.id = :classId AND cs.subject.id = :subjectId AND cs.isActive = :isActive")
    fun findBySchoolClassIdAndSubjectIdAndIsActive(@Param("classId") classId: UUID, @Param("subjectId") subjectId: UUID, @Param("isActive") isActive: Boolean): ClassSubject?
    
    @Query("SELECT cs FROM ClassSubject cs WHERE cs.schoolClass.id = :classId AND cs.isActive = :isActive")
    fun findBySchoolClassIdAndIsActive(@Param("classId") classId: UUID, @Param("isActive") isActive: Boolean): List<ClassSubject>
    
    @Query("SELECT cs FROM ClassSubject cs WHERE cs.subject.id = :subjectId AND cs.isActive = :isActive")
    fun findBySubjectIdAndIsActive(@Param("subjectId") subjectId: UUID, @Param("isActive") isActive: Boolean): List<ClassSubject>
    
    @Query("SELECT cs FROM ClassSubject cs JOIN FETCH cs.subject WHERE cs.schoolClass.id = :classId AND cs.isActive = true")
    fun findBySchoolClassIdWithSubject(@Param("classId") classId: UUID): List<ClassSubject>
    
    @Query("SELECT cs FROM ClassSubject cs JOIN FETCH cs.schoolClass WHERE cs.subject.id = :subjectId AND cs.isActive = true")
    fun findBySubjectIdWithClass(@Param("subjectId") subjectId: UUID): List<ClassSubject>
    
    @Query("SELECT cs FROM ClassSubject cs WHERE cs.subject.id = :subjectId")
    fun findBySubjectId(@Param("subjectId") subjectId: UUID): List<ClassSubject>

    fun findBySchoolIdAndIsActive(schoolId: UUID, isActive: Boolean): List<ClassSubject>

    @Query("SELECT cs FROM ClassSubject cs JOIN FETCH cs.schoolClass JOIN FETCH cs.subject WHERE cs.schoolId = :schoolId AND cs.isActive = :isActive")
    fun findBySchoolIdWithRelationships(@Param("schoolId") schoolId: UUID, @Param("isActive") isActive: Boolean): List<ClassSubject>

    @Query("SELECT cs FROM ClassSubject cs JOIN FETCH cs.schoolClass JOIN FETCH cs.subject WHERE cs.schoolClass.department.id = :departmentId AND cs.isActive = :isActive")
    fun findByDepartmentIdWithRelationships(@Param("departmentId") departmentId: UUID, @Param("isActive") isActive: Boolean): List<ClassSubject>

    @Query("SELECT cs FROM ClassSubject cs JOIN FETCH cs.schoolClass JOIN FETCH cs.subject WHERE cs.schoolClass.id = :classId AND cs.isActive = :isActive")
    fun findByClassIdWithRelationships(@Param("classId") classId: UUID, @Param("isActive") isActive: Boolean): List<ClassSubject>
}
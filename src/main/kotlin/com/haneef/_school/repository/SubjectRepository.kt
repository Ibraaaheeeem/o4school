package com.haneef._school.repository

import java.util.UUID

import com.haneef._school.entity.Subject
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface SubjectRepository : JpaRepository<Subject, UUID>, SecureSubjectRepository {
    
    fun findBySchoolIdAndIsActive(schoolId: UUID, isActive: Boolean): List<Subject>
    
    fun countBySchoolIdAndIsActive(schoolId: UUID, isActive: Boolean): Long
    
    fun findBySubjectNameAndSchoolIdAndIsActive(subjectName: String, schoolId: UUID, isActive: Boolean): Subject?
}
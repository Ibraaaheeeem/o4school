package com.haneef._school.repository

import java.util.UUID

import com.haneef._school.entity.Question
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface QuestionRepository : JpaRepository<Question, UUID> {
    
    fun findByExaminationIdOrderByCreatedAt(examinationId: UUID): List<Question>
    
    fun findByExaminationId(examinationId: UUID): List<Question>
    
    @Query("SELECT COUNT(q) FROM Question q JOIN q.examination e WHERE e.schoolId = :schoolId")
    fun countBySchoolId(@Param("schoolId") schoolId: UUID): Long
    
    fun countByExaminationId(examinationId: UUID): Long
}
package com.haneef._school.repository

import java.util.UUID

import com.haneef._school.entity.Examination
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface ExaminationRepository : JpaRepository<Examination, UUID>, SecureExaminationRepository {
    
    @Query("SELECT DISTINCT e FROM Examination e LEFT JOIN FETCH e.subject LEFT JOIN FETCH e.schoolClass LEFT JOIN FETCH e.questions " +
           "WHERE e.schoolId = :schoolId AND e.isActive = :isActive " +
           "ORDER BY e.createdAt DESC")
    fun findBySchoolIdAndIsActiveOrderByCreatedAtDesc(
        @Param("schoolId") schoolId: UUID, 
        @Param("isActive") isActive: Boolean,
        pageable: org.springframework.data.domain.Pageable
    ): org.springframework.data.domain.Page<Examination>
    
    @Query("SELECT e FROM Examination e WHERE e.schoolId = :schoolId AND e.isActive = :isActive AND e.isPublished = :isPublished ORDER BY e.createdAt DESC")
    fun findBySchoolIdAndIsActiveAndIsPublished(
        @Param("schoolId") schoolId: UUID, 
        @Param("isActive") isActive: Boolean, 
        @Param("isPublished") isPublished: Boolean
    ): List<Examination>
    
    @Query("SELECT DISTINCT e FROM Examination e LEFT JOIN FETCH e.subject LEFT JOIN FETCH e.schoolClass LEFT JOIN FETCH e.questions WHERE e.id = :id")
    fun findByIdWithRelationships(@Param("id") id: UUID): Optional<Examination>
    
    fun countBySchoolIdAndIsActive(schoolId: UUID, isActive: Boolean): Long
    
    fun countBySchoolIdAndIsActiveAndIsPublished(schoolId: UUID, isActive: Boolean, isPublished: Boolean): Long
    
    @Query("SELECT DISTINCT e FROM Examination e LEFT JOIN FETCH e.subject LEFT JOIN FETCH e.schoolClass " +
           "WHERE e.schoolId = :schoolId AND e.isActive = :isActive AND " +
           "(:subjectId IS NULL OR e.subject.id = :subjectId) AND " +
           "(:classId IS NULL OR e.schoolClass.id = :classId) AND " +
           "(:examType IS NULL OR e.examType = :examType) AND " +
           "(:term IS NULL OR e.term = :term) AND " +
           "(:session IS NULL OR e.session = :session) " +
           "ORDER BY e.createdAt DESC")
    fun findBySchoolIdAndFilters(
        @Param("schoolId") schoolId: UUID,
        @Param("isActive") isActive: Boolean,
        @Param("subjectId") subjectId: UUID?,
        @Param("classId") classId: UUID?,
        @Param("examType") examType: String?,
        @Param("term") term: String?,
        @Param("session") session: String?
    ): List<Examination>
    
    @Query("SELECT e FROM Examination e WHERE e.subject.id = :subjectId AND e.schoolClass.id = :classId AND e.term = :term AND e.session = :session AND e.isActive = :isActive ORDER BY e.createdAt DESC")
    fun findBySubjectIdAndSchoolClassIdAndTermAndSessionAndIsActive(
        @Param("subjectId") subjectId: UUID,
        @Param("classId") classId: UUID,
        @Param("term") term: String,
        @Param("session") session: String,
        @Param("isActive") isActive: Boolean
    ): List<Examination>
    
    @Query(value = "SELECT DISTINCT e FROM Examination e LEFT JOIN FETCH e.subject LEFT JOIN FETCH e.schoolClass LEFT JOIN FETCH e.questions " +
           "WHERE e.schoolId = :schoolId AND e.isActive = :isActive AND " +
           "(:subjectId IS NULL OR e.subject.id = :subjectId) AND " +
           "(:classId IS NULL OR e.schoolClass.id = :classId) AND " +
           "(:departmentId IS NULL OR e.schoolClass.department.id = :departmentId) AND " +
           "(:trackId IS NULL OR e.schoolClass.track.id = :trackId) AND " +
           "(:examType IS NULL OR e.examType = :examType) AND " +
           "(:term IS NULL OR e.term = :term) AND " +
           "(:session IS NULL OR e.session = :session) " +
           "ORDER BY e.createdAt DESC",
           countQuery = "SELECT COUNT(DISTINCT e) FROM Examination e " +
           "WHERE e.schoolId = :schoolId AND e.isActive = :isActive AND " +
           "(:subjectId IS NULL OR e.subject.id = :subjectId) AND " +
           "(:classId IS NULL OR e.schoolClass.id = :classId) AND " +
           "(:departmentId IS NULL OR e.schoolClass.department.id = :departmentId) AND " +
           "(:trackId IS NULL OR e.schoolClass.track.id = :trackId) AND " +
           "(:examType IS NULL OR e.examType = :examType) AND " +
           "(:term IS NULL OR e.term = :term) AND " +
           "(:session IS NULL OR e.session = :session)")
    fun findBySchoolIdAndFiltersWithQuestions(
        @Param("schoolId") schoolId: UUID,
        @Param("isActive") isActive: Boolean,
        @Param("subjectId") subjectId: UUID?,
        @Param("classId") classId: UUID?,
        @Param("departmentId") departmentId: UUID?,
        @Param("trackId") trackId: UUID?,
        @Param("examType") examType: String?,
        @Param("term") term: String?,
        @Param("session") session: String?,
        pageable: org.springframework.data.domain.Pageable
    ): org.springframework.data.domain.Page<Examination>

    fun findBySubjectIdAndSchoolClassIdAndTermAndSessionAndExamTypeAndIsActive(
        subjectId: UUID,
        classId: UUID,
        term: String,
        session: String,
        examType: String,
        isActive: Boolean
    ): List<Examination>
}
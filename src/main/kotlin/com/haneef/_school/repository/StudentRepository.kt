package com.haneef._school.repository

import java.util.UUID

import com.haneef._school.entity.Student
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface StudentRepository : JpaRepository<Student, UUID>, SecureStudentRepository {
    
    fun findFirstBySchoolIdOrderByCreatedAtDesc(schoolId: UUID): Student?
    
    fun findBySchoolIdAndIsActive(schoolId: UUID, isActive: Boolean): List<Student>
    
    fun findBySchoolIdAndIsActive(schoolId: UUID, isActive: Boolean, pageable: Pageable): Page<Student>
    
    fun countBySchoolIdAndIsActive(schoolId: UUID, isActive: Boolean): Long
    
    fun countBySchoolId(schoolId: UUID): Long
    
    @Query("SELECT s FROM Student s WHERE s.schoolId = :schoolId AND s.isActive = :isActive AND " +
           "(CAST(s.user.firstName AS string) ILIKE CONCAT('%', :search, '%') OR " +
           "CAST(s.user.lastName AS string) ILIKE CONCAT('%', :search, '%') OR " +
           "CAST(s.studentId AS string) ILIKE CONCAT('%', :search, '%') OR " +
           "CAST(s.admissionNumber AS string) ILIKE CONCAT('%', :search, '%'))")
    fun findBySchoolIdAndIsActiveAndSearch(
        @Param("schoolId") schoolId: UUID,
        @Param("isActive") isActive: Boolean,
        @Param("search") search: String,
        pageable: Pageable
    ): Page<Student>
    
    fun findByUserIdAndSchoolId(userId: UUID, schoolId: UUID): Student?
    
    fun findByStudentIdAndSchoolId(studentId: String, schoolId: UUID): Student?
    
    fun findByAdmissionNumberAndSchoolId(admissionNumber: String, schoolId: UUID): Student?
    
    @Query("SELECT s FROM Student s JOIN FETCH s.user WHERE s.admissionNumber = :admissionNumber")
    fun findByAdmissionNumber(@Param("admissionNumber") admissionNumber: String): Student?
    
    @Query("SELECT s FROM Student s WHERE s.schoolId = :schoolId AND s.isActive = :isActive AND " +
           "(CAST(s.user.firstName AS string) ILIKE CONCAT('%', :search, '%') OR " +
           "CAST(s.user.lastName AS string) ILIKE CONCAT('%', :search, '%') OR " +
           "CAST(s.studentId AS string) ILIKE CONCAT('%', :search, '%') OR " +
           "CAST(s.admissionNumber AS string) ILIKE CONCAT('%', :search, '%'))")
    fun findBySchoolIdAndIsActiveAndUserFullNameContaining(
        @Param("schoolId") schoolId: UUID,
        @Param("isActive") isActive: Boolean,
        @Param("search") search: String,
        pageable: Pageable
    ): Page<Student>
    
    @Query("SELECT s FROM Student s WHERE s.schoolId = :schoolId AND s.isActive = :isActive AND " +
           "EXISTS (SELECT 1 FROM StudentClass ce WHERE ce.student = s AND ce.schoolClass.id = :classId AND ce.isActive = true)")
    fun findBySchoolIdAndIsActiveAndClassId(
        @Param("schoolId") schoolId: UUID,
        @Param("isActive") isActive: Boolean,
        @Param("classId") classId: UUID,
        pageable: Pageable
    ): Page<Student>
    
    @Query("SELECT s FROM Student s WHERE s.schoolId = :schoolId AND s.isActive = :isActive AND " +
           "EXISTS (SELECT 1 FROM StudentClass ce WHERE ce.student = s AND ce.schoolClass.department.track.id = :trackId AND ce.isActive = true)")
    fun findBySchoolIdAndIsActiveAndTrackId(
        @Param("schoolId") schoolId: UUID,
        @Param("isActive") isActive: Boolean,
        @Param("trackId") trackId: UUID,
        pageable: Pageable
    ): Page<Student>
    
    @Query("SELECT s FROM Student s WHERE s.schoolId = :schoolId AND s.isActive = :isActive AND " +
           "EXISTS (SELECT 1 FROM StudentClass ce WHERE ce.student = s AND ce.schoolClass.id = :classId AND ce.isActive = true) AND " +
           "(CAST(s.user.firstName AS string) ILIKE CONCAT('%', :search, '%') OR " +
           "CAST(s.user.lastName AS string) ILIKE CONCAT('%', :search, '%') OR " +
           "CAST(s.studentId AS string) ILIKE CONCAT('%', :search, '%') OR " +
           "CAST(s.admissionNumber AS string) ILIKE CONCAT('%', :search, '%'))")
    fun findBySchoolIdAndIsActiveAndClassIdAndUserFullNameContaining(
        @Param("schoolId") schoolId: UUID,
        @Param("isActive") isActive: Boolean,
        @Param("classId") classId: UUID,
        @Param("search") search: String,
        pageable: Pageable
    ): Page<Student>
    
    @Query("SELECT s FROM Student s WHERE s.schoolId = :schoolId AND s.isActive = :isActive AND " +
           "EXISTS (SELECT 1 FROM StudentClass ce WHERE ce.student = s AND ce.schoolClass.department.track.id = :trackId AND ce.isActive = true) AND " +
           "(CAST(s.user.firstName AS string) ILIKE CONCAT('%', :search, '%') OR " +
           "CAST(s.user.lastName AS string) ILIKE CONCAT('%', :search, '%') OR " +
           "CAST(s.studentId AS string) ILIKE CONCAT('%', :search, '%') OR " +
           "CAST(s.admissionNumber AS string) ILIKE CONCAT('%', :search, '%'))")
    fun findBySchoolIdAndIsActiveAndTrackIdAndUserFullNameContaining(
        @Param("schoolId") schoolId: UUID,
        @Param("isActive") isActive: Boolean,
        @Param("trackId") trackId: UUID,
        @Param("search") search: String,
        pageable: Pageable
    ): Page<Student>
    
    @EntityGraph(attributePaths = ["classEnrollments", "classEnrollments.schoolClass"])
    @Query("SELECT s FROM Student s WHERE s.schoolId = :schoolId AND s.isActive = :isActive")
    fun findBySchoolIdAndIsActiveWithEnrollments(
        @Param("schoolId") schoolId: UUID,
        @Param("isActive") isActive: Boolean,
        pageable: Pageable
    ): Page<Student>

    @Query("SELECT s FROM Student s WHERE s.schoolId = :schoolId AND s.isActive = :isActive AND " +
           "EXISTS (SELECT 1 FROM StudentClass ce WHERE ce.student = s AND ce.schoolClass.id IN :classIds AND ce.isActive = true)")
    fun findBySchoolIdAndIsActiveAndClassIdIn(
        @Param("schoolId") schoolId: UUID,
        @Param("isActive") isActive: Boolean,
        @Param("classIds") classIds: List<UUID>,
        pageable: Pageable
    ): Page<Student>

    @Query("SELECT s FROM Student s WHERE s.schoolId = :schoolId AND s.isActive = :isActive AND " +
           "EXISTS (SELECT 1 FROM StudentClass ce WHERE ce.student = s AND ce.schoolClass.id IN :classIds AND ce.isActive = true) AND " +
           "(CAST(s.user.firstName AS string) ILIKE CONCAT('%', :search, '%') OR " +
           "CAST(s.user.lastName AS string) ILIKE CONCAT('%', :search, '%') OR " +
           "CAST(s.studentId AS string) ILIKE CONCAT('%', :search, '%') OR " +
           "CAST(s.admissionNumber AS string) ILIKE CONCAT('%', :search, '%'))")
    fun findBySchoolIdAndIsActiveAndClassIdInAndSearch(
        @Param("schoolId") schoolId: UUID,
        @Param("isActive") isActive: Boolean,
        @Param("classIds") classIds: List<UUID>,
        @Param("search") search: String,
        pageable: Pageable
    ): Page<Student>
}
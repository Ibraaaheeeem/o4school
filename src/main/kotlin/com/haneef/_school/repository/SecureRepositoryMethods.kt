package com.haneef._school.repository

import com.haneef._school.entity.*
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional
import java.util.UUID

/**
 * Extension methods for repositories to provide secure access patterns
 * that automatically validate school ownership
 */

// Student Repository Extensions
interface SecureStudentRepository {
    @Query("SELECT s FROM Student s WHERE s.id = :id AND s.schoolId = :schoolId AND s.isActive = true")
    fun findByIdAndSchoolIdSecure(@Param("id") id: UUID, @Param("schoolId") schoolId: UUID): Optional<Student>
}

// Parent Repository Extensions  
interface SecureParentRepository {
    @Query("SELECT p FROM Parent p WHERE p.id = :id AND p.schoolId = :schoolId AND p.isActive = true")
    fun findByIdAndSchoolIdSecure(@Param("id") id: UUID, @Param("schoolId") schoolId: UUID): Optional<Parent>
}

// Staff Repository Extensions
interface SecureStaffRepository {
    @Query("SELECT s FROM Staff s WHERE s.id = :id AND s.schoolId = :schoolId AND s.isActive = true")
    fun findByIdAndSchoolIdSecure(@Param("id") id: UUID, @Param("schoolId") schoolId: UUID): Optional<Staff>
}

// Subject Repository Extensions
interface SecureSubjectRepository {
    @Query("SELECT s FROM Subject s WHERE s.id = :id AND s.schoolId = :schoolId AND s.isActive = true")
    fun findByIdAndSchoolIdSecure(@Param("id") id: UUID, @Param("schoolId") schoolId: UUID): Optional<Subject>
}

// School Class Repository Extensions
interface SecureSchoolClassRepository {
    @Query("SELECT sc FROM SchoolClass sc WHERE sc.id = :id AND sc.schoolId = :schoolId AND sc.isActive = true")
    fun findByIdAndSchoolIdSecure(@Param("id") id: UUID, @Param("schoolId") schoolId: UUID): Optional<SchoolClass>
}

// Examination Repository Extensions
interface SecureExaminationRepository {
    @Query("SELECT e FROM Examination e WHERE e.id = :id AND e.schoolId = :schoolId AND e.isActive = true")
    fun findByIdAndSchoolIdSecure(@Param("id") id: UUID, @Param("schoolId") schoolId: UUID): Optional<Examination>
}

// Fee Item Repository Extensions
interface SecureFeeItemRepository {
    @Query("SELECT f FROM FeeItem f WHERE f.id = :id AND f.schoolId = :schoolId AND f.isActive = true")
    fun findByIdAndSchoolIdSecure(@Param("id") id: UUID, @Param("schoolId") schoolId: UUID): Optional<FeeItem>
}

// Academic Session Repository Extensions
interface SecureAcademicSessionRepository {
    @Query("SELECT a FROM AcademicSession a WHERE a.id = :id AND a.schoolId = :schoolId AND a.isActive = true")
    fun findByIdAndSchoolIdSecure(@Param("id") id: UUID, @Param("schoolId") schoolId: UUID): Optional<AcademicSession>
}

// Term Repository Extensions
interface SecureTermRepository {
    @Query("SELECT t FROM Term t WHERE t.id = :id AND t.schoolId = :schoolId AND t.isActive = true")
    fun findByIdAndSchoolIdSecure(@Param("id") id: UUID, @Param("schoolId") schoolId: UUID): Optional<Term>
}

// Department Repository Extensions
interface SecureDepartmentRepository {
    @Query("SELECT d FROM Department d WHERE d.id = :id AND d.schoolId = :schoolId AND d.isActive = true")
    fun findByIdAndSchoolIdSecure(@Param("id") id: UUID, @Param("schoolId") schoolId: UUID): Optional<Department>
}

// Education Track Repository Extensions
interface SecureEducationTrackRepository {
    @Query("SELECT e FROM EducationTrack e WHERE e.id = :id AND e.schoolId = :schoolId AND e.isActive = true")
    fun findByIdAndSchoolIdSecure(@Param("id") id: UUID, @Param("schoolId") schoolId: UUID): Optional<EducationTrack>
}
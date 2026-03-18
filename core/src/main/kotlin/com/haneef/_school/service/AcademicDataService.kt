package com.haneef._school.service

import com.haneef._school.entity.*
import com.haneef._school.repository.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import org.slf4j.LoggerFactory

@Service
class AcademicDataService(
    private val classTeacherRepository: ClassTeacherRepository,
    private val subjectTeacherRepository: SubjectTeacherRepository,
    private val studentClassRepository: StudentClassRepository,
    private val classFeeItemRepository: ClassFeeItemRepository,
    private val termRepository: TermRepository,
    private val academicSessionRepository: AcademicSessionRepository,
    private val schoolClassRepository: SchoolClassRepository
) {
    private val logger = LoggerFactory.getLogger(AcademicDataService::class.java)

    @Transactional
    fun importTermRelationships(
        fromTermId: UUID, 
        toTermId: UUID, 
        schoolId: UUID,
        importFees: Boolean = true,
        importStaff: Boolean = true,
        importStudents: Boolean = true,
        studentPromotions: Map<UUID, String> = emptyMap()
    ) {
        // 1. Fetch Terms with School Context
        val fromTerm = termRepository.findByIdAndSchoolIdAndIsActive(fromTermId, schoolId, true)
            ?: throw RuntimeException("Source term not found or access denied")

        val toTerm = termRepository.findByIdAndSchoolIdAndIsActive(toTermId, schoolId, true)
            ?: throw RuntimeException("Destination term not found or access denied")
        
        val toSession = toTerm.academicSession ?: throw RuntimeException("Destination session not found")

        // --- 2. Import Staff (Class & Subject Teachers) ---
        if (importStaff) {
            val sourceClassTeachers = classTeacherRepository.findBySchoolIdAndIsActiveAndTermWithDetails(schoolId, true, fromTermId)
            
            if (sourceClassTeachers.isNotEmpty()) {
                val existingCTMap = classTeacherRepository.findBySchoolIdAndTermId(schoolId, toTermId)
                    .associateBy { "${it.staff.id}-${it.schoolClass.id}" }

                sourceClassTeachers.mapNotNull { ct ->
                    val key = "${ct.staff.id}-${ct.schoolClass.id}"
                    val existing = existingCTMap[key]
                    when {
                        existing == null -> ClassTeacher(ct.staff, ct.schoolClass, toSession, toTerm).apply { 
                            this.schoolId = schoolId; this.isActive = true 
                        }
                        !existing.isActive -> existing.apply { this.isActive = true }
                        else -> null
                    }
                }.chunked(500).forEach { classTeacherRepository.saveAll(it) }
            }

            // Subject Teachers
            val sourceSubjectTeachers = subjectTeacherRepository.findBySchoolIdAndIsActiveAndTermWithDetails(schoolId, true, fromTermId)
            
            if (sourceSubjectTeachers.isNotEmpty()) {
                val existingSTMap = subjectTeacherRepository.findBySchoolIdAndTermId(schoolId, toTermId)
                    .associateBy { "${it.staff.id}-${it.subject.id}-${it.schoolClass.id}" }

                sourceSubjectTeachers.mapNotNull { st ->
                    val key = "${st.staff.id}-${st.subject.id}-${st.schoolClass.id}"
                    val existing = existingSTMap[key]
                    when {
                        existing == null -> SubjectTeacher(st.staff, st.subject, st.schoolClass, toSession, toTerm).apply { 
                            this.schoolId = schoolId; this.isActive = true 
                        }
                        !existing.isActive -> existing.apply { this.isActive = true }
                        else -> null
                    }
                }.chunked(500).forEach { subjectTeacherRepository.saveAll(it) }
            }
        }

        // --- 3. Import Students & Promotions ---
        if (importStudents) {
            val sourceEnrollments = studentClassRepository.findBySchoolIdAndTermIdAndIsActive(schoolId, fromTermId, true)
            
            if (sourceEnrollments.isNotEmpty()) {
                val allClasses = schoolClassRepository.findBySchoolIdAndIsActive(schoolId, true)
                
                // Optimize Promotion Lookup: Map<TrackID, Map<GradeLevel, SchoolClass>>
                val classHierarchy = allClasses.groupBy { it.track?.id }
                    .mapValues { entry -> entry.value.associateBy { it.gradeLevel } }

                // Bulk pre-fetch all existing enrollments for these students in the target term
                val studentIds = sourceEnrollments.map { it.student.id!! }
                val targetEnrollments = studentClassRepository.findBySchoolIdAndStudentIdInAndTermId(schoolId, studentIds, toTermId)
                    .groupBy { it.student.id!! }

                val now = java.time.LocalDate.now()
                sourceEnrollments.mapNotNull { sc ->
                    val action = studentPromotions[sc.id!!] ?: "RETAIN"
                    val currentClass = sc.schoolClass
                    
                    val targetClass = when (action) {
                        "UPGRADE" -> classHierarchy[currentClass.track?.id]?.get((currentClass.gradeLevel ?: 0) + 1) ?: currentClass
                        "DOWNGRADE" -> classHierarchy[currentClass.track?.id]?.get((currentClass.gradeLevel ?: 0) - 1) ?: currentClass
                        else -> currentClass
                    }

                    val existing = targetEnrollments[sc.student.id!!] ?: emptyList()
                    val sameClassRecord = existing.find { it.schoolClass.id == targetClass.id }
                    val activeInTrack = existing.find { it.isActive && it.schoolClass.track?.id == targetClass.track?.id }

                    when {
                        sameClassRecord != null -> if (!sameClassRecord.isActive) sameClassRecord.apply { isActive = true } else null
                        activeInTrack != null -> activeInTrack.apply { schoolClass = targetClass }
                        else -> StudentClass(sc.student, targetClass, toSession, toTerm).apply {
                            this.schoolId = schoolId; this.isActive = true; this.enrollmentDate = now
                        }
                    }
                }.chunked(500).forEach { studentClassRepository.saveAll(it) }
            }
        }

        // --- 4. Import Fee Items ---
        if (importFees) {
            val sourceFees = classFeeItemRepository.findBySchoolIdAndTermIdAndIsActive(schoolId, fromTermId, true)
            
            if (sourceFees.isNotEmpty()) {
                val existingFees = classFeeItemRepository.findBySchoolIdAndTermId(schoolId, toTermId)
                    .associateBy { "${it.schoolClass.id}-${it.feeItem.id}" }

                sourceFees.map { source ->
                    val key = "${source.schoolClass.id}-${source.feeItem.id}"
                    val target = existingFees[key]

                    if (target == null) {
                        ClassFeeItem(source.schoolClass, source.feeItem, toSession, toTerm, toSession.sessionYear, 
                                    source.customAmount, source.isApplicable, source.notes).apply {
                            this.schoolId = schoolId; this.isActive = true
                        }
                    } else {
                        target.apply {
                            this.isActive = true
                            this.customAmount = source.customAmount
                            this.isApplicable = source.isApplicable
                            this.notes = source.notes
                        }
                    }
                }.chunked(500).forEach { classFeeItemRepository.saveAll(it) }
            }
        }

    }
}
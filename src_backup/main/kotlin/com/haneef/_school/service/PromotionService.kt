package com.haneef._school.service

import com.haneef._school.entity.*
import com.haneef._school.repository.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import org.slf4j.LoggerFactory
import java.time.LocalDate

@Service
class PromotionService(
    private val studentClassRepository: StudentClassRepository,
    private val schoolClassRepository: SchoolClassRepository,
    private val academicSessionRepository: AcademicSessionRepository,
    private val termRepository: TermRepository,
    private val studentRepository: StudentRepository
) {
    private val logger = LoggerFactory.getLogger(PromotionService::class.java)

    data class PromotionCandidate(
        val student: Student,
        val currentClass: SchoolClass,
        val recommendedClass: SchoolClass?,
        val availableClasses: List<SchoolClass>
    )

    fun getPromotionCandidates(
        sourceClassId: UUID,
        sourceSessionId: UUID,
        sourceTermId: UUID,
        targetSessionId: UUID,
        targetTermId: UUID,
        schoolId: UUID
    ): List<PromotionCandidate> {
        val currentClass = schoolClassRepository.findById(sourceClassId).orElseThrow { RuntimeException("Class not found") }
        val students = studentClassRepository.findBySchoolClassIdAndAcademicSessionIdAndTermIdAndIsActive(
            sourceClassId, sourceSessionId, sourceTermId, true
        ).map { it.student }

        val targetGradeLevel = (currentClass.gradeLevel ?: 0) + 1
        
        // Find classes in the same track or school that match the target grade level
        val allClassesInSchool = schoolClassRepository.findBySchoolIdAndIsActive(schoolId, true)
        
        val recommendedClass = allClassesInSchool.find { 
            it.track?.id == currentClass.track?.id && it.gradeLevel == targetGradeLevel 
        } ?: allClassesInSchool.find { it.gradeLevel == targetGradeLevel }

        return students.map { student ->
            PromotionCandidate(
                student = student,
                currentClass = currentClass,
                recommendedClass = recommendedClass,
                availableClasses = allClassesInSchool.sortedBy { it.gradeLevel }
            )
        }
    }

    @Transactional
    fun executePromotion(
        targetSessionId: UUID,
        targetTermId: UUID,
        promotions: Map<UUID, UUID?>, // StudentID -> TargetClassID
        schoolId: UUID
    ) {
        val targetSession = academicSessionRepository.findById(targetSessionId).orElseThrow { RuntimeException("Target session not found") }
        val targetTerm = termRepository.findById(targetTermId).orElseThrow { RuntimeException("Target term not found") }

        if (targetSession.schoolId != schoolId || targetTerm.schoolId != schoolId) {
            throw RuntimeException("Unauthorized access to session or term")
        }

        promotions.forEach { (studentId, targetClassId) ->
            if (targetClassId != null) {
                val student = studentRepository.findById(studentId).orElseThrow { RuntimeException("Student not found") }
                val targetClass = schoolClassRepository.findById(targetClassId).orElseThrow { RuntimeException("Target class not found") }

                if (targetClass.schoolId != schoolId) {
                    throw RuntimeException("Unauthorized access to target class")
                }

                // Check for existing enrollment to avoid duplicates
                val existing = studentClassRepository.findByStudentIdAndAcademicSessionIdAndTermIdAndIsActive(
                    studentId, targetSessionId, targetTermId, true
                )

                if (existing.isEmpty()) {
                    val newEnrollment = StudentClass(
                        student = student,
                        schoolClass = targetClass,
                        academicSession = targetSession,
                        term = targetTerm
                    ).apply {
                        this.schoolId = schoolId
                        this.isActive = true
                        this.enrollmentDate = LocalDate.now()
                    }
                    studentClassRepository.save(newEnrollment)
                } else {
                    // Update existing if it's different? 
                    // Usually we don't want to overwrite unless explicitly asked.
                    // For now, we skip if already enrolled in something.
                    logger.warn("Student {} already enrolled in session {} term {}", studentId, targetSessionId, targetTermId)
                }
            }
        }
        
        logger.info("Executed promotion for {} students in school {}", promotions.size, schoolId)
    }
}

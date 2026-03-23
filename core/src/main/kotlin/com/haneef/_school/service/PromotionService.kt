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
        val currentClass = schoolClassRepository.findByIdAndSchoolIdSecure(sourceClassId, schoolId)
            .orElseThrow { RuntimeException("Class not found for this school") }

        val sourceSession = academicSessionRepository.findByIdAndSchoolIdSecure(sourceSessionId, schoolId)
            .orElseThrow { RuntimeException("Source session not found for this school") }
        val sourceTerm = termRepository.findByIdAndSchoolIdSecure(sourceTermId, schoolId)
            .orElseThrow { RuntimeException("Source term not found for this school") }
        val targetSession = academicSessionRepository.findByIdAndSchoolIdSecure(targetSessionId, schoolId)
            .orElseThrow { RuntimeException("Target session not found for this school") }
        val targetTerm = termRepository.findByIdAndSchoolIdSecure(targetTermId, schoolId)
            .orElseThrow { RuntimeException("Target term not found for this school") }

        if (sourceTerm.academicSession.id != sourceSession.id) {
            throw RuntimeException("Source term does not belong to source session")
        }
        if (targetTerm.academicSession.id != targetSession.id) {
            throw RuntimeException("Target term does not belong to target session")
        }

        val students = studentClassRepository.findBySchoolClassIdAndAcademicSessionIdAndTermIdAndIsActive(
            sourceClassId, sourceSessionId, sourceTermId, true
        ).map { it.student }
            .filter { it.schoolId == schoolId }

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
                availableClasses = allClassesInSchool.sortedWith(compareBy(nullsLast()) { it.gradeLevel })
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
        val targetSession = academicSessionRepository.findByIdAndSchoolIdSecure(targetSessionId, schoolId)
            .orElseThrow { RuntimeException("Target session not found for this school") }
        val targetTerm = termRepository.findByIdAndSchoolIdSecure(targetTermId, schoolId)
            .orElseThrow { RuntimeException("Target term not found for this school") }

        if (targetTerm.academicSession.id != targetSession.id) {
            throw RuntimeException("Target term does not belong to target session")
        }

        promotions.forEach { (studentId, targetClassId) ->
            if (targetClassId != null) {
                val student = studentRepository.findByIdAndSchoolIdSecure(studentId, schoolId)
                    .orElseThrow { RuntimeException("Student not found for this school") }
                val targetClass = schoolClassRepository.findByIdAndSchoolIdSecure(targetClassId, schoolId)
                    .orElseThrow { RuntimeException("Target class not found for this school") }

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
                    val alreadyInTargetClass = existing.any { it.schoolClass.id == targetClass.id }
                    if (alreadyInTargetClass) {
                        logger.info(
                            "Skipping promotion for student {} - already enrolled in target class {} for session {} term {}",
                            studentId,
                            targetClassId,
                            targetSessionId,
                            targetTermId
                        )
                    } else {
                        val enrolledClassIds = existing.mapNotNull { it.schoolClass.id }.joinToString(",")
                        throw RuntimeException(
                            "Student $studentId already has enrollment(s) in target session/term in class(es): $enrolledClassIds"
                        )
                    }
                }
            }
        }
        
        logger.info("Executed promotion for {} students in school {}", promotions.size, schoolId)
    }
}

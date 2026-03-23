package com.haneef._school.service

import com.haneef._school.entity.AcademicSession
import com.haneef._school.entity.EducationTrack
import com.haneef._school.entity.SchoolClass
import com.haneef._school.entity.Student
import com.haneef._school.entity.StudentClass
import com.haneef._school.entity.Term
import com.haneef._school.repository.AcademicSessionRepository
import com.haneef._school.repository.SchoolClassRepository
import com.haneef._school.repository.StudentClassRepository
import com.haneef._school.repository.StudentRepository
import com.haneef._school.repository.TermRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

class PromotionServiceTest {

    private val studentClassRepository = mockk<StudentClassRepository>(relaxed = true)
    private val schoolClassRepository = mockk<SchoolClassRepository>(relaxed = true)
    private val academicSessionRepository = mockk<AcademicSessionRepository>(relaxed = true)
    private val termRepository = mockk<TermRepository>(relaxed = true)
    private val studentRepository = mockk<StudentRepository>(relaxed = true)

    private val service = PromotionService(
        studentClassRepository,
        schoolClassRepository,
        academicSessionRepository,
        termRepository,
        studentRepository
    )

    init {
        every { studentClassRepository.save(any<StudentClass>()) } answers {
            this.invocation.args[0] as StudentClass
        }
    }

    @Test
    fun `executePromotion saves enrollment when student has no existing enrollment in target session and term`() {
        val schoolId = UUID.randomUUID()
        val targetSessionId = UUID.randomUUID()
        val targetTermId = UUID.randomUUID()
        val studentId = UUID.randomUUID()
        val targetClassId = UUID.randomUUID()

        val targetSession = mockk<AcademicSession>(relaxed = true)
        val targetTerm = mockk<Term>(relaxed = true)
        val student = mockk<Student>(relaxed = true)
        val targetClass = mockk<SchoolClass>(relaxed = true)

        every { targetSession.id } returns targetSessionId
        every { targetTerm.id } returns targetTermId
        every { targetTerm.academicSession } returns targetSession

        every { academicSessionRepository.findByIdAndSchoolIdSecure(targetSessionId, schoolId) } returns Optional.of(targetSession)
        every { termRepository.findByIdAndSchoolIdSecure(targetTermId, schoolId) } returns Optional.of(targetTerm)
        every { studentRepository.findByIdAndSchoolIdSecure(studentId, schoolId) } returns Optional.of(student)
        every { schoolClassRepository.findByIdAndSchoolIdSecure(targetClassId, schoolId) } returns Optional.of(targetClass)
        every {
            studentClassRepository.findByStudentIdAndAcademicSessionIdAndTermIdAndIsActive(
                studentId,
                targetSessionId,
                targetTermId,
                true
            )
        } returns emptyList()

        service.executePromotion(
            targetSessionId = targetSessionId,
            targetTermId = targetTermId,
            promotions = mapOf(studentId to targetClassId),
            schoolId = schoolId
        )

        verify(exactly = 1) { studentClassRepository.save(any<StudentClass>()) }
    }

    @Test
    fun `getPromotionCandidates returns candidates with same-track recommendation and null-safe class ordering`() {
        val schoolId = UUID.randomUUID()
        val sourceClassId = UUID.randomUUID()
        val sourceSessionId = UUID.randomUUID()
        val sourceTermId = UUID.randomUUID()
        val targetSessionId = UUID.randomUUID()
        val targetTermId = UUID.randomUUID()
        val trackId = UUID.randomUUID()

        val sourceSession = mockk<AcademicSession>(relaxed = true)
        val targetSession = mockk<AcademicSession>(relaxed = true)
        val sourceTerm = mockk<Term>(relaxed = true)
        val targetTerm = mockk<Term>(relaxed = true)
        val currentClass = mockk<SchoolClass>(relaxed = true)
        val sameTrackRecommended = mockk<SchoolClass>(relaxed = true)
        val nullGradeClass = mockk<SchoolClass>(relaxed = true)
        val sourceTrack = mockk<EducationTrack>(relaxed = true)
        val recommendedTrack = mockk<EducationTrack>(relaxed = true)
        val student = Student().apply {
            this.schoolId = schoolId
            this.studentId = "STD-1"
            this.admissionDate = LocalDate.now()
        }
        val foreignStudent = Student().apply {
            this.schoolId = UUID.randomUUID()
            this.studentId = "STD-2"
            this.admissionDate = LocalDate.now()
        }
        val studentClass1 = mockk<StudentClass>(relaxed = true)
        val studentClass2 = mockk<StudentClass>(relaxed = true)

        every { sourceSession.id } returns sourceSessionId
        every { targetSession.id } returns targetSessionId
        every { sourceTerm.academicSession } returns sourceSession
        every { targetTerm.academicSession } returns targetSession

        every { sourceTrack.id } returns trackId
        every { recommendedTrack.id } returns trackId
        every { currentClass.gradeLevel } returns 5
        every { currentClass.track } returns sourceTrack
        every { sameTrackRecommended.gradeLevel } returns 6
        every { sameTrackRecommended.track } returns recommendedTrack
        every { nullGradeClass.gradeLevel } returns null

        every { studentClass1.student } returns student
        every { studentClass2.student } returns foreignStudent

        every { schoolClassRepository.findByIdAndSchoolIdSecure(sourceClassId, schoolId) } returns Optional.of(currentClass)
        every { academicSessionRepository.findByIdAndSchoolIdSecure(sourceSessionId, schoolId) } returns Optional.of(sourceSession)
        every { academicSessionRepository.findByIdAndSchoolIdSecure(targetSessionId, schoolId) } returns Optional.of(targetSession)
        every { termRepository.findByIdAndSchoolIdSecure(sourceTermId, schoolId) } returns Optional.of(sourceTerm)
        every { termRepository.findByIdAndSchoolIdSecure(targetTermId, schoolId) } returns Optional.of(targetTerm)
        every {
            studentClassRepository.findBySchoolClassIdAndAcademicSessionIdAndTermIdAndIsActive(
                sourceClassId,
                sourceSessionId,
                sourceTermId,
                true
            )
        } returns listOf(studentClass1, studentClass2)
        every { schoolClassRepository.findBySchoolIdAndIsActive(schoolId, true) } returns listOf(nullGradeClass, sameTrackRecommended)

        val candidates = service.getPromotionCandidates(
            sourceClassId = sourceClassId,
            sourceSessionId = sourceSessionId,
            sourceTermId = sourceTermId,
            targetSessionId = targetSessionId,
            targetTermId = targetTermId,
            schoolId = schoolId
        )

        assertEquals(1, candidates.size)
        assertEquals(student, candidates.first().student)
        assertEquals(sameTrackRecommended, candidates.first().recommendedClass)
        assertEquals(6, candidates.first().availableClasses.first().gradeLevel)
        assertNull(candidates.first().availableClasses.last().gradeLevel)
    }

    @Test
    fun `getPromotionCandidates falls back to school-wide recommendation when track match is unavailable`() {
        val schoolId = UUID.randomUUID()
        val sourceClassId = UUID.randomUUID()
        val sourceSessionId = UUID.randomUUID()
        val sourceTermId = UUID.randomUUID()
        val targetSessionId = UUID.randomUUID()
        val targetTermId = UUID.randomUUID()

        val sourceSession = mockk<AcademicSession>(relaxed = true)
        val targetSession = mockk<AcademicSession>(relaxed = true)
        val sourceTerm = mockk<Term>(relaxed = true)
        val targetTerm = mockk<Term>(relaxed = true)
        val currentClass = mockk<SchoolClass>(relaxed = true)
        val fallbackClass = mockk<SchoolClass>(relaxed = true)
        val student = Student().apply {
            this.schoolId = schoolId
            this.studentId = "STD-3"
            this.admissionDate = LocalDate.now()
        }
        val enrollment = mockk<StudentClass>(relaxed = true)

        every { sourceSession.id } returns sourceSessionId
        every { targetSession.id } returns targetSessionId
        every { sourceTerm.academicSession } returns sourceSession
        every { targetTerm.academicSession } returns targetSession
        every { currentClass.gradeLevel } returns 2
        every { currentClass.track } returns null
        every { fallbackClass.gradeLevel } returns 3
        every { fallbackClass.track } returns null
        every { enrollment.student } returns student

        every { schoolClassRepository.findByIdAndSchoolIdSecure(sourceClassId, schoolId) } returns Optional.of(currentClass)
        every { academicSessionRepository.findByIdAndSchoolIdSecure(sourceSessionId, schoolId) } returns Optional.of(sourceSession)
        every { academicSessionRepository.findByIdAndSchoolIdSecure(targetSessionId, schoolId) } returns Optional.of(targetSession)
        every { termRepository.findByIdAndSchoolIdSecure(sourceTermId, schoolId) } returns Optional.of(sourceTerm)
        every { termRepository.findByIdAndSchoolIdSecure(targetTermId, schoolId) } returns Optional.of(targetTerm)
        every {
            studentClassRepository.findBySchoolClassIdAndAcademicSessionIdAndTermIdAndIsActive(
                sourceClassId,
                sourceSessionId,
                sourceTermId,
                true
            )
        } returns listOf(enrollment)
        every { schoolClassRepository.findBySchoolIdAndIsActive(schoolId, true) } returns listOf(fallbackClass)

        val candidates = service.getPromotionCandidates(
            sourceClassId,
            sourceSessionId,
            sourceTermId,
            targetSessionId,
            targetTermId,
            schoolId
        )

        assertEquals(1, candidates.size)
        assertEquals(fallbackClass, candidates.first().recommendedClass)
    }

    @Test
    fun `getPromotionCandidates returns null recommendation when no matching class exists`() {
        val schoolId = UUID.randomUUID()
        val sourceClassId = UUID.randomUUID()
        val sourceSessionId = UUID.randomUUID()
        val sourceTermId = UUID.randomUUID()
        val targetSessionId = UUID.randomUUID()
        val targetTermId = UUID.randomUUID()

        val sourceSession = mockk<AcademicSession>(relaxed = true)
        val targetSession = mockk<AcademicSession>(relaxed = true)
        val sourceTerm = mockk<Term>(relaxed = true)
        val targetTerm = mockk<Term>(relaxed = true)
        val currentClass = mockk<SchoolClass>(relaxed = true)
        val otherClass = mockk<SchoolClass>(relaxed = true)
        val student = Student().apply {
            this.schoolId = schoolId
            this.studentId = "STD-4"
            this.admissionDate = LocalDate.now()
        }
        val enrollment = mockk<StudentClass>(relaxed = true)

        every { sourceSession.id } returns sourceSessionId
        every { targetSession.id } returns targetSessionId
        every { sourceTerm.academicSession } returns sourceSession
        every { targetTerm.academicSession } returns targetSession
        every { currentClass.gradeLevel } returns 10
        every { currentClass.track } returns null
        every { otherClass.gradeLevel } returns 8
        every { enrollment.student } returns student

        every { schoolClassRepository.findByIdAndSchoolIdSecure(sourceClassId, schoolId) } returns Optional.of(currentClass)
        every { academicSessionRepository.findByIdAndSchoolIdSecure(sourceSessionId, schoolId) } returns Optional.of(sourceSession)
        every { academicSessionRepository.findByIdAndSchoolIdSecure(targetSessionId, schoolId) } returns Optional.of(targetSession)
        every { termRepository.findByIdAndSchoolIdSecure(sourceTermId, schoolId) } returns Optional.of(sourceTerm)
        every { termRepository.findByIdAndSchoolIdSecure(targetTermId, schoolId) } returns Optional.of(targetTerm)
        every {
            studentClassRepository.findBySchoolClassIdAndAcademicSessionIdAndTermIdAndIsActive(
                sourceClassId,
                sourceSessionId,
                sourceTermId,
                true
            )
        } returns listOf(enrollment)
        every { schoolClassRepository.findBySchoolIdAndIsActive(schoolId, true) } returns listOf(otherClass)

        val candidates = service.getPromotionCandidates(
            sourceClassId,
            sourceSessionId,
            sourceTermId,
            targetSessionId,
            targetTermId,
            schoolId
        )

        assertEquals(1, candidates.size)
        assertNull(candidates.first().recommendedClass)
    }

    @Test
    fun `getPromotionCandidates throws when source term does not belong to source session`() {
        val schoolId = UUID.randomUUID()
        val sourceClassId = UUID.randomUUID()
        val sourceSessionId = UUID.randomUUID()
        val sourceTermId = UUID.randomUUID()
        val targetSessionId = UUID.randomUUID()
        val targetTermId = UUID.randomUUID()

        val sourceSession = mockk<AcademicSession>(relaxed = true)
        val otherSourceSession = mockk<AcademicSession>(relaxed = true)
        val targetSession = mockk<AcademicSession>(relaxed = true)
        val sourceTerm = mockk<Term>(relaxed = true)
        val targetTerm = mockk<Term>(relaxed = true)
        val currentClass = mockk<SchoolClass>(relaxed = true)

        every { sourceSession.id } returns sourceSessionId
        every { otherSourceSession.id } returns UUID.randomUUID()
        every { targetSession.id } returns targetSessionId
        every { sourceTerm.academicSession } returns otherSourceSession
        every { targetTerm.academicSession } returns targetSession

        every { schoolClassRepository.findByIdAndSchoolIdSecure(sourceClassId, schoolId) } returns Optional.of(currentClass)
        every { academicSessionRepository.findByIdAndSchoolIdSecure(sourceSessionId, schoolId) } returns Optional.of(sourceSession)
        every { academicSessionRepository.findByIdAndSchoolIdSecure(targetSessionId, schoolId) } returns Optional.of(targetSession)
        every { termRepository.findByIdAndSchoolIdSecure(sourceTermId, schoolId) } returns Optional.of(sourceTerm)
        every { termRepository.findByIdAndSchoolIdSecure(targetTermId, schoolId) } returns Optional.of(targetTerm)

        val ex = assertThrows(RuntimeException::class.java) {
            service.getPromotionCandidates(
                sourceClassId,
                sourceSessionId,
                sourceTermId,
                targetSessionId,
                targetTermId,
                schoolId
            )
        }

        assertTrue(ex.message!!.contains("Source term does not belong"))
    }

    @Test
    fun `getPromotionCandidates throws when target term does not belong to target session`() {
        val schoolId = UUID.randomUUID()
        val sourceClassId = UUID.randomUUID()
        val sourceSessionId = UUID.randomUUID()
        val sourceTermId = UUID.randomUUID()
        val targetSessionId = UUID.randomUUID()
        val targetTermId = UUID.randomUUID()

        val sourceSession = mockk<AcademicSession>(relaxed = true)
        val targetSession = mockk<AcademicSession>(relaxed = true)
        val otherTargetSession = mockk<AcademicSession>(relaxed = true)
        val sourceTerm = mockk<Term>(relaxed = true)
        val targetTerm = mockk<Term>(relaxed = true)
        val currentClass = mockk<SchoolClass>(relaxed = true)

        every { sourceSession.id } returns sourceSessionId
        every { targetSession.id } returns targetSessionId
        every { otherTargetSession.id } returns UUID.randomUUID()
        every { sourceTerm.academicSession } returns sourceSession
        every { targetTerm.academicSession } returns otherTargetSession

        every { schoolClassRepository.findByIdAndSchoolIdSecure(sourceClassId, schoolId) } returns Optional.of(currentClass)
        every { academicSessionRepository.findByIdAndSchoolIdSecure(sourceSessionId, schoolId) } returns Optional.of(sourceSession)
        every { academicSessionRepository.findByIdAndSchoolIdSecure(targetSessionId, schoolId) } returns Optional.of(targetSession)
        every { termRepository.findByIdAndSchoolIdSecure(sourceTermId, schoolId) } returns Optional.of(sourceTerm)
        every { termRepository.findByIdAndSchoolIdSecure(targetTermId, schoolId) } returns Optional.of(targetTerm)

        val ex = assertThrows(RuntimeException::class.java) {
            service.getPromotionCandidates(
                sourceClassId,
                sourceSessionId,
                sourceTermId,
                targetSessionId,
                targetTermId,
                schoolId
            )
        }

        assertTrue(ex.message!!.contains("Target term does not belong"))
    }

    @Test
    fun `executePromotion throws when target term does not belong to target session`() {
        val schoolId = UUID.randomUUID()
        val targetSessionId = UUID.randomUUID()
        val targetTermId = UUID.randomUUID()
        val targetSession = mockk<AcademicSession>(relaxed = true)
        val otherSession = mockk<AcademicSession>(relaxed = true)
        val targetTerm = mockk<Term>(relaxed = true)

        every { targetSession.id } returns targetSessionId
        every { otherSession.id } returns UUID.randomUUID()
        every { targetTerm.academicSession } returns otherSession
        every { academicSessionRepository.findByIdAndSchoolIdSecure(targetSessionId, schoolId) } returns Optional.of(targetSession)
        every { termRepository.findByIdAndSchoolIdSecure(targetTermId, schoolId) } returns Optional.of(targetTerm)

        val ex = assertThrows(RuntimeException::class.java) {
            service.executePromotion(
                targetSessionId = targetSessionId,
                targetTermId = targetTermId,
                promotions = emptyMap(),
                schoolId = schoolId
            )
        }

        assertTrue(ex.message!!.contains("Target term does not belong"))
    }

    @Test
    fun `executePromotion skips null target class entries`() {
        val schoolId = UUID.randomUUID()
        val targetSessionId = UUID.randomUUID()
        val targetTermId = UUID.randomUUID()
        val studentId = UUID.randomUUID()
        val targetSession = mockk<AcademicSession>(relaxed = true)
        val targetTerm = mockk<Term>(relaxed = true)

        every { targetSession.id } returns targetSessionId
        every { targetTerm.academicSession } returns targetSession
        every { academicSessionRepository.findByIdAndSchoolIdSecure(targetSessionId, schoolId) } returns Optional.of(targetSession)
        every { termRepository.findByIdAndSchoolIdSecure(targetTermId, schoolId) } returns Optional.of(targetTerm)

        service.executePromotion(
            targetSessionId = targetSessionId,
            targetTermId = targetTermId,
            promotions = mapOf(studentId to null),
            schoolId = schoolId
        )

        verify(exactly = 0) { studentRepository.findByIdAndSchoolIdSecure(any(), any()) }
        verify(exactly = 0) { studentClassRepository.save(any<StudentClass>()) }
    }

    @Test
    fun `executePromotion skips when student is already enrolled in same target class for target session and term`() {
        val schoolId = UUID.randomUUID()
        val targetSessionId = UUID.randomUUID()
        val targetTermId = UUID.randomUUID()
        val studentId = UUID.randomUUID()
        val targetClassId = UUID.randomUUID()

        val targetSession = mockk<AcademicSession>(relaxed = true)
        val targetTerm = mockk<Term>(relaxed = true)
        val student = mockk<Student>(relaxed = true)
        val targetClass = mockk<SchoolClass>(relaxed = true)
        val existingEnrollment = mockk<StudentClass>(relaxed = true)

        every { targetSession.id } returns targetSessionId
        every { targetTerm.id } returns targetTermId
        every { targetTerm.academicSession } returns targetSession

        every { targetClass.id } returns targetClassId
        every { existingEnrollment.schoolClass } returns targetClass

        every { academicSessionRepository.findByIdAndSchoolIdSecure(targetSessionId, schoolId) } returns Optional.of(targetSession)
        every { termRepository.findByIdAndSchoolIdSecure(targetTermId, schoolId) } returns Optional.of(targetTerm)
        every { studentRepository.findByIdAndSchoolIdSecure(studentId, schoolId) } returns Optional.of(student)
        every { schoolClassRepository.findByIdAndSchoolIdSecure(targetClassId, schoolId) } returns Optional.of(targetClass)
        every {
            studentClassRepository.findByStudentIdAndAcademicSessionIdAndTermIdAndIsActive(
                studentId,
                targetSessionId,
                targetTermId,
                true
            )
        } returns listOf(existingEnrollment)

        service.executePromotion(
            targetSessionId = targetSessionId,
            targetTermId = targetTermId,
            promotions = mapOf(studentId to targetClassId),
            schoolId = schoolId
        )

        verify(exactly = 0) { studentClassRepository.save(any<StudentClass>()) }
    }

    @Test
    fun `executePromotion throws conflict when student already has different class enrollment in target session and term`() {
        val schoolId = UUID.randomUUID()
        val targetSessionId = UUID.randomUUID()
        val targetTermId = UUID.randomUUID()
        val studentId = UUID.randomUUID()
        val targetClassId = UUID.randomUUID()
        val otherClassId = UUID.randomUUID()

        val targetSession = mockk<AcademicSession>(relaxed = true)
        val targetTerm = mockk<Term>(relaxed = true)
        val student = mockk<Student>(relaxed = true)
        val targetClass = mockk<SchoolClass>(relaxed = true)
        val otherClass = mockk<SchoolClass>(relaxed = true)
        val existingEnrollment = mockk<StudentClass>(relaxed = true)

        every { targetSession.id } returns targetSessionId
        every { targetTerm.id } returns targetTermId
        every { targetTerm.academicSession } returns targetSession

        every { targetClass.id } returns targetClassId
        every { otherClass.id } returns otherClassId
        every { existingEnrollment.schoolClass } returns otherClass

        every { academicSessionRepository.findByIdAndSchoolIdSecure(targetSessionId, schoolId) } returns Optional.of(targetSession)
        every { termRepository.findByIdAndSchoolIdSecure(targetTermId, schoolId) } returns Optional.of(targetTerm)
        every { studentRepository.findByIdAndSchoolIdSecure(studentId, schoolId) } returns Optional.of(student)
        every { schoolClassRepository.findByIdAndSchoolIdSecure(targetClassId, schoolId) } returns Optional.of(targetClass)
        every {
            studentClassRepository.findByStudentIdAndAcademicSessionIdAndTermIdAndIsActive(
                studentId,
                targetSessionId,
                targetTermId,
                true
            )
        } returns listOf(existingEnrollment)

        assertThrows(RuntimeException::class.java) {
            service.executePromotion(
                targetSessionId = targetSessionId,
                targetTermId = targetTermId,
                promotions = mapOf(studentId to targetClassId),
                schoolId = schoolId
            )
        }

        verify(exactly = 0) { studentClassRepository.save(any<StudentClass>()) }
    }
}

package com.haneef._school.security

import com.haneef._school.entity.*
import com.haneef._school.repository.*
import com.haneef._school.service.AuthorizationService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.*
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.access.AccessDeniedException
import org.springframework.test.context.ActiveProfiles
import java.util.*

@SpringBootTest
@ActiveProfiles("test")
class AuthorizationServiceTest {

    private val studentRepository = mock(StudentRepository::class.java)
    private val parentRepository = mock(ParentRepository::class.java)
    private val staffRepository = mock(StaffRepository::class.java)
    private val subjectRepository = mock(SubjectRepository::class.java)
    private val schoolClassRepository = mock(SchoolClassRepository::class.java)
    private val examinationRepository = mock(ExaminationRepository::class.java)
    private val feeItemRepository = mock(FeeItemRepository::class.java)
    private val academicSessionRepository = mock(AcademicSessionRepository::class.java)
    private val termRepository = mock(TermRepository::class.java)
    private val departmentRepository = mock(DepartmentRepository::class.java)
    private val educationTrackRepository = mock(EducationTrackRepository::class.java)

    private val authorizationService = AuthorizationService(
        studentRepository,
        parentRepository,
        staffRepository,
        subjectRepository,
        schoolClassRepository,
        examinationRepository,
        feeItemRepository,
        academicSessionRepository,
        termRepository,
        departmentRepository,
        educationTrackRepository
    )

    @Test
    fun `validateAndGetStudent should return student when school matches`() {
        val schoolId = UUID.randomUUID()
        val studentId = UUID.randomUUID()
        val student = Student().apply {
            id = studentId
            this.schoolId = schoolId
        }

        `when`(studentRepository.findById(studentId)).thenReturn(Optional.of(student))

        val result = authorizationService.validateAndGetStudent(studentId, schoolId)
        assert(result.id == studentId)
    }

    @Test
    fun `validateAndGetStudent should throw exception when school doesn't match`() {
        val schoolId = UUID.randomUUID()
        val differentSchoolId = UUID.randomUUID()
        val studentId = UUID.randomUUID()
        val student = Student().apply {
            id = studentId
            this.schoolId = differentSchoolId
        }

        `when`(studentRepository.findById(studentId)).thenReturn(Optional.of(student))

        assertThrows<AccessDeniedException> {
            authorizationService.validateAndGetStudent(studentId, schoolId)
        }
    }

    @Test
    fun `validateAndGetStudent should throw exception when student not found`() {
        val schoolId = UUID.randomUUID()
        val studentId = UUID.randomUUID()

        `when`(studentRepository.findById(studentId)).thenReturn(Optional.empty())

        assertThrows<RuntimeException> {
            authorizationService.validateAndGetStudent(studentId, schoolId)
        }
    }

    @Test
    fun `validateAndGetSubject should return subject when school matches`() {
        val schoolId = UUID.randomUUID()
        val subjectId = UUID.randomUUID()
        val subject = Subject().apply {
            id = subjectId
            this.schoolId = schoolId
        }

        `when`(subjectRepository.findById(subjectId)).thenReturn(Optional.of(subject))

        val result = authorizationService.validateAndGetSubject(subjectId, schoolId)
        assert(result.id == subjectId)
    }

    @Test
    fun `validateAndGetSubject should throw exception when school doesn't match`() {
        val schoolId = UUID.randomUUID()
        val differentSchoolId = UUID.randomUUID()
        val subjectId = UUID.randomUUID()
        val subject = Subject().apply {
            id = subjectId
            this.schoolId = differentSchoolId
        }

        `when`(subjectRepository.findById(subjectId)).thenReturn(Optional.of(subject))

        assertThrows<AccessDeniedException> {
            authorizationService.validateAndGetSubject(subjectId, schoolId)
        }
    }

    @Test
    fun `validateSchoolAccess should return schoolId when not null`() {
        val schoolId = UUID.randomUUID()
        val result = authorizationService.validateSchoolAccess(schoolId)
        assert(result == schoolId)
    }

    @Test
    fun `validateSchoolAccess should throw exception when null`() {
        assertThrows<AccessDeniedException> {
            authorizationService.validateSchoolAccess(null)
        }
    }
}
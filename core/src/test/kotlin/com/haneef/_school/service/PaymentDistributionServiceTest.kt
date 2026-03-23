package com.haneef._school.service

import com.haneef._school.entity.AcademicSession
import com.haneef._school.entity.ClassFeeItem
import com.haneef._school.entity.FeeItem
import com.haneef._school.entity.Parent
import com.haneef._school.entity.ParentStudent
import com.haneef._school.entity.PaymentAllocation
import com.haneef._school.entity.SchoolClass
import com.haneef._school.entity.Settlement
import com.haneef._school.entity.Student
import com.haneef._school.entity.StudentClass
import com.haneef._school.entity.Term
import com.haneef._school.repository.ClassFeeItemRepository
import com.haneef._school.repository.PaymentAllocationRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class PaymentDistributionServiceTest {

    private val paymentAllocationRepository = mockk<PaymentAllocationRepository>(relaxed = true)
    private val classFeeItemRepository = mockk<ClassFeeItemRepository>(relaxed = true)

    private val service = PaymentDistributionService(
        paymentAllocationRepository = paymentAllocationRepository,
        classFeeItemRepository = classFeeItemRepository
    )

    @Test
    fun `distributePaymentSequentially returns empty list for non-positive amount`() {
        val settlement = Settlement(
            parent = Parent().apply { id = UUID.randomUUID() },
            amount = BigDecimal.ZERO,
            reference = "ref-zero",
            status = "SUCCESS"
        )

        val allocations = service.distributePaymentSequentially(settlement)

        assertTrue(allocations.isEmpty())
        verify(exactly = 0) { paymentAllocationRepository.findByParentIdAndSessionAndTerm(any(), any(), any()) }
        verify(exactly = 0) { paymentAllocationRepository.save(any()) }
    }

    @Test
    fun `distributePaymentSequentially uses enrollment matching settlement session and term`() {
        val parentId = UUID.randomUUID()
        val schoolId = UUID.randomUUID()

        val targetSession = AcademicSession().apply { id = UUID.randomUUID() }
        val targetTerm = Term().apply {
            id = UUID.randomUUID()
            academicSession = targetSession
        }

        val otherSession = AcademicSession().apply { id = UUID.randomUUID() }
        val otherTerm = Term().apply {
            id = UUID.randomUUID()
            academicSession = otherSession
        }

        val targetClassId = UUID.randomUUID()
        val otherClassId = UUID.randomUUID()
        val targetClass = SchoolClass().apply { id = targetClassId }
        val otherClass = SchoolClass().apply { id = otherClassId }

        val student = Student().apply {
            id = UUID.randomUUID()
            this.schoolId = schoolId
            createdAt = LocalDateTime.now().minusDays(1)
        }

        val matchingEnrollment = StudentClass().apply {
            this.student = student
            this.schoolClass = targetClass
            this.academicSession = targetSession
            this.term = targetTerm
            this.isActive = true
        }
        val nonMatchingEnrollment = StudentClass().apply {
            this.student = student
            this.schoolClass = otherClass
            this.academicSession = otherSession
            this.term = otherTerm
            this.isActive = true
        }
        student.classEnrollments.add(nonMatchingEnrollment)
        student.classEnrollments.add(matchingEnrollment)

        val parent = Parent().apply {
            id = parentId
            this.schoolId = schoolId
        }
        val relationship = ParentStudent().apply {
            this.parent = parent
            this.student = student
            this.relationshipType = "guardian"
            this.isActive = true
        }
        parent.studentRelationships.add(relationship)

        val settlement = Settlement(
            parent = parent,
            amount = BigDecimal("1500.00"),
            reference = "ref-target",
            status = "SUCCESS"
        ).apply {
            this.schoolId = schoolId
            this.academicSession = targetSession
            this.term = targetTerm
            this.transactionDate = LocalDateTime.now()
        }

        every { paymentAllocationRepository.findByParentIdAndSessionAndTerm(parentId, targetSession.id, targetTerm.id) } returns emptyList()

        val targetFee = ClassFeeItem().apply {
            this.schoolClass = targetClass
            this.feeItem = FeeItem().apply { amount = BigDecimal("2000.00") }
            this.customAmount = null
        }
        val otherFee = ClassFeeItem().apply {
            this.schoolClass = otherClass
            this.feeItem = FeeItem().apply { amount = BigDecimal("1000.00") }
            this.customAmount = null
        }

        every {
            classFeeItemRepository.findBySchoolClassIdAndAcademicSessionIdAndTermIdFilters(
                targetClassId,
                targetSession.id!!,
                targetTerm.id!!,
                true
            )
        } returns listOf(targetFee)
        every {
            classFeeItemRepository.findBySchoolClassIdAndAcademicSessionIdAndTermIdFilters(
                otherClassId,
                targetSession.id!!,
                targetTerm.id!!,
                true
            )
        } returns listOf(otherFee)

        every { paymentAllocationRepository.save(any()) } answers { firstArg() }

        val allocations = service.distributePaymentSequentially(settlement)

        assertEquals(1, allocations.size)
        assertEquals(BigDecimal("1500.00"), allocations.first().allocatedAmount)
        verify(exactly = 1) {
            classFeeItemRepository.findBySchoolClassIdAndAcademicSessionIdAndTermIdFilters(
                targetClassId,
                targetSession.id!!,
                targetTerm.id!!,
                true
            )
        }
    }

    @Test
    fun `distributePaymentSequentially preloads previous allocations and avoids per-child total queries`() {
        val parentId = UUID.randomUUID()
        val schoolId = UUID.randomUUID()

        val session = AcademicSession().apply { id = UUID.randomUUID() }
        val term = Term().apply {
            id = UUID.randomUUID()
            academicSession = session
        }

        val classId = UUID.randomUUID()
        val schoolClass = SchoolClass().apply { id = classId }

        val studentOne = Student().apply {
            id = UUID.randomUUID()
            this.schoolId = schoolId
            createdAt = LocalDateTime.now().minusDays(2)
            studentId = "S1"
            admissionDate = LocalDate.now()
        }
        val studentTwo = Student().apply {
            id = UUID.randomUUID()
            this.schoolId = schoolId
            createdAt = LocalDateTime.now().minusDays(1)
            studentId = "S2"
            admissionDate = LocalDate.now()
        }

        studentOne.classEnrollments.add(StudentClass().apply {
            this.student = studentOne
            this.schoolClass = schoolClass
            this.academicSession = session
            this.term = term
            this.isActive = true
        })
        studentTwo.classEnrollments.add(StudentClass().apply {
            this.student = studentTwo
            this.schoolClass = schoolClass
            this.academicSession = session
            this.term = term
            this.isActive = true
        })

        val parent = Parent().apply {
            id = parentId
            this.schoolId = schoolId
        }
        parent.studentRelationships.add(ParentStudent().apply {
            this.parent = parent
            this.student = studentOne
            this.relationshipType = "guardian"
            this.isActive = true
        })
        parent.studentRelationships.add(ParentStudent().apply {
            this.parent = parent
            this.student = studentTwo
            this.relationshipType = "guardian"
            this.isActive = true
        })

        val settlement = Settlement(
            parent = parent,
            amount = BigDecimal("200.00"),
            reference = "ref-preload",
            status = "SUCCESS"
        ).apply {
            this.schoolId = schoolId
            this.academicSession = session
            this.term = term
            this.transactionDate = LocalDateTime.now()
        }

        val preloaded = PaymentAllocation(
            settlement = Settlement(parent = parent, amount = BigDecimal.TEN, reference = "x", status = "SUCCESS"),
            student = studentOne,
            allocatedAmount = BigDecimal("50.00"),
            allocationOrder = 1,
            remainingBalanceBefore = BigDecimal("100.00"),
            remainingBalanceAfter = BigDecimal("50.00")
        )

        every { paymentAllocationRepository.findByParentIdAndSessionAndTerm(parentId, session.id, term.id) } returns listOf(preloaded)

        val classFee = ClassFeeItem().apply {
            this.schoolClass = schoolClass
            this.feeItem = FeeItem().apply { amount = BigDecimal("100.00") }
            this.customAmount = null
        }
        every {
            classFeeItemRepository.findBySchoolClassIdAndAcademicSessionIdAndTermIdFilters(
                classId,
                session.id!!,
                term.id!!,
                true
            )
        } returns listOf(classFee)

        every { paymentAllocationRepository.save(any()) } answers { firstArg() }

        val allocations = service.distributePaymentSequentially(settlement)

        assertEquals(2, allocations.size)
        assertEquals(BigDecimal("50.00"), allocations[0].allocatedAmount)
        assertEquals(BigDecimal("100.00"), allocations[1].allocatedAmount)

        verify(exactly = 1) { paymentAllocationRepository.findByParentIdAndSessionAndTerm(parentId, session.id, term.id) }
        verify(exactly = 0) { paymentAllocationRepository.getTotalAllocatedAmountForStudent(any(), any(), any()) }
    }

    @Test
    fun `getParentPaymentAllocations returns repository results for direct parent id path`() {
        val parentId = UUID.randomUUID()
        val sessionId = UUID.randomUUID()
        val termId = UUID.randomUUID()

        val expected = listOf(
            PaymentAllocation(
                settlement = Settlement(
                    parent = Parent().apply { id = parentId },
                    amount = BigDecimal("100.00"),
                    reference = "ref-parent-query",
                    status = "SUCCESS"
                ),
                student = Student().apply {
                    id = UUID.randomUUID()
                    studentId = "S-QUERY"
                    admissionDate = LocalDate.now()
                },
                allocatedAmount = BigDecimal("100.00"),
                allocationOrder = 1,
                remainingBalanceBefore = BigDecimal("100.00"),
                remainingBalanceAfter = BigDecimal.ZERO
            )
        )

        every {
            paymentAllocationRepository.findByParentIdAndSessionAndTerm(parentId, sessionId, termId)
        } returns expected

        val result = service.getParentPaymentAllocations(parentId, sessionId, termId)

        assertEquals(expected, result)
        verify(exactly = 1) {
            paymentAllocationRepository.findByParentIdAndSessionAndTerm(parentId, sessionId, termId)
        }
    }
}

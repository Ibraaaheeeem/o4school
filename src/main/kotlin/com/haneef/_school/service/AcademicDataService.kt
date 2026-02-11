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
        val fromTerm = termRepository.findById(fromTermId).orElseThrow { RuntimeException("Source term not found") }
        val toTerm = termRepository.findById(toTermId).orElseThrow { RuntimeException("Destination term not found") }

        if (fromTerm.schoolId != schoolId || toTerm.schoolId != schoolId) {
            throw RuntimeException("Unauthorized access to terms")
        }

        val toSession = toTerm.academicSession

        // 1. Import Staff Assignments (Class & Subject Teachers)
        if (importStaff) {
            // Class Teachers
            val classTeachers = classTeacherRepository.findBySchoolIdAndIsActiveAndSessionAndTermWithDetails(
                schoolId, true, fromTerm.academicSession.id!!, fromTermId
            )
            classTeachers.forEach { ct ->
                val existing = classTeacherRepository.findByStaffIdAndSchoolClassIdAndAcademicSessionIdAndTermIdAndSchoolId(
                    ct.staff.id!!, ct.schoolClass.id!!, toSession.id!!, toTermId, schoolId
                )
                if (existing == null) {
                    val newCt = ClassTeacher(
                        staff = ct.staff,
                        schoolClass = ct.schoolClass,
                        academicSession = toSession,
                        term = toTerm
                    ).apply {
                        this.schoolId = schoolId
                        this.isActive = true
                    }
                    classTeacherRepository.save(newCt)
                } else if (!existing.isActive) {
                    existing.isActive = true
                    classTeacherRepository.save(existing)
                }
            }

            // Subject Teachers
            val subjectTeachers = subjectTeacherRepository.findBySchoolIdAndIsActiveAndSessionAndTermWithDetails(
                schoolId, true, fromTerm.academicSession.id!!, fromTermId
            )
            subjectTeachers.forEach { st ->
                val existing = subjectTeacherRepository.findByStaffIdAndSubjectIdAndSchoolClassIdAndAcademicSessionIdAndTermIdAndSchoolId(
                    st.staff.id!!, st.subject.id!!, st.schoolClass.id!!, toSession.id!!, toTermId, schoolId
                )
                if (existing == null) {
                    val newSt = SubjectTeacher(
                        staff = st.staff,
                        subject = st.subject,
                        schoolClass = st.schoolClass,
                        academicSession = toSession,
                        term = toTerm
                    ).apply {
                        this.schoolId = schoolId
                        this.isActive = true
                    }
                    subjectTeacherRepository.save(newSt)
                } else if (!existing.isActive) {
                    existing.isActive = true
                    subjectTeacherRepository.save(existing)
                }
            }
        }

        // 2. Import Student Enrollments & Promotions
        if (importStudents) {
            val studentClasses = studentClassRepository.findByAcademicSessionIdAndTermIdAndIsActive(
                fromTerm.academicSession.id!!, fromTermId, true
            )
            
            // Prefetch classes in school to avoid N+1
            val allClassesInSchool = schoolClassRepository.findBySchoolIdAndIsActive(schoolId, true)

            studentClasses.forEach { sc ->
                val action = studentPromotions[sc.id!!] ?: "RETAIN"
                val currentClass = sc.schoolClass
                
                val targetClass = when (action) {
                    "UPGRADE" -> {
                        val targetGradeLevel = (currentClass.gradeLevel ?: 0) + 1
                        allClassesInSchool.find { 
                            it.track?.id == currentClass.track?.id && it.gradeLevel == targetGradeLevel 
                        } ?: currentClass
                    }
                    "DOWNGRADE" -> {
                        val targetGradeLevel = (currentClass.gradeLevel ?: 0) - 1
                        allClassesInSchool.find { 
                            it.track?.id == currentClass.track?.id && it.gradeLevel == targetGradeLevel 
                        } ?: currentClass
                    }
                    else -> currentClass // RETAIN
                }

                // Check if student already enrolled in this term
                val existingList = studentClassRepository.findByStudentIdAndAcademicSessionIdAndTermId(
                    sc.student.id!!, toSession.id!!, toTermId
                )
                
                // We find if there is an enrollment for the TARGET class specifically
                val sameClassExisting = existingList.find { it.schoolClass.id == targetClass.id }

                if (sameClassExisting == null) {
                    // Check if they are enrolled in an ACTIVE class within the SAME TRACK
                    val activeInTrack = existingList.find { 
                        it.isActive && it.schoolClass.track?.id == targetClass.track?.id 
                    }
                    
                    if (activeInTrack != null) {
                        // OVERSIGHT FIX: If they are in a DIFFERENT class within the same track, MOVE them
                        activeInTrack.schoolClass = targetClass
                        studentClassRepository.save(activeInTrack)
                    } else {
                        // Check for inactive record in the TARGET class to reactivate
                        val inactiveInSameClass = existingList.find { it.schoolClass.id == targetClass.id && !it.isActive }
                        if (inactiveInSameClass != null) {
                            inactiveInSameClass.isActive = true
                            studentClassRepository.save(inactiveInSameClass)
                        } else {
                            val newSc = StudentClass(
                                student = sc.student,
                                schoolClass = targetClass,
                                academicSession = toSession,
                                term = toTerm
                            ).apply {
                                this.schoolId = schoolId
                                this.isActive = true
                                this.enrollmentDate = java.time.LocalDate.now()
                            }
                            studentClassRepository.save(newSc)
                        }
                    }
                } else if (!sameClassExisting.isActive) {
                    sameClassExisting.isActive = true
                    studentClassRepository.save(sameClassExisting)
                }
                // If it exists and is already active, we assume it's correct because sameClassExisting found it.
            }
        }

        // 3. Import Class Fee Items
        if (importFees) {
            val classFeeItems = classFeeItemRepository.findBySchoolIdAndIsActiveOrderBySchoolClassAscFeeItemAsc(
                schoolId, true
            ).filter { it.termId?.id == fromTermId }
            
            classFeeItems.forEach { cfi ->
                 val existing = classFeeItemRepository.findBySchoolClassIdAndFeeItemIdAndAcademicSessionIdAndTermId(
                    cfi.schoolClass.id!!, cfi.feeItem.id!!, toSession.id!!, toTerm
                )
                if (existing.isEmpty) {
                    val newCfi = ClassFeeItem(
                        schoolClass = cfi.schoolClass,
                        feeItem = cfi.feeItem,
                        academicSession = toSession,
                        termId = toTerm,
                        academicYear = toSession.sessionYear,
                        customAmount = cfi.customAmount,
                        isApplicable = cfi.isApplicable,
                        isLocked = cfi.isLocked,
                        notes = cfi.notes
                    ).apply {
                        this.schoolId = schoolId
                        this.isActive = true
                    }
                    classFeeItemRepository.save(newCfi)
                } else {
                    val existingItem = existing.get()
                    // OVERSIGHT FIX: Sync values from source even if already active
                    existingItem.isActive = true
                    existingItem.customAmount = cfi.customAmount
                    existingItem.isApplicable = cfi.isApplicable
                    existingItem.isLocked = cfi.isLocked
                    existingItem.notes = cfi.notes
                    classFeeItemRepository.save(existingItem)
                }
            }
        }
        
        logger.info("Imported relationships from term {} to term {} for school {} (Fees: {}, Staff: {}, Students: {})", 
            fromTermId, toTermId, schoolId, importFees, importStaff, importStudents)
    }
}

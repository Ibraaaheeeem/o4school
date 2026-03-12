package com.haneef._school.service

import com.haneef._school.dto.BroadcastRecipientFilter
import com.haneef._school.dto.BroadcastRecipientDTO
import com.haneef._school.entity.*
import com.haneef._school.repository.*
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.*

@Service
class BroadcastService(
    private val staffRepository: StaffRepository,
    private val parentRepository: ParentRepository,
    private val studentRepository: StudentRepository,
    private val financialService: FinancialService,
    private val classTeacherRepository: ClassTeacherRepository,
    private val subjectTeacherRepository: SubjectTeacherRepository,
    private val userRepository: UserRepository
) {

    fun getDetailedRecipients(filter: BroadcastRecipientFilter, schoolId: UUID): List<BroadcastRecipientDTO> {
        val recipients = mutableListOf<BroadcastRecipientDTO>()

        // 1. Filtered Recipients
        if (filter.recipientType == "ALL" || filter.recipientType == "STAFF") {
            recipients.addAll(getStaffDetailed(filter, schoolId))
        }

        if (filter.recipientType == "ALL" || filter.recipientType == "PARENTS") {
            recipients.addAll(getParentDetailed(filter, schoolId))
        }

        if (filter.recipientType == "STUDENTS") {
            recipients.addAll(getStudentDetailed(filter, schoolId))
        }

        // 2. Manual Recipients (if not already included)
        if (filter.manualUserIds.isNotEmpty()) {
            val existingIds = recipients.map { it.userId }.toSet()
            val missingIds = filter.manualUserIds.filter { it !in existingIds }
            
            missingIds.forEach { userId ->
                // Check Staff
                staffRepository.findByUserIdAndSchoolId(userId, schoolId)?.let { staff ->
                    recipients.add(staff.toDTO())
                }
                // Check Parents
                parentRepository.findByUserIdAndSchoolId(userId, schoolId)?.let { parent ->
                    recipients.add(parent.toDTO())
                }
                // Check Students
                studentRepository.findByUserIdAndSchoolId(userId, schoolId)?.let { student ->
                    recipients.add(student.toDTO())
                }
            }
        }

        // 3. Manual Phone Numbers (Unregistered)
        if (filter.manualPhoneNumbers.isNotEmpty()) {
            filter.manualPhoneNumbers.forEach { phone ->
                if (recipients.none { it.phoneNumber == phone }) {
                    recipients.add(BroadcastRecipientDTO(
                        userId = UUID.nameUUIDFromBytes(phone.toByteArray()), // Deterministic UUID for the phone number
                        name = "Manual: $phone",
                        phoneNumber = phone,
                        roles = listOf("Manual"),
                        type = "MANUAL"
                    ))
                }
            }
        }

        return recipients.distinctBy { it.userId }
            .filter { it.userId !in filter.excludedUserIds }
            .filter { it.phoneNumber == null || it.phoneNumber !in filter.excludedPhoneNumbers }
    }

    fun searchDetailedRecipients(query: String, schoolId: UUID, recipientType: String? = null): List<BroadcastRecipientDTO> {
        if (query.length < 2) return emptyList()
        val results = mutableListOf<BroadcastRecipientDTO>()

        // Search Staff
        if (recipientType == null || recipientType == "ALL" || recipientType == "STAFF") {
            staffRepository.findBySchoolIdAndIsActiveAndSearch(schoolId, true, query, org.springframework.data.domain.PageRequest.of(0, 10))
                .forEach { results.add(it.toDTO()) }
        }

        // Search Parents
        if (recipientType == null || recipientType == "ALL" || recipientType == "PARENTS") {
            parentRepository.findBySchoolIdAndIsActiveAndSearch(schoolId, true, query, org.springframework.data.domain.PageRequest.of(0, 10))
                .forEach { results.add(it.toDTO()) }
        }

        // Search Students
        if (recipientType == null || recipientType == "ALL" || recipientType == "STUDENTS") {
            studentRepository.findBySchoolIdAndIsActiveAndSearch(schoolId, true, query, org.springframework.data.domain.PageRequest.of(0, 10))
                .forEach { results.add(it.toDTO()) }
        }

        return results.distinctBy { it.userId }
    }

    private fun getStaffDetailed(filter: BroadcastRecipientFilter, schoolId: UUID): List<BroadcastRecipientDTO> {
        var staffList = staffRepository.findBySchoolIdAndIsActive(schoolId, true)

        if (filter.trackIds.isNotEmpty()) {
            staffList = staffList.filter { staff ->
                staff.classTeacherAssignments.any { it.schoolClass.track?.id in filter.trackIds } ||
                staff.subjectTeacherAssignments.any { it.schoolClass.track?.id in filter.trackIds }
            }
        }

        if (filter.departmentNames.isNotEmpty()) {
            staffList = staffList.filter { staff ->
                staff.department in filter.departmentNames ||
                staff.classTeacherAssignments.any { it.schoolClass.department?.name in filter.departmentNames } ||
                staff.subjectTeacherAssignments.any { it.schoolClass.department?.name in filter.departmentNames }
            }
        }

        if (filter.classIds.isNotEmpty()) {
            staffList = staffList.filter { staff ->
                staff.classTeacherAssignments.any { it.schoolClass.id in filter.classIds } ||
                staff.subjectTeacherAssignments.any { it.schoolClass.id in filter.classIds }
            }
        }

        return staffList.map { it.toDTO() }
    }

    private fun getParentDetailed(filter: BroadcastRecipientFilter, schoolId: UUID): List<BroadcastRecipientDTO> {
        val parents = parentRepository.findBySchoolIdAndIsActiveWithRelationships(schoolId, true)
        
        return parents.filter { parent ->
            val children = parent.studentRelationships.map { it.student }.filter { it.isActive }
            if (children.isEmpty()) return@filter false

            val matchesClass = if (filter.classIds.isNotEmpty()) {
                children.any { student ->
                    student.classEnrollments.any { it.isActive && it.schoolClass.id in filter.classIds }
                }
            } else true

            val matchesTrack = if (filter.trackIds.isNotEmpty()) {
                children.any { student ->
                    student.classEnrollments.any { it.isActive && it.schoolClass.track?.id in filter.trackIds }
                }
            } else true

            val matchesDept = if (filter.departmentNames.isNotEmpty()) {
                children.any { student ->
                    student.classEnrollments.any { it.isActive && it.schoolClass.department?.name in filter.departmentNames }
                }
            } else {
                true
            }

            val matchesGender = if (filter.studentGender != "ANY") {
                children.any { it.user.gender == filter.studentGender }
            } else true

            val matchesStatus = if (filter.studentStatus != "ANY") {
                val isNewTarget = filter.studentStatus == "NEW"
                children.any { it.isNew == isNewTarget }
            } else true

            val matchesFees = when (filter.feeStatus) {
                "OWING" -> financialService.calculateParentBalance(parent) > BigDecimal.ZERO
                "COMPLETED" -> financialService.calculateParentBalance(parent) <= BigDecimal.ZERO
                else -> true
            }

            matchesClass && matchesTrack && matchesDept && matchesGender && matchesStatus && matchesFees
        }.map { it.toDTO() }
    }

    private fun getStudentDetailed(filter: BroadcastRecipientFilter, schoolId: UUID): List<BroadcastRecipientDTO> {
        val students = studentRepository.findBySchoolIdAndIsActive(schoolId, true)
        
        return students.filter { student ->
            val matchesClass = if (filter.classIds.isNotEmpty()) {
                student.classEnrollments.any { it.isActive && it.schoolClass.id in filter.classIds }
            } else true

            val matchesTrack = if (filter.trackIds.isNotEmpty()) {
                student.classEnrollments.any { it.isActive && it.schoolClass.track?.id in filter.trackIds }
            } else true

            val matchesGender = if (filter.studentGender != "ANY") {
                student.user.gender == filter.studentGender
            } else true

            val matchesStatus = if (filter.studentStatus != "ANY") {
                val isNewTarget = filter.studentStatus == "NEW"
                student.isNew == isNewTarget
            } else true

            matchesClass && matchesTrack && matchesGender && matchesStatus
        }.map { it.toDTO() }
    }

    private fun Staff.toDTO(): BroadcastRecipientDTO {
        val roles = mutableListOf("Staff")
        if (this.classTeacherAssignments.isNotEmpty()) roles.add("Class Teacher")
        if (this.subjectTeacherAssignments.isNotEmpty()) roles.add("Subject Teacher")
        
        return BroadcastRecipientDTO(
            userId = this.user.id!!,
            name = "${this.user.firstName} ${this.user.lastName}",
            phoneNumber = this.user.phoneNumber,
            roles = roles,
            type = "STAFF"
        )
    }

    private fun Parent.toDTO(): BroadcastRecipientDTO {
        return BroadcastRecipientDTO(
            userId = this.user.id!!,
            name = "${this.user.firstName} ${this.user.lastName}",
            phoneNumber = this.user.phoneNumber,
            roles = listOf("Parent"),
            type = "PARENT"
        )
    }

    private fun Student.toDTO(): BroadcastRecipientDTO {
        return BroadcastRecipientDTO(
            userId = this.user.id!!,
            name = "${this.user.firstName} ${this.user.lastName}",
            phoneNumber = this.user.phoneNumber,
            roles = listOf("Student"),
            type = "STUDENT"
        )
    }
}

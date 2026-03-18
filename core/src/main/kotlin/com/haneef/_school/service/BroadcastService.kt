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

        // 1. Filtered Recipients (Only added if addAll is true)
        if (filter.addAll) {
            if (filter.recipientType == "ALL" || filter.recipientType == "STAFF") {
                recipients.addAll(getStaffDetailed(filter, schoolId))
            }

            if (filter.recipientType == "ALL" || filter.recipientType == "PARENTS") {
                recipients.addAll(getParentDetailed(filter, schoolId))
            }
        }

        // 2. Manual Recipients (if not already included)
        if (filter.manualUserIds.isNotEmpty()) {
            val existingIds = recipients.map { it.userId }.toMutableSet()
            val missingIds = filter.manualUserIds.filter { it !in existingIds }
            
            missingIds.forEach { userId ->
                // Check Staff - only if broadcast is for STAFF or ALL
                if (filter.recipientType == "ALL" || filter.recipientType == "STAFF") {
                    staffRepository.findByUserIdAndSchoolId(userId, schoolId)?.let { staff ->
                        recipients.add(staff.toDTO())
                        existingIds.add(userId)
                    }
                }
                
                // Check Parents - only if broadcast is for PARENTS or ALL, AND not already added as staff
                if (userId !in existingIds && (filter.recipientType == "ALL" || filter.recipientType == "PARENTS")) {
                    parentRepository.findByUserIdAndSchoolId(userId, schoolId)?.let { parent ->
                        recipients.add(parent.toDTO())
                        existingIds.add(userId)
                    }
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
            staffRepository.findBySchoolIdAndIsActiveAndSearch(schoolId, true, query)
                .forEach { results.add(it.toDTO()) }
        }

        // Search Parents
        if (recipientType == null || recipientType == "ALL" || recipientType == "PARENTS") {
            parentRepository.findBySchoolIdAndIsActiveAndSearch(schoolId, true, query)
                .forEach { results.add(it.toDTO()) }
        }

        return results.distinctBy { it.userId }
    }

    private fun getStaffDetailed(filter: BroadcastRecipientFilter, schoolId: UUID): List<BroadcastRecipientDTO> {
        return staffRepository.findByFilter(
            schoolId = schoolId,
            isActive = true,
            hasTrackFilter = filter.trackIds.isNotEmpty(),
            trackIds = filter.trackIds.ifEmpty { null },
            hasDeptFilter = filter.departmentNames.isNotEmpty(),
            deptNames = filter.departmentNames.ifEmpty { null },
            hasClassFilter = filter.classIds.isNotEmpty(),
            classIds = filter.classIds.ifEmpty { null }
        ).map { staff: Staff -> staff.toDTO() }
    }

    private fun getParentDetailed(filter: BroadcastRecipientFilter, schoolId: UUID): List<BroadcastRecipientDTO> {
        val parents = parentRepository.findByFilter(
            schoolId = schoolId,
            isActive = true,
            hasClassFilter = filter.classIds.isNotEmpty(),
            classIds = filter.classIds.ifEmpty { null },
            hasTrackFilter = filter.trackIds.isNotEmpty(),
            trackIds = filter.trackIds.ifEmpty { null },
            hasDeptFilter = filter.departmentNames.isNotEmpty(),
            deptNames = filter.departmentNames.ifEmpty { null },
            studentGender = filter.studentGender,
            studentStatus = filter.studentStatus,
            isNew = filter.studentStatus == "NEW"
        )
        
        return parents.filter { parent ->
            val status = financialService.calculateParentFinancialStatus(parent)
            
            // 1. Basic Fee Status Filter
            val matchesFeeStatus = when (filter.feeStatus) {
                "OWING" -> status.balance > BigDecimal.ZERO
                "COMPLETED" -> status.balance <= BigDecimal.ZERO
                else -> true
            }
            if (!matchesFeeStatus) return@filter false
            
            // 2. Amount Owed Filter
            filter.minAmountOwed?.let { if (status.balance < it) return@filter false }
            filter.maxAmountOwed?.let { if (status.balance > it) return@filter false }
            
            // 3. Percentage Owed Filter
            if (filter.minFeePercentage != null || filter.maxFeePercentage != null) {
                val percentage = if (status.totalOwed > BigDecimal.ZERO) {
                    status.balance.multiply(BigDecimal(100)).divide(status.totalOwed, 2, java.math.RoundingMode.HALF_UP).toDouble()
                } else {
                    0.0
                }
                filter.minFeePercentage?.let { if (percentage < it) return@filter false }
                filter.maxFeePercentage?.let { if (percentage > it) return@filter false }
            }
            
            true
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
            type = "STAFF",
            roles = roles
        )
    }

    private fun Parent.toDTO(): BroadcastRecipientDTO {
        return BroadcastRecipientDTO(
            userId = this.user.id!!,
            name = "${this.user.firstName} ${this.user.lastName}",
            phoneNumber = this.user.phoneNumber,
            type = "PARENT",
            roles = listOf("Parent")
        )
    }
}
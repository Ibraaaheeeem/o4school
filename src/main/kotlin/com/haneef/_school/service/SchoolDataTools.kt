package com.haneef._school.service

import com.haneef._school.repository.*
import com.haneef._school.entity.*
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

@Component
class SchoolDataTools(
    private val parentRepository: ParentRepository,
    private val staffRepository: StaffRepository,
    private val studentRepository: StudentRepository,
    private val financialService: FinancialService,
    private val userRepository: UserRepository,
    private val schoolRepository: SchoolRepository
) {

    @Tool(description = "Query parents based on criteria like owing fees, student status, or name. Always provide the schoolId.")
    fun queryParents(
        @ToolParam(description = "The criteria for filtering parents") criteria: String,
        @ToolParam(description = "The school ID of the current school") schoolId: UUID
    ): List<ParentInfo> {
        val allParents = parentRepository.findBySchoolIdAndIsActive(schoolId, true)
        
        return if (criteria.contains("owing", ignoreCase = true)) {
            allParents.filter { parent ->
                val balance = financialService.calculateParentBalance(parent)
                balance > BigDecimal.ZERO
            }.map { parent ->
                ParentInfo(
                    id = parent.user.id!!, 
                    name = parent.user.fullName ?: "Unknown",
                    phone = parent.user.phoneNumber ?: "N/A",
                    balance = financialService.calculateParentBalance(parent)
                )
            }
        } else {
            allParents.map { parent ->
                ParentInfo(
                    id = parent.user.id!!,
                    name = parent.user.fullName ?: "Unknown",
                    phone = parent.user.phoneNumber ?: "N/A",
                    balance = financialService.calculateParentBalance(parent)
                )
            }
        }
    }

    @Tool(description = "Query staff members based on criteria like department, designation, or name. Always provide the schoolId.")
    fun queryStaff(
        @ToolParam(description = "The criteria for filtering staff") criteria: String,
        @ToolParam(description = "The school ID of the current school") schoolId: UUID
    ): List<ParentInfo> {
        val allStaff = staffRepository.findBySchoolIdAndIsActive(schoolId, true)
        
        // Basic filtering by criteria if provided
        val filteredStaff = if (criteria.isNotBlank() && !criteria.contains("all", ignoreCase = true)) {
            allStaff.filter { staff ->
                staff.user.fullName?.contains(criteria, ignoreCase = true) == true ||
                staff.department?.contains(criteria, ignoreCase = true) == true ||
                staff.designation.contains(criteria, ignoreCase = true)
            }
        } else {
            allStaff
        }

        return filteredStaff.map { staff ->
            ParentInfo(
                id = staff.user.id!!,
                name = staff.user.fullName ?: "Unknown",
                phone = staff.user.phoneNumber ?: "N/A",
                balance = BigDecimal.ZERO
            )
        }
    }

    @Tool(description = "Get detailed financial status for a parent. Always provide the schoolId.")
    fun getFinancialStatus(
        @ToolParam(description = "The parent name or ID") parentName: String,
        @ToolParam(description = "The school ID of the current school") schoolId: UUID
    ): String {
        val parents = parentRepository.findBySchoolIdAndIsActive(schoolId, true)
            .filter { it.user.fullName?.contains(parentName, ignoreCase = true) == true }
        
        if (parents.isEmpty()) return "No parent found matching '$parentName' in this school."
        
        val parent = parents.first()
        val balance = financialService.calculateParentBalance(parent)
        return "Parent ${parent.user.fullName} has a balance of $balance."
    }

    @Tool(description = "Get student information including class and status. Always provide the schoolId.")
    fun getStudentInfo(
        @ToolParam(description = "The student name") studentName: String,
        @ToolParam(description = "The school ID of the current school") schoolId: UUID
    ): String {
        val students = studentRepository.findBySchoolIdAndIsActive(schoolId, true)
            .filter { it.user.fullName?.contains(studentName, ignoreCase = true) == true }
            
        if (students.isEmpty()) return "No student found matching '$studentName' in this school."
        
        val student = students.first()
        val currentClass = student.classEnrollments.find { it.isActive }?.schoolClass?.className ?: "No class"
        return "Student ${student.user.fullName} is in class $currentClass. Status: ${if (student.isNew) "New" else "Returning"}."
    }

    data class ParentInfo(val id: UUID, val name: String, val phone: String, val balance: BigDecimal)
}

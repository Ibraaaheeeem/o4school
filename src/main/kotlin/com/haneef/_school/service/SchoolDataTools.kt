package com.haneef._school.service

import com.haneef._school.repository.*
import com.haneef._school.entity.*
import org.springframework.ai.tool.annotation.Tool
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

@Component
class SchoolDataTools(
    private val parentRepository: ParentRepository,
    private val studentRepository: StudentRepository,
    private val financialService: FinancialService,
    private val userRepository: UserRepository,
    private val schoolRepository: SchoolRepository
) {

    @Tool(description = "Query parents based on criteria like owing fees, student status, or name")
    fun queryParents(criteria: String): List<ParentInfo> {
        val allParents = parentRepository.findAll()
        
        return if (criteria.contains("owing", ignoreCase = true)) {
            allParents.filter { parent ->
                val balance = financialService.calculateParentBalance(parent)
                balance > BigDecimal.ZERO
            }.map { parent ->
                ParentInfo(
                    parent.id!!, 
                    parent.user.fullName, 
                    parent.user.phoneNumber ?: "N/A", 
                    financialService.calculateParentBalance(parent)
                )
            }
        } else if (criteria.contains("new", ignoreCase = true)) {
            val thirtyDaysAgo = LocalDateTime.now().minusDays(30)
            val students = studentRepository.findAll()
            val newParentIds = students.filter { it.createdAt.isAfter(thirtyDaysAgo) }
                .flatMap { it.parentRelationships.map { rel -> rel.parent.id } }
                .toSet()
            
            allParents.filter { it.id in newParentIds }.map { parent ->
                ParentInfo(
                    parent.id!!, 
                    parent.user.fullName, 
                    parent.user.phoneNumber ?: "N/A", 
                    financialService.calculateParentBalance(parent)
                )
            }
        } else {
            // General search by name if no specific criteria matches
            allParents.filter { it.user.fullName.contains(criteria, ignoreCase = true) }
                .map { parent ->
                    ParentInfo(
                        parent.id!!, 
                        parent.user.fullName, 
                        parent.user.phoneNumber ?: "N/A", 
                        financialService.calculateParentBalance(parent)
                    )
                }
        }
    }

    @Tool(description = "Get detailed financial status for a parent")
    fun getFinancialStatus(parentId: String): ParentFinancialStatus {
        val parentIdUuid = UUID.fromString(parentId)
        val parent = parentRepository.findById(parentIdUuid).orElseThrow { RuntimeException("Parent not found") }
        val balance = financialService.calculateParentBalance(parent)
        val breakdown = financialService.getFeeBreakdown(parent)
        
        return ParentFinancialStatus(
            parentName = parent.user.fullName,
            totalBalance = balance,
            breakdown = breakdown.toString()
        )
    }

    @Tool(description = "Get academic info for a student")
    fun getStudentInfo(studentName: String): List<StudentInfo> {
        val students = studentRepository.findAll().filter { 
            it.user.fullName.contains(studentName, ignoreCase = true) 
        }
        return students.map { student ->
            val className = student.classEnrollments.find { it.isActive }?.schoolClass?.className ?: "N/A"
            StudentInfo(
                student.id!!.toString(), 
                student.user.fullName, 
                student.admissionNumber ?: "N/A", 
                className
            )
        }
    }

    data class ParentInfo(val id: UUID, val name: String, val phone: String, val balance: BigDecimal)
    data class ParentFinancialStatus(val parentName: String, val totalBalance: BigDecimal, val breakdown: String)
    data class StudentInfo(val id: String, val name: String, val admissionNumber: String, val className: String)
}

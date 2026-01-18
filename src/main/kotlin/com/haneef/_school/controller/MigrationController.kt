package com.haneef._school.controller

import com.haneef._school.service.StudentClassMigrationService
import jakarta.servlet.http.HttpSession
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*
import java.util.UUID

@Controller
@RequestMapping("/admin/migration")
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
class MigrationController(
    private val studentClassMigrationService: StudentClassMigrationService
) {
    
//    @PostMapping("/student-classes")
//    @ResponseBody
//    fun migrateStudentClasses(session: HttpSession): Map<String, Any> {
//        return try {
//            studentClassMigrationService.migrateStudentClassesToNewStructure()
//            mapOf("success" to true, "message" to "Student classes migration completed successfully")
//        } catch (e: Exception) {
//            mapOf("success" to false, "message" to "Migration failed: ${e.message}")
//        }
//    }
//    
//    @PostMapping("/populate-current-session")
//    @ResponseBody
//    fun populateCurrentSession(session: HttpSession): Map<String, Any> {
//        val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
//            ?: return mapOf("success" to false, "message" to "No school selected")
//        
//        return try {
//            studentClassMigrationService.populateWithCurrentSessionAndTerm(selectedSchoolId)
//            mapOf("success" to true, "message" to "Current session population completed successfully")
//        } catch (e: Exception) {
//            mapOf("success" to false, "message" to "Population failed: ${e.message}")
//        }
//    }
}
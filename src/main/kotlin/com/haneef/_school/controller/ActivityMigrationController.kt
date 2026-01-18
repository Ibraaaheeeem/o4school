package com.haneef._school.controller

import com.haneef._school.service.ActivityBackfillService
import com.haneef._school.service.CustomUserDetails
import jakarta.servlet.http.HttpSession
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.util.UUID

@Controller
@RequestMapping("/admin/system")
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
class ActivityMigrationController(
    private val activityBackfillService: ActivityBackfillService
) {
    
    @PostMapping("/migrate-activities")
    @ResponseBody
    fun migrateHistoricalActivities(
        authentication: Authentication,
        session: HttpSession
    ): Map<String, Any> {
        return try {
            val customUser = authentication.principal as CustomUserDetails
            val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
                ?: return mapOf("success" to false, "message" to "No school selected")
            
            // Run the backfill process
            activityBackfillService.backfillHistoricalActivities(selectedSchoolId, customUser.user.id!!)
            activityBackfillService.createSystemInitializationActivity(selectedSchoolId, customUser.user.id!!)
            
            mapOf(
                "success" to true,
                "message" to "Historical activities have been successfully migrated"
            )
        } catch (e: Exception) {
            mapOf(
                "success" to false,
                "message" to "Migration failed: ${e.message}"
            )
        }
    }
    
//    @PostMapping("/migrate-activities-redirect")
//    fun migrateHistoricalActivitiesRedirect(
//        authentication: Authentication,
//        session: HttpSession,
//        redirectAttributes: RedirectAttributes
//    ): String {
//        return try {
//            val customUser = authentication.principal as CustomUserDetails
//            val selectedSchoolId = session.getAttribute("selectedSchoolId") as? UUID
//                ?: return "redirect:/select-school"
//            
//            activityBackfillService.backfillHistoricalActivities(selectedSchoolId, customUser.user.id!!)
//            activityBackfillService.createSystemInitializationActivity(selectedSchoolId, customUser.user.id!!)
//            
//            redirectAttributes.addFlashAttribute("success", "Historical activities have been successfully migrated")
//            "redirect:/admin/activities"
//        } catch (e: Exception) {
//            redirectAttributes.addFlashAttribute("error", "Migration failed: ${e.message}")
//            "redirect:/admin/activities"
//        }
//    }
}
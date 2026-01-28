package com.haneef._school.controller

import com.haneef._school.dto.BulkImportDataDTO
import com.haneef._school.dto.BulkImportPreviewDTO
import com.haneef._school.service.BulkImportService
import jakarta.servlet.http.HttpSession
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import java.util.UUID

@Controller
@RequestMapping("/admin/community/bulk-import")
class BulkImportController(
    private val bulkImportService: BulkImportService
) {

    /**
     * Step 1: Validate the imported data and return preview
     */
    @PostMapping("/validate")
    @ResponseBody
    fun validateData(
        @RequestBody data: BulkImportDataDTO,
        session: HttpSession
    ): ResponseEntity<BulkImportPreviewDTO> {
        return try {
            val schoolId = session.getAttribute("selectedSchoolId") as? UUID
                ?: throw RuntimeException("No school selected")
            
            // Validate and get preview
            val preview = bulkImportService.validateAndPreview(data, schoolId)
            
            // Store preview in session for confirmation step
            session.setAttribute("bulkImportPreview", preview)
            
            ResponseEntity.ok(preview)
        } catch (e: Exception) {
            throw RuntimeException("Validation failed: ${e.message}", e)
        }
    }

    /**
     * Step 2: Show preview page
     */
    @GetMapping("/preview")
    fun showPreview(
        session: HttpSession,
        model: Model
    ): String {
        val preview = session.getAttribute("bulkImportPreview") as? BulkImportPreviewDTO
            ?: return "redirect:/admin/community/home"
        
        model.addAttribute("preview", preview)
        model.addAttribute("hasErrors", preview.hasErrors)
        
        return "admin/community/bulk-import-preview"
    }

    /**
     * Step 3: User confirms the import after reviewing preview
     */
    @PostMapping("/confirm")
    fun confirmImport(
        session: HttpSession,
        model: Model
    ): String {
        return try {
            val schoolId = session.getAttribute("selectedSchoolId") as? UUID
                ?: throw RuntimeException("No school selected")
            
            val preview = session.getAttribute("bulkImportPreview") as? BulkImportPreviewDTO
                ?: throw RuntimeException("No preview data found. Please upload the file again.")
            
            // Perform the import
            val result = bulkImportService.performImport(preview, schoolId)
            
            // Clear the preview from session
            session.removeAttribute("bulkImportPreview")
            
            model.addAttribute("result", result)
            model.addAttribute("success", result.success)
            
            return "admin/community/bulk-import-result"
        } catch (e: Exception) {
            model.addAttribute("error", "Import failed: ${e.message}")
            return "admin/community/bulk-import-result"
        }
    }

    /**
     * Download sample Excel template
     */
    @GetMapping("/download-template")
    fun downloadTemplate(): ResponseEntity<org.springframework.core.io.Resource> {
        val file = org.springframework.core.io.ClassPathResource("static/templates/4school_bulk_import_template.xlsx")
        
        return ResponseEntity.ok()
            .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, 
                "attachment; filename=\"4school_bulk_import_template.xlsx\"")
            .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, 
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            .body(file)
    }
}

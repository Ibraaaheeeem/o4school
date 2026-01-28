package com.haneef._school.dto

data class BulkImportDataDTO(
    val students: List<StudentImportData>,
    val parents: List<ParentImportData>,
    val staff: List<StaffImportData>
)

data class StudentImportData(
    val firstName: String,
    val lastName: String,
    val middleName: String?,
    val gender: String,
    val dateOfBirth: String, // DD/MM/YYYY format
    val admissionNumber: String?,
    val address: String?
)

data class ParentImportData(
    val firstName: String,
    val lastName: String,
    val email: String,
    val phoneNumber: String,
    val address: String?
)

data class StaffImportData(
    val firstName: String,
    val lastName: String,
    val email: String,
    val phoneNumber: String,
    val designation: String,
    val dateOfHire: String? // DD/MM/YYYY format
)

data class BulkImportPreviewDTO(
    val students: ImportCategoryPreview,
    val parents: ImportCategoryPreview,
    val staff: ImportCategoryPreview,
    val hasErrors: Boolean
)

data class ImportCategoryPreview(
    val totalRows: Int,
    val validEntries: Int,
    val duplicates: Int,
    val errors: List<ImportError>,
    val validData: List<Map<String, String>>
)

data class ImportError(
    val row: Int,
    val field: String?,
    val message: String,
    val severity: ErrorSeverity
)

enum class ErrorSeverity {
    ERROR,   // Blocks import
    WARNING, // Informational, entry will be skipped
    INFO     // Just informational
}

data class BulkImportResultDTO(
    val studentsImported: Int,
    val parentsImported: Int,
    val staffImported: Int,
    val studentsSkipped: Int,
    val parentsSkipped: Int,
    val staffSkipped: Int,
    val errors: List<String>,
    val success: Boolean
)

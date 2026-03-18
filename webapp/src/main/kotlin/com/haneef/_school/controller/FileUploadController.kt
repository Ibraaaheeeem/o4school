package com.haneef._school.controller

import org.apache.tika.Tika
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

@RestController
@RequestMapping("/uploads")
class FileUploadController {

    private val tika = Tika()

    @PostMapping
    fun uploadFile(@RequestParam("file") file: MultipartFile) {
        // Sanitize filename
        val sanitizedFileName = sanitizeFileName(file.originalFilename!!)
        
        // Validate file content type
        if (!isValidContentType(file)) {
            throw IllegalArgumentException("Invalid file type")
        }

        // Check magic number
        if (!isValidMagicNumber(file)) {
            throw IllegalArgumentException("File does not have a valid format")
        }

        // Save the file
        val targetLocation = Paths.get("uploads/$sanitizedFileName")
        file.inputStream.use { inputStream ->
            Files.copy(inputStream, targetLocation)
        }
    }

    private fun sanitizeFileName(originalFilename: String): String {
        return originalFilename.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    }

    private fun isValidContentType(file: MultipartFile): Boolean {
        val mimeType = tika.detect(file.inputStream)
        val allowedTypes = listOf("image/jpeg", "image/png", "application/pdf") // Example allowed types
        return allowedTypes.contains(mimeType)
    }

    private fun isValidMagicNumber(file: MultipartFile): Boolean {
        // Implement magic number checks based on file type
        val magicNumber = tika.detect(file.inputStream)
        // Logic to verify magic numbers...
        return true // Placeholder for actual logic
    }

}
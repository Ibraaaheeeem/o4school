package com.haneef._school.controller

import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

@RestController
@RequestMapping("/uploads")
class FileUploadController {
    private val uploadDir: Path = Paths.get("uploads").toAbsolutePath().normalize()
    private val allowedMimeTypes = setOf("image/jpeg", "image/png", "application/pdf")
    private val allowedExtensions = setOf("jpg", "jpeg", "png", "pdf")

    @PostMapping
    fun uploadFile(@RequestParam("file") file: MultipartFile) {
        require(!file.isEmpty) { "File is empty" }

        // Sanitize filename
        val originalFilename = file.originalFilename ?: throw IllegalArgumentException("Filename is missing")
        val sanitizedFileName = sanitizeFileName(originalFilename)
        
        // Validate file content type
        if (!isValidContentType(file)) {
            throw IllegalArgumentException("Invalid file type")
        }

        // Check magic number
        if (!isValidMagicNumber(file)) {
            throw IllegalArgumentException("File does not have a valid format")
        }

        // Save the file
        Files.createDirectories(uploadDir)
        val targetLocation = uploadDir.resolve(sanitizedFileName).normalize()
        require(targetLocation.startsWith(uploadDir)) { "Invalid file path" }

        file.inputStream.use { inputStream ->
            Files.copy(inputStream, targetLocation, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun sanitizeFileName(originalFilename: String): String {
        return originalFilename.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    }

    private fun isValidContentType(file: MultipartFile): Boolean {
        val mimeType = file.contentType?.lowercase()
        val extension = file.originalFilename
            ?.substringAfterLast('.', "")
            ?.lowercase()
            .orEmpty()

        return mimeType in allowedMimeTypes && extension in allowedExtensions
    }

    private fun isValidMagicNumber(file: MultipartFile): Boolean {
        return file.inputStream.use { input ->
            val header = ByteArray(8)
            val bytesRead = input.read(header)
            if (bytesRead < 4) return@use false

            val isJpeg = header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() && header[2] == 0xFF.toByte()
            val isPng = bytesRead >= 8 &&
                header[0] == 0x89.toByte() &&
                header[1] == 0x50.toByte() &&
                header[2] == 0x4E.toByte() &&
                header[3] == 0x47.toByte() &&
                header[4] == 0x0D.toByte() &&
                header[5] == 0x0A.toByte() &&
                header[6] == 0x1A.toByte() &&
                header[7] == 0x0A.toByte()
            val isPdf = header[0] == 0x25.toByte() && header[1] == 0x50.toByte() && header[2] == 0x44.toByte() && header[3] == 0x46.toByte()

            isJpeg || isPng || isPdf
        }
    }

}
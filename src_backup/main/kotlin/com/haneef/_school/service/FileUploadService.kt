package com.haneef._school.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.*

@Service
class FileUploadService {

    @Value("\${app.upload.dir:uploads}")
    private lateinit var uploadDir: String

    @Value("\${app.upload.max-file-size:5242880}") // 5MB default
    private var maxFileSize: Long = 5242880

    private val allowedImageTypes = setOf("image/jpeg", "image/jpg", "image/png", "image/gif")
    private val allowedImageExtensions = setOf("jpg", "jpeg", "png", "gif")

    fun uploadPassportPhoto(file: MultipartFile, studentId: String): String {
        validateImageFile(file)
        
        val uploadPath = createUploadDirectory("passport-photos")
        val fileName = generateUniqueFileName(file.originalFilename ?: "photo", studentId)
        val filePath = uploadPath.resolve(fileName)
        
        try {
            Files.copy(file.inputStream, filePath, StandardCopyOption.REPLACE_EXISTING)
            return "/uploads/passport-photos/$fileName"
        } catch (e: IOException) {
            throw RuntimeException("Failed to upload passport photo: ${e.message}", e)
        }
    }

    fun deletePassportPhoto(photoUrl: String?) {
        if (photoUrl.isNullOrBlank()) return
        
        try {
            // Extract filename from URL (e.g., "/uploads/passport-photos/filename.jpg" -> "filename.jpg")
            val fileName = photoUrl.substringAfterLast("/")
            val filePath = Paths.get(uploadDir, "passport-photos", fileName)
            
            if (Files.exists(filePath)) {
                Files.delete(filePath)
            }
        } catch (e: Exception) {
            // Log error but don't throw - file deletion failure shouldn't break the application
            println("Warning: Failed to delete passport photo file: ${e.message}")
        }
    }

    private fun validateImageFile(file: MultipartFile) {
        if (file.isEmpty) {
            throw IllegalArgumentException("File is empty")
        }

        if (file.size > maxFileSize) {
            throw IllegalArgumentException("File size exceeds maximum allowed size of ${maxFileSize / 1024 / 1024}MB")
        }

        val contentType = file.contentType
        if (contentType == null || !allowedImageTypes.contains(contentType.lowercase())) {
            throw IllegalArgumentException("Invalid file type. Only JPEG, PNG, and GIF images are allowed")
        }

        val extension = getFileExtension(file.originalFilename ?: "")
        if (!allowedImageExtensions.contains(extension.lowercase())) {
            throw IllegalArgumentException("Invalid file extension. Only jpg, jpeg, png, and gif files are allowed")
        }

        // Magic Byte / Content Validation
        try {
            val image = javax.imageio.ImageIO.read(file.inputStream)
            if (image == null) {
                throw IllegalArgumentException("Invalid image file content. The file does not appear to be a valid image.")
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid image file content. The file does not appear to be a valid image.")
        }
    }

    private fun createUploadDirectory(subDir: String): Path {
        val uploadPath = Paths.get(uploadDir, subDir)
        
        if (!Files.exists(uploadPath)) {
            try {
                Files.createDirectories(uploadPath)
            } catch (e: IOException) {
                throw RuntimeException("Failed to create upload directory: ${e.message}", e)
            }
        }
        
        return uploadPath
    }

    private fun generateUniqueFileName(originalFileName: String, studentId: String): String {
        val extension = getFileExtension(originalFileName)
        val timestamp = System.currentTimeMillis()
        val uuid = UUID.randomUUID().toString().substring(0, 8)
        return "passport_${studentId}_${timestamp}_${uuid}.$extension"
    }

    private fun getFileExtension(fileName: String): String {
        return if (fileName.contains(".")) {
            fileName.substringAfterLast(".")
        } else {
            ""
        }
    }
}
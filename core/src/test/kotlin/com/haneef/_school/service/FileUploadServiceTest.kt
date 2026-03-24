package com.haneef._school.service

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.mock.web.MockMultipartFile
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

class FileUploadServiceTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `uploadPassportPhoto sanitizes student id in generated filename`() {
        val service = FileUploadService()
        setField(service, "uploadDir", tempDir.toString())
        setField(service, "maxFileSize", 5_242_880L)

        val pngBytes = createPngBytes()
        val file = MockMultipartFile("file", "photo.png", "image/png", pngBytes)

        val url = service.uploadPassportPhoto(file, "STU/001..A")

        assertTrue(url.contains("passport_STU_001__A_"))
        assertFalse(url.contains("/../"))

        val storedPath = tempDir.resolve("passport-photos").resolve(url.substringAfterLast('/'))
        assertTrue(Files.exists(storedPath))
    }

    private fun createPngBytes(): ByteArray {
        val image = BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB)
        val output = ByteArrayOutputStream()
        ImageIO.write(image, "png", output)
        return output.toByteArray()
    }

    private fun setField(target: Any, fieldName: String, value: Any) {
        val field = target.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(target, value)
    }
}

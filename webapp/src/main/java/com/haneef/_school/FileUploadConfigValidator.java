package com.haneef._school;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class FileUploadConfigValidator {

    private static final Logger logger = LoggerFactory.getLogger(FileUploadConfigValidator.class);

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    public void validateConfig() {
        validateDirectory(uploadDir);
    }

    public boolean validateDirectory(String directoryPath) {
        try {
            Path path = Paths.get(directoryPath).toAbsolutePath().normalize();
            Files.createDirectories(path);
            logger.info("Validated upload directory: {}", path);
            return true;
        } catch (IOException | RuntimeException ex) {
            logger.error("Failed to validate upload directory: {}", directoryPath, ex);
            throw new IllegalStateException("Invalid upload directory configuration: " + directoryPath, ex);
        }
    }
}
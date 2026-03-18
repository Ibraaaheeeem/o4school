package com.haneef._school.config;

import com.haneef._school.FileUploadConfigValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class AppStartupConfiguration implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(AppStartupConfiguration.class);
    private final FileUploadConfigValidator fileUploadConfigValidator;

    public AppStartupConfiguration(FileUploadConfigValidator fileUploadConfigValidator) {
        this.fileUploadConfigValidator = fileUploadConfigValidator;
    }

    @Override
    public void run(String... args) {
        logger.info("Starting up the application");
        fileUploadConfigValidator.validateConfig();
    }
}
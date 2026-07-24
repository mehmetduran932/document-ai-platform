package com.documentai.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.processing")
public record ProcessingProperties(
        int chunkMinWords,
        int chunkMaxWords,
        int chunkOverlapWords,
        String tesseractDataPath,
        String tesseractLanguage,
        String ftsLanguage
) {
}

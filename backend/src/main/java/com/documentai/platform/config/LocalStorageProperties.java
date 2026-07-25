package com.documentai.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage.local")
public record LocalStorageProperties(
        String basePath,
        /** Used to build the URL returned by presignedDownloadUrl() - must be reachable by
         *  whatever calls that URL later (the frontend browser, or an MCP agent). */
        String publicBaseUrl
) {
}

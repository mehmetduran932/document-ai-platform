package com.documentai.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage.r2")
public record R2Properties(
        String endpoint,
        String accessKey,
        String secretKey,
        String bucket,
        String region
) {
}

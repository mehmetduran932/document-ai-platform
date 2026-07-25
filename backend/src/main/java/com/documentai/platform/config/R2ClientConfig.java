package com.documentai.platform.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.storage-provider", havingValue = "r2", matchIfMissing = true)
public class R2ClientConfig {

    private final R2Properties r2Properties;

    @Bean
    public S3Client r2S3Client() {
        return S3Client.builder()
                .endpointOverride(URI.create(r2Properties.endpoint()))
                .region(Region.of(r2Properties.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(r2Properties.accessKey(), r2Properties.secretKey())))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    @Bean
    public S3Presigner r2S3Presigner() {
        return S3Presigner.builder()
                .endpointOverride(URI.create(r2Properties.endpoint()))
                .region(Region.of(r2Properties.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(r2Properties.accessKey(), r2Properties.secretKey())))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }
}

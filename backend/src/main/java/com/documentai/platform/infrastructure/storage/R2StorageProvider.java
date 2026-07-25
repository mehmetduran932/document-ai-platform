package com.documentai.platform.infrastructure.storage;

import com.documentai.platform.config.R2Properties;
import com.documentai.platform.exception.DocumentProcessingException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.InputStream;
import java.net.URL;
import java.time.Duration;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.storage-provider", havingValue = "r2", matchIfMissing = true)
public class R2StorageProvider implements StorageProvider {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final R2Properties properties;

    @Override
    public void upload(String key, InputStream content, long size, String contentType) {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(key)
                    .contentType(contentType)
                    .contentLength(size)
                    .build();
            s3Client.putObject(request, RequestBody.fromInputStream(content, size));
        } catch (Exception e) {
            throw new DocumentProcessingException("Failed to upload object to storage: " + key, e);
        }
    }

    @Override
    public InputStream download(String key) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .build();
        return s3Client.getObject(request);
    }

    @Override
    public void delete(String key) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .build());
    }

    @Override
    public URL presignedDownloadUrl(String key, Duration ttl) {
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(getRequest)
                .build();
        return s3Presigner.presignGetObject(presignRequest).url();
    }
}

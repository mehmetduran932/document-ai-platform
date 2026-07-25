package com.documentai.platform.infrastructure.storage;

import com.documentai.platform.config.LocalStorageProperties;
import com.documentai.platform.exception.DocumentProcessingException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Filesystem-backed StorageProvider for local development, so `docker compose up` doesn't require
 * a real Cloudflare R2 (or S3/MinIO) bucket just to try the app out. Not intended for production -
 * it has no replication/durability story and its "presigned" URL (see LocalDownloadUrlSigner) is a
 * simpler HMAC scheme, not real S3 request signing.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.storage-provider", havingValue = "local")
public class LocalFileStorageProvider implements StorageProvider {

    private static final Duration MAX_TTL = Duration.ofDays(1);

    private final LocalStorageProperties properties;
    private final LocalDownloadUrlSigner urlSigner;

    @Override
    public void upload(String key, InputStream content, long size, String contentType) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(content, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new DocumentProcessingException("Failed to write local storage file: " + key, e);
        }
    }

    @Override
    public InputStream download(String key) {
        try {
            return Files.newInputStream(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read local storage file: " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete local storage file: " + key, e);
        }
    }

    @Override
    public URL presignedDownloadUrl(String key, Duration ttl) {
        Duration cappedTtl = ttl.compareTo(MAX_TTL) > 0 ? MAX_TTL : ttl;
        long expires = System.currentTimeMillis() + cappedTtl.toMillis();
        String signature = urlSigner.sign(key, expires);
        String encodedKey = URLEncoder.encode(key, StandardCharsets.UTF_8);
        String url = properties.publicBaseUrl() + "/api/local-storage/download?key=" + encodedKey
                + "&expires=" + expires + "&sig=" + signature;
        try {
            return URI.create(url).toURL();
        } catch (MalformedURLException e) {
            throw new IllegalStateException("Failed to build local storage download URL", e);
        }
    }

    /** Resolves key against basePath and rejects anything that would escape it (path traversal). */
    private Path resolve(String key) {
        Path base = Path.of(properties.basePath()).toAbsolutePath().normalize();
        Path resolved = base.resolve(key).normalize();
        if (!resolved.startsWith(base)) {
            throw new IllegalArgumentException("Invalid storage key: " + key);
        }
        return resolved;
    }
}

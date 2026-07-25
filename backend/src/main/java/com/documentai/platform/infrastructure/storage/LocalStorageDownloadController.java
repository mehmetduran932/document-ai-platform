package com.documentai.platform.infrastructure.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Serves the URLs LocalFileStorageProvider.presignedDownloadUrl() hands out. Deliberately outside
 * JWT auth (see SecurityConfig) - the key/expires/sig triple in the query string is its own
 * self-contained credential, exactly like a real R2/S3 presigned URL needs no separate app auth.
 * Only registered when app.storage-provider=local (see LocalFileStorageProvider's Javadoc).
 */
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.storage-provider", havingValue = "local")
public class LocalStorageDownloadController {

    private final LocalDownloadUrlSigner urlSigner;
    private final StorageProvider storageProvider;

    @GetMapping("/api/local-storage/download")
    public ResponseEntity<InputStreamResource> download(
            @RequestParam String key, @RequestParam long expires, @RequestParam String sig) {
        if (!urlSigner.isValid(key, expires, sig)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid or expired download link");
        }
        try {
            InputStreamResource resource = new InputStreamResource(storageProvider.download(key));
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);
        } catch (java.io.UncheckedIOException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found", e);
        }
    }
}

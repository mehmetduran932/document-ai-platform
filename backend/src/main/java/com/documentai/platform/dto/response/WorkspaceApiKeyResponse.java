package com.documentai.platform.dto.response;

import java.time.Instant;
import java.util.UUID;

public record WorkspaceApiKeyResponse(
        UUID id,
        String name,
        String keyPrefix,
        String plaintextKey,
        Instant createdAt
) {
    /** plaintextKey is populated only once, in the create-response - never retrievable again. */
    public static WorkspaceApiKeyResponse withSecret(UUID id, String name, String keyPrefix, String plaintextKey, Instant createdAt) {
        return new WorkspaceApiKeyResponse(id, name, keyPrefix, plaintextKey, createdAt);
    }

    public static WorkspaceApiKeyResponse metadataOnly(UUID id, String name, String keyPrefix, Instant createdAt) {
        return new WorkspaceApiKeyResponse(id, name, keyPrefix, null, createdAt);
    }
}

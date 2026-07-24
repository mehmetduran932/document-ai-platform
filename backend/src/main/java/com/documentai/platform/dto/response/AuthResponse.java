package com.documentai.platform.dto.response;

import java.util.UUID;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        UUID userId,
        UUID workspaceId,
        String email
) {
    public static AuthResponse bearer(String accessToken, long expiresInSeconds, UUID userId, UUID workspaceId, String email) {
        return new AuthResponse(accessToken, "Bearer", expiresInSeconds, userId, workspaceId, email);
    }
}

package com.documentai.platform.infrastructure.security;

import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Generates and hashes workspace API keys used by external MCP agents. Keys are shown to the
 * user exactly once at creation time; only the SHA-256 hash is persisted.
 */
@Component
public class ApiKeyHasher {

    private static final String KEY_PREFIX = "dap_";
    private static final SecureRandom RANDOM = new SecureRandom();

    public String generateRawKey() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String prefixOf(String rawKey) {
        return rawKey.substring(0, Math.min(rawKey.length(), 12));
    }

    public String hash(String rawKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawKey.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}

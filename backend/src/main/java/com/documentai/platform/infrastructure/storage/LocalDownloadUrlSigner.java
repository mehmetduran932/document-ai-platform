package com.documentai.platform.infrastructure.storage;

import com.documentai.platform.config.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

/**
 * Signs/verifies the {@code key}+{@code expires} pair baked into LocalFileStorageProvider's
 * "presigned" URLs, so {@code /api/local-storage/download} can stay outside JWT auth (mirroring
 * how a real R2/S3 presigned URL needs no app-level auth either) without becoming an open,
 * unlimited-lifetime file server for anyone who learns a storage key. Reuses app.jwt.secret rather
 * than adding a second required secret - this is local-dev-only tooling, not a production path.
 */
@Component
@RequiredArgsConstructor
class LocalDownloadUrlSigner {

    private static final String ALGORITHM = "HmacSHA256";

    private final JwtProperties jwtProperties;

    String sign(String key, long expiresEpochMillis) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(jwtProperties.secret().getBytes(StandardCharsets.UTF_8), ALGORITHM));
            byte[] raw = mac.doFinal((key + "|" + expiresEpochMillis).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to sign local storage download URL", e);
        }
    }

    boolean isValid(String key, long expiresEpochMillis, String signature) {
        if (expiresEpochMillis < System.currentTimeMillis()) {
            return false;
        }
        String expected = sign(key, expiresEpochMillis);
        return java.security.MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
    }
}

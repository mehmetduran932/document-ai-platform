package com.documentai.platform.infrastructure.security;

import com.documentai.platform.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtService {

    private static final String CLAIM_WORKSPACE_ID = "workspaceId";
    private static final String CLAIM_USER_ID = "userId";

    private final JwtProperties properties;

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(properties.secret().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public String generateAccessToken(UserPrincipal principal) {
        Instant now = Instant.now();
        Instant expiry = now.plus(Duration.ofMinutes(properties.accessTokenTtlMinutes()));
        return Jwts.builder()
                .subject(principal.getUsername())
                .claim(CLAIM_USER_ID, principal.getUserId().toString())
                .claim(CLAIM_WORKSPACE_ID, principal.getWorkspaceId().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey())
                .compact();
    }

    public long accessTokenTtlSeconds() {
        return Duration.ofMinutes(properties.accessTokenTtlMinutes()).toSeconds();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractEmail(String token) {
        return parseClaims(token).getSubject();
    }

    public UUID extractUserId(String token) {
        return UUID.fromString(parseClaims(token).get(CLAIM_USER_ID, String.class));
    }

    public UUID extractWorkspaceId(String token) {
        return UUID.fromString(parseClaims(token).get(CLAIM_WORKSPACE_ID, String.class));
    }

    public boolean isValid(String token, UserPrincipal principal) {
        Claims claims = parseClaims(token);
        return claims.getSubject().equals(principal.getUsername()) && claims.getExpiration().after(new Date());
    }
}

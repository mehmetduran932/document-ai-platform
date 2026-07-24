package com.documentai.platform.infrastructure.security;

import com.documentai.platform.domain.entity.WorkspaceApiKey;
import com.documentai.platform.repository.WorkspaceApiKeyRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class WorkspaceApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";

    private final WorkspaceApiKeyRepository apiKeyRepository;
    private final ApiKeyHasher apiKeyHasher;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        String rawKey = request.getHeader(API_KEY_HEADER);
        if (rawKey != null && !rawKey.isBlank() && SecurityContextHolder.getContext().getAuthentication() == null) {
            Optional<WorkspaceApiKey> found = apiKeyRepository.findByKeyHashAndRevokedFalse(apiKeyHasher.hash(rawKey));
            if (found.isPresent()) {
                WorkspaceApiKey key = found.get();
                key.setLastUsedAt(Instant.now());
                apiKeyRepository.save(key);

                WorkspacePrincipal principal = new WorkspacePrincipal(key.getWorkspace().getId(), key.getId(), key.getName());
                var authToken = new UsernamePasswordAuthenticationToken(
                        principal, null, List.of(new SimpleGrantedAuthority("ROLE_AGENT")));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }
        filterChain.doFilter(request, response);
    }
}

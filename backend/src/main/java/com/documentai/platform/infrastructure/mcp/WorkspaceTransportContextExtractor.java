package com.documentai.platform.infrastructure.mcp;

import com.documentai.platform.domain.entity.WorkspaceApiKey;
import com.documentai.platform.infrastructure.security.ApiKeyHasher;
import com.documentai.platform.repository.WorkspaceApiKeyRepository;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpTransportContextExtractor;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/**
 * Resolves the calling agent's workspace from the X-API-Key header at the transport layer, so
 * tool call handlers can read it from {@code exchange.transportContext()} regardless of which
 * thread the MCP SDK ends up dispatching the call on. This is the authoritative workspace-scoping
 * mechanism for MCP tools - the coarse-grained Spring Security filter on /mcp/** only rejects
 * requests with no/garbage key up front.
 */
@Component
@RequiredArgsConstructor
public class WorkspaceTransportContextExtractor implements McpTransportContextExtractor<HttpServletRequest> {

    private static final String API_KEY_HEADER = "X-API-Key";

    private final WorkspaceApiKeyRepository apiKeyRepository;
    private final ApiKeyHasher apiKeyHasher;

    @Override
    public McpTransportContext extract(HttpServletRequest request) {
        String rawKey = request.getHeader(API_KEY_HEADER);
        if (rawKey == null || rawKey.isBlank()) {
            return McpTransportContext.EMPTY;
        }

        return apiKeyRepository.findByKeyHashAndRevokedFalse(apiKeyHasher.hash(rawKey))
                .map(this::toContext)
                .orElse(McpTransportContext.EMPTY);
    }

    private McpTransportContext toContext(WorkspaceApiKey key) {
        key.setLastUsedAt(Instant.now());
        apiKeyRepository.save(key);
        return McpTransportContext.create(Map.of(
                McpAuthContext.WORKSPACE_ID_KEY, key.getWorkspace().getId(),
                McpAuthContext.API_KEY_ID_KEY, key.getId()));
    }
}

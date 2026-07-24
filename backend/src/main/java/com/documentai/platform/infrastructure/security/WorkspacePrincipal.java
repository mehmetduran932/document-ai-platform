package com.documentai.platform.infrastructure.security;

import java.util.UUID;

/** Principal for an external MCP agent authenticated with a workspace-scoped API key. */
public record WorkspacePrincipal(UUID workspaceId, UUID apiKeyId, String apiKeyName) {
}

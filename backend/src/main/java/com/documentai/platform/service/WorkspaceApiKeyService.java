package com.documentai.platform.service;

import com.documentai.platform.dto.response.WorkspaceApiKeyResponse;

import java.util.List;
import java.util.UUID;

/**
 * Manages long-lived API keys external AI agents (Claude Code, Codex, Gemini CLI, ...) use to
 * authenticate to the MCP server for a single workspace.
 */
public interface WorkspaceApiKeyService {

    WorkspaceApiKeyResponse createKey(UUID workspaceId, String name);

    List<WorkspaceApiKeyResponse> listKeys(UUID workspaceId);

    void revokeKey(UUID workspaceId, UUID keyId);
}

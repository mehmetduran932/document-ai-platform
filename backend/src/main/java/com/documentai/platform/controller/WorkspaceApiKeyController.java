package com.documentai.platform.controller;

import com.documentai.platform.dto.request.CreateApiKeyRequest;
import com.documentai.platform.dto.response.WorkspaceApiKeyResponse;
import com.documentai.platform.infrastructure.security.SecurityUtils;
import com.documentai.platform.service.WorkspaceApiKeyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/workspace/api-keys")
@RequiredArgsConstructor
@Tag(name = "Workspace API Keys", description = "Credentials external AI agents use to call the MCP server")
public class WorkspaceApiKeyController {

    private final WorkspaceApiKeyService apiKeyService;

    @PostMapping
    @Operation(summary = "Create a new API key for the current workspace (secret shown once)")
    public ResponseEntity<WorkspaceApiKeyResponse> create(@Valid @RequestBody CreateApiKeyRequest request) {
        WorkspaceApiKeyResponse response = apiKeyService.createKey(SecurityUtils.currentWorkspaceId(), request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "List active API keys for the current workspace")
    public ResponseEntity<List<WorkspaceApiKeyResponse>> list() {
        return ResponseEntity.ok(apiKeyService.listKeys(SecurityUtils.currentWorkspaceId()));
    }

    @DeleteMapping("/{keyId}")
    @Operation(summary = "Revoke an API key")
    public ResponseEntity<Void> revoke(@PathVariable UUID keyId) {
        apiKeyService.revokeKey(SecurityUtils.currentWorkspaceId(), keyId);
        return ResponseEntity.noContent().build();
    }
}

package com.documentai.platform.service.impl;

import com.documentai.platform.domain.entity.Workspace;
import com.documentai.platform.domain.entity.WorkspaceApiKey;
import com.documentai.platform.dto.response.WorkspaceApiKeyResponse;
import com.documentai.platform.exception.ResourceNotFoundException;
import com.documentai.platform.infrastructure.security.ApiKeyHasher;
import com.documentai.platform.repository.WorkspaceApiKeyRepository;
import com.documentai.platform.repository.WorkspaceRepository;
import com.documentai.platform.service.WorkspaceApiKeyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkspaceApiKeyServiceImpl implements WorkspaceApiKeyService {

    private final WorkspaceApiKeyRepository apiKeyRepository;
    private final WorkspaceRepository workspaceRepository;
    private final ApiKeyHasher apiKeyHasher;

    @Override
    @Transactional
    public WorkspaceApiKeyResponse createKey(UUID workspaceId, String name) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found: " + workspaceId));

        String rawKey = apiKeyHasher.generateRawKey();
        WorkspaceApiKey key = WorkspaceApiKey.builder()
                .workspace(workspace)
                .name(name)
                .keyHash(apiKeyHasher.hash(rawKey))
                .keyPrefix(apiKeyHasher.prefixOf(rawKey))
                .revoked(false)
                .build();
        key = apiKeyRepository.save(key);

        return WorkspaceApiKeyResponse.withSecret(key.getId(), key.getName(), key.getKeyPrefix(), rawKey, key.getCreatedAt());
    }

    @Override
    public List<WorkspaceApiKeyResponse> listKeys(UUID workspaceId) {
        return apiKeyRepository.findAllByWorkspaceId(workspaceId).stream()
                .filter(k -> !k.isRevoked())
                .map(k -> WorkspaceApiKeyResponse.metadataOnly(k.getId(), k.getName(), k.getKeyPrefix(), k.getCreatedAt()))
                .toList();
    }

    @Override
    @Transactional
    public void revokeKey(UUID workspaceId, UUID keyId) {
        WorkspaceApiKey key = apiKeyRepository.findByIdAndWorkspaceId(keyId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("API key not found: " + keyId));
        key.setRevoked(true);
        apiKeyRepository.save(key);
    }
}

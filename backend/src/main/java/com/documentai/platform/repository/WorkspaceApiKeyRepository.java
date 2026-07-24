package com.documentai.platform.repository;

import com.documentai.platform.domain.entity.WorkspaceApiKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkspaceApiKeyRepository extends JpaRepository<WorkspaceApiKey, UUID> {

    Optional<WorkspaceApiKey> findByKeyHashAndRevokedFalse(String keyHash);

    Optional<WorkspaceApiKey> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    List<WorkspaceApiKey> findAllByWorkspaceId(UUID workspaceId);
}

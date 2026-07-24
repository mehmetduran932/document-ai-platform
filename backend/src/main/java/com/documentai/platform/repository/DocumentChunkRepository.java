package com.documentai.platform.repository;

import com.documentai.platform.domain.entity.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {

    List<DocumentChunk> findAllByDocumentIdOrderByChunkIndexAsc(UUID documentId);

    Optional<DocumentChunk> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    long countByDocumentId(UUID documentId);

    void deleteAllByDocumentId(UUID documentId);
}

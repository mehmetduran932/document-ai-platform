package com.documentai.platform.repository;

import com.documentai.platform.domain.entity.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Optional<Document> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    Page<Document> findAllByWorkspaceId(UUID workspaceId, Pageable pageable);
}

package com.documentai.platform.repository;

import com.documentai.platform.domain.entity.AskHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AskHistoryRepository extends JpaRepository<AskHistory, UUID> {

    Page<AskHistory> findAllByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId, Pageable pageable);

    void deleteAllByWorkspaceId(UUID workspaceId);
}

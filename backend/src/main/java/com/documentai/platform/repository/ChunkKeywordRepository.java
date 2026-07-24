package com.documentai.platform.repository;

import com.documentai.platform.domain.entity.ChunkKeyword;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ChunkKeywordRepository extends JpaRepository<ChunkKeyword, UUID> {

    void deleteAllByChunkId(UUID chunkId);
}

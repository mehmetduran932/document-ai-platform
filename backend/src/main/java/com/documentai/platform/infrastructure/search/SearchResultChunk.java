package com.documentai.platform.infrastructure.search;

import java.util.UUID;

/** Backend-agnostic search result. No FTS/embedding-specific concepts belong here. */
public record SearchResultChunk(
        UUID chunkId,
        UUID documentId,
        String documentFilename,
        Integer page,
        int chunkIndex,
        String content,
        double relevanceScore
) {
    public SearchResultChunk withScore(double newScore) {
        return new SearchResultChunk(chunkId, documentId, documentFilename, page, chunkIndex, content, newScore);
    }
}

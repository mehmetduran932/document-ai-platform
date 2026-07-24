package com.documentai.platform.infrastructure.processing;

public record ChunkDraft(Integer page, int chunkIndex, String content, int wordCount) {
}

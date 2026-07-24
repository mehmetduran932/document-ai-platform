package com.documentai.platform.dto.response;

import java.util.UUID;

public record DocumentChunkResponse(
        UUID id,
        UUID documentId,
        Integer page,
        int chunkIndex,
        String content,
        int wordCount
) {
}

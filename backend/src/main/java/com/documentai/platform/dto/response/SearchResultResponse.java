package com.documentai.platform.dto.response;

import java.util.List;
import java.util.UUID;

public record SearchResultResponse(
        UUID chunkId,
        UUID documentId,
        String documentFilename,
        Integer page,
        int chunkIndex,
        String content,
        double relevanceScore
) {
    /** Full content still went to search/ranking (and, for /api/ask, to the LLM) - this only shrinks the HTTP payload. */
    public SearchResultResponse withTruncatedContent(int maxChars) {
        if (content == null || content.length() <= maxChars) {
            return this;
        }
        return new SearchResultResponse(chunkId, documentId, documentFilename, page, chunkIndex,
                content.substring(0, maxChars) + "...", relevanceScore);
    }

    public record SearchResponseWrapper(String query, List<String> extractedKeywords, List<SearchResultResponse> results) {
    }
}

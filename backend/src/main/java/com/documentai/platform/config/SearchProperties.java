package com.documentai.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.search")
public record SearchProperties(
        int maxResults,
        /** How many of the matched chunks are actually sent to the LLM for POST /api/ask - kept
         *  smaller than maxResults to bound token usage; the full match set is still visible via
         *  POST /api/search. */
        int maxAnswerChunks,
        /** sourceChunks[].content is truncated to this length in API responses (both /api/search
         *  and /api/ask) - the full content is still what the LLM sees, this only shrinks the
         *  HTTP response payload. Fetch full text via GET /api/documents/chunks/{chunkId}. */
        int contentPreviewChars
) {
}

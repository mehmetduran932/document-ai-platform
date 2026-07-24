package com.documentai.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cross-encoder reranking over EmbeddingSearchProvider's candidate set - off by default until
 * validated on the target hardware (see the embedding subsystem plan for the CPU/RAM tradeoffs of
 * the model choice). candidatePoolMultiplier controls how many extra candidates are pulled from
 * pgvector before reranking narrows back down to maxResults (SearchProperties.maxResults).
 */
@ConfigurationProperties(prefix = "app.reranker")
public record RerankerProperties(
        boolean enabled,
        String baseUrl,
        int candidatePoolMultiplier
) {
}

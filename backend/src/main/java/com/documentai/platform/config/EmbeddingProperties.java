package com.documentai.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * FallbackEmbeddingProvider tries {@code gemini} first, then {@code openai} on failure - two
 * independent vendor configs so either can be swapped or pointed at a free/local server on its
 * own. {@code dimensions} is the shared pgvector column width both providers must produce
 * (Gemini's gemini-embedding-001 defaults to 3072 dims but supports truncation via
 * outputDimensionality; OpenAI's text-embedding-3-small natively produces 1536).
 */
@ConfigurationProperties(prefix = "app.embedding")
public record EmbeddingProperties(
        int dimensions,
        @NestedConfigurationProperty Gemini gemini,
        @NestedConfigurationProperty OpenAi openai
) {
    public record Gemini(String apiKey, String model, String baseUrl) {
    }

    public record OpenAi(String apiKey, String model, String baseUrl) {
    }
}

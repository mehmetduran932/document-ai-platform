package com.documentai.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * FallbackEmbeddingProvider tries {@code gemini} first, then {@code openai}, then {@code voyage} on
 * failure - three independent vendor configs so any can be swapped or pointed at a free/local server
 * on its own. {@code dimensions} is the shared pgvector column width all three fallback-chain
 * providers must produce (Gemini's gemini-embedding-001 defaults to 3072 dims but supports truncation
 * via outputDimensionality; OpenAI's text-embedding-3-small natively produces 1536 but also accepts an
 * arbitrary truncation size; Voyage only accepts output_dimension in {256, 512, 1024, 2048}, which is
 * why the shared default is 1024 rather than 1536). Not used by {@code e5}, which is a separate,
 * exclusive provider with its own fixed 384-dim output - see {@link E5}.
 */
@ConfigurationProperties(prefix = "app.embedding")
public record EmbeddingProperties(
        int dimensions,
        @NestedConfigurationProperty Gemini gemini,
        @NestedConfigurationProperty OpenAi openai,
        @NestedConfigurationProperty Voyage voyage,
        @NestedConfigurationProperty E5 e5
) {
    public record Gemini(String apiKey, String model, String baseUrl) {
    }

    public record OpenAi(String apiKey, String model, String baseUrl) {
    }

    public record Voyage(String apiKey, String model, String baseUrl) {
    }

    public record E5(String baseUrl, String model) {
    }
}

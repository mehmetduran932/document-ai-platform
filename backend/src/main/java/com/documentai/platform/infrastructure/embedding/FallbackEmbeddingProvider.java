package com.documentai.platform.infrastructure.embedding;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The only bean the rest of the app sees as {@link EmbeddingProvider} when app.embedding-provider is
 * unset or 'fallback' (see {@link E5EmbeddingProvider} for the mutually-exclusive alternative). Tries
 * Gemini first (cheap free-tier quota), then OpenAI, then Voyage AI, so ingestion/search doesn't go
 * down just because one vendor is unavailable or rate-limited.
 *
 * embed() cannot simply delegate to embedBatch(List.of(text)) the way it used to: Voyage's API
 * distinguishes "query" text from "document" text via an explicit input_type parameter
 * (VoyageEmbeddingClient.embedQuery vs embedBatch), so a single query must be routed through the
 * query-specific method at every step of the chain, not just wrapped in a one-element batch.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.search-provider", havingValue = "embedding")
@ConditionalOnProperty(name = "app.embedding-provider", havingValue = "fallback", matchIfMissing = true)
public class FallbackEmbeddingProvider implements EmbeddingProvider {

    private static final Logger log = LoggerFactory.getLogger(FallbackEmbeddingProvider.class);

    private final GeminiEmbeddingClient geminiEmbeddingClient;
    private final OpenAiEmbeddingClient openAiEmbeddingClient;
    private final VoyageEmbeddingClient voyageEmbeddingClient;

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        try {
            return geminiEmbeddingClient.embedBatch(texts);
        } catch (EmbeddingGenerationException e) {
            log.warn("Gemini embeddings failed, falling back to OpenAI: {}", e.getMessage());
            try {
                return openAiEmbeddingClient.embedBatch(texts);
            } catch (EmbeddingGenerationException e2) {
                log.warn("OpenAI embeddings failed, falling back to Voyage: {}", e2.getMessage());
                return voyageEmbeddingClient.embedBatch(texts);
            }
        }
    }

    @Override
    public float[] embed(String text) {
        try {
            return geminiEmbeddingClient.embedBatch(List.of(text)).get(0);
        } catch (EmbeddingGenerationException e) {
            log.warn("Gemini embeddings failed, falling back to OpenAI: {}", e.getMessage());
            try {
                return openAiEmbeddingClient.embedBatch(List.of(text)).get(0);
            } catch (EmbeddingGenerationException e2) {
                log.warn("OpenAI embeddings failed, falling back to Voyage: {}", e2.getMessage());
                return voyageEmbeddingClient.embedQuery(text);
            }
        }
    }
}

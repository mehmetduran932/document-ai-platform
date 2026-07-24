package com.documentai.platform.infrastructure.embedding;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The only bean the rest of the app sees as {@link EmbeddingProvider}. Tries Gemini first (cheap
 * free-tier quota); if that call fails for any reason, falls back to OpenAI so ingestion/search
 * doesn't go down just because one vendor is unavailable or rate-limited.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.search-provider", havingValue = "embedding")
public class FallbackEmbeddingProvider implements EmbeddingProvider {

    private static final Logger log = LoggerFactory.getLogger(FallbackEmbeddingProvider.class);

    private final GeminiEmbeddingClient geminiEmbeddingClient;
    private final OpenAiEmbeddingClient openAiEmbeddingClient;

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        try {
            return geminiEmbeddingClient.embedBatch(texts);
        } catch (EmbeddingGenerationException e) {
            log.warn("Gemini embeddings failed, falling back to OpenAI: {}", e.getMessage());
            return openAiEmbeddingClient.embedBatch(texts);
        }
    }

    @Override
    public float[] embed(String text) {
        return embedBatch(List.of(text)).get(0);
    }
}

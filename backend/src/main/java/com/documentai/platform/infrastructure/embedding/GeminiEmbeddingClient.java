package com.documentai.platform.infrastructure.embedding;

import com.documentai.platform.config.EmbeddingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

/**
 * Talks Google's Gemini embeddings API (batchEmbedContents). Not itself an EmbeddingProvider -
 * wrapped by {@link FallbackEmbeddingProvider}, which is the primary/fallback decision point.
 */
@Component
@RequiredArgsConstructor
class GeminiEmbeddingClient {

    private final EmbeddingProperties properties;
    private final RestClient restClient;

    List<float[]> embedBatch(List<String> texts) {
        if (texts.isEmpty()) {
            return List.of();
        }

        EmbeddingProperties.Gemini config = properties.gemini();
        List<Map<String, Object>> requests = texts.stream()
                .map(text -> Map.<String, Object>of(
                        "model", "models/" + config.model(),
                        "content", Map.of("parts", List.of(Map.of("text", text))),
                        "embedContentConfig", Map.of("outputDimensionality", properties.dimensions())
                ))
                .toList();

        try {
            BatchResponse response = restClient.post()
                    .uri(config.baseUrl() + "/models/" + config.model() + ":batchEmbedContents")
                    .header("x-goog-api-key", config.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("requests", requests))
                    .retrieve()
                    .body(BatchResponse.class);

            if (response == null || response.embeddings() == null || response.embeddings().size() != texts.size()) {
                throw new EmbeddingGenerationException("Gemini embedding API returned an unexpected number of vectors");
            }

            int targetDimensions = properties.dimensions();
            return response.embeddings().stream()
                    .map(values -> truncateAndNormalize(values, targetDimensions))
                    .toList();
        } catch (RestClientException e) {
            throw new EmbeddingGenerationException("Gemini embedding call failed: " + e.getMessage(), e);
        }
    }

    /**
     * gemini-embedding-001 is trained with Matryoshka Representation Learning, so its first N
     * dimensions already form a valid lower-dimensional embedding on their own - truncating (then
     * re-normalizing to unit length, since raw truncated values are no longer unit-norm) is
     * Google's documented way to get a smaller vector. Done defensively here regardless of
     * whether the API actually honored our requested outputDimensionality, since in practice the
     * batch endpoint has been observed to ignore it and return the full 3072-dim vector.
     */
    private static float[] truncateAndNormalize(EmbeddingValues values, int targetDimensions) {
        List<Double> raw = values.values();
        int size = Math.min(targetDimensions, raw.size());

        double sumSquares = 0;
        float[] truncated = new float[size];
        for (int i = 0; i < size; i++) {
            truncated[i] = raw.get(i).floatValue();
            sumSquares += truncated[i] * (double) truncated[i];
        }

        double norm = Math.sqrt(sumSquares);
        if (norm > 0) {
            for (int i = 0; i < size; i++) {
                truncated[i] = (float) (truncated[i] / norm);
            }
        }
        return truncated;
    }

    private record BatchResponse(List<EmbeddingValues> embeddings) {
    }

    private record EmbeddingValues(List<Double> values) {
    }
}

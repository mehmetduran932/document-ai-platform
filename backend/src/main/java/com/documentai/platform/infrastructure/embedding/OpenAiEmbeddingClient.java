package com.documentai.platform.infrastructure.embedding;

import com.documentai.platform.config.EmbeddingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Comparator;
import java.util.List;

/**
 * Talks the OpenAI /v1/embeddings wire format. Not itself an EmbeddingProvider - wrapped by
 * {@link FallbackEmbeddingProvider} as the fallback when Gemini fails. baseUrl also works
 * against a local OpenAI-embeddings-API-compatible server (e.g. Ollama's nomic-embed-text).
 */
@Component
@RequiredArgsConstructor
class OpenAiEmbeddingClient {

    private final EmbeddingProperties properties;
    private final RestClient restClient;

    List<float[]> embedBatch(List<String> texts) {
        if (texts.isEmpty()) {
            return List.of();
        }

        EmbeddingProperties.OpenAi config = properties.openai();
        EmbeddingRequest request = new EmbeddingRequest(config.model(), texts, properties.dimensions());

        try {
            EmbeddingResponse response = restClient.post()
                    .uri(config.baseUrl() + "/embeddings")
                    .header("Authorization", "Bearer " + config.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(EmbeddingResponse.class);

            if (response == null || response.data() == null || response.data().size() != texts.size()) {
                throw new EmbeddingGenerationException("OpenAI embedding API returned an unexpected number of vectors");
            }

            return response.data().stream()
                    .sorted(Comparator.comparingInt(EmbeddingData::index))
                    .map(EmbeddingData::embedding)
                    .toList();
        } catch (RestClientException e) {
            throw new EmbeddingGenerationException("OpenAI embedding call failed: " + e.getMessage(), e);
        }
    }

    private record EmbeddingRequest(String model, List<String> input, int dimensions) {
    }

    private record EmbeddingResponse(List<EmbeddingData> data) {
    }

    private record EmbeddingData(int index, float[] embedding) {
    }
}

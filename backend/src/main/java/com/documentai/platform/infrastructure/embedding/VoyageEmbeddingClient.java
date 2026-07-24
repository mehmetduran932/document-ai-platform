package com.documentai.platform.infrastructure.embedding;

import com.documentai.platform.config.EmbeddingProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Comparator;
import java.util.List;

/**
 * Talks Voyage AI's /v1/embeddings API (https://api.voyageai.com/v1/embeddings). Not itself an
 * EmbeddingProvider - wrapped by {@link FallbackEmbeddingProvider} as the third fallback vendor
 * after Gemini and OpenAI. Unlike OpenAI, Voyage takes an explicit input_type ("query" or
 * "document") instead of a single symmetric embedding call, so this client exposes two methods
 * rather than one - callers must pick the one matching which side of retrieval the text is on.
 */
@Component
@RequiredArgsConstructor
class VoyageEmbeddingClient {

    private final EmbeddingProperties properties;
    private final RestClient restClient;

    List<float[]> embedBatch(List<String> texts) {
        return call(texts, "document");
    }

    float[] embedQuery(String text) {
        return call(List.of(text), "query").get(0);
    }

    private List<float[]> call(List<String> texts, String inputType) {
        if (texts.isEmpty()) {
            return List.of();
        }

        EmbeddingProperties.Voyage config = properties.voyage();
        EmbeddingRequest request = new EmbeddingRequest(config.model(), texts, inputType, properties.dimensions());

        try {
            EmbeddingResponse response = restClient.post()
                    .uri(config.baseUrl() + "/embeddings")
                    .header("Authorization", "Bearer " + config.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(EmbeddingResponse.class);

            if (response == null || response.data() == null || response.data().size() != texts.size()) {
                throw new EmbeddingGenerationException("Voyage embedding API returned an unexpected number of vectors");
            }

            return response.data().stream()
                    .sorted(Comparator.comparingInt(EmbeddingData::index))
                    .map(EmbeddingData::embedding)
                    .toList();
        } catch (RestClientException e) {
            throw new EmbeddingGenerationException("Voyage embedding call failed: " + e.getMessage(), e);
        }
    }

    private record EmbeddingRequest(
            String model,
            List<String> input,
            @JsonProperty("input_type") String inputType,
            @JsonProperty("output_dimension") int outputDimension
    ) {
    }

    private record EmbeddingResponse(List<EmbeddingData> data) {
    }

    private record EmbeddingData(int index, float[] embedding) {
    }
}

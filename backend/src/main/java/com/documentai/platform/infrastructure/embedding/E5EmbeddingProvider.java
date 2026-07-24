package com.documentai.platform.infrastructure.embedding;

import com.documentai.platform.config.EmbeddingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Comparator;
import java.util.List;

/**
 * Talks to a locally-hosted multilingual-e5-small instance (e.g. Hugging Face's Text Embeddings
 * Inference) over its OpenAI-compatible /v1/embeddings route. Implements {@link EmbeddingProvider}
 * directly rather than being wrapped like {@link GeminiEmbeddingClient}/{@link OpenAiEmbeddingClient},
 * because e5 requires different text prefixes depending on which method is called - embedBatch is
 * only ever invoked for chunk ingestion (DocumentProcessingServiceImpl) so it prefixes "passage: ",
 * while embed is only ever invoked for search queries (EmbeddingSearchProvider) so it prefixes
 * "query: ". FallbackEmbeddingProvider's embed() delegates to embedBatch(), which would wrongly
 * apply the passage prefix to a query - that shortcut doesn't work here.
 *
 * Deliberately does not reuse OpenAiEmbeddingClient's request shape: that record always sends a
 * "dimensions" field (OpenAI's Matryoshka truncation parameter), which multilingual-e5-small does
 * not support and some OpenAI-compatible servers (e.g. vLLM) reject outright for non-Matryoshka
 * models.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.embedding-provider", havingValue = "e5")
public class E5EmbeddingProvider implements EmbeddingProvider {

    private final EmbeddingProperties properties;
    private final RestClient restClient;

    @Override
    public List<EmbeddingResult> embedBatch(List<String> texts) {
        if (texts.isEmpty()) {
            return List.of();
        }
        List<String> prefixed = texts.stream().map(t -> "passage: " + t).toList();
        return callTei(prefixed);
    }

    @Override
    public EmbeddingResult embed(String text) {
        return callTei(List.of("query: " + text)).get(0);
    }

    private List<EmbeddingResult> callTei(List<String> prefixedTexts) {
        EmbeddingProperties.E5 config = properties.e5();
        EmbeddingRequest request = new EmbeddingRequest(config.model(), prefixedTexts);
        String modelIdentity = "e5:" + config.model();

        try {
            EmbeddingResponse response = restClient.post()
                    .uri(config.baseUrl() + "/v1/embeddings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(EmbeddingResponse.class);

            if (response == null || response.data() == null || response.data().size() != prefixedTexts.size()) {
                throw new EmbeddingGenerationException("e5 embedding server returned an unexpected number of vectors");
            }

            return response.data().stream()
                    .sorted(Comparator.comparingInt(EmbeddingData::index))
                    .map(d -> new EmbeddingResult(d.embedding(), modelIdentity))
                    .toList();
        } catch (RestClientException e) {
            throw new EmbeddingGenerationException("e5 embedding call failed: " + e.getMessage(), e);
        }
    }

    private record EmbeddingRequest(String model, List<String> input) {
    }

    private record EmbeddingResponse(List<EmbeddingData> data) {
    }

    private record EmbeddingData(int index, float[] embedding) {
    }
}

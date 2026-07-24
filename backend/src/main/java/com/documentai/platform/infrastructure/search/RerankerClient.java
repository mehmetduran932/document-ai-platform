package com.documentai.platform.infrastructure.search;

import com.documentai.platform.config.RerankerProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Comparator;
import java.util.List;

/**
 * Talks to a locally-hosted cross-encoder reranker (Hugging Face TEI's native /rerank route - not
 * the OpenAI-compatible /v1/embeddings shape used elsewhere in this package). Used only by
 * EmbeddingSearchProvider to re-score its bi-encoder candidate set; keyword search never reranks
 * since ts_rank scores aren't comparable to a cross-encoder's.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.reranker.enabled", havingValue = "true")
public class RerankerClient {

    private final RerankerProperties properties;
    private final RestClient restClient;

    /** Re-scores each of {@code candidates} against {@code query}, sorted best-first. */
    public List<RerankedCandidate> rerank(String query, List<String> candidates) {
        if (candidates.isEmpty()) {
            return List.of();
        }

        RerankRequest request = new RerankRequest(query, candidates);
        try {
            RerankResult[] results = restClient.post()
                    .uri(properties.baseUrl() + "/rerank")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(RerankResult[].class);

            if (results == null) {
                throw new RerankException("Reranker returned no results");
            }

            return List.of(results).stream()
                    .sorted(Comparator.comparingDouble(RerankResult::score).reversed())
                    .map(r -> new RerankedCandidate(r.index(), r.score()))
                    .toList();
        } catch (RestClientException e) {
            throw new RerankException("Reranker call failed: " + e.getMessage(), e);
        }
    }

    /** {@code index} refers to the position of the reranked text in the original candidates list. */
    public record RerankedCandidate(int index, double score) {
    }

    private record RerankRequest(String query, List<String> texts) {
    }

    private record RerankResult(int index, double score) {
    }
}

package com.documentai.platform.infrastructure.search;

import com.documentai.platform.config.RerankerProperties;
import com.documentai.platform.infrastructure.embedding.EmbeddingProvider;
import com.documentai.platform.infrastructure.embedding.PgVectorFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Semantic search over document_chunks.embedding (pgvector). Selected when
 * app.search-provider=embedding. All vector/pgvector/cosine-distance knowledge is confined to
 * this class, matching {@link KeywordSearchProvider}'s discipline for tsvector.
 *
 * Unlike keyword search, this embeds the raw question directly rather than a stripped keyword
 * list - the whole point of embeddings is capturing meaning (e.g. "poliçe tutarı" ~ "prim
 * tutarı") that literal keyword matching cannot.
 *
 * When a {@link RerankerClient} bean is present (app.reranker.enabled=true), a wider candidate pool
 * is pulled from pgvector and re-scored by the cross-encoder before truncating back down to
 * maxResults - bi-encoder cosine similarity alone was observed to compress scores into a narrow
 * band (~0.81-0.84) for both relevant and irrelevant Turkish chunks in the same workspace, giving
 * poor discrimination on its own.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.search-provider", havingValue = "embedding")
public class EmbeddingSearchProvider implements SearchProvider {

    private static final String SEARCH_SQL = """
            SELECT c.id AS chunk_id,
                   c.document_id AS document_id,
                   d.filename AS document_filename,
                   c.page AS page,
                   c.chunk_index AS chunk_index,
                   c.content AS content,
                   1 - (c.embedding <=> CAST(:queryEmbedding AS vector)) AS score
            FROM document_chunks c
            JOIN documents d ON d.id = c.document_id
            WHERE c.workspace_id = :workspaceId
              AND c.embedding IS NOT NULL
            ORDER BY c.embedding <=> CAST(:queryEmbedding AS vector)
            LIMIT :maxResults
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final EmbeddingProvider embeddingProvider;
    private final Optional<RerankerClient> rerankerClient;
    private final RerankerProperties rerankerProperties;

    @Override
    public List<SearchResultChunk> search(SearchQuery query) {
        String text = query.rawQuestion() != null && !query.rawQuestion().isBlank()
                ? query.rawQuestion()
                : String.join(" ", query.keywords());
        if (text.isBlank()) {
            return List.of();
        }

        float[] queryEmbedding = embeddingProvider.embed(text);
        boolean reranking = rerankerClient.isPresent();
        int candidatePoolSize = reranking
                ? query.maxResults() * rerankerProperties.candidatePoolMultiplier()
                : query.maxResults();

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("workspaceId", query.workspaceId())
                .addValue("queryEmbedding", PgVectorFormat.toLiteral(queryEmbedding))
                .addValue("maxResults", candidatePoolSize);

        List<SearchResultChunk> candidates = jdbcTemplate.query(SEARCH_SQL, params, (rs, rowNum) -> new SearchResultChunk(
                (UUID) rs.getObject("chunk_id"),
                (UUID) rs.getObject("document_id"),
                rs.getString("document_filename"),
                rs.getObject("page") != null ? rs.getInt("page") : null,
                rs.getInt("chunk_index"),
                rs.getString("content"),
                rs.getDouble("score")
        ));

        if (!reranking || candidates.isEmpty()) {
            return candidates;
        }

        List<String> candidateContents = candidates.stream().map(SearchResultChunk::content).toList();
        List<RerankerClient.RerankedCandidate> reranked = rerankerClient.get().rerank(text, candidateContents);

        return reranked.stream()
                .limit(query.maxResults())
                .map(r -> candidates.get(r.index()).withScore(r.score()))
                .toList();
    }
}

package com.documentai.platform.infrastructure.search;

import com.documentai.platform.config.ProcessingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * MVP search implementation: PostgreSQL full-text search over document_chunks.search_vector.
 * All tsvector/ts_rank/to_tsquery/regconfig knowledge is confined to this class - nothing above
 * {@link SearchProvider} may depend on it. Selected when app.search-provider=keyword (default);
 * see {@link EmbeddingSearchProvider} for the semantic alternative - swapping is a config change,
 * not a code change, in either direction.
 *
 * Terms are OR-combined (to_tsquery with '|'), not AND-combined like plainto_tsquery would do.
 * The caller passes several independently-extracted keywords; requiring every single one of
 * them to be present (AND) makes the match fragile - one keyword-extraction miss (a word that
 * isn't a stopword but also isn't meaningful, e.g. "does") would silently zero out an otherwise
 * good match. OR + ts_rank ranking gives graceful degradation instead.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.search-provider", havingValue = "keyword", matchIfMissing = true)
public class KeywordSearchProvider implements SearchProvider {

    private static final String SEARCH_SQL = """
            SELECT c.id AS chunk_id,
                   c.document_id AS document_id,
                   d.filename AS document_filename,
                   c.page AS page,
                   c.chunk_index AS chunk_index,
                   c.content AS content,
                   ts_rank(c.search_vector, to_tsquery(CAST(:regconfig AS regconfig), :query)) AS score
            FROM document_chunks c
            JOIN documents d ON d.id = c.document_id
            WHERE c.workspace_id = :workspaceId
              AND c.search_vector @@ to_tsquery(CAST(:regconfig AS regconfig), :query)
            ORDER BY score DESC
            LIMIT :maxResults
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ProcessingProperties processingProperties;

    @Override
    public List<SearchResultChunk> search(SearchQuery query) {
        List<String> keywords = query.keywords();
        String fallback = (keywords == null || keywords.isEmpty()) ? query.rawQuestion() : String.join(" ", keywords);
        if (fallback == null || fallback.isBlank()) {
            return List.of();
        }

        String orQuery = toOrTsQuery(fallback);
        if (orQuery.isBlank()) {
            return List.of();
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("workspaceId", query.workspaceId())
                .addValue("query", orQuery)
                .addValue("regconfig", processingProperties.ftsLanguage())
                .addValue("maxResults", query.maxResults());

        return jdbcTemplate.query(SEARCH_SQL, params, (rs, rowNum) -> new SearchResultChunk(
                (UUID) rs.getObject("chunk_id"),
                (UUID) rs.getObject("document_id"),
                rs.getString("document_filename"),
                rs.getObject("page") != null ? rs.getInt("page") : null,
                rs.getInt("chunk_index"),
                rs.getString("content"),
                rs.getDouble("score")
        ));
    }

    /** Splits on whitespace, strips anything that isn't a letter/digit, OR-joins for to_tsquery. */
    private String toOrTsQuery(String text) {
        return Arrays.stream(text.trim().split("\\s+"))
                .map(word -> word.replaceAll("[^\\p{L}\\p{N}]", ""))
                .filter(word -> !word.isBlank())
                .collect(Collectors.joining(" | "));
    }
}

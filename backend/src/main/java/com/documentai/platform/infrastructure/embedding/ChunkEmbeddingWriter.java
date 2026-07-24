package com.documentai.platform.infrastructure.embedding;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Writes to document_chunks.embedding/embedding_model via native SQL, the same way the tsvector
 * column is only ever touched by KeywordSearchProvider - the JPA entity never maps either pgvector-
 * related column type.
 */
@Component
@RequiredArgsConstructor
public class ChunkEmbeddingWriter {

    private static final Logger log = LoggerFactory.getLogger(ChunkEmbeddingWriter.class);

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public void write(UUID chunkId, EmbeddingProvider.EmbeddingResult result) {
        int rows = jdbcTemplate.update(
                "UPDATE document_chunks SET embedding = CAST(:embedding AS vector), embedding_model = :embeddingModel WHERE id = :id",
                new MapSqlParameterSource()
                        .addValue("id", chunkId)
                        .addValue("embedding", PgVectorFormat.toLiteral(result.vector()))
                        .addValue("embeddingModel", result.modelIdentity()));
        if (rows == 0) {
            // Should be unreachable as long as callers flush pending chunk inserts first (see
            // DocumentProcessingServiceImpl) - kept as a loud signal in case that invariant ever breaks.
            log.warn("Embedding UPDATE matched 0 rows for chunk {} - was the chunk insert flushed first?", chunkId);
        }
    }

    /**
     * Documents with at least one chunk that's either missing an embedding entirely, or embedded
     * by a vendor/model other than the one most other chunks currently use (e.g. a burst of calls
     * served by a fallback vendor while the primary was rate-limited, followed by a return to the
     * primary - see FallbackEmbeddingProvider's sticky-vendor comment for the full scenario). Used
     * by {@link EmbeddingBackfillRunner} to automatically re-embed anything a schema/config change
     * or vendor drift left inconsistent, instead of requiring a manual per-document reprocess loop.
     */
    public List<UUID> findDocumentIdsNeedingReembedding() {
        return jdbcTemplate.getJdbcTemplate().queryForList("""
                SELECT DISTINCT document_id FROM document_chunks
                WHERE embedding IS NULL
                   OR embedding_model IS NULL
                   OR embedding_model <> (
                        SELECT mode() WITHIN GROUP (ORDER BY embedding_model)
                        FROM document_chunks WHERE embedding_model IS NOT NULL
                      )
                """, UUID.class);
    }
}

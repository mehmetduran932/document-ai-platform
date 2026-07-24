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
 * Writes to document_chunks.embedding via native SQL, the same way the tsvector column is only
 * ever touched by KeywordSearchProvider - the JPA entity never maps the pgvector column type.
 */
@Component
@RequiredArgsConstructor
public class ChunkEmbeddingWriter {

    private static final Logger log = LoggerFactory.getLogger(ChunkEmbeddingWriter.class);

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public void write(UUID chunkId, float[] embedding) {
        int rows = jdbcTemplate.update(
                "UPDATE document_chunks SET embedding = CAST(:embedding AS vector) WHERE id = :id",
                new MapSqlParameterSource()
                        .addValue("id", chunkId)
                        .addValue("embedding", PgVectorFormat.toLiteral(embedding)));
        if (rows == 0) {
            // Should be unreachable as long as callers flush pending chunk inserts first (see
            // DocumentProcessingServiceImpl) - kept as a loud signal in case that invariant ever breaks.
            log.warn("Embedding UPDATE matched 0 rows for chunk {} - was the chunk insert flushed first?", chunkId);
        }
    }

    /**
     * Documents with at least one chunk left without an embedding - e.g. a migration just resized/
     * reset document_chunks.embedding after an EMBEDDING_PROVIDER change. Used by
     * {@link EmbeddingBackfillRunner} to automatically re-embed anything a schema or config change
     * left stale, instead of requiring a manual per-document reprocess loop.
     */
    public List<UUID> findDocumentIdsWithNullEmbedding() {
        return jdbcTemplate.getJdbcTemplate().queryForList(
                "SELECT DISTINCT document_id FROM document_chunks WHERE embedding IS NULL", UUID.class);
    }
}

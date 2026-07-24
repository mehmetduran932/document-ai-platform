package com.documentai.platform.infrastructure.embedding;

import com.documentai.platform.service.DocumentProcessingService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Automatically re-embeds any document left with null embeddings by a schema or config change -
 * e.g. a migration that resized document_chunks.embedding after an EMBEDDING_PROVIDER switch. Before
 * this existed, fixing that required manually looping POST /api/documents/{id}/reprocess over every
 * affected document by hand; now the app heals itself on next restart.
 *
 * Deliberately does not check *which* model produced any non-null embeddings already present - only
 * whether they're null. Detecting "non-null but from a different, incompatible vendor" (e.g.
 * FallbackEmbeddingProvider silently switching from Gemini to OpenAI mid-run) would need a per-chunk
 * model-identity column and is a separate, lower-priority gap that predates this runner.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.search-provider", havingValue = "embedding")
public class EmbeddingBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingBackfillRunner.class);

    private final ChunkEmbeddingWriter chunkEmbeddingWriter;
    private final DocumentProcessingService documentProcessingService;

    @Override
    public void run(ApplicationArguments args) {
        List<UUID> staleDocumentIds = chunkEmbeddingWriter.findDocumentIdsWithNullEmbedding();
        if (staleDocumentIds.isEmpty()) {
            return;
        }

        log.info("Backfilling embeddings for {} document(s) left stale by a config/migration change",
                staleDocumentIds.size());
        for (UUID documentId : staleDocumentIds) {
            documentProcessingService.process(documentId);
        }
        log.info("Embedding backfill dispatched for {} document(s) (processing continues asynchronously)",
                staleDocumentIds.size());
    }
}

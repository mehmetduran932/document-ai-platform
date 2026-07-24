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
 * Automatically re-embeds any document left with null or vendor-mismatched embeddings by a schema
 * or config change - e.g. a migration that resized document_chunks.embedding after an
 * EMBEDDING_PROVIDER switch, or FallbackEmbeddingProvider serving a burst of chunks with a fallback
 * vendor while the primary was rate-limited. Before this existed, fixing the null case required
 * manually looping POST /api/documents/{id}/reprocess over every affected document by hand; now the
 * app heals itself on next restart - see
 * {@link ChunkEmbeddingWriter#findDocumentIdsNeedingReembedding()} for exactly what counts as stale.
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
        List<UUID> staleDocumentIds = chunkEmbeddingWriter.findDocumentIdsNeedingReembedding();
        if (staleDocumentIds.isEmpty()) {
            return;
        }

        log.info("Backfilling embeddings for {} document(s) left stale by a config/migration change "
                + "or vendor drift", staleDocumentIds.size());
        for (UUID documentId : staleDocumentIds) {
            documentProcessingService.process(documentId);
        }
        log.info("Embedding backfill dispatched for {} document(s) (processing continues asynchronously)",
                staleDocumentIds.size());
    }
}

package com.documentai.platform.service.impl;

import com.documentai.platform.domain.entity.ChunkKeyword;
import com.documentai.platform.domain.entity.Document;
import com.documentai.platform.domain.entity.DocumentChunk;
import com.documentai.platform.domain.enums.ProcessingStatus;
import com.documentai.platform.exception.ResourceNotFoundException;
import com.documentai.platform.infrastructure.embedding.ChunkEmbeddingWriter;
import com.documentai.platform.infrastructure.embedding.EmbeddingProvider;
import com.documentai.platform.infrastructure.keyword.ExtractedKeyword;
import com.documentai.platform.infrastructure.keyword.KeywordExtractionService;
import com.documentai.platform.infrastructure.processing.ChunkDraft;
import com.documentai.platform.infrastructure.processing.ChunkingService;
import com.documentai.platform.infrastructure.processing.ExtractedText;
import com.documentai.platform.infrastructure.processing.TextExtractor;
import com.documentai.platform.infrastructure.processing.TextExtractorFactory;
import com.documentai.platform.infrastructure.storage.StorageProvider;
import com.documentai.platform.repository.ChunkKeywordRepository;
import com.documentai.platform.repository.DocumentChunkRepository;
import com.documentai.platform.repository.DocumentRepository;
import com.documentai.platform.service.DocumentProcessingService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates the async pipeline: download -> extract text -> chunk -> extract keywords -> persist.
 * Text extraction/chunking (CPU/IO bound, no DB) runs outside any transaction; the persistence
 * step runs in its own transaction so a mid-pipeline failure never leaves half-written chunks
 * alongside a stale PENDING/PROCESSING status.
 */
@Service
@RequiredArgsConstructor
public class DocumentProcessingServiceImpl implements DocumentProcessingService {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessingServiceImpl.class);
    private static final int MAX_KEYWORDS_PER_CHUNK = 8;
    private static final int MAX_ERROR_MESSAGE_LENGTH = 2000;

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final ChunkKeywordRepository keywordRepository;
    private final StorageProvider storageProvider;
    private final TextExtractorFactory textExtractorFactory;
    private final ChunkingService chunkingService;
    private final KeywordExtractionService keywordExtractionService;
    private final TransactionTemplate transactionTemplate;
    private final Optional<EmbeddingProvider> embeddingProvider;
    private final ChunkEmbeddingWriter chunkEmbeddingWriter;

    @Override
    @Async("documentProcessingExecutor")
    public void process(UUID documentId) {
        markStatus(documentId, ProcessingStatus.PROCESSING, null);

        try {
            Document document = documentRepository.findById(documentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));

            ExtractedText extracted;
            try (InputStream stream = storageProvider.download(document.getStorageKey())) {
                TextExtractor extractor = textExtractorFactory.forExtension(document.getExtension());
                extracted = extractor.extract(stream);
            }

            List<ChunkDraft> drafts = chunkingService.chunk(extracted.text(), null);

            transactionTemplate.executeWithoutResult(status -> persistResult(documentId, drafts, extracted.pageCount()));
        } catch (Exception e) {
            log.error("Document processing failed for document {}", documentId, e);
            markStatus(documentId, ProcessingStatus.FAILED, truncate(e.getMessage()));
        }
    }

    private void persistResult(UUID documentId, List<ChunkDraft> drafts, Integer pageCount) {
        Document managed = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));

        // Idempotent: a retry/reprocess (e.g. after the app restarted mid-processing) must not
        // pile up duplicate chunks or hit the (document_id, chunk_index) unique constraint.
        // The explicit flush forces the DELETE to hit the DB before the INSERTs below are
        // batched - without it Hibernate can order them so an old row is still present when a
        // new chunk with the same (document_id, chunk_index) is inserted.
        chunkRepository.deleteAllByDocumentId(documentId);
        chunkRepository.flush();

        List<DocumentChunk> savedChunks = new java.util.ArrayList<>();
        for (ChunkDraft draft : drafts) {
            DocumentChunk chunk = DocumentChunk.builder()
                    .document(managed)
                    .workspace(managed.getWorkspace())
                    .page(draft.page())
                    .chunkIndex(draft.chunkIndex())
                    .content(draft.content())
                    .wordCount(draft.wordCount())
                    .build();
            chunk = chunkRepository.save(chunk);
            savedChunks.add(chunk);

            List<ExtractedKeyword> keywords = keywordExtractionService.extract(draft.content(), MAX_KEYWORDS_PER_CHUNK);
            for (ExtractedKeyword keyword : keywords) {
                keywordRepository.save(ChunkKeyword.builder()
                        .chunk(chunk)
                        .document(managed)
                        .workspace(managed.getWorkspace())
                        .keyword(keyword.keyword())
                        .score(keyword.score())
                        .build());
            }
        }

        // Only computed when app.search-provider=embedding; keyword search never needs this and
        // embeddings cost real money/time, so skip the API calls entirely otherwise.
        //
        // The flush is required: chunkRepository.save() above only queues the INSERT in
        // Hibernate's session - it isn't sent to the DB until a flush happens. The embedding
        // UPDATE below goes through raw JDBC (NamedParameterJdbcTemplate), which doesn't trigger
        // a Hibernate auto-flush, so without this the UPDATE runs against rows that don't exist
        // in the database yet and silently matches zero of them.
        if (embeddingProvider.isPresent()) {
            chunkRepository.flush();
        }
        embeddingProvider.ifPresent(provider -> {
            List<String> contents = savedChunks.stream().map(DocumentChunk::getContent).toList();
            List<EmbeddingProvider.EmbeddingResult> embeddings = provider.embedBatch(contents);
            for (int i = 0; i < savedChunks.size(); i++) {
                chunkEmbeddingWriter.write(savedChunks.get(i).getId(), embeddings.get(i));
            }
        });

        managed.setProcessingStatus(ProcessingStatus.COMPLETED);
        managed.setProcessingError(null);
        managed.setPageCount(pageCount);
        documentRepository.save(managed);
    }

    private void markStatus(UUID documentId, ProcessingStatus status, String error) {
        transactionTemplate.executeWithoutResult(txStatus ->
                documentRepository.findById(documentId).ifPresent(doc -> {
                    doc.setProcessingStatus(status);
                    doc.setProcessingError(error);
                    documentRepository.save(doc);
                }));
    }

    private String truncate(String message) {
        if (message == null) {
            return "Unknown error during document processing";
        }
        return message.length() > MAX_ERROR_MESSAGE_LENGTH ? message.substring(0, MAX_ERROR_MESSAGE_LENGTH) : message;
    }
}

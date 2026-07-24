package com.documentai.platform.service.impl;

import com.documentai.platform.service.DocumentProcessingService;
import com.documentai.platform.service.DocumentUploadedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Kicks off async processing only after the upload transaction has committed, so the worker
 * thread never races the transaction that inserted the Document row.
 */
@Component
@RequiredArgsConstructor
public class DocumentUploadedEventListener {

    private final DocumentProcessingService documentProcessingService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDocumentUploaded(DocumentUploadedEvent event) {
        documentProcessingService.process(event.documentId());
    }
}

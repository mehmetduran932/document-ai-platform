package com.documentai.platform.service;

import java.util.UUID;

public interface DocumentProcessingService {

    /** Runs asynchronously; failures are caught and recorded on the document, never rethrown. */
    void process(UUID documentId);
}

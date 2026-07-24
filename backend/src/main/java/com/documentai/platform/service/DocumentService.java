package com.documentai.platform.service;

import com.documentai.platform.dto.response.DocumentChunkResponse;
import com.documentai.platform.dto.response.DocumentResponse;
import com.documentai.platform.dto.response.PageResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

public interface DocumentService {

    DocumentResponse uploadDocument(UUID workspaceId, UUID userId, MultipartFile file);

    PageResponse<DocumentResponse> listDocuments(UUID workspaceId, int page, int size);

    DocumentResponse getDocumentMetadata(UUID workspaceId, UUID documentId);

    DocumentChunkResponse getChunk(UUID workspaceId, UUID chunkId);

    DownloadHandle openDownloadStream(UUID workspaceId, UUID documentId);

    String getPresignedDownloadUrl(UUID workspaceId, UUID documentId);

    /** Re-runs the processing pipeline for a document already in storage (e.g. after a stuck/failed run). */
    void reprocess(UUID workspaceId, UUID documentId);

    record DownloadHandle(InputStream content, String filename, String contentType, long size) {
    }
}

package com.documentai.platform.controller;

import com.documentai.platform.dto.response.BulkUploadResponse;
import com.documentai.platform.dto.response.DocumentChunkResponse;
import com.documentai.platform.dto.response.DocumentResponse;
import com.documentai.platform.dto.response.PageResponse;
import com.documentai.platform.infrastructure.security.SecurityUtils;
import com.documentai.platform.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Validated
@Tag(name = "Documents")
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a document (PDF, Word, Excel, Markdown, or image); processing runs asynchronously")
    public ResponseEntity<DocumentResponse> upload(@RequestParam("file") MultipartFile file) {
        DocumentResponse response = documentService.uploadDocument(
                SecurityUtils.currentWorkspaceId(), SecurityUtils.currentUserId(), file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping(value = "/bulk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload multiple documents in one request; each file is uploaded independently so one "
            + "unsupported/invalid file doesn't block the rest, and processing runs asynchronously per document")
    public ResponseEntity<BulkUploadResponse> uploadBulk(@RequestParam("files") List<MultipartFile> files) {
        UUID workspaceId = SecurityUtils.currentWorkspaceId();
        UUID userId = SecurityUtils.currentUserId();

        List<DocumentResponse> uploaded = new ArrayList<>();
        List<BulkUploadResponse.Failure> failed = new ArrayList<>();
        for (MultipartFile file : files) {
            try {
                uploaded.add(documentService.uploadDocument(workspaceId, userId, file));
            } catch (RuntimeException e) {
                failed.add(new BulkUploadResponse.Failure(file.getOriginalFilename(), e.getMessage()));
            }
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(new BulkUploadResponse(uploaded, failed));
    }

    @GetMapping
    @Operation(summary = "List documents in the current workspace")
    public ResponseEntity<PageResponse<DocumentResponse>> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(documentService.listDocuments(SecurityUtils.currentWorkspaceId(), page, size));
    }

    @GetMapping("/{documentId}")
    @Operation(summary = "Get document metadata")
    public ResponseEntity<DocumentResponse> get(@PathVariable UUID documentId) {
        return ResponseEntity.ok(documentService.getDocumentMetadata(SecurityUtils.currentWorkspaceId(), documentId));
    }

    @GetMapping("/{documentId}/download")
    @Operation(summary = "Download the original file")
    public ResponseEntity<InputStreamResource> download(@PathVariable UUID documentId) {
        var handle = documentService.openDownloadStream(SecurityUtils.currentWorkspaceId(), documentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(handle.contentType()))
                .contentLength(handle.size())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(handle.filename()).build().toString())
                .body(new InputStreamResource(handle.content()));
    }

    @GetMapping("/chunks/{chunkId}")
    @Operation(summary = "Read a single document chunk by id")
    public ResponseEntity<DocumentChunkResponse> getChunk(@PathVariable UUID chunkId) {
        return ResponseEntity.ok(documentService.getChunk(SecurityUtils.currentWorkspaceId(), chunkId));
    }

    @PostMapping("/{documentId}/reprocess")
    @Operation(summary = "Re-run text extraction/chunking/keyword extraction for a document already in storage "
            + "(e.g. it's stuck in PENDING/PROCESSING after an app restart, or FAILED)")
    public ResponseEntity<Void> reprocess(@PathVariable UUID documentId) {
        documentService.reprocess(SecurityUtils.currentWorkspaceId(), documentId);
        return ResponseEntity.accepted().build();
    }
}

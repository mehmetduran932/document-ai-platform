package com.documentai.platform.service.impl;

import com.documentai.platform.domain.entity.Document;
import com.documentai.platform.domain.entity.DocumentChunk;
import com.documentai.platform.domain.enums.ProcessingStatus;
import com.documentai.platform.dto.response.DocumentChunkResponse;
import com.documentai.platform.dto.response.DocumentResponse;
import com.documentai.platform.dto.response.PageResponse;
import com.documentai.platform.exception.ResourceNotFoundException;
import com.documentai.platform.exception.UnsupportedDocumentTypeException;
import com.documentai.platform.infrastructure.processing.SupportedExtensions;
import com.documentai.platform.infrastructure.storage.StorageProvider;
import com.documentai.platform.mapper.DocumentMapper;
import com.documentai.platform.repository.DocumentChunkRepository;
import com.documentai.platform.repository.DocumentRepository;
import com.documentai.platform.repository.UserRepository;
import com.documentai.platform.repository.WorkspaceRepository;
import com.documentai.platform.service.DocumentProcessingService;
import com.documentai.platform.service.DocumentService;
import com.documentai.platform.service.DocumentUploadedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private static final Duration DOWNLOAD_URL_TTL = Duration.ofMinutes(15);

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final WorkspaceRepository workspaceRepository;
    private final UserRepository userRepository;
    private final StorageProvider storageProvider;
    private final DocumentMapper documentMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final DocumentProcessingService documentProcessingService;

    @Override
    @Transactional
    public DocumentResponse uploadDocument(UUID workspaceId, UUID userId, MultipartFile file) {
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unnamed";
        String extension = extractExtension(filename);
        if (!SupportedExtensions.isSupported(extension)) {
            throw new UnsupportedDocumentTypeException("Unsupported file type: ." + extension);
        }

        String storageKey = workspaceId + "/" + UUID.randomUUID() + "." + extension;
        try {
            storageProvider.upload(storageKey, file.getInputStream(), file.getSize(), file.getContentType());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read uploaded file", e);
        }

        Document document = Document.builder()
                .workspace(workspaceRepository.getReferenceById(workspaceId))
                .uploadedBy(userRepository.getReferenceById(userId))
                .filename(filename)
                .extension(extension)
                .size(file.getSize())
                .storageKey(storageKey)
                .processingStatus(ProcessingStatus.PENDING)
                .build();
        document = documentRepository.save(document);

        eventPublisher.publishEvent(new DocumentUploadedEvent(document.getId()));

        return documentMapper.toResponse(document);
    }

    @Override
    public PageResponse<DocumentResponse> listDocuments(UUID workspaceId, int page, int size) {
        var result = documentRepository.findAllByWorkspaceId(
                workspaceId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "uploadDate")));
        return PageResponse.from(result, documentMapper::toResponse);
    }

    @Override
    public DocumentResponse getDocumentMetadata(UUID workspaceId, UUID documentId) {
        return documentMapper.toResponse(findDocument(workspaceId, documentId));
    }

    @Override
    public DocumentChunkResponse getChunk(UUID workspaceId, UUID chunkId) {
        DocumentChunk chunk = chunkRepository.findByIdAndWorkspaceId(chunkId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Chunk not found: " + chunkId));
        return documentMapper.toChunkResponse(chunk);
    }

    @Override
    public DownloadHandle openDownloadStream(UUID workspaceId, UUID documentId) {
        Document document = findDocument(workspaceId, documentId);
        return new DownloadHandle(
                storageProvider.download(document.getStorageKey()),
                document.getFilename(),
                contentTypeFor(document.getExtension()),
                document.getSize());
    }

    @Override
    public String getPresignedDownloadUrl(UUID workspaceId, UUID documentId) {
        Document document = findDocument(workspaceId, documentId);
        return storageProvider.presignedDownloadUrl(document.getStorageKey(), DOWNLOAD_URL_TTL).toString();
    }

    @Override
    public void reprocess(UUID workspaceId, UUID documentId) {
        findDocument(workspaceId, documentId); // 404s if it doesn't belong to this workspace
        documentProcessingService.process(documentId);
    }

    private Document findDocument(UUID workspaceId, UUID documentId) {
        return documentRepository.findByIdAndWorkspaceId(documentId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));
    }

    private String extractExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            throw new UnsupportedDocumentTypeException("File has no extension: " + filename);
        }
        return filename.substring(dot + 1).toLowerCase();
    }

    private String contentTypeFor(String extension) {
        return switch (extension.toLowerCase()) {
            case "pdf" -> "application/pdf";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls" -> "application/vnd.ms-excel";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "tif", "tiff" -> "image/tiff";
            case "bmp" -> "image/bmp";
            case "gif" -> "image/gif";
            default -> "application/octet-stream";
        };
    }
}

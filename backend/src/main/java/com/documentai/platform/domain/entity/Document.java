package com.documentai.platform.domain.entity;

import com.documentai.platform.domain.enums.ProcessingStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "documents", indexes = {
        @Index(name = "idx_documents_workspace", columnList = "workspace_id"),
        @Index(name = "idx_documents_status", columnList = "processing_status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Document {

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by")
    private User uploadedBy;

    @Column(nullable = false)
    private String filename;

    @Column(nullable = false, length = 20)
    private String extension;

    @Column(nullable = false)
    private long size;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(name = "upload_date", nullable = false, updatable = false)
    private Instant uploadDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 20)
    private ProcessingStatus processingStatus;

    @Column(name = "processing_error", columnDefinition = "text")
    private String processingError;

    @Column(name = "page_count")
    private Integer pageCount;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (uploadDate == null) {
            uploadDate = now;
        }
        updatedAt = now;
        if (processingStatus == null) {
            processingStatus = ProcessingStatus.PENDING;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}

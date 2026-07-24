package com.documentai.platform.dto.response;

import com.documentai.platform.domain.enums.ProcessingStatus;

import java.time.Instant;
import java.util.UUID;

public record DocumentResponse(
        UUID id,
        String filename,
        String extension,
        long size,
        Instant uploadDate,
        ProcessingStatus processingStatus,
        String processingError,
        Integer pageCount
) {
}

package com.documentai.platform.dto.response;

import java.util.List;

public record BulkUploadResponse(
        List<DocumentResponse> uploaded,
        List<Failure> failed
) {
    public record Failure(String filename, String error) {
    }
}

package com.documentai.platform.dto.request;

import jakarta.validation.constraints.NotBlank;

public record SearchRequest(
        @NotBlank String query
) {
}

package com.documentai.platform.dto.response;

import java.time.Instant;
import java.util.UUID;

public record AskHistoryResponse(
        UUID id,
        String question,
        String answer,
        Instant createdAt
) {
}

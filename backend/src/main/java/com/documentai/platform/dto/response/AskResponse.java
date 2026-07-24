package com.documentai.platform.dto.response;

import java.util.List;

public record AskResponse(
        String question,
        String answer,
        List<SearchResultResponse> sourceChunks
) {
}

package com.documentai.platform.service;

import com.documentai.platform.dto.response.AskHistoryResponse;
import com.documentai.platform.dto.response.AskResponse;
import com.documentai.platform.dto.response.PageResponse;

import java.util.UUID;

public interface AskService {

    AskResponse ask(UUID workspaceId, UUID userId, String question);

    PageResponse<AskHistoryResponse> listHistory(UUID workspaceId, int page, int size);

    void clearHistory(UUID workspaceId);
}

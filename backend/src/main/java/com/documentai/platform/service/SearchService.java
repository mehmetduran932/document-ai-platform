package com.documentai.platform.service;

import com.documentai.platform.dto.response.SearchResultResponse;

import java.util.UUID;

public interface SearchService {

    SearchResultResponse.SearchResponseWrapper search(UUID workspaceId, String questionText);
}

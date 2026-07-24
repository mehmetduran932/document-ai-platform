package com.documentai.platform.controller;

import com.documentai.platform.config.SearchProperties;
import com.documentai.platform.dto.request.SearchRequest;
import com.documentai.platform.dto.response.SearchResultResponse;
import com.documentai.platform.infrastructure.security.SecurityUtils;
import com.documentai.platform.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
@Tag(name = "Search", description = "Keyword search over document chunks, scoped to the caller's workspace")
public class SearchController {

    private final SearchService searchService;
    private final SearchProperties searchProperties;

    @PostMapping
    @Operation(summary = "Search the workspace's documents; returns the best-matching chunks, ranked, max 8. "
            + "content is previewed/truncated - fetch the full chunk via GET /api/documents/chunks/{chunkId}.")
    public ResponseEntity<SearchResultResponse.SearchResponseWrapper> search(@Valid @RequestBody SearchRequest request) {
        var result = searchService.search(SecurityUtils.currentWorkspaceId(), request.query());
        var truncated = result.results().stream()
                .map(r -> r.withTruncatedContent(searchProperties.contentPreviewChars()))
                .toList();
        return ResponseEntity.ok(new SearchResultResponse.SearchResponseWrapper(
                result.query(), result.extractedKeywords(), truncated));
    }
}

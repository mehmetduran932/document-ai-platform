package com.documentai.platform.service.impl;

import com.documentai.platform.config.SearchProperties;
import com.documentai.platform.dto.response.SearchResultResponse;
import com.documentai.platform.infrastructure.keyword.ExtractedKeyword;
import com.documentai.platform.infrastructure.keyword.KeywordExtractionService;
import com.documentai.platform.infrastructure.search.SearchProvider;
import com.documentai.platform.infrastructure.search.SearchQuery;
import com.documentai.platform.infrastructure.search.SearchResultChunk;
import com.documentai.platform.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private static final int MAX_QUERY_KEYWORDS = 10;

    private final KeywordExtractionService keywordExtractionService;
    private final SearchProvider searchProvider;
    private final SearchProperties searchProperties;

    @Override
    public SearchResultResponse.SearchResponseWrapper search(UUID workspaceId, String questionText) {
        List<ExtractedKeyword> extracted = keywordExtractionService.extract(questionText, MAX_QUERY_KEYWORDS);
        List<String> keywords = extracted.stream().map(ExtractedKeyword::keyword).toList();

        // rawQuestion is used as-is by embedding-based providers; keywords (OR-combined) by
        // keyword-based providers. Each SearchProvider picks whichever signal it needs.
        SearchQuery query = new SearchQuery(workspaceId, questionText, keywords, searchProperties.maxResults());
        List<SearchResultChunk> results = searchProvider.search(query);

        List<SearchResultResponse> mapped = results.stream()
                .map(r -> new SearchResultResponse(
                        r.chunkId(), r.documentId(), r.documentFilename(), r.page(),
                        r.chunkIndex(), r.content(), r.relevanceScore()))
                .collect(Collectors.toList());

        return new SearchResultResponse.SearchResponseWrapper(questionText, keywords, mapped);
    }
}

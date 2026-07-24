package com.documentai.platform.service.impl;

import com.documentai.platform.config.SearchProperties;
import com.documentai.platform.dto.response.AskResponse;
import com.documentai.platform.dto.response.SearchResultResponse;
import com.documentai.platform.infrastructure.answer.AnswerProvider;
import com.documentai.platform.infrastructure.answer.SourceChunk;
import com.documentai.platform.service.AskService;
import com.documentai.platform.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AskServiceImpl implements AskService {

    private static final String NO_RESULTS_ANSWER =
            "I couldn't find anything relevant to this question in the workspace's documents.";

    private final SearchService searchService;
    private final AnswerProvider answerProvider;
    private final SearchProperties searchProperties;

    @Override
    public AskResponse ask(UUID workspaceId, String question) {
        SearchResultResponse.SearchResponseWrapper searchResult = searchService.search(workspaceId, question);

        if (searchResult.results().isEmpty()) {
            return new AskResponse(question, NO_RESULTS_ANSWER, List.of());
        }

        // /api/search already ranks best-first and returns at most app.search.max-results; only
        // the top few of those actually go to the LLM, to bound token usage per answer.
        List<SearchResultResponse> grounding = searchResult.results().stream()
                .limit(searchProperties.maxAnswerChunks())
                .toList();

        List<SourceChunk> sourceChunks = grounding.stream()
                .map(r -> new SourceChunk(r.documentFilename(), r.page(), r.content()))
                .toList();

        String answer = answerProvider.generateAnswer(question, sourceChunks);

        List<SearchResultResponse> truncatedGrounding = grounding.stream()
                .map(r -> r.withTruncatedContent(searchProperties.contentPreviewChars()))
                .toList();
        return new AskResponse(question, answer, truncatedGrounding);
    }
}

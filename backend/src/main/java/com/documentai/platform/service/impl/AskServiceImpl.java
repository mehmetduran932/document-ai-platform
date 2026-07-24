package com.documentai.platform.service.impl;

import com.documentai.platform.config.SearchProperties;
import com.documentai.platform.domain.entity.AskHistory;
import com.documentai.platform.dto.response.AskHistoryResponse;
import com.documentai.platform.dto.response.AskResponse;
import com.documentai.platform.dto.response.PageResponse;
import com.documentai.platform.dto.response.SearchResultResponse;
import com.documentai.platform.infrastructure.answer.AnswerProvider;
import com.documentai.platform.infrastructure.answer.SourceChunk;
import com.documentai.platform.repository.AskHistoryRepository;
import com.documentai.platform.repository.UserRepository;
import com.documentai.platform.repository.WorkspaceRepository;
import com.documentai.platform.service.AskService;
import com.documentai.platform.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final AskHistoryRepository askHistoryRepository;
    private final WorkspaceRepository workspaceRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public AskResponse ask(UUID workspaceId, UUID userId, String question) {
        SearchResultResponse.SearchResponseWrapper searchResult = searchService.search(workspaceId, question);

        if (searchResult.results().isEmpty()) {
            saveHistory(workspaceId, userId, question, NO_RESULTS_ANSWER);
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
        saveHistory(workspaceId, userId, question, answer);

        List<SearchResultResponse> truncatedGrounding = grounding.stream()
                .map(r -> r.withTruncatedContent(searchProperties.contentPreviewChars()))
                .toList();
        return new AskResponse(question, answer, truncatedGrounding);
    }

    @Override
    public PageResponse<AskHistoryResponse> listHistory(UUID workspaceId, int page, int size) {
        var result = askHistoryRepository.findAllByWorkspaceIdOrderByCreatedAtDesc(
                workspaceId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return PageResponse.from(result, h -> new AskHistoryResponse(h.getId(), h.getQuestion(), h.getAnswer(), h.getCreatedAt()));
    }

    @Override
    @Transactional
    public void clearHistory(UUID workspaceId) {
        askHistoryRepository.deleteAllByWorkspaceId(workspaceId);
    }

    private void saveHistory(UUID workspaceId, UUID userId, String question, String answer) {
        AskHistory history = AskHistory.builder()
                .workspace(workspaceRepository.getReferenceById(workspaceId))
                .askedBy(userId != null ? userRepository.getReferenceById(userId) : null)
                .question(question)
                .answer(answer)
                .build();
        askHistoryRepository.save(history);
    }
}

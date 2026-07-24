package com.documentai.platform.controller;

import com.documentai.platform.dto.request.AskRequest;
import com.documentai.platform.dto.response.AskHistoryResponse;
import com.documentai.platform.dto.response.AskResponse;
import com.documentai.platform.dto.response.PageResponse;
import com.documentai.platform.infrastructure.security.SecurityUtils;
import com.documentai.platform.service.AskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ask")
@RequiredArgsConstructor
@Validated
@Tag(name = "Ask", description = "Search + LLM-synthesized answer, grounded only in the matched chunks")
public class AskController {

    private final AskService askService;

    @PostMapping
    @Operation(summary = "Ask a question; searches the workspace and asks the configured LLM to answer "
            + "using only the matched chunks (never whole documents)")
    public ResponseEntity<AskResponse> ask(@Valid @RequestBody AskRequest request) {
        return ResponseEntity.ok(askService.ask(
                SecurityUtils.currentWorkspaceId(), SecurityUtils.currentUserId(), request.question()));
    }

    @GetMapping("/history")
    @Operation(summary = "List this workspace's ask history, newest first; persists across logout/login "
            + "until explicitly cleared")
    public ResponseEntity<PageResponse<AskHistoryResponse>> history(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(askService.listHistory(SecurityUtils.currentWorkspaceId(), page, size));
    }

    @DeleteMapping("/history")
    @Operation(summary = "Permanently clear this workspace's ask history")
    public ResponseEntity<Void> clearHistory() {
        askService.clearHistory(SecurityUtils.currentWorkspaceId());
        return ResponseEntity.noContent().build();
    }
}

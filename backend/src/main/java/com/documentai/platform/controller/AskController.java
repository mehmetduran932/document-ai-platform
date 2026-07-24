package com.documentai.platform.controller;

import com.documentai.platform.dto.request.AskRequest;
import com.documentai.platform.dto.response.AskResponse;
import com.documentai.platform.infrastructure.security.SecurityUtils;
import com.documentai.platform.service.AskService;
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
@RequestMapping("/api/ask")
@RequiredArgsConstructor
@Tag(name = "Ask", description = "Search + LLM-synthesized answer, grounded only in the matched chunks")
public class AskController {

    private final AskService askService;

    @PostMapping
    @Operation(summary = "Ask a question; searches the workspace and asks the configured LLM to answer "
            + "using only the matched chunks (never whole documents)")
    public ResponseEntity<AskResponse> ask(@Valid @RequestBody AskRequest request) {
        return ResponseEntity.ok(askService.ask(SecurityUtils.currentWorkspaceId(), request.question()));
    }
}

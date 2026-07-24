package com.documentai.platform.infrastructure.answer;

import com.documentai.platform.config.AnswerProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.answer-provider", havingValue = "claude", matchIfMissing = true)
public class ClaudeAnswerProvider implements AnswerProvider {

    private static final Logger log = LoggerFactory.getLogger(ClaudeAnswerProvider.class);

    private final AnswerProperties properties;
    private final RestClient restClient;

    @Override
    public String generateAnswer(String question, List<SourceChunk> sourceChunks) {
        AnswerProperties.Claude config = properties.claude();
        ClaudeRequest request = new ClaudeRequest(
                config.model(),
                config.maxTokens(),
                AnswerPrompt.system(),
                List.of(new ClaudeMessage("user", AnswerPrompt.user(question, sourceChunks))));

        try {
            ClaudeResponse response = restClient.post()
                    .uri(config.baseUrl() + "/v1/messages")
                    .header("x-api-key", config.apiKey())
                    .header("anthropic-version", config.apiVersion())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ClaudeResponse.class);

            if (response == null || response.content() == null || response.content().isEmpty()) {
                throw new AnswerGenerationException("Claude API returned an empty response");
            }
            return response.content().get(0).text();
        } catch (RestClientException e) {
            log.error("Claude API call failed", e);
            throw new AnswerGenerationException("Failed to generate answer via Claude: " + e.getMessage(), e);
        }
    }

    private record ClaudeRequest(
            String model,
            @JsonProperty("max_tokens") int maxTokens,
            String system,
            List<ClaudeMessage> messages) {
    }

    private record ClaudeMessage(String role, String content) {
    }

    private record ClaudeResponse(List<ClaudeContentBlock> content) {
    }

    private record ClaudeContentBlock(String type, String text) {
    }
}

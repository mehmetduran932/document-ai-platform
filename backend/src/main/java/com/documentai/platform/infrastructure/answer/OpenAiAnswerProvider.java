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

/**
 * Talks the OpenAI chat-completions wire format. baseUrl is configurable (default
 * https://api.openai.com/v1) specifically so this same class can target a local
 * OpenAI-API-compatible model server (Ollama, LM Studio, vLLM, ...) later - just change
 * app.openai.base-url and app.answer-provider=openai, no code change.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.answer-provider", havingValue = "openai")
public class OpenAiAnswerProvider implements AnswerProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiAnswerProvider.class);

    private final AnswerProperties properties;
    private final RestClient restClient;

    @Override
    public String generateAnswer(String question, List<SourceChunk> sourceChunks) {
        AnswerProperties.OpenAi config = properties.openai();
        ChatRequest request = new ChatRequest(
                config.model(),
                config.maxTokens(),
                List.of(
                        new ChatMessage("system", AnswerPrompt.system()),
                        new ChatMessage("user", AnswerPrompt.user(question, sourceChunks))));

        try {
            ChatResponse response = restClient.post()
                    .uri(config.baseUrl() + "/chat/completions")
                    .header("Authorization", "Bearer " + config.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ChatResponse.class);

            if (response == null || response.choices() == null || response.choices().isEmpty()) {
                throw new AnswerGenerationException("OpenAI-compatible API returned an empty response");
            }
            return response.choices().get(0).message().content();
        } catch (RestClientException e) {
            log.error("OpenAI-compatible API call failed", e);
            throw new AnswerGenerationException("Failed to generate answer via OpenAI-compatible API: " + e.getMessage(), e);
        }
    }

    // max_completion_tokens is the current OpenAI parameter name (gpt-5.x, o-series); the older
    // max_tokens name is deprecated for these models. If pointing this at an older local
    // OpenAI-compatible shim that only understands max_tokens, that's a one-line change here.
    private record ChatRequest(
            String model,
            @JsonProperty("max_completion_tokens") int maxTokens,
            List<ChatMessage> messages) {
    }

    private record ChatMessage(String role, String content) {
    }

    private record ChatResponse(List<Choice> choices) {
    }

    private record Choice(ChatMessage message) {
    }
}

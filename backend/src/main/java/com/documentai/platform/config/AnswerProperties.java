package com.documentai.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(prefix = "app")
public record AnswerProperties(
        String answerProvider,
        @NestedConfigurationProperty Claude claude,
        @NestedConfigurationProperty OpenAi openai
) {

    public record Claude(String apiKey, String model, int maxTokens, String apiVersion, String baseUrl) {
    }

    /**
     * baseUrl defaults to OpenAI's own API but is deliberately overridable: most local model
     * servers (Ollama, LM Studio, vLLM) speak an OpenAI-compatible /v1/chat/completions API, so
     * pointing this at http://localhost:11434/v1 (etc.) with app.answer-provider=openai is enough
     * to run against a local model - no code change needed.
     */
    public record OpenAi(String apiKey, String model, int maxTokens, String baseUrl) {
    }
}

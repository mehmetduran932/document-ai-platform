package com.documentai.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.mcp")
public record McpProperties(
        boolean enabled,
        /** Exposes an `ask_documents` MCP tool that generates an answer server-side (same
         *  AnswerProvider as POST /api/ask), instead of the MCP layer staying read-only and
         *  leaving synthesis to the calling agent. Off by default - a deliberate per-deployment
         *  choice, not a code change, for whoever operates this instance. */
        boolean answerToolEnabled
) {
}

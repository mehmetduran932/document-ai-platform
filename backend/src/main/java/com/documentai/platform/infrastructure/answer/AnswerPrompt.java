package com.documentai.platform.infrastructure.answer;

import java.util.List;

/** Builds the shared prompt text so every provider grounds its answer the same way. */
final class AnswerPrompt {

    private AnswerPrompt() {
    }

    static String system() {
        return "You answer questions using ONLY the numbered document excerpts the user provides. "
                + "Never use outside knowledge. If the excerpts do not contain enough information to "
                + "answer, say so plainly instead of guessing. When you use an excerpt, cite it by its "
                + "source filename in parentheses.";
    }

    static String user(String question, List<SourceChunk> chunks) {
        StringBuilder sb = new StringBuilder();
        sb.append("Question: ").append(question).append("\n\nExcerpts:\n");
        for (int i = 0; i < chunks.size(); i++) {
            SourceChunk chunk = chunks.get(i);
            sb.append("[").append(i + 1).append("] source=").append(chunk.documentFilename());
            if (chunk.page() != null) {
                sb.append(" page=").append(chunk.page());
            }
            sb.append("\n").append(chunk.content()).append("\n\n");
        }
        return sb.toString();
    }
}

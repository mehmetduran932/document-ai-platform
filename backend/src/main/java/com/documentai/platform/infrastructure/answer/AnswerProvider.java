package com.documentai.platform.infrastructure.answer;

import java.util.List;

/**
 * Synthesizes a natural-language answer from chunks that were already found by search - never
 * from a whole document. Same swap pattern as {@link com.documentai.platform.infrastructure.search.SearchProvider}:
 * callers depend only on this interface, so switching model/provider (Claude, OpenAI, or a
 * future local model server) means adding an implementation and flipping app.answer-provider,
 * with zero changes to AskService or the controller.
 */
public interface AnswerProvider {

    String generateAnswer(String question, List<SourceChunk> sourceChunks);
}

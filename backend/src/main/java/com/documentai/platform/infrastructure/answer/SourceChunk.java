package com.documentai.platform.infrastructure.answer;

/** Backend-agnostic input to an AnswerProvider - deliberately just the chunk, never a whole document. */
public record SourceChunk(String documentFilename, Integer page, String content) {
}

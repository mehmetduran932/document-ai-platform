package com.documentai.platform.infrastructure.embedding;

public class EmbeddingGenerationException extends RuntimeException {

    public EmbeddingGenerationException(String message, Throwable cause) {
        super(message, cause);
    }

    public EmbeddingGenerationException(String message) {
        super(message);
    }
}

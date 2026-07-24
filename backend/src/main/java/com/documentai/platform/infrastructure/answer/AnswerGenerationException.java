package com.documentai.platform.infrastructure.answer;

public class AnswerGenerationException extends RuntimeException {

    public AnswerGenerationException(String message, Throwable cause) {
        super(message, cause);
    }

    public AnswerGenerationException(String message) {
        super(message);
    }
}

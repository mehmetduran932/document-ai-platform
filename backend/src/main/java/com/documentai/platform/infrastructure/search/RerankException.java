package com.documentai.platform.infrastructure.search;

public class RerankException extends RuntimeException {

    public RerankException(String message, Throwable cause) {
        super(message, cause);
    }

    public RerankException(String message) {
        super(message);
    }
}

package com.documentai.platform.infrastructure.processing;

import com.documentai.platform.exception.UnsupportedDocumentTypeException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TextExtractorFactory {

    private final List<TextExtractor> extractors;

    public TextExtractorFactory(List<TextExtractor> extractors) {
        this.extractors = extractors;
    }

    public TextExtractor forExtension(String extension) {
        return extractors.stream()
                .filter(e -> e.supports(extension))
                .findFirst()
                .orElseThrow(() -> new UnsupportedDocumentTypeException("No text extractor available for extension: " + extension));
    }
}

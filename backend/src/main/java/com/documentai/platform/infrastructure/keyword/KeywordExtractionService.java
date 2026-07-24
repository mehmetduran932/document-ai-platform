package com.documentai.platform.infrastructure.keyword;

import java.util.List;

/**
 * Extracts representative keywords from a chunk of text to improve keyword search precision.
 * Implementation is a plain interface so the scoring strategy (currently term-frequency) can be
 * swapped (e.g. TF-IDF across the corpus, or a language model) without touching callers.
 */
public interface KeywordExtractionService {

    List<ExtractedKeyword> extract(String text, int maxKeywords);
}

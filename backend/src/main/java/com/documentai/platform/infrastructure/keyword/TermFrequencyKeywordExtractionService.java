package com.documentai.platform.infrastructure.keyword;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

@Component
public class TermFrequencyKeywordExtractionService implements KeywordExtractionService {

    private static final Pattern WORD_PATTERN = Pattern.compile("[\\p{L}]{3,}");
    private static final int MIN_TOKEN_LENGTH = 3;

    // Deliberately covers both English and Turkish, since document/workspace content is not
    // assumed to be single-language (see app.processing.fts-language default of 'simple').
    private static final Set<String> STOPWORDS = Set.of(
            "the", "and", "for", "are", "but", "not", "you", "all", "can", "her", "was", "one",
            "our", "out", "day", "get", "has", "him", "his", "how", "man", "new", "now", "old",
            "see", "two", "way", "who", "boy", "did", "its", "let", "put", "say", "she", "too",
            "use", "with", "this", "that", "from", "have", "will", "your", "which", "their",
            "about", "would", "there", "could", "other", "than", "then", "them", "these",
            "into", "such", "over", "also", "been", "were", "when", "what", "does", "should",
            "just", "some", "any", "very", "much", "many",
            "bir", "bu", "şu", "ve", "veya", "ile", "için", "gibi", "daha", "çok", "ama", "ancak",
            "de", "da", "ki", "mi", "mu", "mü", "ne", "nasıl", "ise", "olan", "olarak",
            "kadar", "sonra", "önce", "üzerine", "göre", "diye", "yani", "hem", "eğer", "değil"
    );

    @Override
    public List<ExtractedKeyword> extract(String text, int maxKeywords) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        Map<String, Integer> counts = new HashMap<>();
        var matcher = WORD_PATTERN.matcher(text.toLowerCase());
        int totalTokens = 0;
        while (matcher.find()) {
            String token = matcher.group();
            if (token.length() < MIN_TOKEN_LENGTH || STOPWORDS.contains(token)) {
                continue;
            }
            counts.merge(token, 1, Integer::sum);
            totalTokens++;
        }

        if (totalTokens == 0) {
            return List.of();
        }

        final int totalCount = totalTokens;
        return counts.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(maxKeywords)
                .map(e -> new ExtractedKeyword(e.getKey(), (double) e.getValue() / totalCount))
                .toList();
    }
}

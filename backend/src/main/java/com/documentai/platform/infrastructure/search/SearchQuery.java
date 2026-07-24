package com.documentai.platform.infrastructure.search;

import java.util.List;
import java.util.UUID;

/**
 * Backend-agnostic search request. No FTS/embedding-specific concepts belong here.
 *
 * Carries both the raw question and pre-extracted keywords because different providers want
 * different signal: KeywordSearchProvider OR-combines {@code keywords} into a tsquery;
 * EmbeddingSearchProvider embeds {@code rawQuestion} directly, since embedding models capture
 * more meaning from the full sentence than from a stopword-stripped keyword list.
 */
public record SearchQuery(UUID workspaceId, String rawQuestion, List<String> keywords, int maxResults) {
}

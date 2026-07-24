package com.documentai.platform.infrastructure.search;

import java.util.List;

/**
 * The single seam between "how we search" and everything else. Today: PostgreSQL full-text
 * search ({@link KeywordSearchProvider}). Later: a pgvector-backed embedding provider
 * (e.g. FutureEmbeddingSearchProvider), swapped in with zero changes to controllers, services,
 * or the MCP tools that call {@link com.documentai.platform.service.SearchService}.
 *
 * No tsvector/ts_rank/regconfig/embedding-model concept may appear outside an implementation of
 * this interface.
 */
public interface SearchProvider {

    List<SearchResultChunk> search(SearchQuery query);
}

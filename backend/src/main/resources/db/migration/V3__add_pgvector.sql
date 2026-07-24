-- Adds pgvector support for EmbeddingSearchProvider. Column is nullable: chunks created before
-- this migration (or under app.search-provider=keyword, where embeddings are never computed)
-- simply have no embedding and are skipped by EmbeddingSearchProvider's "WHERE embedding IS NOT
-- NULL" clause - keyword search via search_vector is unaffected either way.

CREATE EXTENSION IF NOT EXISTS vector;

-- text-embedding-3-small (OpenAI) produces 1536-dimensional vectors.
ALTER TABLE document_chunks ADD COLUMN embedding vector(1536);

CREATE INDEX idx_chunks_embedding ON document_chunks USING hnsw (embedding vector_cosine_ops);

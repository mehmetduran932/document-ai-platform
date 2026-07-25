-- Switching EMBEDDING_PROVIDER back to fallback (1024 dims) from e5 (384 dims, V10) - the
-- production server's e5+reranker combo exceeded its 3.8GB RAM budget (swapping heavily
-- alongside the existing openflowhub stack), so embedding moved back to the cloud fallback chain
-- while Ask keeps using the containerized Qwen/Ollama setup. Same hard-reset shape as every
-- preceding embedding-width migration: existing vectors can't be reinterpreted at a different
-- width, so they're discarded; EmbeddingBackfillRunner re-embeds everything this leaves null on
-- the next backend startup.

DROP INDEX idx_chunks_embedding;
ALTER TABLE document_chunks DROP COLUMN embedding;
ALTER TABLE document_chunks ADD COLUMN embedding vector(1024);
CREATE INDEX idx_chunks_embedding ON document_chunks USING hnsw (embedding vector_cosine_ops);

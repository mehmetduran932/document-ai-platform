-- Switching back to e5 (384 dims) from fallback (1024 dims, V11) - retrying e5+reranker for
-- retrieval quality after freeing up server resources (openflowhub stack stopped). Same hard-reset
-- shape as every preceding embedding-width migration: existing vectors can't be reinterpreted at a
-- different width, so they're discarded; EmbeddingBackfillRunner re-embeds everything this leaves
-- null on the next backend startup.

DROP INDEX idx_chunks_embedding;
ALTER TABLE document_chunks DROP COLUMN embedding;
ALTER TABLE document_chunks ADD COLUMN embedding vector(384);
CREATE INDEX idx_chunks_embedding ON document_chunks USING hnsw (embedding vector_cosine_ops);

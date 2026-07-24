-- Switching EMBEDDING_PROVIDER back to fallback (1024 dims) from e5 (384 dims, V8). Same
-- hard-reset shape as the preceding V5/V6/V7/V8 migrations: existing 384-dim vectors can't be
-- reinterpreted at 1024 width, so they're discarded. EmbeddingBackfillRunner automatically
-- re-embeds everything this leaves null on the next backend startup - no manual reprocess loop
-- needed.

DROP INDEX idx_chunks_embedding;
ALTER TABLE document_chunks DROP COLUMN embedding;
ALTER TABLE document_chunks ADD COLUMN embedding vector(1024);
CREATE INDEX idx_chunks_embedding ON document_chunks USING hnsw (embedding vector_cosine_ops);

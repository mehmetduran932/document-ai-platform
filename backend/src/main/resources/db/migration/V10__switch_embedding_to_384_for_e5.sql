-- Switching EMBEDDING_PROVIDER back to e5 (384 dims) from fallback (1024 dims, V9). Same
-- hard-reset shape as the preceding V5/V6/V7/V8/V9 migrations: existing 1024-dim vectors can't be
-- reinterpreted at 384 width, so they're discarded. EmbeddingBackfillRunner automatically
-- re-embeds everything this leaves null on the next backend startup - no manual reprocess loop
-- needed. On a brand-new database this just sets the fresh-install default to 384 (e5), since
-- Flyway applies every migration in order and this is currently the last one.

DROP INDEX idx_chunks_embedding;
ALTER TABLE document_chunks DROP COLUMN embedding;
ALTER TABLE document_chunks ADD COLUMN embedding vector(384);
CREATE INDEX idx_chunks_embedding ON document_chunks USING hnsw (embedding vector_cosine_ops);

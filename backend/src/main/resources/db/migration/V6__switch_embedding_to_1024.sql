-- Switching EMBEDDING_PROVIDER from e5 (384 dims) back to fallback (Gemini/OpenAI/Voyage, sharing a
-- configured 1024-dim output - see EmbeddingProperties/application.yml). Same hard-reset shape as
-- V5: incompatible widths can't be reinterpreted in place, so existing vectors are discarded, not
-- migrated. EmbeddingBackfillRunner (infrastructure/embedding/EmbeddingBackfillRunner.java) now
-- automatically re-embeds every chunk this leaves null on the next backend startup - no manual
-- reprocess loop needed, unlike the V5 rollout.

DROP INDEX idx_chunks_embedding;
ALTER TABLE document_chunks DROP COLUMN embedding;
ALTER TABLE document_chunks ADD COLUMN embedding vector(1024);
CREATE INDEX idx_chunks_embedding ON document_chunks USING hnsw (embedding vector_cosine_ops);

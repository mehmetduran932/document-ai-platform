-- Local dev experiment: try e5 + reranker + a containerized local Qwen (via the new 'ollama'
-- compose service) together on a machine with more headroom than the 3.8GB production server
-- where this combination didn't fit. Same hard-reset shape as every preceding embedding-width
-- migration; EmbeddingBackfillRunner re-embeds existing chunks on next backend startup.

DROP INDEX idx_chunks_embedding;
ALTER TABLE document_chunks DROP COLUMN embedding;
ALTER TABLE document_chunks ADD COLUMN embedding vector(384);
CREATE INDEX idx_chunks_embedding ON document_chunks USING hnsw (embedding vector_cosine_ops);

-- Moving fully to cloud APIs for both embedding and answer generation - the production server's
-- 3.8GB RAM couldn't run e5+reranker+a local Qwen model together even with the other app on this
-- host stopped (e5 alone peaked at ~1.4GB, reranker ~1.4GB, actively embedding). Switching back to
-- fallback (1024 dims). Same hard-reset shape as every preceding embedding-width migration.

DROP INDEX idx_chunks_embedding;
ALTER TABLE document_chunks DROP COLUMN embedding;
ALTER TABLE document_chunks ADD COLUMN embedding vector(1024);
CREATE INDEX idx_chunks_embedding ON document_chunks USING hnsw (embedding vector_cosine_ops);

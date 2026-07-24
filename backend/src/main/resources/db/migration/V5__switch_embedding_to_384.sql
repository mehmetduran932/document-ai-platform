-- multilingual-e5-small produces 384-dim vectors, incompatible with the existing 1536-wide column
-- (sized for OpenAI's text-embedding-3-small). Old vectors can't be reinterpreted at a different
-- width, so this is a hard reset, not an ALTER ... USING cast - every chunk's embedding must be
-- recomputed afterward via POST /api/documents/{id}/reprocess, same backfill shape CLAUDE.md
-- already documents for a SEARCH_PROVIDER change.

DROP INDEX idx_chunks_embedding;
ALTER TABLE document_chunks DROP COLUMN embedding;
ALTER TABLE document_chunks ADD COLUMN embedding vector(384);
CREATE INDEX idx_chunks_embedding ON document_chunks USING hnsw (embedding vector_cosine_ops);

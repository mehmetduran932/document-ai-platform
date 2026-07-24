-- Tracks which vendor/model embedded each chunk (e.g. "openai:text-embedding-3-small",
-- "gemini:gemini-embedding-001", "e5:intfloat/multilingual-e5-small"). Without this, comparing a
-- query vector against stored vectors from a *different* vendor via cosine similarity silently
-- produces meaningless scores - observed live when FallbackEmbeddingProvider embedded a burst of
-- chunks with OpenAI (Gemini was rate-limited) but a later query was embedded fresh with Gemini
-- (whose quota had since recovered). EmbeddingSearchProvider now filters on this column so a query
-- only ever matches chunks from its own vector space; EmbeddingBackfillRunner re-embeds anything
-- that doesn't match the dominant vendor, the same way it already re-embeds null embeddings.
--
-- Nullable, not backfilled: existing rows predate this column, so their vendor is unknown and they
-- read as NULL - caught by EmbeddingBackfillRunner's existing null-embedding handling either way.

ALTER TABLE document_chunks ADD COLUMN embedding_model VARCHAR(150);

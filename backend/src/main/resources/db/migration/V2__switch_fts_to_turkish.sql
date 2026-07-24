-- Switches the FTS regconfig used by the document_chunks trigger (see V1) to '${ftsLanguage}'
-- and backfills existing rows, since the trigger only recomputes search_vector on INSERT/UPDATE.
-- Query-side (KeywordSearchProvider.plainto/to_tsquery) reads the same app.processing.fts-language
-- property, so both sides stay in sync.

CREATE OR REPLACE FUNCTION chunk_search_vector_update() RETURNS trigger AS $$
BEGIN
    NEW.search_vector := to_tsvector('${ftsLanguage}', coalesce(NEW.content, ''));
    RETURN NEW;
END
$$ LANGUAGE plpgsql;

UPDATE document_chunks
SET search_vector = to_tsvector('${ftsLanguage}', coalesce(content, ''));

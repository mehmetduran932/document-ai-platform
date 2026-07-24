-- Core schema for the Document AI Platform.
-- FTS regconfig is injected via Flyway placeholder ${ftsLanguage} (see application.yml
-- spring.flyway.placeholders.ftsLanguage) so the document-side tsvector trigger below and the
-- query-side plainto_tsquery() in KeywordSearchProvider always use the same configuration.

CREATE TABLE workspaces (
    id          UUID PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE users (
    id             UUID PRIMARY KEY,
    email          VARCHAR(255) NOT NULL UNIQUE,
    password_hash  VARCHAR(255) NOT NULL,
    full_name      VARCHAR(255) NOT NULL,
    workspace_id   UUID NOT NULL REFERENCES workspaces (id),
    role           VARCHAR(20)  NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_users_workspace ON users (workspace_id);

CREATE TABLE workspace_api_keys (
    id            UUID PRIMARY KEY,
    workspace_id  UUID NOT NULL REFERENCES workspaces (id),
    name          VARCHAR(255) NOT NULL,
    key_hash      VARCHAR(255) NOT NULL UNIQUE,
    key_prefix    VARCHAR(12)  NOT NULL,
    revoked       BOOLEAN      NOT NULL DEFAULT FALSE,
    last_used_at  TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_api_keys_workspace ON workspace_api_keys (workspace_id);

CREATE TABLE documents (
    id                 UUID PRIMARY KEY,
    workspace_id       UUID NOT NULL REFERENCES workspaces (id),
    uploaded_by        UUID REFERENCES users (id),
    filename           VARCHAR(1024) NOT NULL,
    extension          VARCHAR(20)   NOT NULL,
    size               BIGINT        NOT NULL,
    storage_key        VARCHAR(1024) NOT NULL,
    upload_date        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    processing_status  VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    processing_error   TEXT,
    page_count         INT,
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_documents_workspace ON documents (workspace_id);
CREATE INDEX idx_documents_status ON documents (processing_status);

CREATE TABLE document_chunks (
    id             UUID PRIMARY KEY,
    document_id    UUID NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    workspace_id   UUID NOT NULL REFERENCES workspaces (id),
    page           INT,
    chunk_index    INT  NOT NULL,
    content        TEXT NOT NULL,
    word_count     INT  NOT NULL,
    search_vector  TSVECTOR,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_chunks_document ON document_chunks (document_id);
CREATE INDEX idx_chunks_workspace ON document_chunks (workspace_id);
CREATE INDEX idx_chunks_search_vector ON document_chunks USING GIN (search_vector);
CREATE UNIQUE INDEX uq_chunks_document_index ON document_chunks (document_id, chunk_index);

CREATE FUNCTION chunk_search_vector_update() RETURNS trigger AS $$
BEGIN
    NEW.search_vector := to_tsvector('${ftsLanguage}', coalesce(NEW.content, ''));
    RETURN NEW;
END
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_chunk_search_vector_update
    BEFORE INSERT OR UPDATE OF content ON document_chunks
    FOR EACH ROW EXECUTE FUNCTION chunk_search_vector_update();

CREATE TABLE chunk_keywords (
    id            UUID PRIMARY KEY,
    chunk_id      UUID NOT NULL REFERENCES document_chunks (id) ON DELETE CASCADE,
    document_id   UUID NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    workspace_id  UUID NOT NULL REFERENCES workspaces (id),
    keyword       VARCHAR(100) NOT NULL,
    score         DOUBLE PRECISION NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_keywords_chunk ON chunk_keywords (chunk_id);
CREATE INDEX idx_keywords_workspace_keyword ON chunk_keywords (workspace_id, keyword);

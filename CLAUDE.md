# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A platform where users upload documents (PDF/Word/Excel/images) and AI agents (Claude Code, Codex,
Gemini CLI, ...) search and read only the relevant chunks via MCP - never whole documents. A REST
API also exposes the same search plus an optional LLM-synthesized answer (`POST /api/ask`).

## Commands

All commands run from `backend/`.

```bash
mvn compile                      # compile only
mvn test                         # full test suite - requires Docker running (Testcontainers spins up real Postgres)
mvn test -Dtest=ClassName                       # single test class
mvn test -Dtest=ClassName#methodName            # single test method
mvn package -DskipTests          # build the jar without running tests
```

From the repo root:

```bash
cp .env.example .env             # fill in JWT_SECRET (openssl rand -base64 48) + provider keys
docker compose up --build        # postgres (pgvector/pgvector:pg16) + pgadmin + backend
docker compose up --build -d backend   # rebuild/restart just the backend after a code change
```

- Swagger UI: `http://localhost:8080/swagger-ui.html` — register via `/api/auth/register` to get a
  JWT, click **Authorize** and paste it (Swagger adds the `Bearer ` prefix).
- pgAdmin: `http://localhost:5050` (`PGADMIN_EMAIL`/`PGADMIN_PASSWORD` from `.env`).
- MCP endpoint for external agents: `POST /mcp`, authenticated via `X-API-Key` (minted through
  `POST /api/workspace/api-keys`), not the JWT used by the REST API.

There is no local (non-Docker) run path documented — Tesseract OCR needs the native binary the
Docker image installs, and the app expects `SPRING_PROFILES_ACTIVE=docker` for the Postgres
hostname. Use `docker compose` even for iterating on backend code; the Dockerfile's dependency
layer is cached so rebuilds after a source-only change are fast.

## Architecture

### Provider-swap pattern (the central design decision)

Four capabilities are each hidden behind an interface with zero knowledge of the concrete backend
anywhere outside the implementation class. Swapping providers is a config/env-var change, never a
code change to a controller or service:

| Interface | Implementations | Selected by |
|---|---|---|
| `infrastructure/storage/StorageProvider` | `R2StorageProvider` (AWS S3 SDK v2 — also works unmodified against real AWS S3 or self-hosted MinIO by changing `R2_ENDPOINT`/`R2_REGION`) | only one impl exists |
| `infrastructure/search/SearchProvider` | `KeywordSearchProvider` (Postgres FTS), `EmbeddingSearchProvider` (pgvector, semantic) | `app.search-provider` = `keyword` \| `embedding` |
| `infrastructure/answer/AnswerProvider` | `ClaudeAnswerProvider`, `OpenAiAnswerProvider` (also works against any OpenAI-API-compatible local server — Ollama, LM Studio, vLLM — by pointing `base-url` at it) | `app.answer-provider` = `claude` \| `openai` |
| `infrastructure/embedding/EmbeddingProvider` | `FallbackEmbeddingProvider`, which tries `GeminiEmbeddingClient` first then `OpenAiEmbeddingClient` on any failure | active only when `app.search-provider=embedding` |

When touching search or answer generation, respect this: **no tsvector/ts_rank/regconfig,
pgvector/cosine-distance, or vendor-specific request/response shape may leak outside its one
provider class.** `SearchQuery` carries both `rawQuestion` and pre-extracted `keywords` because
keyword search wants the OR-combined keyword list while embedding search wants the raw sentence —
each provider picks the signal it needs.

`document_chunks.search_vector` (tsvector) and `document_chunks.embedding` (pgvector) are **not**
mapped on the `DocumentChunk` JPA entity. Both are written/read exclusively via native SQL
(`NamedParameterJdbcTemplate`) inside their owning infrastructure class — this is deliberate, so
Hibernate never needs to understand either Postgres extension type.

### Async document processing pipeline

`DocumentController.upload` → `DocumentServiceImpl` stores the file via `StorageProvider`, saves a
`Document` row (`PENDING`), and publishes `DocumentUploadedEvent`. `DocumentUploadedEventListener`
picks it up with `@TransactionalEventListener(phase = AFTER_COMMIT)` so the async worker never
races the upload transaction, then calls `DocumentProcessingService.process()` (`@Async`).

`DocumentProcessingServiceImpl.persistResult()` runs the pipeline in one DB transaction:
`TextExtractorFactory` picks Tika (PDF/Word) / POI (Excel) / Tesseract (images) by extension →
`ChunkingService` splits into 400–700-word chunks with 50-word overlap → per chunk, keyword
extraction (`TermFrequencyKeywordExtractionService`) → if embedding search is active, embeddings
are computed in one batched call and written via `ChunkEmbeddingWriter`.

Two ordering gotchas that live here and must not regress:

- `chunkRepository.deleteAllByDocumentId(...)` (idempotency for reprocessing) is followed by an
  explicit `chunkRepository.flush()` before re-inserting — without it the delete and the new
  inserts can be batched by Hibernate in the wrong order and hit the
  `(document_id, chunk_index)` unique constraint.
- Chunk inserts are flushed **again** before the embedding-write step, because
  `chunkRepository.save()` only queues the insert in Hibernate's session; the raw-JDBC embedding
  `UPDATE` doesn't trigger a Hibernate auto-flush and will silently match zero rows against chunks
  that aren't in the database yet.

`POST /api/documents/{id}/reprocess` re-runs this whole pipeline for a document already in
storage — use it after a stuck/failed run (e.g. the app restarted mid-processing, which loses the
in-memory `DocumentUploadedEvent`) or after changing `SEARCH_PROVIDER` to backfill embeddings for
already-ingested documents.

### Search → Ask flow and token/payload control

`SearchServiceImpl.search()` is the single entry point both `POST /api/search` and
`POST /api/ask` go through (and what the MCP `search_documents` tool calls). It always keyword-extracts
the question, builds one `SearchQuery`, and delegates to whichever `SearchProvider` is active.

`AskServiceImpl` then: takes only the top `app.search.max-answer-chunks` (default 3) of the
matched results as LLM grounding (bounds token cost independent of `app.search.max-results`,
which is how many results `/api/search` itself returns); if there are zero matches, returns a
canned "not found" answer without calling the LLM at all; and truncates `sourceChunks[].content`
in the HTTP response to `app.search.content-preview-chars` (default 300) — the LLM prompt itself
always uses full chunk content, only the returned JSON is shortened. Fetch full chunk text via
`GET /api/documents/chunks/{chunkId}`. The MCP `search_documents` tool does **not** truncate —
agents consuming chunks directly get full content, since round-tripping through
`read_document_chunk` for every result would be worse for that use case.

### MCP server

Mounted at `/mcp` (`infrastructure/mcp/McpServerConfig`, using the official
`io.modelcontextprotocol.sdk:mcp-core` + `mcp-json-jackson2` — not the `mcp` facade artifact, which
pulls in a separate Jackson 3 stack via `mcp-json-jackson3` that isn't needed here). Auth is
**not** the JWT the REST API uses: `WorkspaceTransportContextExtractor` resolves the workspace from
the `X-API-Key` header at the transport layer and stores it in `McpTransportContext`, which each
tool handler in `DocumentMcpTools` reads via `exchange.transportContext()` — this avoids relying on
`SecurityContextHolder` thread-locals, which aren't guaranteed to propagate through the MCP SDK's
transport dispatch. Spring Security's `/mcp/**` filter chain (`WorkspaceApiKeyAuthFilter`) is a
coarse-grained perimeter check on top of that, not the source of truth for which workspace a call
is scoped to. The MCP layer only reads data — it never calls an LLM or generates an answer; that's
the calling agent's job.

### FTS language

`document_chunks.search_vector` and the `to_tsquery`/`plainto_tsquery` calls in
`KeywordSearchProvider` both read `app.processing.fts-language` (defaults to `simple`; this
deployment runs `turkish`). The regconfig is baked into the trigger function at migration time via
a Flyway placeholder (`spring.flyway.placeholders.ftsLanguage`), so changing the language for an
existing database needs a new migration that recreates the trigger function *and* backfills
`search_vector` for existing rows (see `V2__switch_fts_to_turkish.sql`) — restarting with a
different `FTS_LANGUAGE` alone does nothing to already-applied schema.

Keyword search OR-combines extracted keywords (`to_tsquery` with `|`), not AND (`plainto_tsquery`'s
default) — requiring every extracted keyword to match makes results fragile to any single
imprecise keyword.

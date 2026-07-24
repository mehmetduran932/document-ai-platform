# Document AI Platform

Upload documents; let AI agents (Claude Code, Codex, Gemini CLI, ...) search and read only the
relevant chunks via MCP - never whole documents.

## Run it

```bash
cp .env.example .env   # fill in JWT_SECRET (openssl rand -base64 48) and R2_* credentials
docker compose up --build
```

- API + Swagger UI: http://localhost:8080/swagger-ui.html
- pgAdmin: http://localhost:5050 (login with PGADMIN_EMAIL / PGADMIN_PASSWORD from .env)
- MCP endpoint (for external agents): `POST http://localhost:8080/mcp` with header `X-API-Key: <key>`

R2 credentials are only required for document upload/download; auth, search, and the rest of the
API work without them.

## Try it via Swagger

1. `POST /api/auth/register` with an email/password/fullName/workspaceName -> copy `accessToken`.
2. Click **Authorize** in Swagger UI, paste the token (no `Bearer ` prefix needed, Swagger adds it).
3. `POST /api/documents` (multipart) to upload a PDF/Word/Excel/image - processing runs async;
   poll `GET /api/documents/{id}` until `processingStatus` is `COMPLETED`.
4. `POST /api/search` with `{"query": "..."}` to get the top matching chunks (no LLM call).
5. `POST /api/ask` with `{"question": "..."}` to get the same search plus a synthesized answer -
   the model only ever sees the matched chunks, never whole documents.
6. `POST /api/workspace/api-keys` to mint a key for an external MCP agent; the plaintext key is
   shown once in the response.

## Architecture

- **Backend**: Java 17, Spring Boot 3.5, PostgreSQL, Flyway. Clean layering: controller -> service
  -> repository, with `infrastructure/` holding swappable seams (storage, search, processing).
- **Search**: `SearchProvider` interface. `KeywordSearchProvider` (PostgreSQL full-text search,
  `SEARCH_PROVIDER=keyword`, default) or `EmbeddingSearchProvider` (pgvector semantic search,
  `SEARCH_PROVIDER=embedding`) - all tsvector/ts_rank/regconfig or vector/cosine-distance knowledge
  stays confined to its own class; swapping is a config change, zero controller/service changes.
  See **Embeddings** below for the provider chain and the pgvector dimension constraint this
  implies.
- **Storage**: `StorageProvider` interface, implemented by `R2StorageProvider` on the AWS S3 SDK
  v2 - so the exact same code runs against real AWS S3 or a self-hosted MinIO too, by changing
  only `R2_ENDPOINT`/`R2_REGION`, no code change.
- **Answers**: `AnswerProvider` interface (same swap pattern as `SearchProvider`), implemented by
  `ClaudeAnswerProvider` (default) and `OpenAiAnswerProvider`, selected via `ANSWER_PROVIDER`.
  `OpenAiAnswerProvider`'s `OPENAI_BASE_URL` also works against any OpenAI-API-compatible local
  model server (Ollama, LM Studio, vLLM), so running fully local later is a config change, not a
  code change. Used only by `POST /api/ask` - `/api/search` and the MCP tools stay answer-free.
- **Processing**: async pipeline (Tika for PDF/Word, POI for Excel, Tesseract OCR for images) ->
  word-count chunking (400-700 words, 50-word overlap) -> keyword extraction, per chunk.
- **MCP server**: mounted at `/mcp`, workspace-scoped by the `X-API-Key` header (see
  `POST /api/workspace/api-keys`). Exposes `list_documents`, `search_documents`,
  `read_document_chunk`, `get_document_metadata`, `download_document`. It only returns data - it
  never calls an LLM or generates an answer.

## Embeddings (`SEARCH_PROVIDER=embedding`)

`EmbeddingProvider` has two **mutually exclusive** implementations, selected by
`EMBEDDING_PROVIDER`:

| `EMBEDDING_PROVIDER` | Bean | Vendor(s) | Output dims |
|---|---|---|---|
| `fallback` (default) | `FallbackEmbeddingProvider` | Gemini → OpenAI → Voyage AI, in that order; each is only tried if the previous one throws | `EMBEDDING_DIMENSIONS` (default **1024** - Voyage's `output_dimension` only accepts 256/512/1024/2048, so this is the one value all three vendors can produce; Gemini/OpenAI would also happily do 1536 but Voyage would reject it) |
| `e5` | `E5EmbeddingProvider` | a locally-hosted `intfloat/multilingual-e5-small` (served by the `embeddings` docker-compose service, via Hugging Face [TEI](https://github.com/huggingface/text-embeddings-inference)) | fixed **384**, not configurable |

**Anthropic/Claude has no embeddings API** - if you were expecting a Claude option here, that's why
it's Voyage AI instead (Anthropic's own recommended embeddings vendor).

### Why this is one config value, not two independent knobs

`document_chunks.embedding` is a single `pgvector` column with a fixed width (`vector(N)`). Every
vector written to it - regardless of which provider produced it - must be exactly that width, and
pgvector's cosine-distance operator (`<=>`) errors if you compare vectors of different widths. So
**switching `EMBEDDING_PROVIDER` to a value with a different output width always requires a new
Flyway migration** that drops and re-adds the column at the new width (see `V5__switch_embedding_to_384.sql`
and `V6__switch_embedding_to_1024.sql` for the two migrations so far - each one is a hard reset:
existing vectors can't be reinterpreted at a different width, so they're discarded, not converted).

**This is deliberately not automated.** Auto-`ALTER TABLE`-ing a vector column's width based on a
runtime config value would mean a typo in an env var could silently destroy production embeddings
with no review step - schema changes stay versioned, reviewable SQL files, same as every other
migration in this project.

### What *is* automated: re-embedding after a width change

Dropping/re-adding the column (or any provider swap) leaves every existing chunk with
`embedding = NULL`, which is invisible to `EmbeddingSearchProvider` (its query has
`WHERE embedding IS NOT NULL`). `EmbeddingBackfillRunner`
(`infrastructure/embedding/EmbeddingBackfillRunner.java`) runs on every backend startup, finds every
document with at least one null-embedding chunk, and re-triggers processing for it automatically -
no manual per-document `POST /api/documents/{id}/reprocess` loop needed. It does **not** detect the
subtler case of non-null embeddings from *different, incompatible* vendors coexisting in the column
(e.g. `FallbackEmbeddingProvider` silently switching from Gemini to OpenAI mid-run because Gemini's
quota ran out) - those vectors are technically the right width but come from different vector
spaces, so cosine similarity between them is meaningless. That's a known, pre-existing gap, not
something this runner claims to fix.

### Reranker (optional, off by default)

Bi-encoder embedding models (especially smaller ones like e5-small) can compress cosine scores into
a narrow band regardless of true relevance - e.g. we observed 0.81-0.84 for both a relevant
insurance-policy chunk and a completely unrelated mobile-game doc, in the same workspace, for the
same Turkish query. Setting `RERANKER_ENABLED=true` makes `EmbeddingSearchProvider` pull a wider
candidate pool from pgvector (`maxResults * RERANKER_CANDIDATE_POOL_MULTIPLIER`, default 3x) and
re-score them with a cross-encoder (`cross-encoder/mmarco-mMiniLMv2-L12-H384-v1`, served by the
`reranker` docker-compose service) before truncating back to `maxResults`. Chosen for its small
footprint (~117M params, fits a 4GB-RAM deployment); its training data (mMARCO) does **not** include
Turkish, so any Turkish benefit comes from cross-lingual transfer, not in-language fine-tuning -
validate quality/latency on your own data before relying on it in production.

## Tests

```bash
cd backend
mvn test   # needs Docker running (Testcontainers spins up real Postgres)
```

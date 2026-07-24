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
- **Search**: `SearchProvider` interface, currently implemented by `KeywordSearchProvider`
  (PostgreSQL full-text search). All tsvector/ts_rank/regconfig knowledge is confined to that one
  class - swapping in a future pgvector-based `EmbeddingSearchProvider` means adding a new
  implementation and rebinding the Spring bean, with zero changes to controllers or services.
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

## Tests

```bash
cd backend
mvn test   # needs Docker running (Testcontainers spins up real Postgres)
```

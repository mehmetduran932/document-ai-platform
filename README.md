# Document AI Platform

> [English](#english) | [Türkçe](#türkçe)

## English

Upload documents; let AI agents (Claude Code, Codex, Gemini CLI, ...) search and read only the
relevant chunks via MCP - never whole documents.

### Run it

```bash
cp .env.example .env   # fill in JWT_SECRET (openssl rand -base64 48) and R2_* credentials
docker compose up --build
```

- API + Swagger UI: http://localhost:8080/swagger-ui.html
- Frontend: http://localhost:5173
- pgAdmin: http://localhost:5050 (login with PGADMIN_EMAIL / PGADMIN_PASSWORD from .env)
- MCP endpoint (for external agents): `POST http://localhost:8080/mcp` with header `X-API-Key: <key>`

R2 credentials are only required for document upload/download; auth, search, and the rest of the
API work without them.

### Try it via Swagger

1. `POST /api/auth/register` with an email/password/fullName/workspaceName -> copy `accessToken`.
2. Click **Authorize** in Swagger UI, paste the token (no `Bearer ` prefix needed, Swagger adds it).
3. `POST /api/documents` (multipart) to upload a PDF/Word/Excel/image - processing runs async;
   poll `GET /api/documents/{id}` until `processingStatus` is `COMPLETED`.
4. `POST /api/search` with `{"query": "..."}` to get the top matching chunks (no LLM call).
5. `POST /api/ask` with `{"question": "..."}` to get the same search plus a synthesized answer -
   the model only ever sees the matched chunks, never whole documents.
6. `POST /api/workspace/api-keys` to mint a key for an external MCP agent; the plaintext key is
   shown once in the response.

### MCP setup with Claude Code

To connect this platform as an MCP server to Claude Code (or any other agent supporting streamable
HTTP MCP):

1. Mint an API key - either from the frontend's **API Keys** page
   (http://localhost:5173/api-keys) or via `POST /api/workspace/api-keys` in Swagger. The plaintext
   key (starts with `dap_...`) is shown only once, copy it immediately.

2. Register the server with Claude Code (default `local` scope, does not commit the key to git):

   ```bash
   claude mcp add --transport http document-ai-platform http://localhost:8080/mcp \
     --header "X-API-Key: <your-key>"
   ```

3. Verify the connection:

   ```bash
   claude mcp list
   # document-ai-platform: http://localhost:8080/mcp (HTTP) - ✔ Connected
   ```

4. **Important**: MCP servers are only loaded at session start - if you ran `claude mcp add` while
   a Claude Code session was already running, that session won't see the new tools. **Start a new
   session** (re-run `claude`) to actually use the tools (`list_documents`, `search_documents`,
   `read_document_chunk`, `get_document_metadata`, `download_document`).

5. No special command needed in the new session - just ask naturally, Claude calls the right tool
   itself (e.g. "list my documents", "which chunk mentions X in my policy?").

**The MCP layer never calls an LLM or generates an answer** - it only returns the relevant data
(semantically, if embedding search is active); synthesis is always the connected agent's own job.
`POST /api/ask`, by contrast, is a separate path where the backend generates the answer itself
(via Claude/OpenAI).

### Architecture

- **Backend**: Java 17, Spring Boot 3.5, PostgreSQL, Flyway. Clean layering: controller -> service
  -> repository, with `infrastructure/` holding swappable seams (storage, search, processing).
- **Frontend**: React + Vite + TypeScript + Tailwind/shadcn-ui. Includes auth, document management
  (upload, listing, status tracking, reprocess), search, Ask (Q&A with persistent history), and
  workspace API key management screens.
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

### Embeddings (`SEARCH_PROVIDER=embedding`)

`EmbeddingProvider` has two **mutually exclusive** implementations, selected by
`EMBEDDING_PROVIDER`:

| `EMBEDDING_PROVIDER` | Bean | Vendor(s) | Output dims |
| --- | --- | --- | --- |
| `fallback` (default) | `FallbackEmbeddingProvider` | Gemini → OpenAI → Voyage AI, in that order; each is only tried if the previous one throws | `EMBEDDING_DIMENSIONS` (default **1024** - Voyage's `output_dimension` only accepts 256/512/1024/2048, so this is the one value all three vendors can produce; Gemini/OpenAI would also happily do 1536 but Voyage would reject it) |
| `e5` | `E5EmbeddingProvider` | a locally-hosted `intfloat/multilingual-e5-small` (served by the `embeddings` docker-compose service, via Hugging Face [TEI](https://github.com/huggingface/text-embeddings-inference)) | fixed **384**, not configurable |

**Anthropic/Claude has no embeddings API** - if you were expecting a Claude option here, that's why
it's Voyage AI instead (Anthropic's own recommended embeddings vendor).

#### Why this is one config value, not two independent knobs

`document_chunks.embedding` is a single `pgvector` column with a fixed width (`vector(N)`). Every
vector written to it - regardless of which provider produced it - must be exactly that width, and
pgvector's cosine-distance operator (`<=>`) errors if you compare vectors of different widths. So
**switching `EMBEDDING_PROVIDER` to a value with a different output width always requires a new
Flyway migration** that drops and re-adds the column at the new width (see
`V5__switch_embedding_to_384.sql` and `V6__switch_embedding_to_1024.sql` for the two migrations so
far - each one is a hard reset: existing vectors can't be reinterpreted at a different width, so
they're discarded, not converted).

**This is deliberately not automated.** Auto-`ALTER TABLE`-ing a vector column's width based on a
runtime config value would mean a typo in an env var could silently destroy production embeddings
with no review step - schema changes stay versioned, reviewable SQL files, same as every other
migration in this project.

#### What *is* automated: re-embedding after a width change

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

#### Reranker (optional, off by default)

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

### Tests

```bash
cd backend
mvn test   # needs Docker running (Testcontainers spins up real Postgres)
```

## Türkçe

Doküman yükleyin; AI ajanları (Claude Code, Codex, Gemini CLI, ...) MCP üzerinden yalnızca ilgili
parçaları arasın ve okusun - hiçbir zaman dokümanın tamamını değil.

### Çalıştırma

```bash
cp .env.example .env   # JWT_SECRET (openssl rand -base64 48) ve R2_* bilgilerini doldurun
docker compose up --build
```

- API + Swagger UI: http://localhost:8080/swagger-ui.html
- Frontend: http://localhost:5173
- pgAdmin: http://localhost:5050 (giriş için .env'deki PGADMIN_EMAIL / PGADMIN_PASSWORD)
- MCP endpoint (harici ajanlar için): `POST http://localhost:8080/mcp`, `X-API-Key: <key>` header'ı
  ile

R2 bilgileri yalnızca doküman yükleme/indirme için gereklidir; auth, arama ve API'nin geri kalanı
bunlar olmadan da çalışır.

### Swagger Üzerinden Deneme

1. `POST /api/auth/register` (email/password/fullName/workspaceName) -> `accessToken`'ı kopyalayın.
2. Swagger UI'da **Authorize**'a tıklayıp token'ı yapıştırın (`Bearer ` ön eki gerekmez, Swagger
   ekler).
3. `POST /api/documents` (multipart) ile bir PDF/Word/Excel/görsel yükleyin - işleme asenkron
   çalışır; `processingStatus` `COMPLETED` olana kadar `GET /api/documents/{id}`'yi yoklayın.
4. `POST /api/search` ile `{"query": "..."}` gönderip en iyi eşleşen parçaları alın (LLM çağrısı
   yok).
5. `POST /api/ask` ile `{"question": "..."}` gönderip aynı aramayı + sentezlenmiş bir cevabı alın -
   model yalnızca eşleşen parçaları görür, hiçbir zaman dokümanın tamamını değil.
6. `POST /api/workspace/api-keys` ile harici bir MCP ajanı için bir anahtar üretin; düz metin
   anahtar yanıtta yalnızca bir kez gösterilir.

### Claude Code ile MCP Kurulumu

Bu platformu bir MCP sunucusu olarak Claude Code'a (veya streamable-HTTP MCP destekleyen başka bir
ajana) bağlamak için:

1. Bir API anahtarı üretin - ya frontend'deki **API Keys** sayfasından
   (http://localhost:5173/api-keys) ya da Swagger'da `POST /api/workspace/api-keys` ile. Düz metin
   anahtar (`dap_...` ile başlar) yalnızca bir kez gösterilir, hemen kopyalayın.

2. Sunucuyu Claude Code'a ekleyin (varsayılan `local` scope, anahtarı git'e commit etmez):

   ```bash
   claude mcp add --transport http document-ai-platform http://localhost:8080/mcp \
     --header "X-API-Key: <anahtarınız>"
   ```

3. Bağlantıyı doğrulayın:

   ```bash
   claude mcp list
   # document-ai-platform: http://localhost:8080/mcp (HTTP) - ✔ Connected
   ```

4. **Önemli**: MCP sunucuları yalnızca oturum başlangıcında yüklenir - `claude mcp add` komutunu
   zaten açık bir Claude Code oturumu çalışırken çalıştırdıysanız, o oturum yeni araçları görmez.
   Araçları (`list_documents`, `search_documents`, `read_document_chunk`,
   `get_document_metadata`, `download_document`) kullanabilmek için **yeni bir oturum başlatın**
   (`claude`'u yeniden çalıştırın).

5. Yeni oturumda özel bir komuta gerek yok - doğal dille sorun, Claude gerekli aracı kendisi
   çağırır (örn. "dokümanlarımı listele", "poliçemde X hangi chunk'ta geçiyor?").

**MCP katmanı hiçbir zaman bir LLM çağırmaz veya cevap üretmez** - yalnızca (embedding araması
aktifse anlamsal olarak) ilgili veriyi döndürür; sentezleme her zaman bağlı ajanın kendi işidir.
`POST /api/ask` bunun aksine, cevabı backend'in kendi içinde (Claude/OpenAI ile) ürettiği ayrı bir
yoldur.

### Mimari

- **Backend**: Java 17, Spring Boot 3.5, PostgreSQL, Flyway. Temiz katmanlama: controller -> service
  -> repository; `infrastructure/` değiştirilebilir noktaları barındırır (storage, search,
  processing).
- **Frontend**: React + Vite + TypeScript + Tailwind/shadcn-ui. Auth, doküman yönetimi (yükleme,
  listeleme, durum takibi, yeniden işleme), arama, kalıcı geçmişli Ask (soru-cevap) ve workspace API
  key yönetimi ekranlarını içerir.
- **Search**: `SearchProvider` arayüzü. `KeywordSearchProvider` (PostgreSQL tam metin arama,
  `SEARCH_PROVIDER=keyword`, varsayılan) veya `EmbeddingSearchProvider` (pgvector semantik arama,
  `SEARCH_PROVIDER=embedding`) - tsvector/ts_rank/regconfig ya da vector/cosine-distance bilgisi
  yalnızca kendi sınıfında kalır; değiştirmek bir config değişikliğidir, controller/service'e
  dokunulmaz. Provider zinciri ve pgvector boyut kısıtı için aşağıdaki **Embedding'ler** bölümüne
  bakın.
- **Storage**: `StorageProvider` arayüzü, AWS S3 SDK v2 üzerinde `R2StorageProvider` ile
  implemente edilmiştir - aynı kod, yalnızca `R2_ENDPOINT`/`R2_REGION` değiştirilerek gerçek AWS S3
  veya kendi barındırdığınız MinIO'ya karşı da çalışır, kod değişikliği gerekmez.
- **Answers**: `AnswerProvider` arayüzü (`SearchProvider` ile aynı değiştirme deseni),
  `ClaudeAnswerProvider` (varsayılan) ve `OpenAiAnswerProvider` ile implemente edilmiştir,
  `ANSWER_PROVIDER` ile seçilir. `OpenAiAnswerProvider`'ın `OPENAI_BASE_URL`'i herhangi bir
  OpenAI-API-uyumlu yerel model sunucusuna karşı da çalışır (Ollama, LM Studio, vLLM), yani
  tamamen yerel çalıştırmak ileride bir config değişikliğidir, kod değişikliği değil. Yalnızca
  `POST /api/ask` tarafından kullanılır - `/api/search` ve MCP araçları cevap üretmez.
- **Processing**: asenkron pipeline (PDF/Word için Tika, Excel için POI, görseller için Tesseract
  OCR) -> kelime sayımına dayalı chunking (400-700 kelime, 50 kelime overlap) -> chunk başına
  anahtar kelime çıkarımı.
- **MCP server**: `/mcp`'de mount edilmiştir, `X-API-Key` header'ı ile workspace'e bağlanır (bkz.
  `POST /api/workspace/api-keys`). `list_documents`, `search_documents`, `read_document_chunk`,
  `get_document_metadata`, `download_document` araçlarını sunar. Yalnızca veri döner - hiçbir zaman
  LLM çağırmaz veya cevap üretmez.

### Embedding'ler (`SEARCH_PROVIDER=embedding`)

`EmbeddingProvider`'ın **birbirini dışlayan** iki implementasyonu vardır, `EMBEDDING_PROVIDER` ile
seçilir:

| `EMBEDDING_PROVIDER` | Bean | Sağlayıcı(lar) | Çıktı boyutu |
| --- | --- | --- | --- |
| `fallback` (varsayılan) | `FallbackEmbeddingProvider` | Gemini → OpenAI → Voyage AI, bu sırayla; her biri yalnızca öncekinin hata vermesi durumunda denenir | `EMBEDDING_DIMENSIONS` (varsayılan **1024** - Voyage'ın `output_dimension`'ı yalnızca 256/512/1024/2048 kabul eder, bu yüzden üç sağlayıcının da üretebildiği ortak değer budur; Gemini/OpenAI 1536'yı da sorunsuz yapar ama Voyage reddeder) |
| `e5` | `E5EmbeddingProvider` | yerelde barındırılan `intfloat/multilingual-e5-small` (Hugging Face [TEI](https://github.com/huggingface/text-embeddings-inference) üzerinden `embeddings` docker-compose servisi tarafından sunulur) | sabit **384**, yapılandırılamaz |

**Anthropic/Claude'un hiçbir embedding API'si yoktur** - burada Claude seçeneği beklediyseniz,
yerine Voyage AI'ın olma sebebi budur (Anthropic'in kendi önerdiği embedding sağlayıcısı).

#### Neden tek bir config değeri, iki bağımsız ayar değil

`document_chunks.embedding`, sabit genişlikli (`vector(N)`) tek bir `pgvector` kolonudur. Hangi
sağlayıcı üretmiş olursa olsun, yazılan her vektör tam olarak bu genişlikte olmalıdır; pgvector'ın
cosine-distance operatörü (`<=>`) farklı genişlikteki vektörleri karşılaştırırken hata verir.
Bu yüzden **`EMBEDDING_PROVIDER`'ı farklı bir çıktı genişliğine sahip bir değere değiştirmek her
zaman yeni bir Flyway migration gerektirir** (kolonu yeni genişlikte drop edip yeniden ekleyen) -
bkz. `V5__switch_embedding_to_384.sql` ve `V6__switch_embedding_to_1024.sql` (şimdiye kadarki iki
migration - her biri sert bir sıfırlama: mevcut vektörler farklı bir genişlikte yeniden
yorumlanamaz, bu yüzden dönüştürülmez, atılır).

**Bu bilinçli olarak otomatikleştirilmemiştir.** Bir vector kolonunun genişliğini runtime config
değerine bakarak otomatik `ALTER TABLE` ile değiştirmek, bir env var'daki yazım hatasının hiçbir
gözden geçirme adımı olmadan production embedding'lerini sessizce yok edebileceği anlamına gelir -
şema değişiklikleri, bu projedeki her migration gibi versiyonlanmış, gözden geçirilebilir SQL
dosyaları olarak kalır.

#### Otomatik olan: genişlik değişikliğinden sonra yeniden embed etme

Kolonu drop edip yeniden eklemek (veya herhangi bir provider değişikliği), mevcut her chunk'ı
`embedding = NULL` bırakır; bu da `EmbeddingSearchProvider` için görünmezdir (sorgusunda
`WHERE embedding IS NOT NULL` vardır). `EmbeddingBackfillRunner`
(`infrastructure/embedding/EmbeddingBackfillRunner.java`) her backend başlangıcında çalışır, en az
bir null-embedding chunk'ı olan her dokümanı bulur ve onun için işlemeyi otomatik olarak yeniden
tetikler - elle doküman başına `POST /api/documents/{id}/reprocess` döngüsüne gerek kalmaz.
Farklı, **uyumsuz** sağlayıcılardan gelen null-olmayan embedding'lerin aynı kolonda bir arada
bulunması gibi daha ince bir durumu **tespit etmez** (örn. `FallbackEmbeddingProvider`'ın Gemini
kotası bittiği için çalışma ortasında sessizce OpenAI'a geçmesi) - bu vektörler teknik olarak doğru
genişliktedir ama farklı vektör uzaylarından gelir, yani aralarındaki cosine similarity anlamsızdır.
Bu, bilinen ve önceden var olan bir sınırlamadır, bu runner'ın çözdüğünü iddia ettiği bir şey
değildir.

#### Reranker (opsiyonel, varsayılan olarak kapalı)

Bi-encoder embedding modelleri (özellikle e5-small gibi küçük olanlar), gerçek relevansdan bağımsız
olarak cosine skorlarını dar bir bant içine sıkıştırabilir - örneğin aynı workspace'te, aynı
Türkçe sorgu için hem alakalı bir sigorta poliçesi chunk'ında hem de tamamen alakasız bir mobil
oyun dokümanında 0.81-0.84 arası skorlar gözlemledik. `RERANKER_ENABLED=true` ayarlandığında
`EmbeddingSearchProvider`, pgvector'dan daha geniş bir aday havuzu çeker
(`maxResults * RERANKER_CANDIDATE_POOL_MULTIPLIER`, varsayılan 3x) ve bunları `maxResults`'a
kırpmadan önce bir cross-encoder ile (`cross-encoder/mmarco-mMiniLMv2-L12-H384-v1`, `reranker`
docker-compose servisi tarafından sunulur) yeniden skorlar. Küçük ayak izi nedeniyle seçildi
(~117M parametre, 4GB RAM'lik bir deployment'a sığar); eğitim verisi (mMARCO) Türkçe
**içermez**, yani Türkçe için herhangi bir fayda in-language fine-tuning'den değil, diller arası
transferden gelir - production'da güvenmeden önce kendi verinizde kalite/gecikmeyi doğrulayın.

### Testler

```bash
cd backend
mvn test   # Docker çalışıyor olmalı (Testcontainers gerçek bir Postgres ayağa kaldırır)
```

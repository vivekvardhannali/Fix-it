# Placeholders To Change Later

> The embedding model, API key location, search weights and thresholds are collected in `SEARCH_AND_EMBEDDING_SETTINGS.md`.
>
> Decisions awaiting your confirmation and the full waiting-for list are in `NOTES_AND_OPEN_DECISIONS.md`.

Everything below is a **temporary placeholder**. Replace it before real use.
Values are read from environment variables (defaults live in `backend/src/main/resources/application.properties`).
Never commit real secrets to git.

## Google Sign-In — PARKED (off by default; username + password login is active)

To turn Google back on later: `GOOGLE_AUTH_ENABLED=true` plus the two values below. Until then nothing here is needed.


| Env variable | Current placeholder | What to put there |
|---|---|---|
| `GOOGLE_CLIENT_ID` | `PLACEHOLDER_GOOGLE_CLIENT_ID` | OAuth 2.0 Client ID from Google Cloud Console (APIs & Services → Credentials) |
| `GOOGLE_CLIENT_SECRET` | `PLACEHOLDER_GOOGLE_CLIENT_SECRET` | The matching client secret |

In Google Cloud Console, when creating the OAuth client (type: Web application):
- Authorised redirect URI must be exactly: `http://localhost:8080/login/oauth2/code/google`
  (add the production URL equivalent when deploying).
- Configure the OAuth consent screen. Use **Internal** only if the Google Workspace belongs to IITM;
  otherwise use External and rely on Fix It's own `@smail.iitm.ac.in` check.

Until these are set, the app logs a startup warning and the "Continue with Google" redirect will
reach Google but be rejected there. Tests do not need real credentials.

## App settings (Phase 5)

| Env variable | Current default | Change when |
|---|---|---|
| `ALLOWED_EMAIL_DOMAIN` | `smail.iitm.ac.in` | The allowed-account rule changes (confirm this is the correct domain) |
| `FRONTEND_URL` | `http://localhost:3000` | The frontend exists. Users are redirected here after login, and to `<FRONTEND_URL>/login?error=<code>` on failure |

## Database (Phase 2)

| Env variable | Current default | Change when |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/fix_it` | Using a non-local database |
| `DB_USERNAME` | `vivekspc` | Using a dedicated app user (recommended outside local dev) |
| `DB_PASSWORD` | *(empty — local trust auth)* | Any non-local database. Must be set |

## Security decisions to revisit

- **CSRF protection is off**; instead the session cookie is `HttpOnly` + `SameSite=Lax` and the API is JSON-only.
  Revisit if the frontend ends up on a different *site* than the backend (then add CORS config and CSRF tokens).
- `Secure` cookie flag is not set (local HTTP). Set `server.servlet.session.cookie.secure=true` when served over HTTPS.
- All `/api/**` endpoints except `/api/ping` and `/api/health/**` require login, including reading questions.
  Loosen if question pages should be public. `/api/health/*` now also require login (only `/api/ping` is public).

## Tags and time zone (decided, nothing to change)

Tags are fixed to `tech, math, code, others` (`ALLOWED_TAGS` env var to change the list, comma separated; changing it only affects what can be
chosen from now on - existing questions keep their tags). Time zone is IST. See `NOTES_AND_OPEN_DECISIONS.md` D17 / D21.

## Embeddings — CONFIGURED (Jina AI)

Provider `jina`, model `jina-embeddings-v4`, endpoint `https://api.jina.ai/v1/embeddings`, vector size **2048** are now the
defaults in `application.properties`, and `JinaEmbeddingService` implements the call. Verified live against the real API.

| Env variable | Value now | Notes |
|---|---|---|
| `EMBEDDING_PROVIDER` | `jina` (default) | |
| `EMBEDDING_MODEL` | `jina-embeddings-v4` (default) | |
| `EMBEDDING_API_URL` | `https://api.jina.ai/v1/embeddings` (default) | Must be `https`; the key is refused over plain http to a remote host |
| `EMBEDDING_DIMENSION` | `2048` (default) | The model's native size (it also accepts smaller sizes, e.g. 512/1024, via the same request). **Changing it requires re-embedding all questions** (`EMBEDDING_REINDEX_ON_STARTUP=true` once) |
| **`EMBEDDING_API_KEY`** | **secret, set in `backend/.env.local`** | Git-ignored (mode 600). Start the app with `backend/run-local.sh`, which loads it. In production set it as a real environment variable. The key is never logged, never returned by any endpoint, and `mvn test` cannot see it |

> The key was pasted into a chat to set this up. If that chat is shared or stored anywhere you don't control, **rotate the
> key** in the Jina dashboard and put the new one in `.env.local`.

How it behaves: stored questions are embedded with Jina's `retrieval.passage` mode, search text with `retrieval.query`
(Jina v4 embeds them differently). If the key is missing/rejected or Jina is down: questions still save (no vector yet),
semantic/hybrid search returns 503, lexical works; the startup log says what is missing.

**After first configuring, embed the existing questions once:** `EMBEDDING_REINDEX_ON_STARTUP=true ./run-local.sh`, then
remove the flag. (New and edited questions are embedded automatically.) Nothing in the database yet, so not needed today.

Free-tier note: check Jina's current limits/pricing yourself; each question save/edit makes one request and each search one
request. A request that hits a rate limit is logged and the question is saved without a vector (re-embed later).

## Search tuning — PROVISIONAL defaults are now set (override with env vars)

| Env variable | Default now | Meaning |
|---|---|---|
| `SEARCH_SEMANTIC_WEIGHT` | **0.7** | **Your hybrid fraction**: share of the semantic score. Lexical share = `1 - this` (0.7 = 70% semantic + 30% lexical). `1` = semantic only, `0` = lexical only (works without an embedding provider) |
| `SEARCH_HYBRID_THRESHOLD` | **0.38** (was 0.5) | Hybrid search (the default mode): minimum hybrid score, 0..1 (inclusive). **The threshold that matters** |
| `SEARCH_SEMANTIC_THRESHOLD` | **0.6** | Standalone semantic mode only: minimum cosine similarity |
| `SEARCH_LEXICAL_THRESHOLD` | **0.3** | Standalone lexical mode only: minimum *normalised* BM25 score |
| `SEARCH_MAX_RESULTS` | `20` | Engineering limit |
| `SEARCH_BM25_K1` / `SEARCH_BM25_B` | `1.2` / `0.75` | Standard Okapi BM25 constants |

**Where these came from:** a small live experiment with Jina v4 (6 campus questions, 6 queries; `JinaLiveTest` prints the table).
Relevant questions scored hybrid **0.62-0.71** (semantic 0.67-0.80), unrelated **0.24-0.43** (semantic 0.34-0.50 — Jina's cosine
"floor" is high, hence the 0.6 semantic threshold). 0.38 sits just under the loosely related ones (0.39-0.42) and above the unrelated ones (<= 0.37). It was 0.5 until 2026-10-05, which showed only clearly relevant questions; lower it further for more results, raise it for fewer. The live test now also asserts that, with these values, the
clear cases return their matches and nothing else, so it will tell you if a change breaks that.
**They are NOT tuned on your real questions.** Known weak spot: a query that shares only one common word with unrelated questions
(`my laptop battery drains fast` vs two wifi/laptop questions) still scores ≈ 0.54, so those appear at the bottom of the list.
Tune by running real searches with `GET /api/search?q=...&mode=hybrid|semantic|lexical` (each result shows `semanticScore`,
`lexicalScore`, `relevance`) or re-run `JINA_LIVE_KEY=... mvn test -Dtest=JinaLiveTest` with your own questions.
If a value is set to blank, searching that way returns 503 instead of guessing.

**Decisions made while building lexical/hybrid (please confirm):**
- Lexical = Okapi BM25 over title + body, implemented in the app (in memory, rebuilt automatically when questions
  change; fine for campus scale), English stop-words dropped, **no stemming** ("disconnect" ≠ "disconnecting" — that gap is
  what the semantic half is for).
- BM25 is **normalised to 0..1** by dividing by the best possible score for the query's terms, so scores are comparable
  across queries and mixable with cosine. Raw BM25 can't be mixed or thresholded meaningfully.
- Semantic cosine is **clamped to 0..1** inside the hybrid mix so a negative cosine can't cancel lexical evidence.
- A question missing from one part (no embedding yet / no shared words) scores 0 for that part, so **every question is
  still findable** through the other part.
- If the embedding provider is down and `0 < weight < 1`, search **fails with 503** rather than silently ranking by a
  different formula. (Option if you prefer: fall back to lexical-only with its own threshold.)
- Title is not weighted higher than body. Easy to add if experiments show it helps.

## Embeddings: decisions made (please confirm) and still open

Checked `Fix_It_Design_Document.md` and `Fix_It_Database_Design.md`: neither defines embedding storage, the embedded
text, the dimension, thresholds or BM25 details ("cosine, BM25 or a combination, decided during implementation").
So these were decided in the implementation:

1. **Vectors live in a separate table `question_embeddings`** (10th table; the 9 design tables are untouched). It keeps
   the model name and a hash of the embedded text, so changing model or editing a question marks the vector stale.
   The column is an **unsized** `vector` because the dimension is unknown; the app only compares vectors whose model and
   dimension match the current settings. *Confirm, or ask me to move it into `questions`.*
2. **Every question is searchable** (your decision). A question with no current embedding is invisible to *semantic*
   search until embedded (lexical search will not have this gap).
3. **Embedded text = `TITLE_AND_BODY`** is still only a *proposed default* (`EMBEDDING_TEXT_SOURCE`). Confirm.
4. **Tag filter = ANY of the selected tags** (union, like the homepage); no tags selected = search everything.
   The design only says "restricted to the selected tags". Confirm.
5. **Question save never fails because of embeddings.** The vector is created/refreshed *after* the question commits;
   if the provider is down or unconfigured the question is saved without one.
6. **Backfill:** set `EMBEDDING_REINDEX_ON_STARTUP=true` for one run after configuring the provider (or whenever the
   model changes) to embed every missing/stale question. Not exposed over HTTP (there are no admin roles yet).

## Notifications (Phase 13) — product decisions to confirm

Currently notified: top-level comment -> question author; reply -> the parent comment's author. Not notified:
your own actions; answers; votes; `+`; the question author about *replies* to other people's comments.
Say if "someone answered your question" etc. should also notify.

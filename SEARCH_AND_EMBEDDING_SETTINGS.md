# Search and Embedding Settings — one place for everything

Last updated: 2026-10-05. This file is the single reference for the embedding model, its API key, the search weights and the
thresholds. (Older, scattered notes: `PLACEHOLDERS_TO_CHANGE.md`, `NOTES_AND_OPEN_DECISIONS.md`, `DEV_NOTES.md`.)

> **The API key itself is deliberately NOT written in this file or in any other document or in git.** It lives only in
> `backend/.env.local` (git-ignored, file mode 600). This file says where it is and how to change it.

---

## 1. The embedding model (Jina AI)

| Setting | Current value | Environment variable | Notes |
|---|---|---|---|
| Provider | `jina` | `EMBEDDING_PROVIDER` | Selects `JinaEmbeddingService` |
| Model | `jina-embeddings-v4` | `EMBEDDING_MODEL` | |
| Endpoint | `https://api.jina.ai/v1/embeddings` | `EMBEDDING_API_URL` | Must be `https` (the key is refused over plain http to a remote host) |
| Vector size | **2048** numbers per text | `EMBEDDING_DIMENSION` | The model's native size. Also sent to Jina as `dimensions`. Changing it means re-embedding every question |
| **API key** | *secret — see section 2* | `EMBEDDING_API_KEY` | Never logged, never returned by any endpoint |
| Text that is embedded | title + body (`TITLE_AND_BODY`) | `EMBEDDING_TEXT_SOURCE` | Alternatives: `TITLE_ONLY`, `BODY_ONLY`. A proposed default, not yet confirmed |
| Re-embed everything once | off | `EMBEDDING_REINDEX_ON_STARTUP=true` | Needed after changing the model, dimension or text source; remove the flag afterwards |

How it is called (verified live against the real API):
- Stored questions use Jina's task `retrieval.passage`; what a person types in the search box uses `retrieval.query` (Jina v4 embeds the two differently).
- `POST https://api.jina.ai/v1/embeddings` with `{model, task, dimensions, input:[text]}` and header `Authorization: Bearer <key>`.
- Vectors are stored in PostgreSQL table `question_embeddings` (unsized `vector` column + the model name + a hash of the embedded text, so changed
  text or a different model marks a vector stale and old-size vectors are ignored).
- A question is embedded **after** it is saved; if Jina is down, rate-limited or the key is wrong, the question is still saved (without a vector) and
  semantic/hybrid search returns "temporarily unavailable" until it is embedded again. Word-based (lexical) search keeps working.

Code: `backend/src/main/java/com/fixit/embedding/` (`JinaEmbeddingService`, `EmbeddingProperties`) and `.../search/QuestionEmbeddingService`.

## 2. The API key — where it is and how to manage it

- **Stored in:** `backend/.env.local` as `export EMBEDDING_API_KEY=...` (git-ignored; permission `600`). A template without a real key is `backend/.env.example`.
- **Loaded by:** `backend/run-local.sh` (it sources `.env.local` and starts the app). Always start the app with `./run-local.sh dev`, not plain `mvn spring-boot:run`,
  or the key is not set and search says "temporarily unavailable".
- **Not seen by tests:** `mvn test` blanks `EMBEDDING_API_KEY` on purpose (see `backend/pom.xml`), so tests never call the real API. The optional live test uses
  a separate variable: `JINA_LIVE_KEY=<key> mvn test -Dtest=JinaLiveTest` (it only runs on an empty database).
- **In production:** set `EMBEDDING_API_KEY` as a real environment variable on the server. Do not put it in any file in the repository.
- **To change or rotate it:** create a new key in the Jina dashboard, replace the value in `backend/.env.local`, restart the app. Old key values stay valid at
  Jina until you delete them there.
- **Rotate it now if this chat or its transcript is shared anywhere you don't control** — the original key was pasted into the conversation that set this up.
- **Quota:** each question save/edit and each search makes one Jina request. The app limits search to 30/minute and question create/edit to 10/minute per user,
  and sign-up to 10/hour per IP address, so one person cannot use up your quota (`RATE_LIMIT_*` variables; see `DEV_NOTES.md`). Check Jina's current free-tier limits yourself.

## 3. Search weights

The search screen uses **hybrid** ranking:

```
hybrid score = SEMANTIC_WEIGHT × semantic score  +  (1 − SEMANTIC_WEIGHT) × lexical score
```

| | Weight | Setting |
|---|---|---|
| Semantic (meaning, Jina cosine similarity) | **0.7** (70 %) | `SEARCH_SEMANTIC_WEIGHT` |
| Lexical (matching words, BM25) | **0.3** (30 %) | automatically `1 − semantic weight` |

Rules: semantic cosine is clamped to 0–1 before mixing; a question missing from one half scores 0 there (no vector yet / no shared words), so every
question stays findable; weight `1` = semantic only, weight `0` = lexical only (works with no embedding provider at all). If the weight is strictly between
0 and 1 and Jina is unavailable, the search fails with "temporarily unavailable" instead of silently ranking by a different formula.

## 4. Thresholds (minimum score to be shown, inclusive)

| Search mode | Threshold | Setting | Used when |
|---|---|---|---|
| **Hybrid** | **0.38** | `SEARCH_HYBRID_THRESHOLD` | the normal search screen (this is the one that matters) |
| Semantic only | 0.6 | `SEARCH_SEMANTIC_THRESHOLD` | only the hidden tuning view (`&debug=1`, mode semantic) |
| Lexical only | 0.3 | `SEARCH_LEXICAL_THRESHOLD` | only the hidden tuning view (`&debug=1`, mode lexical) |

Other search values: `SEARCH_MAX_RESULTS` = **20** (results returned); BM25 constants `SEARCH_BM25_K1` = **1.2** and `SEARCH_BM25_B` = **0.75** (standard
algorithm defaults, not tuning knobs). The lexical score is normalised to 0–1 (BM25 divided by the best possible score for the query's words) so it can be
mixed with cosine similarity and compared with a threshold.

**History of the hybrid threshold:** 0.5 (first provisional value) → **0.38** on 2026-10-05 at the owner's request, to also show loosely related questions.

**These values are provisional, chosen from a small experiment, not tuned on real questions** (see section 6).

## 5. How to change any of them

Defaults live in `backend/src/main/resources/application.properties` (section "Search tuning"); override without touching code by adding lines to
`backend/.env.local`, then restart the app:

```bash
export SEARCH_SEMANTIC_WEIGHT=0.6      # 60% semantic / 40% lexical
export SEARCH_HYBRID_THRESHOLD=0.3     # show more results (raise it for fewer)
```

If a value is set to an empty string the matching search refuses (HTTP 503) instead of guessing. After changing a **model, dimension or text source**, re-embed:
`export EMBEDDING_REINDEX_ON_STARTUP=true`, start once, then remove it.

To see what a setting does, open `http://localhost:8080/search.html?q=your+words&debug=1`: every result shows its semantic, lexical and final score and you can
switch between hybrid / semantic / lexical.

## 6. What the current numbers are based on (Jina v4, 22 campus questions, 6 queries)

| Kind of question for a query | Hybrid score (70/30) | Semantic cosine | Shown at 0.38? |
|---|---|---|---|
| Clearly relevant | 0.64 – 0.67 | 0.67 – 0.80 | yes (also at 0.5) |
| Loosely related (e.g. "internet slow" for a wifi query) | 0.39 – 0.42 | 0.55 – 0.60 | **yes** (hidden at 0.5) |
| Unrelated | 0.24 – 0.37 | 0.34 – 0.50 | no |
| Nonsense query, any question | ≤ 0.30 | 0.34 – 0.43 | no |

Jina's cosine "floor" is high (even unrelated text scores 0.34 – 0.50), which is why the semantic-only threshold is 0.6 and a semantic threshold below about 0.5
would let noise through. Known weak spot: a query that shares just one ordinary word with unrelated questions (e.g. "laptop" in "my laptop battery drains fast")
still scores about 0.56 for those questions, so they appear at the bottom of the list. The margin on the "unrelated" side of 0.38 is thin (0.37 vs 0.38).

To re-measure with your own questions: with an **empty** database, `JINA_LIVE_KEY=<key> mvn test -Dtest=JinaLiveTest` prints this table (semantic / lexical / hybrid
per question per query) and lists what would also be shown at the current threshold.

## 7. Quick reference — every related variable

| Variable | Default | Meaning |
|---|---|---|
| `EMBEDDING_PROVIDER` | `jina` | provider |
| `EMBEDDING_MODEL` | `jina-embeddings-v4` | model |
| `EMBEDDING_API_URL` | `https://api.jina.ai/v1/embeddings` | endpoint |
| `EMBEDDING_API_KEY` | *(secret, in `.env.local`)* | key |
| `EMBEDDING_DIMENSION` | `2048` | vector size |
| `EMBEDDING_TEXT_SOURCE` | `TITLE_AND_BODY` | what text is embedded |
| `EMBEDDING_REINDEX_ON_STARTUP` | `false` | re-embed all questions once |
| `SEARCH_SEMANTIC_WEIGHT` | `0.7` | semantic share (lexical = 1 − this) |
| `SEARCH_HYBRID_THRESHOLD` | `0.38` | hybrid minimum score |
| `SEARCH_SEMANTIC_THRESHOLD` | `0.6` | semantic-only minimum cosine |
| `SEARCH_LEXICAL_THRESHOLD` | `0.3` | lexical-only minimum (normalised BM25) |
| `SEARCH_MAX_RESULTS` | `20` | results returned |
| `SEARCH_BM25_K1`, `SEARCH_BM25_B` | `1.2`, `0.75` | BM25 constants |
| `RATE_LIMIT_SEARCH_PER_MINUTE` | `30` | searches per user per minute |
| `RATE_LIMIT_QUESTION_WRITES_PER_MINUTE` | `10` | question create/edit per user per minute |
| `RATE_LIMIT_REGISTRATIONS_PER_IP_PER_HOUR` | `10` | sign-ups per IP address per hour |

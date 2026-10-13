# Fix It — Notes, Open Decisions and Waiting-For List

**This is the file to update when you have new information.** Edit the *Your answer* column or add notes, then tell
me ("I updated the notes file") and I will apply the change. Related files:

- `PLACEHOLDERS_TO_CHANGE.md` — every environment variable that still holds a placeholder, and where to get the value.
- `DEV_NOTES.md` — how to run/debug things, design decisions, gotchas, final checklist.
- `SEARCH_AND_EMBEDDING_SETTINGS.md` — **the one place for the embedding model, where its API key lives, the search weights and thresholds** (and how to change them).
- `API_REFERENCE.md` — every endpoint (for the frontend).
- `Specifications_and_designs/Fix_It_Frontend_Design_Document.md` — the frontend design, 13 phases (F0–F12) with checkpoints.

Status of the roadmap: **all 23 phases are coded and tested (164 tests).** What remains is *information I am waiting
for from you*, not code. Nothing is committed to git.

---

## 1. Information I am waiting for from you

| # | What | Where it plugs in | Effect until supplied | Your answer / notes |
|---|---|---|---|---|
| W1 | **PARKED (Google sign-in is off; password login is used for now).** Google OAuth client ID + secret (`GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`); redirect URI to register: `http://localhost:8080/login/oauth2/code/google` | env vars | Real Google sign-in cannot work (login redirects to Google, which rejects the placeholder). All tests simulate Google | |
| W2 | ~~Embedding provider~~ **DONE: Jina AI, `jina-embeddings-v4`, 2048 dims**; key in `backend/.env.local` (run with `backend/run-local.sh`). Consider rotating the key (it was pasted in chat) | env vars + `JinaEmbeddingService` (written) | Works. Existing questions (none yet) need `EMBEDDING_REINDEX_ON_STARTUP=true` once | |
| W3 | **Search tuning, found by experimentation**: `SEARCH_SEMANTIC_WEIGHT` (your hybrid fraction), `SEARCH_HYBRID_THRESHOLD`, optionally `SEARCH_SEMANTIC_THRESHOLD`, `SEARCH_LEXICAL_THRESHOLD` | env vars | Search returns 503 rather than guessing a value | |
| W4 | Database password / URL / user if not the local default | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Works locally as-is | |
| W5 | Frontend URL (`FRONTEND_URL`), HTTPS/hosting details (needed for the `Secure` cookie flag and CORS) | env var + a config change | Defaults assume `http://localhost:3000` | |

**Embedding provider (W2) is done.** `JinaEmbeddingService` calls Jina with `retrieval.passage` for stored questions and
`retrieval.query` for searches. To switch provider later: write another class extending `AbstractEmbeddingService`, register
it `@Primary`, and re-embed (a different model's vectors are never mixed with the old ones).
Next for you: tune W3 (first measurements and starting guesses are in `PLACEHOLDERS_TO_CHANGE.md`).

**Search values are now provisional defaults (W3):** semantic weight **0.7** (70% semantic / 30% lexical), hybrid threshold **0.38** (lowered from 0.5 on request),
semantic-only threshold **0.6**, lexical-only threshold **0.3** — chosen from the live Jina experiment, checked again live. Not
tuned: change any of them with the `SEARCH_*` env vars. Known weak spot: a query sharing only one common word (e.g. "laptop")
can still surface unrelated questions at the bottom (hybrid ≈ 0.54).

**Tuning tip:** `GET /api/search?q=...&mode=semantic|lexical|hybrid` — every result shows `semanticScore`,
`lexicalScore` and the final `relevance`, so you can compare modes and pick the weight/threshold from real queries.

---

## 2. Decisions I made that you should confirm (change the answer if you disagree)

| # | Decision | Why / alternative | Your answer |
|---|---|---|---|
| D1 | Embeddings live in a **separate table `question_embeddings`** (10th table), unsized `vector`, with model name + text hash | The design defines no embedding storage. Alternative: a column on `questions` | |
| D2 | Embedded text = **title + body** (`EMBEDDING_TEXT_SOURCE`) | Proposed default only; changes search behaviour | |
| D22 | **Gaps fixed for building the frontend:** the backend serves the website (same origin, no CORS); `authorName` on all authors; notifications carry `questionTitle` + `actorName`; **rate limits** (search 30/min/user, question create/edit 10/min/user, sign-up 10/hour/IP; 429 + Retry-After) so nobody can drain your Jina quota; a Content-Security-Policy header (frontend must use external scripts only) | Limits are env-configurable (`RATE_LIMIT_*`). Not built: a global daily cap on embedding calls | |
| D23 | **TESTING CONVENIENCE - per-tab sessions (change/remove before real use).** A browser has one login cookie per site, so two tabs could not be two accounts. Now, in the `dev` profile, login/sign-up also return a token that each tab keeps in its own `sessionStorage` and sends as `Authorization: Bearer ...`; it overrides the shared cookie, so tab 1 can be alice and tab 2 bob. **How it behaves:** a tab with no login of its own (e.g. opened fresh) still uses the browser's cookie; a tab whose token is unknown/expired is logged out (never silently someone else); logout in one tab leaves the other logged in; tokens are in server memory (lost on restart), expire after 12 h idle, and are stored hashed. **Why it must change before real use:** a token in `sessionStorage` is readable by any script on the page, unlike the HttpOnly cookie (the CSP + safe DOM building make that unlikely, but it is a weaker setup). **To switch it OFF:** `export PER_TAB_SESSIONS=false` in `backend/.env.local` (or delete the line in `application-dev.properties`) - nothing else changes, the website then just uses the cookie. **To REMOVE the code completely:** grep for `TESTING-CONVENIENCE` / `D23` (backend: `TabSessionService`, `TabSessionFilter`, `AuthTokenResponse`, `MeResponse.perTabSessions`, 3 lines in `SecurityConfig`/`AuthController`/`MeController`, 2 properties; frontend: the token code at the top of `js/api.js` and the "already logged in as ..." notice in `js/pages/login.js`; tests: `TabSession*Test`, `f13-two-accounts.spec.mjs`, and the per-tab cases in `api.test.mjs`/`f2-auth.spec.mjs`). | Keep it only while you are testing with several accounts | |
| D3 | Tag filter in search = **ANY** of the selected tags (from the fixed four); no tags = search everything | Design only says "restricted to the selected tags". Homepage is explicitly a union | |
| D4 | Homepage discovery: union of tags, sorted by `+` count, **ties newest first**; none selected = all questions; paged (20, max 50) | Tie-break not in the design | |
| D5 | Lexical search = in-app **BM25, normalised to 0..1**, no stemming, title not weighted extra | Raw BM25 can't be thresholded/mixed with cosine | |
| D6 | Hybrid = `w·clamp01(cosine) + (1-w)·lexical`; a question missing from one part scores 0 there | Keeps every question findable | |
| D7 | Provider down and `0 < w < 1` → search **503** (no silent fallback) | Alternative: fall back to lexical-only with its own threshold | |
| D8 | Question saves **never fail** because of embeddings; vector is made after commit, backfilled by `EMBEDDING_REINDEX_ON_STARTUP` | Re-embedding is not exposed over HTTP (no admin role exists) | |
| D9 | Notifications only for: top-level comment → question author; reply → parent comment's author; never for your own actions. **No** notification for answers, votes, `+`, or replies in a thread you only own the question of | Design only gives comment examples. Likely wanted: "someone answered your question" | |
| D10 | **Multiple answers** per question allowed (also by the question author); only the answer's author can edit | Design wording is ambiguous; roadmap's accepted-answer step implies several | |
| D11 | Accepted answer: question author only, can be switched, **no un-accept**, doesn't change the question's "modified" time | Not specified | |
| D12 | Comments reply to **any depth**; no delete; authors may vote on their own comments | Not specified | |
| D13 | **All APIs need login** except `/api/ping` (even reading questions); `/api/health/*` need login too | Loosen if question pages should be public | |
| D14 | **CSRF protection off**; session cookie is HttpOnly + SameSite=Lax, API is JSON-only | Revisit if frontend is on a different *site*; then add CORS + CSRF tokens and `Secure` cookie | |
| D15 | Responses show `authorId` only, never a user's smail | Privacy. Tell me if the UI needs a display name | |
| D16 | Profile = `GET /api/me` + `GET /api/me/questions` (design lists a Profile module; the roadmap did not) | | |
| D17 | **DECIDED: tags are a fixed set — `tech`, `math`, `code`, `others`.** Users pick from them when asking/editing; a question needs **1 to 4**; nobody can create tags; unknown tags are rejected everywhere (ask, edit, homepage filter, search). The set lives in config (`app.tags.allowed` / `ALLOWED_TAGS`) and is seeded into the `tags` table at startup | Different/more tags later = change the config, no code | |
| D21 | **DECIDED: Indian Standard Time.** The app forces IST (`Asia/Kolkata`) and every API timestamp carries `+05:30` | | |
| D18 | **Login is username + password for now** (Google off, code kept). Register with username + IITM smail + password; log in with username **or** smail. BCrypt hashes only (new nullable `username` / `password_hash` columns on `users` — a **deviation** from the database design, which stores no password) | Google is the intended final method | |
| D19 | **The smail typed at sign-up is not verified** (no email is sent): anyone can register any untaken `@smail.iitm.ac.in` address, and that name is then taken. A Google-created account's smail cannot be claimed this way (409) | Verify by email, or switch Google back on before real users | |
| D20 | Passwords: 8-72 bytes, any characters, not a common password / the username / the smail name. Failed-login throttle: 5 per account and 20 per IP per 15 min -> 429 (in memory, resets on restart, per server). No password reset / change yet | Add reset/change (needs email or admin) | |

---

## 2b. Frontend decisions to confirm (from `Specifications_and_designs/Fix_It_Frontend_Design_Document.md`)

| # | Decision | Alternative | Your answer |
|---|---|---|---|
| FD1 | Frontend is plain **HTML/CSS/JS, no framework, no build step** (as the design doc says), **multi-page**, served by the Spring Boot backend (same origin) - **the hosting is now in place** (`frontend/` folder, placeholder pages) | A framework / separate dev server (would need CORS + CSRF work, see D14) | |
| FD2 | ~~Backend changes first~~ **All done** (static hosting B1, authorName B4, notification fields B6, CSP B7); B2/B3 dropped (password login exists) | | |
| FD3 | ~~Author names~~ **Done:** `authorName` = username (or `user<id>`); smail is never shown | | |
| FD4 | ~~Time zones~~ **Decided: IST; timestamps carry `+05:30`** | | |
| FD5 | Notifications are a **bell dropdown only** (no separate page), refreshed every 60 s | Add a full notifications page | |
| FD6 | Question rows show only what the design lists: title, few lines, tags, resolved box, `+` box (no answer counts) | Add answer count (needs backend change) | |
| FD7 | Profile dropdown shows the numeric **User ID** plus your own smail, and **My Questions** | | |
| FD8 | Hidden tuning view `search.html?...&debug=1` shows mode switch + semantic/lexical/final scores for tuning the hybrid weight | Remove | |
| FD9 | ~~Question form requires at least 1 tag~~ **Done in the backend too**: 1-4 tags from the fixed set | | |
| FD10 | Not built (not in the design): Users/Badges/Tags pages, reputation, delete, markdown, dark mode | | |

## 2c. Frontend status

The whole frontend (phases F0-F12 of the frontend design document) is built: 60 unit + 75 browser tests pass. What differs from the document and what
is not verified (Firefox/Safari, live semantic search through the screens, a real phone) is listed in section 13 of
`Specifications_and_designs/Fix_It_Frontend_Design_Document.md`. Open choices for you: FD7 (profile menu shows numeric User ID + own smail - built that way),
FD8 (hidden `&debug=1` tuning view on the search page - built), and everything in section 2 above that was already pending (D9, D13, D14, D19).

## 3. Verified only with fakes (be aware)

- **Real Google login** — never exercised end to end. Tests simulate the identity Google returns and run Fix It's own code after that.
- **Semantic quality** — the normal tests use a fake bag-of-words "embedding" (mechanics only). The **real Jina model was exercised
  live** (`JinaLiveTest`, run on demand with `JINA_LIVE_KEY`): real 2048-dim vectors stored and searched, relevant questions
  ranked above unrelated ones on a small sample. Quality on *your* real questions and the tuned weight/thresholds are still to do (W3).
- Roadmap Checkpoints **14, 15, 16, 18** are verified for mechanics **and** a live smoke run with Jina; final tuning pending.
- Concurrency of the `+` button is verified with real threads; everything else is single-user.

## 4. Ideas that need your go-ahead (not built)

- **README / run instructions** — you asked to write this later.
- Notify on answers / accepted answers (D9).
- Lexical-only fallback when the embedding provider is down (D7).
- Un-accept an answer; delete questions; admin role (also needed to expose re-indexing over HTTP).
- Title boost / stemming in lexical search.
- Rate limiting; cleaning up old notifications.

## 5. Quick facts

- Run: see `DEV_NOTES.md` (needs Java 21 `JAVA_HOME`).
- Check what is still a placeholder (after logging in): `GET /api/health/embedding`, `GET /api/health/search`; startup log warnings say the same.

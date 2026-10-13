# Fix It — Developer Notes (read this when something breaks)

## Running things
Always set Java 21 first. The default `mvn` uses Java 27 and `java_home` points at JDK 17; JDK 17 fails with
`release version 21 not supported`.

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
cd backend
mvn test                 # full test suite (needs local PostgreSQL running)
mvn spring-boot:run      # http://localhost:8080
```

## Database
- Local PostgreSQL 18 (Homebrew) on `localhost:5432`, database `fix_it`, user `vivekspc`, no password (trust auth).
  `pg_isready` should say "accepting connections"; start with `brew services start postgresql@18`.
- `fix_it` already existed and already had `pgvector` before this project's setup.
- Schema = `backend/db/schema.sql`. Hibernate is `ddl-auto=none`: **entity changes do NOT change the DB**.
  Change `schema.sql` AND apply it to the live DB (`psql -d fix_it -c ...`); a full reset is
  `DROP SCHEMA public CASCADE; CREATE SCHEMA public;` then re-run `schema.sql` (destroys all data).
- All tests are `@Transactional` and roll back, so **`fix_it` should stay empty** after tests.
  If it isn't, a test escaped its transaction. Quick check:
  `psql -d fix_it -tc "select count(*) from users"`.
- `db/checkpoint3_test_data.sql` is a manual relational check that also rolls back.

## Running with the real embedding key
`backend/run-local.sh` loads `backend/.env.local` (git-ignored; holds `EMBEDDING_API_KEY`) and starts the app with Java 21.
`mvn test` deliberately blanks `EMBEDDING_API_KEY` (surefire config in `pom.xml`) so tests can never call the real API even if
you export the key in your shell. Live check on demand: `JINA_LIVE_KEY=<key> mvn test -Dtest=JinaLiveTest` (skipped otherwise).

## Login (username + password) — what to know
- `POST /api/auth/register` / `POST /api/auth/login` (public) -> `AccountService` -> session. `AuthController.startSession` saves the
  security context **explicitly** into the HTTP session (Spring Security 6 no longer does it automatically for custom logins) and
  changes the session id (fixation). Principal = `PasswordFixItPrincipal` (id + display name only; no hash in the session).
  Controllers use the interface `FixItPrincipal`; `GoogleFixItPrincipal` is the Google flavour (only if `app.auth.google-enabled=true`).
- Passwords: BCrypt; max 72 *bytes* (BCrypt ignores the rest); failures are uniform + equal-time (dummy hash compare); `LoginThrottle`
  limits failures (5/account, 20/IP per 15 min; in memory; tests call `throttle.clear()` because it is a singleton shared by a context).
- `users.username` / `users.password_hash` are new nullable columns (apply `db/schema.sql` changes to a live DB by hand). Deviates from the
  database design doc (no password stored) - recorded as D18.
- **Bug found by the Google tests:** when Google is enabled, permit-rules must be added *before* `anyRequest().authenticated()`
  (Spring throws "Can't configure mvcMatchers after anyRequest"). `SecurityConfig` builds the list in order; `AuthenticationTest`
  runs with `app.auth.google-enabled=true` so this path stays covered.
- Mutation-checked (broke code on purpose, confirmed the test fails, restored): any-password-accepted, no new session id on login,
  throttle not consulted, password stored in plain text.

## Tags are a fixed set (tech, math, code, others)
- `TagCatalog` (config `app.tags.allowed`, default `tech,math,code,others`) is the single source of truth; `TagService.resolve` validates
  against it; `TagService.ensureCatalogRows()` inserts the rows at startup (`on conflict do nothing`) and `schema.sql` seeds them.
  There is **no tag-creation endpoint** (`POST /api/tags` is 405). `TagService.findOrCreate` is internal (tests use it for setup).
- A question needs 1-4 tags: create without tags -> 400; update with `[]` -> 400; omitted `tags` on update = unchanged.
- Homepage and search validate the `tags` filter against the catalog (unknown -> 400). Service-level search tests (scorers called
  directly) still use arbitrary tag names on purpose - validation lives in `SearchService` / `DiscoveryService`.
- **Test gotcha:** the four tag rows are permanent now, so `fix_it` is "empty" except `tags` (4 rows). Questions always have
  `question_tags` rows, so any test that deletes questions by hand must delete `question_tags` first (a cleanup that forgot this
  failed and left rows behind - symptom: `users`/`questions` count not 0 after a run).

## Time: Indian Standard Time
- `FixItApplication` sets the JVM default zone to `Asia/Kolkata` in a static initialiser (so it also holds in tests and does not depend on
  the machine); Postgres sessions pick it up from the JVM. Timestamps are stored as IST wall-clock `timestamp` values.
- `JsonTimeConfig` writes every `LocalDateTime` with its offset (`2026-10-04T23:30:59.479+05:30`). `IstTimestampTest` checks the JVM zone,
  the DB session zone, the `+05:30` suffix and that the value is really "now" (not just labelled IST). Error bodies use UTC `Z`.

## Frontend (the website) - how it is built and tested
- Plain HTML/CSS/JS in `frontend/`, no framework, no build step; pages are `index/login/search/question/ask/profile/styleguide.html`,
  logic in `frontend/js/` (`api.js` is the only file that calls `fetch`; `dom.js` builds elements safely; user text is never parsed as HTML).
- **Unit tests** (`cd frontend && npm install && npm run test:unit`, Node's built-in runner): URL/time/validation helpers, the API client with a fake
  fetch, colour contrast of every text/background pair (reads the real CSS), and **safety rules** (no `innerHTML`/`eval`/inline scripts,
  `fetch` only in `api.js`, no external URLs).
- **Browser tests** (`npx playwright test --config test/e2e/playwright.config.mjs`): one file per phase (`f0`...`f12`) plus `f11-hardening` and
  `f11-errors`. They start their own backend (`test/e2e/start-backend.sh`: port 8089, database `fix_it_e2e` created automatically, NO paid calls -
  empty embedding key and word-only search), drive the installed Google Chrome, and delete everything they created (users named `e2e_...`).
  Real `fix_it` is never touched. Failure artefacts go to `frontend/test-results/` (git-ignored).
- **Sample data:** `node frontend/test/seed.mjs http://localhost:8080` (users `demo_*`, password `correct horse battery`); `--clear` removes it.
- Gotchas found while building: Playwright starts the web server BEFORE global setup (so the e2e database is created by the launcher script);
  list helpers must wait for the list to finish loading (`titlesIn`); the login page's "already logged in?" probe produces one expected 401 console
  line; a missing-question page produces one expected 404 console line; in tests, form fields are targeted by id (login and sign-up forms share labels).
- Every error and not-found state still shows an `<h1>` (`ensureHeading`), and `<main tabindex="-1">` lets the skip link move focus.

## Serving the website (frontend)
- Source lives in `frontend/` (repo root). `pom.xml` copies it to `static/` inside the app, so `mvn package`/`mvn test` include it and
  Spring Boot serves it at `/`, `/login.html`, `/css/**`, `/js/**`, `/img/**`. Only those GET paths are public (`SecurityConfig`);
  everything under `/api/**` still needs a session. Same origin as the API => cookies work, no CORS.
- `./run-local.sh dev` activates profile `dev` (`application-dev.properties`): serves `../frontend/` live from disk, no caching, so
  editing a file shows on a browser refresh (verified). Without `dev`, the packaged copy is served (rebuild to update).
- **Content-Security-Policy** (`default-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; frame-ancestors 'none';
  base-uri 'self'; form-action 'self'`) is sent on every response. Consequences for page code: **no inline `<script>`, no `eval`, no
  CDN/font links**; inline `style="..."` attributes are allowed. A page that "does nothing" with a CSP error in the console is this.
- `StaticHostingTest` also pins which paths are public; `AuthorizationAuditTest` still checks every API route is protected.

## Rate limits
- `RateLimiter` (sliding window, in memory): `search` 30/min/user, `question-write` (create + edit) 10/min/user, `register` 10/hour/IP
  (`RATE_LIMIT_SEARCH_PER_MINUTE`, `RATE_LIMIT_QUESTION_WRITES_PER_MINUTE`, `RATE_LIMIT_REGISTRATIONS_PER_IP_PER_HOUR`). Invalid requests
  count too. Because it is keyed by user id, tests get fresh quotas per test; tests that register many users from one address call
  `rateLimiter.clear()`. Resets on restart; per server instance; behind a proxy the proxy must pass the real client address.
- Why: every search and every question save calls the paid embedding API, and sign-up is open and unverified (D19) - without limits one
  script could exhaust the quota. Not built: a global cap on embedding calls per day.

## Author names
- Responses carry `authorName` = `User.getDisplayName()` = username, else `user<id>`; the smail is never returned (`AuthorNameTest`
  checks several endpoints for any `@smail` text). Notifications also carry `questionTitle` and `actorName`.

## Per-tab sessions (TESTING-CONVENIENCE, decision D23)
Lets two tabs of one browser be two accounts. ON in the `dev` profile (`./run-local.sh dev`), OFF otherwise; `PER_TAB_SESSIONS=true|false` overrides.
- Backend: `TabSessionService` (in-memory token store, SHA-256-hashed, 12 h idle expiry) + `TabSessionFilter` (placed right after `SecurityContextHolderFilter`:
  a Bearer token always wins over the cookie; an unknown token = not logged in). Login/sign-up still start the cookie session AND return `sessionToken`;
  `/api/me` returns `perTabSessions` so the login page knows; logout revokes the tab's token.
- Frontend: `api.js` keeps the token in `sessionStorage` (per tab), sends it, drops it on 401/logout. `login.js`: if the browser is logged in only through the shared
  cookie and this tab has no token, it shows "This browser is already logged in as X - Continue as X, or log in below as someone else" instead of bouncing away.
- Quick no-code alternative: use `http://localhost:8080` in one tab and `http://127.0.0.1:8080` in the other (different cookie jars).
- Tests: `TabSessionTest`, `TabSessionDisabledTest`, `TabSessionServiceTest`, the per-tab cases in `api.test.mjs`, `f2-auth.spec.mjs`, `f13-two-accounts.spec.mjs`.
- Everything is tagged with the word `TESTING-CONVENIENCE`: `grep -rn TESTING-CONVENIENCE backend frontend` lists every piece to delete.

## Test data warning
The backend tests (`mvn test`) and `JinaLiveTest` assume the real `fix_it` database holds NO questions (e.g. homepage counts, rankings). If you loaded the
sample data (`node frontend/test/seed.mjs`), run `node frontend/test/seed.mjs --clear` first, otherwise some tests fail for reasons unrelated to the code.
(`JinaLiveTest` now skips itself unless the database is empty.) To run the backend suite without touching your data: `DB_URL=jdbc:postgresql://localhost:5432/fix_it_e2e mvn test -Dtest='!DatabaseConnectionTest'` (the e2e database exists after the first browser-test run). The browser tests use their own database and are not affected.

## Non-obvious design decisions
- **Composite FKs** in the schema: `questions(accepted_answer_id, question_id) -> answers(answer_id, question_id)`
  (accepted answer must belong to the question) and `question_comments(parent_comment_id, question_id)`
  (a reply must be on the same question). Violations surface as `DataIntegrityViolationException`.
- Tag names are unique **case-insensitively** (`uq_tags_name_lower`); `TagService` trims/collapses whitespace and
  reuses existing tags. Tags are created on demand (final tag set not decided).
- `updated_at` is only bumped by explicit `touch()` calls in services (not by every column change), so e.g. a
  `+` count change must not touch the question.
- **Tag removal is deleted explicitly** in `QuestionService.replaceTags`. `orphanRemoval` alone silently failed to
  delete rows when the question was created in the same transaction (found by a raw-SQL assertion in `TagApiTest`).
  Lesson: assert persistence with raw SQL (`JdbcTemplate`) after `em.flush()`, not only through the API/JPA, since
  tests share one persistence context.
- Services return DTOs (not entities): `open-in-view=false`, so lazy collections must be read inside the transaction.
- Auth = Google OIDC -> `FixItOidcUserService` -> `UserProvisioningService` (verified email + exact
  `@smail.iitm.ac.in` domain match) -> session cookie. Controllers get the user via `@AuthenticationPrincipal FixItPrincipal`
  (`getUserId()`). Tests skip Google using `TestAuth.as(user)`.
- CSRF is disabled on purpose (HttpOnly + SameSite=Lax cookie, JSON-only API). See `PLACEHOLDERS_TO_CHANGE.md`.
- Multiple answers per question are allowed (question author may also answer); only the answer's author can edit it.
- Accepted answer: `PUT /api/questions/{id}/accepted-answer {answerId}`. Question author only (403 for everyone else,
  including the answer's author); answer from another question -> 400; unknown answer -> 404; re-selecting switches it.
  It does **not** bump `questions.updated_at` (that means "content edited"). There is no un-accept operation.
- Comments: `POST /api/questions/{id}/comments`, replies via `POST /api/comments/{id}/replies` (reply inherits the
  parent's question), `GET /api/questions/{id}/comments` returns a nested tree, `PUT /api/comments/{id}` edits (author
  only). Replies may nest to any depth (data model allows it). No delete endpoint, by design.
- Comment votes: `PUT /api/comments/{id}/vote {"voteType":"UP"|"DOWN"}` has click semantics (none -> vote; same again ->
  removes; other type -> switches); `DELETE .../vote` removes explicitly (idempotent). `GET .../comments` includes
  `upCount`, `downCount`, `myVote` per comment. Votes never touch interest_count, ordering, `updated_at` or (later) search.
  Authors may vote on their own comments. Lowercase/unknown vote types -> 400.
- `+` interest: `POST /api/questions/{id}/interest` toggles (returns `interested`, `interestCount`); `GET` gives the
  current user's state. `InterestService` **row-locks the question** (`findByIdForUpdate`) so `interest_count` always
  equals the row count under concurrency. Verified: without the lock, 12 simultaneous clicks gave count 1, not 12.
  The question's author may also click `+`. Doesn't touch `updated_at`.
- Notifications: created inside `CommentService.save` (same transaction as the comment) via `NotificationService`.
  Types `COMMENT_ON_QUESTION` / `REPLY_TO_COMMENT`; never for your own action; a reply notifies only the parent
  comment's author. API: `GET /api/notifications[?unreadOnly=true&limit=N]`, `GET .../unread-count`,
  `PUT .../{id}/read` (recipient only, else 403; idempotent). `commentId`/`actorId` identify what triggered it.
- Embeddings: **Jina v4 is wired** (`JinaEmbeddingService`, selected by `EMBEDDING_PROVIDER=jina` via `@ConditionalOnProperty`, `@Primary`). `EmbeddingPurpose` (DOCUMENT/QUERY) -> Jina task `retrieval.passage` / `retrieval.query`; `dimensions` = `EMBEDDING_DIMENSION` is sent to the model. Tested against a local stub server (`JinaEmbeddingServiceTest`: request shape, parsing, 401/429/5xx, malformed, wrong size, timeout, unreachable, https-only, no key leakage). Older notes about the placeholder: `com.fixit.embedding`. `EmbeddingService` interface ->
  `AbstractEmbeddingService` (refuses when unconfigured, wraps provider errors, validates dimension/finite values) ->
  `PlaceholderEmbeddingService` (always "not configured"). Failures -> generic 503 `Search is temporarily unavailable`
  (detail only in logs). Plug in the real provider as a `@Primary` bean. **Do not use `@ConditionalOnMissingBean`
  for this**: it depends on bean registration order and silently failed in a test (two beans found).
  `GET /api/health/embedding` shows which settings are still placeholders (never values/keys).
- Search (`com.fixit.search`): `question_embeddings` table (derived data; unsized `vector`, `model`, `text_hash`).
  `QuestionEmbeddingService` embeds after commit (`@TransactionalEventListener(AFTER_COMMIT)`, event published by
  `QuestionService` on create / title-or-body change), never throws for provider problems, skips unchanged text,
  `reindexAll()` backfills. `SemanticScorer` = cosine for all candidates (no threshold) so hybrid can combine parts;
  `SemanticSearchService` = threshold + order + limit + `SearchResultAssembler`. Vectors go to Postgres as text
  literals cast with `CAST(? AS vector)` (no Hibernate vector mapping). Tag filter = ANY tag, case-insensitive.
  Semantic search API endpoint does not exist yet (Phase 19); only the services.
- Lexical/hybrid: `Tokenizer` -> `Bm25Index` (in-memory postings; rebuilt when `LexicalCorpusRepository.fingerprint()` =
  count + max id + max updated_at changes, so **any text edit must bump `updated_at`** - `QuestionService` does; raw SQL
  text edits need `Bm25Index.invalidate()`) -> `LexicalScorer` (normalised 0..1) / `LexicalSearchService`.
  `HybridRanking.combine` is a pure function (weights tested without a DB); `HybridSearchService` skips a component whose
  weight is 0. Results carry `semanticScore`/`lexicalScore` for tuning. No search endpoint yet (Phase 19).
  BM25 mutation-tested: disabling IDF or length normalisation makes `LexicalSearchTest` fail.
- **Testing search:** `FakeEmbeddings` (src/test) is a bag-of-words hash, TEST ONLY: proves mechanics, says nothing
  about real semantic quality. After-commit behaviour needs non-`@Transactional` tests
  (`EmbeddingCommittedFlowTest`, `EmbeddingNotConfiguredFlowTest`, both self-cleaning; leftovers are
  `flow-*@smail.iitm.ac.in` users).
- `DataIntegrityViolationException` (e.g. duplicate-PK race) is mapped to **409** "Conflicting update, please retry".
- `InterestConcurrencyTest` is the only non-`@Transactional` test (real concurrent transactions). It cleans up after
  itself; if it ever crashes midway, delete leftover `conc-*@smail.iitm.ac.in` users/questions by hand.
- Ordering of lists is `created_at, id` (the id tie-break matters: timestamps can collide inside one transaction).

## Gotchas hit so far
| Symptom | Cause / fix |
|---|---|
| `release version 21 not supported` | Wrong JAVA_HOME (see top) |
| `Failed to determine a suitable driver class` | No datasource config; set `DB_URL` etc. (defaults in application.properties) |
| Every `/api/**` call is 401 | Not logged in; only `/api/ping` and `/api/health/**` are public |
| Login redirect to Google fails with "invalid_client" | Still using placeholder `GOOGLE_CLIENT_ID/SECRET` |
| `FATAL: sorry, too many clients already` / `Unable to determine Dialect without JDBC metadata` while running tests | Every distinct test configuration is a cached Spring context with its own DB pool (default 10) and PostgreSQL allows 100. Tests cap the pool (`SPRING_DATASOURCE_HIKARI_*` in the surefire config). Hit when adding ~15 test contexts |
| `LazyInitializationException` | Mapping an entity to a DTO outside the service transaction |
| Tags/rows missing after "successful" test | Check with raw SQL after `em.flush()` |

## Progress (roadmap: Specifications_and_designs/Fix_It_Backend_Implementation_Roadmap.md)
**All 23 backend phases are coded; 238 backend tests pass (2 live-Jina tests are skipped unless `JINA_LIVE_KEY` is set).** Login is now username + password (Google off). Not committed to git. What is left is information from the owner, not code:
see `NOTES_AND_OPEN_DECISIONS.md` (waiting-for list + decisions to confirm) and `PLACEHOLDERS_TO_CHANGE.md`.
Working agreement used earlier: two phases at a time, then ask (the owner lifted it for the final run: "code the remaining parts").

### Checkpoint 23 checklist (evidence)
- [x] Spring Boot starts - real run + `HealthControllerTest`
- [x] PostgreSQL connection works - `DatabaseConnectionTest`, `/api/health/db`
- [x] pgvector is available - `DatabaseConnectionTest`
- [x] All 9 database tables work (+ the 10th derived `question_embeddings`) - `JpaMappingTest`, `db/checkpoint3_test_data.sql`
- [~] Google authentication works - **simulated only**; real Google needs credentials (W1). Verified: domain/verified-email rules, user find-or-create, redirect to Google, failure redirect, 401s - `AuthenticationTest`, `EndToEndFlowTest`
- [x] IITM smail identity handled correctly - `AuthenticationTest`
- [x] User creation/retrieval - `AuthenticationTest`, `EndToEndFlowTest`
- [x] Question CRUD / tags / answers / accepted answer / comments / voting / `+` / notifications - one test class each + `EndToEndFlowTest`
- [~] Embeddings work - **mechanics verified with a fake provider** (`QuestionEmbeddingTest`, `EmbeddingCommittedFlowTest`); real provider pending (W2)
- [~] Semantic search / threshold / ranking - mechanics verified (`SemanticSearchTest`); quality needs the real model
- [x] Lexical (BM25) search - fully verified (`LexicalSearchTest`, mutation-tested)
- [~] Hybrid search - mechanics + weights verified (`HybridRankingTest`, `HybridSearchTest`); final weights/threshold are placeholders (W3)
- [x] Search API - `SearchApiTest`, `SearchNotConfiguredApiTest`
- [x] Homepage discovery (union, `+` order) - `DiscoveryApiTest`
- [x] Ownership checks - `AuthorizationAuditTest` (route enumeration + matrix; mutation-tested)
- [x] Error handling - `ErrorHandlingTest` (success/invalid/unauthorised/missing for every major API; leak check; mutation-tested)
- [x] Full end-to-end backend flow - `EndToEndFlowTest` (real commits)
- [x] Real application smoke run: starts, protected routes return 401 JSON, Google redirect works

### Phase 19-23 notes
- Search endpoint `GET /api/search` -> `SearchService.searchQuestions(mode, q, tags)`. Tags are read raw from the request
  (`RequestParams.tags`) so a comma inside a tag name isn't split.
- Homepage: `QuestionRepository.findByAnyTagRanked` / `findAllRanked` (`order by interestCount desc, createdAt desc, id desc`);
  `hibernate.default_batch_fetch_size=50` avoids one tag query per question.
- **Security audit finding fixed:** `/api/health/*` was public and revealed DB name / config state; now login-only. Only
  `/api/ping` and `/error` are public. `AuthorizationAuditTest` enumerates every registered route and fails if a new route is
  reachable anonymously, so adding an endpoint without protection breaks the build.
- **Error handling:** `GlobalExceptionHandler extends ResponseEntityExceptionHandler` so Spring's own errors (405, 415, unknown
  path, bad parameter types) also use the standard JSON; unexpected exceptions -> generic 500 (details only in the log).
  `ErrorHandlingTest` fails if any error body contains exception/class/SQL/stack-trace text.
- `EndToEndFlowTest` and `EmbeddingCommittedFlowTest`, `EmbeddingNotConfiguredFlowTest`, `InterestConcurrencyTest` are the
  only non-`@Transactional` tests (real commits). All self-clean; leftovers would be users `e2e-*@`, `flow-*@`, `conc-*@smail.iitm.ac.in`.
- Mutation checks done (temporarily broke code, confirmed the right test fails, restored): BM25 IDF, BM25 length
  normalisation, an accidentally public route, a removed ownership check, an error handler echoing exception text,
  and removing the `+` row lock.

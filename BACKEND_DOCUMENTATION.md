# Fix It — Backend Documentation

Spring Boot 3.5 / Java 21 / Maven, PostgreSQL 18 + pgvector. Code lives in `backend/`. All Java is under
`backend/src/main/java/com/fixit/`. This document describes the code as it is today: what each file is for, the
important methods in it, and how a request travels through the system. For the endpoint table see `API_REFERENCE.md`;
for tuning values see `SEARCH_AND_EMBEDDING_SETTINGS.md`.

---

## 1. The big picture

```
Browser ──HTTP──▶ Spring Security filter chain ──▶ Controller ──▶ Service ──▶ Repository ──▶ PostgreSQL
                  (session cookie / Bearer tab        (thin:        (rules,      (Spring Data JPA
                   token, public-path rules,           validates,    transactions) or plain JDBC)
                   CSP header, 401/403 as JSON)        calls one
                                                       service)
                                   ▲                       │
                                   └── GlobalExceptionHandler turns every exception into one JSON error shape
```

Layers, from outside in:

| Layer | Package | Job |
|---|---|---|
| Security | `security` | Who is the caller? Which paths are public? Rate / login throttling. |
| Controllers | `controller` | Map URLs to service calls. No business rules. |
| DTOs | `dto` | Java `record`s for request bodies (with validation annotations) and responses. |
| Services | `service` | Business rules, authorisation ("only the author may edit"), transactions. |
| Search | `search` | Hybrid (semantic + lexical) search, embedding upkeep. |
| Embedding | `embedding` | Turns text into a vector via a replaceable provider (Jina). |
| Repositories | `repository` | Spring Data JPA interfaces (and `search` has two plain-JDBC repositories). |
| Entities | `entity` | JPA classes mapped to tables. |
| Exceptions | `exception` | Domain exceptions + the single global error handler. |

<!-- The backend **also serves the website** (the `frontend/` folder) as static files on the same origin, so session cookies
work with no CORS. -->

### Design rules worth knowing
* **Time zone:** everything is IST. `FixItApplication` sets the JVM default zone to `Asia/Kolkata`; `JsonTimeConfig`
  writes every `LocalDateTime` in responses with an explicit `+05:30` offset.
* **Schema is plain SQL** (`backend/db/schema.sql`); Hibernate `ddl-auto=none`. Schema changes must be applied to the
  database by hand.
* **Errors never leak internals.** Clients get a short safe message; the details go to the log.
* **Embeddings are derived data.** A question is always saved even if embedding it fails.
* **Nothing is ever deleted** through the API (no delete for questions, answers or comments).

---

## 2. Running it

```bash
cd backend
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./run-local.sh dev        # loads .env.local, uses profile "dev" (serves ../frontend live from disk)
```
* `run-local.sh` — sources the git-ignored `.env.local` (secrets such as `EMBEDDING_API_KEY`), sets Java 21, runs
  `mvn spring-boot:run`. Passing `dev` adds `-Dspring-boot.run.profiles=dev`.
* `mvn test` — the test suite (all tests roll back; the `fix_it` DB stays empty).
* `mvn clean package` — builds the jar; `pom.xml` copies `../frontend` into `static/` (excluding tests / node_modules).

### Configuration files
| File | What it holds |
|---|---|
| `src/main/resources/application.properties` | Every setting, each overridable by environment variable (DB URL, Google OAuth, embedding provider, search weights/thresholds, login & rate limits, allowed tags, per-tab sessions). |
| `src/main/resources/application-dev.properties` | `dev` profile: serve `../frontend/` from disk with no caching; turn per-tab sessions on (testing convenience D23). |
| `db/schema.sql` | The 9 design tables + `question_embeddings`; seeds the four tags. |
| `db/checkpoint3_test_data.sql` | Small hand-made sample data used during early development. |
| `run-local.sh` | Local launcher (above). |

---

## 3. Database (`db/schema.sql`)

| Table | Purpose | Notable constraints |
|---|---|---|
| `users` | Accounts. `smail` unique; `username` + `password_hash` (BCrypt) are NULL for Google-created accounts. | Unique index on `lower(username)`. |
| `questions` | Title, body, `is_resolved`, `accepted_answer_id`, `interest_count` (the "+" count), timestamps. | `interest_count >= 0`. Composite FK `(accepted_answer_id, question_id)` → answers, so an accepted answer must belong to its question. |
| `tags` | The fixed tags (`tech`, `math`, `code`, `others`). | Unique on `lower(name)`. |
| `question_tags` | Question ↔ tag many-to-many. | PK `(question_id, tag_id)`. |
| `answers` | Answers to a question. | `UNIQUE (answer_id, question_id)` (needed by the FK above). |
| `question_comments` | Comments and replies (`parent_comment_id`). | A reply's parent must be on the same question (composite FK). |
| `question_comment_votes` | One UP/DOWN vote per user per comment. | PK `(comment_id, user_id)`; `vote_type IN ('UP','DOWN')`. |
| `question_interests` | Who pressed "+" on which question. | PK `(question_id, user_id)`. |
| `notifications` | Comment/reply notifications with an `is_read` flag. | |
| `question_embeddings` | *Derived*: one pgvector embedding per question + `model` + SHA-256 `text_hash`. | `embedding` is an un-sized `vector`; search only compares vectors whose model and dimension match the current configuration. |

---

## 4. File-by-file reference

### 4.1 Application root

| File | Contents |
|---|---|
| `FixItApplication.java` | `@SpringBootApplication` + `@ConfigurationPropertiesScan`. A `static {}` block sets the default `TimeZone` to IST *before* any DB connection opens. `main()` starts Spring. |
| `AppTime.java` | Holds `ZONE = Asia/Kolkata`, used everywhere IST is needed. |
| `config/JsonTimeConfig.java` | `istTimestamps()` bean: a Jackson customizer that serialises every `LocalDateTime` as ISO-8601 with `+05:30`. |

### 4.2 `security/`

| File | Contents |
|---|---|
| `SecurityConfig.java` | `filterChain(...)` builds the whole security setup: adds `TabSessionFilter`; CSRF off (cookie is SameSite=Lax and the API is JSON-only); public paths = `/api/ping`, `/error`, `GET /`, `/*.html`, `/css/**`, `/js/**`, `/img/**`, `/favicon.ico`, `/api/auth/register`, `/api/auth/login` (+ OAuth paths if Google is on); **everything else needs login**. Sets a strict Content-Security-Policy (`default-src 'self'` — no inline scripts, no CDNs). 401/403 are returned as JSON `ApiError`. Logout is `POST /api/logout` → 204, revokes the tab token, kills the session, deletes `JSESSIONID`. Google `oauth2Login` is only wired if `app.auth.google-enabled=true`. `loginFailureHandler(...)` redirects Google failures to the frontend with an error code. |
| `FixItPrincipal.java` | Interface with `getUserId()`. Controllers depend only on this, whatever the login method. |
| `PasswordFixItPrincipal.java` | The logged-in user for password login (id + display name; **no password or hash**). Stored in the HTTP session. |
| `GoogleFixItPrincipal.java` | Same, for Google sign-in (extends `DefaultOidcUser`). |
| `FixItOidcUserService.java` | After Google authenticates: `loadUser(...)` → `toPrincipal(...)` calls `UserProvisioningService.loginOrRegister` (smail-domain rule) and wraps the result. Maps rejection to OAuth error `account_not_allowed`. |
| `PasswordConfig.java` | `PasswordEncoder` bean = BCrypt (strength 10, random salt). |
| `LoginThrottle.java` | Failed-login lockout. `checkAllowed(account, ip)` throws 429 if locked; `recordFailure(...)`; `recordSuccess(account)` clears the account counter; limits 5/account and 20/IP per 15 min (configurable). Unknown account names are counted too, so nothing reveals which accounts exist. In-memory. |
| `RateLimiter.java` | Sliding-window limiter protecting paid/abusable calls. `check(limit, key)` for `SEARCH` (30/min/user), `QUESTION_WRITE` (10/min/user), `REGISTER` (10/hour/IP); throws 429 with `Retry-After`. In-memory. |
| `TabSessionService.java` | **Testing convenience (D23).** Issues random 256-bit tokens (stored only as SHA-256 hashes in memory, 12 h sliding idle expiry) so each browser *tab* can be a different account. `issue`, `resolve`, `revoke`, `bearer(request)` (reads `Authorization: Bearer …`). Off unless `app.auth.per-tab-sessions=true`. |
| `TabSessionFilter.java` | If a request carries a Bearer token, it decides the caller and **always beats the cookie**; an unknown/expired token means "not logged in". |
| `PlaceholderConfigWarning.java` | On `ApplicationReadyEvent`, logs warnings for any placeholder settings (Google creds when enabled, embedding config, search tuning). |

### 4.3 `controller/`

All controllers are thin: they read the logged-in user with `@AuthenticationPrincipal FixItPrincipal me`, validate bodies
with `@Valid`, and call one service method.

| File | Endpoints (method path → service call) |
|---|---|
| `AuthController` | `POST /api/auth/register` → rate-limit by IP, `AccountService.register`, start session. `POST /api/auth/login` → `AccountService.authenticate`, start session. `startSession(...)` stores the principal in the HTTP session (and calls `changeSessionId()` first to prevent session fixation). `toResponse` returns `AuthTokenResponse`, including a tab token if enabled. |
| `MeController` | `GET /api/me` → `{userId, username, smail, perTabSessions}`. `GET /api/me/questions?page&size` → `DiscoveryService.getMyQuestions`. |
| `QuestionController` | `GET /api/questions?tags&page&size` (homepage) → `DiscoveryService.getQuestions`. `POST /api/questions` (rate-limited) → `QuestionService.create`. `GET /{id}`, `PUT /{id}` (rate-limited), `PUT /{id}/accepted-answer`, `GET /{id}/tags`, `PUT /{id}/tags`. |
| `AnswerController` | `POST /api/questions/{id}/answers`, `GET /api/questions/{id}/answers`, `PUT /api/answers/{id}`. |
| `CommentController` | `POST /api/questions/{id}/comments`, `POST /api/comments/{id}/replies`, `GET /api/questions/{id}/comments` (nested tree), `PUT /api/comments/{id}`. |
| `VoteController` | `PUT /api/comments/{id}/vote` (`{voteType: UP\|DOWN}`), `DELETE /api/comments/{id}/vote`. |
| `InterestController` | `POST /api/questions/{id}/interest` (toggle "+"), `GET …/interest` (my state + count). |
| `NotificationController` | `GET /api/notifications?unreadOnly&limit`, `GET …/unread-count`, `PUT …/{id}/read`. |
| `SearchController` | `GET /api/search?q&tags&mode` → rate-limit (per user) → `SearchService.searchQuestions`. |
| `TagController` | `GET /api/tags` → the fixed tag list. |
| `HealthController` | `GET /api/ping` (public); `/api/health/db`, `/embedding`, `/search` (login needed; report what is configured, never values or secrets). |
<!-- | `RequestParams` | `tags(request)` reads repeated `?tags=a&tags=b` raw, because Spring would split a single value on commas. | -->

### 4.4 `service/` — the business rules

| File | Methods and rules |
|---|---|
| `AccountService` | `register(username, smail, password)`: username regex `^[A-Za-z0-9][A-Za-z0-9_.-]{2,29}$`; smail must be `@smail.iitm.ac.in` (normalised, **not email-verified**); password ≥ 8 chars, ≤ 72 bytes, not a common/obvious password; 409 if username or smail taken; saves BCrypt hash. `authenticate(identifier, password, ip)`: checks `LoginThrottle`, finds user by username **or** smail, always does one BCrypt compare (even for unknown users, against a dummy hash, so timing is the same), records failure/success, one generic message `Invalid username or password`. |
| `UserProvisioningService` | `loginOrRegister(email, verified)` (Google path: requires a verified email, creates the user if new). `normalizeAllowedSmail(email)`: trim + lower-case + domain check; shared with registration. |
| `QuestionService` | `create` (needs ≥ 1 valid tag; saves question; applies tags; publishes `QuestionContentChanged`). `get`. `update` (author only; republishes the event only if title/body actually changed; `request.tags()==null` leaves tags alone). `acceptAnswer` (author only; answer must belong to the question; sets `resolved=true`; picking another answer later switches it). `getTags`, `setTags`. Private helpers `applyTags` (makes the tag set exactly the wanted one), `findOwned…` (404 / 403 checks). |
| `AnswerService` | `add`, `listForQuestion` (oldest first), `update` (**only the answer's author** — not even the question's author). |
| `CommentService` | `addComment`, `addReply` (lands on the parent's question), `thread` (builds the nested tree from one flat query, vote counts loaded with two queries), `edit` (author only). Every new comment calls `NotificationService.onCommentCreated` in the same transaction. Comments belong to questions only — never to answers. |
| `CommentVoteService` | `vote(user, comment, clicked)`: no vote → create; same type again → remove; other type → switch. `remove`. Touches only `question_comment_votes` — votes never affect ranking, search or "+". |
| `InterestService` | `toggle`: row-locks the question (`findByIdForUpdate`) so concurrent clicks can't corrupt `interest_count`; adds/removes the interest row and ±1. `status`. |
| `NotificationService` | `onCommentCreated`: top-level comment → notify the question's author (`COMMENT_ON_QUESTION`); reply → notify the parent comment's author (`REPLY_TO_COMMENT`); never notify yourself. `list` (limit 1–100, default 50), `unreadCount`, `markRead` (recipient only, idempotent). |
| `DiscoveryService` | Homepage list: questions having **any** selected tag (none = all), ordered by "+" count desc then newest. `getMyQuestions` newest first. Page size 1–50 (default 20). Not search ranking. |
| `TagCatalog` | The fixed allowed tags (`app.tags.allowed`). `names()`, `canonicalOrNull(raw)`, `requireKnown(list)` (400 for blank/unknown, de-duplicates, case-insensitive). |
| `TagService` | Keeps DB tag rows in step with the catalog: `ensureCatalogRows()` (runs at startup, `insert … on conflict do nothing`), `listAll`, `resolve(names)` (validates then returns rows), `findOrCreate` (internal only). |

### 4.5 `entity/`
JPA mappings, one per table: `User`, `Question` (has `touch()` to bump `updated_at`, tag set, accepted answer),
`Answer`, `QuestionComment` (self-reference for replies), `QuestionCommentVote` + `QuestionCommentVoteId` (composite key),
`QuestionInterest` + `…Id`, `QuestionTag` + `…Id`, `Tag`, `Notification` (+ `NotificationType`: `COMMENT_ON_QUESTION`,
`REPLY_TO_COMMENT`), `VoteType` (`UP`, `DOWN`). `User.getDisplayName()` returns the username, or `user<id>` when none;
the smail is **never** shown as a display name.

### 4.6 `repository/`
Spring Data JPA interfaces. Notable queries: `QuestionRepository` — `findByIdForUpdate` (pessimistic lock),
`findAllRanked` / `findByAnyTagRanked` (homepage ordering), `findByAuthorIdOrderBy…` (profile), `findAllIds`.
`QuestionCommentVoteRepository` — `countsForQuestion` and `findByUserAndQuestion` (the two vote queries behind a comment
thread). `UserRepository` — lookups by smail/username (case-insensitive). `NotificationRepository` — newest-first, unread
filters, unread count. Others (`AnswerRepository`, `QuestionCommentRepository`, `TagRepository`, `QuestionTagRepository`,
`QuestionInterestRepository`) are small derived-query interfaces.

### 4.7 `dto/`
Request records carry validation (`@NotBlank`, `@Size`): `RegisterRequest`, `LoginRequest` (both hide the password in
`toString`), `QuestionRequest` (title ≤ 255, body ≤ 20000, ≤ 10 tags), `AnswerRequest` (≤ 20000), `CommentRequest`
(≤ 5000), `VoteRequest`, `AcceptAnswerRequest`, `TagListRequest`. Response records: `AuthTokenResponse`,
`MeResponse`, `QuestionResponse` (full), `QuestionSummary` (list row with a body preview), `AnswerResponse`
(`isAccepted`), `CommentResponse` (nested `replies`, `upCount`, `downCount`, `myVote`), `VoteResponse`,
`InterestResponse`, `NotificationResponse`, `TagResponse`, `PageResponse<T>` (`items, page, size, totalItems,
totalPages`). `from(...)` factory methods must be called inside a transaction (they read lazy collections).

### 4.8 `exception/`
`NotFoundException` (404), `ForbiddenException` (403), `BadRequestException` (400), `ConflictException` (409),
`InvalidCredentialsException` (401), `TooManyRequestsException` (429 + `Retry-After`), `AccountNotAllowedException`.
`ApiError` is the one JSON error shape `{timestamp, status, error, message}`; `ApiError.write(...)` is used by the
security filters. `GlobalExceptionHandler` (`@RestControllerAdvice`) maps each exception to a status + safe message;
database integrity races → 409 "Conflicting update, please retry"; `EmbeddingException` / `SearchNotConfiguredException`
→ 503 "Search is temporarily unavailable"; anything unexpected → 500 "Something went wrong" (details only in the log).
It also gives friendly, safe messages for Spring's own errors (bad JSON, missing parameter, wrong method, 404 path…).

### 4.9 `embedding/` — text → vector

| File | Contents |
|---|---|
| `EmbeddingService` | Interface: `generateEmbedding(text)` and `generateEmbedding(text, purpose)`. |
| `EmbeddingPurpose` | `DOCUMENT` (stored question) vs `QUERY` (search box); asymmetric models embed the two differently. |
| `AbstractEmbeddingService` | Shared guard: refuses blank text or an unconfigured provider, wraps provider failures, validates the vector (present, right dimension, all finite). Subclasses implement only `callProvider`. |
| `JinaEmbeddingService` | Real provider. POSTs `{model, task (retrieval.passage/query), dimensions, input}` to the Jina API with a Bearer key (timeouts 5 s connect / 30 s request, no redirects). The URL must be https (http only to loopback). The key is never logged or placed in an exception. |
| `PlaceholderEmbeddingService` | Fallback that always throws "not configured" — never fabricates vectors. |
| `EmbeddingConfig` | Registers the placeholder always, and the Jina bean as `@Primary` when `app.embedding.provider=jina`. |
| `EmbeddingProperties` | `app.embedding.*` record. `missing()` lists env vars still unset/placeholder; `isConfigured()`; `toString()` hides the key. |
| `EmbeddingTextBuilder` | Builds the embedded text: title + body (default), title only, or body only (`app.embedding.text-source`). |
| `EmbeddingTextSource`, `EmbeddingStatus`, `EmbeddingException`, `EmbeddingNotConfiguredException` | Enum / health DTO / error types. |

### 4.10 `search/` — hybrid search

| File | Contents |
|---|---|
| `SearchService` | Entry point `searchQuestions(mode, query, tags)`: validates tags against the catalog, then dispatches to hybrid / semantic / lexical. |
| `SearchMode` | `HYBRID` (default), `SEMANTIC`, `LEXICAL`; `parse(raw)` → 400 on unknown. |
| `SearchInput` | Validation: query non-empty and ≤ 500 chars; ≤ 10 tags, each ≤ 50 chars, lower-cased and de-duplicated. |
| `HybridSearchService` | `search`: score = **w·semantic + (1−w)·lexical** (default w = 0.7), keep ≥ threshold (0.38), sort by score then id, top `max-results` (20). A component with weight 0 is not computed at all. |
| `HybridRanking` | Pure function `combine(semantic, lexical, w)`; clamps each score to 0..1; missing = 0. |
| `SemanticScorer` | Embeds the query (`QUERY` purpose) and asks `QuestionEmbeddingRepository.cosineScores` for every candidate's cosine similarity. |
| `SemanticSearchService` | Semantic-only mode (threshold 0.6). |
| `LexicalScorer` | Tokenises the query, scores with `Bm25Index`, then applies the tag filter via `LexicalCorpusRepository.idsWithAnyTag`. No embedding provider needed. |
| `LexicalSearchService` | Lexical-only mode (threshold 0.3). |
| `Bm25Index` | In-memory Okapi BM25 over title + body; rebuilt automatically when the questions table changes (cheap fingerprint = count:max id:max updated_at). Scores are normalised to 0..1 so thresholds and the hybrid mix are comparable across queries. |
| `Tokenizer` | Lower-case, split on non-letters/digits, drop 1-char tokens and a small English stop-word list; no stemming. |
| `LexicalCorpusRepository` | Plain SQL: `fingerprint()`, `forEachQuestion`, `idsWithAnyTag`. |
| `QuestionEmbeddingRepository` | Plain SQL on `question_embeddings`: `find`, `upsert`, `cosineScores` (pgvector `<=>`; only rows with the current model **and** dimension). |
| `QuestionEmbeddingService` | Keeps embeddings current. `onQuestionChanged` runs **after commit** on `QuestionContentChanged`, so the paid API call never holds a DB transaction open; `refresh` skips work if the SHA-256 of the text, model and dimension are unchanged; `reindexAll()` backfills everything missing/stale; never throws for provider problems (returns `EMBEDDED / UP_TO_DATE / FAILED`). |
| `EmbeddingReindexRunner` | If `app.embedding.reindex-on-startup=true`, runs `reindexAll()` once at startup. |
| `QuestionContentChanged` | Event record published when a question is created or its text changes. |
| `SearchResultAssembler` | Turns ranked ids into `SearchResult` rows in the same order; `preview(body)` = first ≤ 3 non-empty lines, ≤ 200 chars. |
| `SearchResult`, `ScoredQuestion`, `ReindexReport` | Result / score / report records. `SearchResult` carries `relevance`, `semanticScore`, `lexicalScore` (null when not computed). |
| `SearchProperties` | `app.search.*` record: thresholds, weight, `maxResults`, BM25 `k1`/`b`. `require…()` methods throw `SearchNotConfiguredException` (→ 503) if a needed value is missing/out of range. |

---

## 5. Request flows

### 5.1 Every request (the common path)
1. **Embedded Tomcat** receives it. `TabSessionFilter` looks for `Authorization: Bearer …`; if present (and the feature is
   on) that token decides who the caller is, otherwise Spring Security uses the `JSESSIONID` cookie.
2. **Authorisation rules** (`SecurityConfig`): public paths pass; anything else without a login → JSON `401`.
3. A **controller** method matches the URL, `@Valid` checks the JSON body (failure → `400` listing the bad fields).
4. The controller calls **one service method**, which runs in a transaction, enforces the rules (404 if missing, 403 if
   not the owner, 400/409 for bad input), and talks to repositories.
5. The result is mapped to a **DTO** and returned as JSON (timestamps with `+05:30`).
6. If anything throws, `GlobalExceptionHandler` converts it to `{timestamp,status,error,message}`.
7. A CSP header is added to every response.

### 5.2 Sign up / log in
`POST /api/auth/register` → `RateLimiter.check(REGISTER, ip)` → `AccountService.register` (validate → check duplicates →
BCrypt → save) → `startSession` (new session id, principal stored in the session) → `201 {userId, username, smail,
sessionToken}` + `Set-Cookie: JSESSIONID`. `POST /api/auth/login` is the same minus the rate limiter, using
`AccountService.authenticate`; wrong credentials → `401` (and a `429` lockout after repeated failures). The browser then
sends the cookie (and, in dev, the tab token) on every later call.

### 5.3 Ask a question
`POST /api/questions` → `RateLimiter.check(QUESTION_WRITE, userId)` → `QuestionService.create`: resolve tags (unknown →
400 before anything is saved) → save question → attach tags → publish `QuestionContentChanged` → response `201`.
**After the transaction commits**, `QuestionEmbeddingService.onQuestionChanged` embeds title+body (Jina, purpose
`DOCUMENT`) and upserts the vector. If that fails, only a log line is written; the question stays saved.

### 5.4 Search
`GET /api/search?q=…&tags=…` → `RateLimiter.check(SEARCH, userId)` → `SearchService` (validate tags) →
`HybridSearchService`:
1. `SemanticScorer`: embed the query (purpose `QUERY`) → cosine similarity against stored vectors (tag-filtered in SQL).
2. `LexicalScorer`: tokenise → `Bm25Index.score` → tag filter.
3. `HybridRanking.combine` → drop below threshold → sort → top 20.
4. `SearchResultAssembler` loads the questions and returns `SearchResult`s (title, 3-line preview, tags, resolved, "+" count,
   `relevance`, component scores).

If the embedding provider is down or unconfigured → `503 "Search is temporarily unavailable"` (the frontend then offers
browse/ask links).

### 5.5 Homepage and profile lists
`GET /api/questions?tags=a&tags=b&page=0&size=20` → `DiscoveryService.getQuestions` → tags validated → JPQL ordered by
`interest_count desc, created_at desc` → `PageResponse<QuestionSummary>`. `GET /api/me/questions` is the same but filtered
by author and ordered newest first.

### 5.6 Comment / reply and notification
`POST /api/questions/{id}/comments` (or `/api/comments/{id}/replies`) → `CommentService.save` stores the comment, then
`NotificationService.onCommentCreated` (same transaction) inserts a notification for the question author (or parent
comment author), unless that is the commenter. The recipient's browser polls `GET /api/notifications/unread-count` every
60 s; opening a notification calls `PUT /api/notifications/{id}/read`. `GET /api/questions/{id}/comments` returns the full
nested tree with vote counts and the caller's own vote.

### 5.7 Vote on a comment
`PUT /api/comments/{id}/vote {voteType}` → `CommentVoteService.vote`: no vote → add; same → remove; different → switch →
returns `{commentId, upCount, downCount, myVote}`.

### 5.8 The "+" button
`POST /api/questions/{id}/interest` → `InterestService.toggle` (locks the question row, adds/removes the interest row,
adjusts `interest_count`) → `{questionId, interested, interestCount}`.

### 5.9 Accept an answer
`PUT /api/questions/{id}/accepted-answer {answerId}` → `QuestionService.acceptAnswer`: caller must be the question's
author, the answer must belong to the question → sets `accepted_answer_id` and `is_resolved = true` → returns the question.

---

## 6. Security summary
* Password login (BCrypt), HttpOnly SameSite=Lax session cookie, new session id on login, generic login error, equal-time
  check for unknown users, per-account and per-IP lockout.
* CSRF protection relies on SameSite=Lax + JSON-only bodies (see comment in `SecurityConfig`; revisit if the frontend
  moves to another site).
* CSP blocks inline scripts and third-party sources; error messages never include exception text.
* Rate limits protect the paid embedding calls; the Jina key is only ever in the `Authorization` header.
* Known limitation: the smail entered at sign-up is **not verified** by email (Google sign-in is the intended proof, kept
  in the code but switched off).
* Per-tab sessions (D23) are a testing convenience — leave **off** in real use.

## 7. Tests (`backend/src/test/java/com/fixit/`)
About 240 tests, named by feature: `*ApiTest` (HTTP-level per endpoint group), `AuthenticationTest` / `PasswordAuthTest` /
`AuthorizationAuditTest` (who may do what), `ErrorHandlingTest`, `RateLimit*` / `LoginThrottle*`, `*Embedding*` /
`JinaEmbeddingServiceTest` (mocked HTTP), `SemanticSearchTest` / `LexicalSearchTest` / `HybridSearchTest` /
`HybridRankingTest`, `EndToEndFlowTest`, `JpaMappingTest`, `IstTimestampTest`, `StaticHostingTest`, `TabSession*`.
`support/FakeEmbeddings` supplies deterministic fake vectors. `JinaLiveTest` hits the real API and only runs when
`JINA_LIVE_KEY` is set. Tests blank the embedding key and cap DB pools.

# Fix It — API Reference

Base URL (local): `http://localhost:8080`. JSON everywhere. **The backend also serves the website** (files from `frontend/`: `/`, `/login.html`, `/css/…`, `/js/…`, `/img/…`) without a login, on the same origin as the API - so cookies just work and no CORS is needed. Page scripts must be external files (a Content-Security-Policy blocks inline scripts and outside sources). **Every endpoint needs a logged-in session except
`GET /api/ping`, `POST /api/auth/register` and `POST /api/auth/login`.** Not logged in = `401`. Sign up / log in with a
username and password (below); logout = `POST /api/logout` (204). The session cookie is `JSESSIONID`. (Google sign-in exists in
the code but is switched off.)

**Per-tab sessions (testing convenience, dev profile only, decision D23):** when `perTabSessions` is true, login/sign-up also return `sessionToken`; send it as `Authorization: Bearer <token>` and it identifies the caller instead of the shared cookie (so two tabs can be two accounts). An unknown/expired token = 401, never the cookie's user. In normal (non-dev) runs `sessionToken` is null and the cookie is the only login.

Errors always look like `{"timestamp","status","error","message"}`. Statuses: 400 invalid input · 401 not logged in ·
403 not allowed (not the owner) · 429 too many failed logins **or too many requests** (see `Retry-After`) · 404 not found · 405/415 wrong method/content type · 409 conflicting update, retry ·
503 search/embedding unavailable · 500 unexpected (message is always generic).

| Method | Path | What it does | Notes |
|---|---|---|---|
| GET | `/api/ping` | Liveness | Public |
| GET | `/api/health/db`, `/api/health/embedding`, `/api/health/search` | What is configured / still placeholder | Login needed; never shows secrets |
| POST | `/api/auth/register` | Sign up `{username, smail, password}` → `{userId, username, smail, sessionToken}` and logs in | Public. 201. username 3-30 (`A-Z a-z 0-9 . _ -`), smail must end `@smail.iitm.ac.in` (not email-verified), password 8-72 bytes. 409 if username/smail taken |
| POST | `/api/auth/login` | `{identifier, password}` (username **or** smail) → `{userId, username, smail, sessionToken}` | Public. 401 `Invalid username or password` for every failure. 429 after 5 failed tries per account (20 per IP) for 15 min |
| GET | `/api/me` | Current user `{userId, username, smail, perTabSessions}` | `username` is null for Google-created accounts. `perTabSessions` = the testing convenience below is on |
| GET | `/api/me/questions?page&size` | My questions, newest first | Profile page |
| GET | `/api/questions?tags=A&tags=B&page&size` | **Homepage discovery**: union of tags, most `+` first | No tags = all. size ≤ 50 |
| POST | `/api/questions` | Create `{title, body, tags:[…]}` | 201. **At least 1 tag, from the fixed set** (400 otherwise) |
| GET | `/api/questions/{id}` | Full question | |
| PUT | `/api/questions/{id}` | Edit `{title, body, tags?[]}` | Author only. Omitted `tags` = unchanged; `[]` is rejected (a question always keeps ≥ 1 tag) |
| GET / PUT | `/api/questions/{id}/tags` | Read / replace tags `{tags:[…]}` | PUT: author only, 1+ tags from the fixed set |
| PUT | `/api/questions/{id}/accepted-answer` | `{answerId}` → question resolved | Question author only |
| POST / GET | `/api/questions/{id}/answers` | Add `{body}` / list | |
| PUT | `/api/answers/{id}` | Edit `{body}` | Answer author only |
| POST / GET | `/api/questions/{id}/comments` | Add `{body}` / nested discussion tree | Tree has `upCount`, `downCount`, `myVote` |
| POST | `/api/comments/{id}/replies` | Reply `{body}` | |
| PUT | `/api/comments/{id}` | Edit `{body}` | Comment author only. No delete exists |
| PUT | `/api/comments/{id}/vote` | `{voteType: UP|DOWN}` — same again removes, other switches | |
| DELETE | `/api/comments/{id}/vote` | Remove my vote | |
| POST / GET | `/api/questions/{id}/interest` | `+` toggle / my state `{interested, interestCount}` | |
| GET | `/api/notifications?unreadOnly&limit` | My notifications, newest first: `{notificationId, type, questionId, questionTitle, commentId, actorId, actorName, isRead, createdAt}` | Enough to render "bob commented on your question 'WiFi…'" without another request |
| GET | `/api/notifications/unread-count` | Bell badge | |
| PUT | `/api/notifications/{id}/read` | Mark read | Recipient only |
| GET | `/api/tags` | The fixed tag set `[{tagId, name}]`: **tech, math, code, others** (in that order) | Nobody can create tags. Names are case-insensitive on input and always returned lowercase |
| GET | `/api/search?q=...&tags=A&tags=B&mode=hybrid` | **Search** → `[{questionId, title, bodyPreview, tags, isResolved, interestCount, relevance, semanticScore, lexicalScore}]` | `mode` = hybrid (default) / semantic / lexical. `q` ≤ 500 chars. 503 until search values are configured |

**Author names:** question, answer, comment and list responses carry `authorName` (the username; `user<id>` for an account without one). An author's smail is never returned.

**Rate limits (429 + `Retry-After`):** search 30/min per user · question create/edit 10/min per user (each makes a paid embedding call) · sign-up 10/hour per IP address. Invalid requests count too. Everything else is unlimited.

Tags in query strings are passed as repeated `tags=` parameters (`?tags=tech&tags=math`). Every tag — when asking, editing, filtering the homepage or searching — must be one of the four; anything else is a 400 `Unknown tag 'x'. Choose from: tech, math, code, others`.

**Time zone: all timestamps are Indian Standard Time and carry the offset**, e.g. `2026-10-04T23:30:59.479+05:30` (error bodies use UTC `Z`). Parse them with any ISO-8601 parser; no guessing needed.

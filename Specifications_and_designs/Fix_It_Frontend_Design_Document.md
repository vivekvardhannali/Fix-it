# Fix It — Frontend Design Document

**Project:** Fix It  
**Scope:** The website the user sees (HTML / CSS / JavaScript), built on the finished backend  
**Look and feel:** An old-school, Stack Overflow–style Q&A site (roughly the 2010–2014 era)  
**Sources:** `Fix_It_Design_Document.md`, `Fix_It_Database_Design.md`, `Fix_It_Backend_Implementation_Roadmap.md`, and the
backend as built (`API_REFERENCE.md`).

> **Update (login method):** for now the site signs users in with **username + password** (sign-up and login forms), not
> Google. Google sign-in is kept in the backend but switched off, so every Google-specific item below (the "Continue with
> Google" button, Google error codes, the dev-only login B3, redirects B2) is replaced by the password forms described in
> Sections 2.5, 4.1 and 8. Everything else is unchanged.

> **Rule carried over from the design document:** the frontend adds **no functionality beyond what the design already
> defines**. Every screen below exists because the design document names it. Section 9 traces each design requirement to a
> phase; Section 1.3 lists what is deliberately *not* built.

---

## 0. Target Frontend Flow

The frontend is built in this order. Every step ends in a checkpoint that must pass before the next step starts.

```text
F0  Foundations (hosting, dev data)
        ↓
F1  Design system + style guide (the "old-school" look, no data)
        ↓
F2  App shell + username/password login + profile dropdown
        ↓
F3  Homepage / question discovery (tags, + ordering)
        ↓
F4  Question page, read-only (question, answers, comments)
        ↓
F5  Search (tags + query, results list)
        ↓
F6  Ask / edit question
        ↓
F7  Answers + accepting the correct answer
        ↓
F8  Comments, replies, edit, up/down voting
        ↓
F9  "+" interest + notification bell
        ↓
F10 Profile → My Questions
        ↓
F11 Errors, empty states, security hardening, accessibility, responsive pass
        ↓
F12 End-to-end test + release checklist
```

**Important:** do not start with search. Build the plain pages that show and create data first (F3–F4, F6–F8); search
(F5) is only a list page on top of the same components.

---

## 1. Principles and Constraints

### 1.1 Technology (from the design document)

| Choice | Decision |
|---|---|
| Languages | HTML, CSS, JavaScript — **no framework and no build step** (design doc §2) |
| JavaScript | Modern browser JS, ES modules (`<script type="module">`), `fetch` for all calls |
| Libraries | None required. (If one is ever added it must be vendored into the repo, not loaded from a CDN.) |
| Pages | Multi-page site: one HTML file per screen, shared CSS and JS modules. Fits the "old-school" feel, works with plain links, back/forward and bookmarks |
| Hosting | **Served by the Spring Boot backend itself** (same origin). This keeps the session cookie, the "CSRF off + SameSite=Lax" decision (D14), and the login redirects simple, and matches "runs as a hosted web application on a department or CFI server" |
| Browsers | Current Chrome, Firefox, Safari, Edge |
| Language of UI | English |

### 1.2 Non-negotiable rules

1. **Escape everything.** Questions, answers and comments are user text. The frontend never inserts user text with
   `innerHTML`; it builds DOM nodes and sets `textContent`. (Section 5.4.)
2. **The backend is the authority.** Hiding an "Edit" button is a convenience only; the backend already rejects
   non-owners with 403. The UI must still show a clear message if that ever happens.
3. **No internal details in the UI.** Show the backend's `message`, or a friendly fallback. Never show status dumps.
4. **Search is never faked.** If the backend says search is unavailable (503) the UI says so and offers the rest of the site.
5. **Comment votes and `+` are separate things** and look different (design §5.4 vs `+`).
6. **No secrets in frontend code.** There is nothing secret on the frontend; Google credentials live only in the backend.

### 1.3 Deliberately NOT built (not in the design)

Users page, Badges, a Tags browsing page, reputation, bounties, "unanswered" tab, answer comments (the design has none),
deleting anything, markdown/rich-text editing, image upload, dark mode, phone push notifications, real-time sockets,
admin screens. If you want any of these, they become a design change first.

---

## 2. Visual Design — "Old-School Q&A"

An original design in the spirit of early Q&A sites. **Do not copy any real site's logo, name or assets.**

### 2.1 Page frame

```text
┌────────────────────────────────────────────────────────────────────────────────┐ ← 3px orange stripe (#F48024)
│  Fix It      [ Questions ]  [ Ask Question ]   [ search…           ] 🔔2  cs23b ▾│  header 50px, light gray
├────────────────────────────────────────────────────────────────────────────────┤
│                                                                                │
│             ┌──────────────────────────── 980px centred ───────────────────┐   │
│             │  main content                                                │   │
│             └──────────────────────────────────────────────────────────────┘   │
│                                                                                │
│   footer: "Fix It — IIT Madras" · plain links, small gray text                 │
└────────────────────────────────────────────────────────────────────────────────┘
```

- Fixed content width **980 px**, centred, white background, no shadows or rounded "cards" (flat, bordered, 1 px lines).
- Below **720 px** wide the layout simply stacks to one column (Section F11).

### 2.2 Design tokens (CSS custom properties, defined once in `css/fixit.css`)

| Token | Value | Use |
|---|---|---|
| `--bg` | `#FFFFFF` | Page |
| `--bg-header` | `#F8F9F9` | Header bar |
| `--accent` | `#F48024` | Header top stripe, selected tab underline, key highlights |
| `--text` | `#242729` | Body text |
| `--muted` | `#6A737C` | Meta text ("asked 3 hours ago") |
| `--link` | `#0077CC` | Links and question titles |
| `--link-hover` | `#005A9C` | Link hover |
| `--border` | `#D6D9DC` | Lines and inputs |
| `--row-border` | `#E4E6E8` | Between list rows |
| `--tag-bg` / `--tag-text` | `#E1ECF4` / `#39739D` | Tag chips |
| `--btn-primary` | `#0095FF` | Primary button (white text) |
| `--ok` | `#5EBA7D` | Resolved / accepted (green) |
| `--ok-dark` | `#2F6F44` | Text on green outline |
| `--sig-bg` | `#D9EAF7` | Author "signature" box |
| `--warn-bg` / `--warn-border` | `#FFF9DB` / `#F3E7A8` | Info message bar |
| `--error-bg` / `--error-border` | `#FDF2F2` / `#E8B4B4` | Error message bar |

Type: `font-family: "Helvetica Neue", Helvetica, Arial, sans-serif`; body **13 px / 1.4**; list title **17 px**; question
page title **27 px**; meta **12 px**. Line-based and flat: 1 px borders, 3 px radius on buttons and tags only. Primary
buttons get the classic subtle top highlight (`box-shadow: inset 0 1px 0 rgba(255,255,255,.4)`).
All text/background pairs must reach WCAG AA contrast (4.5 : 1) — verified in F1, not assumed.

### 2.3 Components

| Component | Spec |
|---|---|
| **Header** | Logo text "Fix It" (own wordmark, no image needed), tabs *Questions* and *Ask Question*, search box, notification bell with unread badge, profile dropdown |
| **Tab** | Gray text; the current page's tab has black text and a 3 px orange bottom border |
| **Question row** | Left two stat boxes, right summary (Section 2.4) |
| **Tag chip** | `--tag-bg` background, `--tag-text` text, 12 px, 3 px radius, 2×6 px padding; link to nothing (tags only filter the homepage via the tag picker) |
| **Stat boxes** | **`+` box**: big number, small label "plus". **Status box**: `✓ resolved` — green outline, becomes solid green when resolved; or `open` — gray outline |
| **Buttons** | Primary (blue), secondary (white, gray border), link-style. Disabled state visibly dimmed |
| **Post** | Left gutter 64 px (the interaction column), body on the right, signature box bottom-right |
| **Signature box** | `--sig-bg`: "asked/answered/edited *3 hours ago*" and the author name (Section 4.3 / B4) |
| **Comment** | 12 px, muted, top dotted border; mini vote arrows on the left; "reply" and "edit" as small links |
| **Message bar** | Full-width strip above content: info (yellow) or error (pink) |
| **Pager** | Numbered links like `Prev 1 2 [3] 4 5 Next`, current page boxed in orange |
| **Empty state** | One plain sentence plus the next action (e.g. "No questions match. **Ask a new question**") |

### 2.4 Question row (homepage, search results, My Questions)

```text
┌─────┬─────────┬──────────────────────────────────────────────────────────────────┐
│  3  │  ✓      │  WiFi keeps disconnecting in hostel                  ← blue title │
│plus │resolved │  my laptop loses the wifi connection every hour in hostel       │
│     │         │  block since last week, started after the update…    ← 2–3 lines │
│     │         │  [networking] [wifi] [hostel]                    asked 3 hrs ago │
└─────┴─────────┴──────────────────────────────────────────────────────────────────┘
```

Shows exactly what the design requires (§1.6): **title, a few body lines, tags, resolved status, `+` count.**

### 2.5 Wireframes

**Login (design §1.4 step 1)**

```text
        ┌──────────────────────────────────────┐
        │                Fix It                │
        │   Ask. Search. Get it fixed.         │
        │                                      │
        │  Username or smail  [____________]   │
        │  Password           [____________]   │
        │  [ Log in ]                          │
        │  ──────────────────────────────────  │
        │  New here?  [ Create an account ]    │
        └──────────────────────────────────────┘
   Sign-up form: Username · IITM smail (@smail.iitm.ac.in) · Password (min 8) · [ Sign up ]
   (error bar above the form: "Invalid username or password", "Too many attempts, try again in N minutes", …)
```

**Homepage (design §3)**

```text
Questions                                                         [ Ask Question ]
Selected tags: [networking ✕] [hostel ✕]   Add tag: [ choose… ▾ ]
 "Questions with any selected tag, most + first"
────────────────────────────────────────────────────────────────────────────────
 [question row] [question row] [question row] …
                                                      Prev 1 [2] 3 Next
```

**Search results (design §1.5 steps 2–8)**

```text
Search results for "wifi disconnecting"        Tags: [networking ✕]
────────────────────────────────────────────────────────────────────────────────
 [question row, most relevant first] …
 ┌──────────────────────────────────────────────────────────────────────────────┐
 │ Not what you were looking for?   [ Ask a new question ]  (title and tags prefilled)
 └──────────────────────────────────────────────────────────────────────────────┘
```

**Question page (design §1.7)**

```text
WiFi keeps disconnecting in hostel                                  [ Ask Question ]
asked 3 hours ago · modified 1 hour ago · ✓ resolved
──────────────────────────────────────────────────────────────────────────────────
 ┌────┐  Full question body, line breaks kept.
 │ +  │
 │ 3  │  [networking] [wifi]
 └────┘                                  ┌────────────────────────┐
         edit                            │ asked 3 hours ago      │
                                         │ cs23b043               │
                                         └────────────────────────┘
 Comments (discussion)
  ▲ 2 ▼  Try changing the DNS.                      — cs23b024, 2 hours ago  reply
        ▲ 0 ▼  Tried that, no luck.                 — cs23b043, 1 hour ago
  [ Add a comment… ]
──────────────────────────────────────────────────────────────────────────────────
 2 Answers
 ┌────┐  Update the router firmware.            (accepted answer shown first)
 │ ✓  │
 └────┘                                  ┌────────────────────────┐
                                         │ answered 1 hour ago    │
                                         │ edited 20 mins ago     │
                                         └────────────────────────┘
 Your Answer
 [ big text box ]                                                   [ Post Your Answer ]
```

The design keeps answers and comments separate (design §1 / DB §14): comments hang on the *question*, never on an
answer, and answers show no comment box.

**Ask question (design §1.7 end, §5.2)**

```text
Ask a question
 Title  [____________________________________________]   (max 255)
 Tags   ( ) tech  ( ) math  ( ) code  ( ) others          (pick 1 to 4 — the only tags that exist)
 Body   [                                                   ]
        [                                                   ]
        [ Post Your Question ]
```

**Bell dropdown (design §4)** and **profile dropdown (design §4.2)**

```text
 🔔 2 ▾                                   cs23b043 ▾
┌──────────────────────────────┐         ┌───────────────────────┐
│ ● Someone commented on your  │         │ User ID: 12           │
│   question "WiFi keeps…"     │         │ cs23b043@smail.iitm…  │
│   2 mins ago                 │         │ ───────────────────── │
│ ○ Someone replied to your    │         │ My Questions          │
│   comment on "Mess food…"    │         │ Log out               │
└──────────────────────────────┘         └───────────────────────┘
```

---

## 3. Site Map and Routes

One HTML file per screen. Routes use query strings so they work as plain static files.

| Screen | File / URL | Needs login (data) | Design source |
|---|---|---|---|
| Login | `/login.html` | no (shell + login button) | §1.4 |
| Homepage / discovery | `/` (`index.html`) `?tags=A&tags=B&page=2` | yes | §3 |
| Search results | `/search.html?q=…&tags=A&tags=B` | yes | §1.5 |
| Question details | `/question.html?id=12` (`#comment-34`, `#answer-5` anchors) | yes | §1.7 |
| Ask a question | `/ask.html` (`?title=…&tags=…` to prefill) | yes | §1.5 step 11 |
| Edit my question | `/ask.html?edit=12` | yes (author) | §5.2 |
| My Questions (profile) | `/profile.html?page=0` | yes | §4.2 |
| Notifications | the bell dropdown (no separate page) | yes | §4 |

The HTML files themselves are public shells (they contain no user data). Every data call goes to `/api/**`; a `401`
from any call sends the browser to `/login.html`.

---

## 4. Screen Specifications

How to read: **Data** = which backend calls feed the screen; **States** = what must be handled.

### 4.1 Login and sign-up — `login.html`
- **Data:** `POST /api/auth/login {identifier, password}` (identifier = username **or** smail) and
  `POST /api/auth/register {username, smail, password}` (also signs the user in). Both set the session cookie; on success go to `/`.
- Client-side checks mirror the backend: username 3-30 chars (letters, digits, `.` `_` `-`), smail ends with `@smail.iitm.ac.in`,
  password 8-72 bytes and not trivially guessable. The backend's `message` is shown if it still rejects.
- **States:** 401 → "Invalid username or password" (the same text for every cause) · 409 → "That username is taken" / "An account with
  that smail already exists" · 429 → "Too many failed attempts. Try again in N minutes" (use the `Retry-After` header) · 400 → the field message.
- If a valid session already exists (`GET /api/me` = 200) go straight to `/`. Password fields use `type="password"` with
  `autocomplete="current-password"` / `"new-password"`.
- **Not verified:** the smail typed at sign-up is not checked by email, so the page must not imply it is proof of identity.

### 4.2 Header (every page except login)
- **Data:** `GET /api/me` (user ID and smail for the dropdown), `GET /api/notifications/unread-count` (badge).
- Search box submits to `/search.html?q=…` (keeping the currently selected tags if any).
- Profile dropdown: **User ID**, own smail, **My Questions**, Log out (`POST /api/logout`, then `/login.html`).
- Bell: Section 4.9.

### 4.3 Author display (see Section 6 / B4)
Every author appears as **`authorName`** (the username; accounts without one show `user<id>`). Show that; `authorId` is only for "is this mine?" checks.

### 4.4 Homepage — `index.html`
- **Data:** `GET /api/tags` (the four tag toggles), `GET /api/questions?tags=…&page=&size=20`.
- Behaviour: selected tags live in the URL (bookmarkable). Adding/removing a tag reloads the list from page 0.
  Union of tags, most `+` first — shown to the user in one sentence under the title.
- **States:** loading, empty ("No questions yet for these tags"), error bar, pager.

### 4.5 Search — `search.html`
- **Data:** `GET /api/search?q=&tags=` (`mode` omitted = hybrid), `GET /api/tags` for the tag chips.
- Shows rows exactly like Section 2.4 in the order given (already thresholded and ranked by the backend). The relevance
  numbers are **not** shown to normal users.
- **States:** empty query → inline message before any request (design §5.5); no results → "No similar questions found" + the
  prominent **Ask a new question** box (prefilled with the typed query as title and the selected tags);
  `503` → "Search is temporarily unavailable. You can still browse questions or ask a new one." with links.
- **Tuning view (hidden):** adding `&debug=1` shows a mode switch (hybrid / semantic / lexical) and the semantic, lexical
  and final scores per row. This exists so the hybrid weight and thresholds can be tuned from real queries.

### 4.6 Question page — `question.html?id=`
- **Data:** `GET /api/questions/{id}`, `GET …/answers`, `GET …/comments`, `GET …/interest` (my `+` state), plus `GET /api/me`
  (to know "is this mine").
- Order on the page: title → status line → question post (with `+` control) → comments (the discussion) → answers → "Your
  answer" form. The accepted answer, if any, is listed **first** among answers.
- Owner-only controls (shown only when `authorId == me`): *edit* the question; *✓ select as correct* on each answer.
  Answer author: *edit* on their own answer. Comment author: *edit* on their own comment.
- Anchors `#comment-<id>` / `#answer-<id>` scroll to and briefly highlight that item (used by notifications).
- **States:** 404 → "This question does not exist." · load error bar · each section loads independently so one failure
  doesn't blank the page.

### 4.7 Ask / edit question — `ask.html`
- **Data:** `GET /api/tags` (the four choices), `POST /api/questions` or `PUT /api/questions/{id}`.
- Fields and rules mirror the backend: title required ≤ 255; body required ≤ 20000; **tags: 1–4, chosen from the fixed set
  tech / math / code / others** (checkboxes or toggle chips fed by `GET /api/tags`; nothing can be typed or created). The design's "Invalid or missing tags" error maps to "choose at least one tag".
- Validation messages appear inline under the field **and** are re-shown from the backend's `message` if it still rejects.
- Edit mode (`?edit=id`): prefilled; only the author may open it (others get "You can only edit your own questions" and a link back).
  After saving, go to the question page.

### 4.8 Answers, discussion and `+` (on `question.html`)
- **Answer:** `POST …/answers`, `PUT /api/answers/{id}` (edit in place), select correct: `PUT …/accepted-answer`.
  Selecting an answer marks the question resolved and updates the status line and green box without reloading.
- **Comment / reply / edit:** `POST …/comments`, `POST /api/comments/{id}/replies`, `PUT /api/comments/{id}`.
  Replies indent under their parent at any depth (limit visual indentation to 6 levels, deeper replies stay at level 6).
  **No delete button anywhere** (design: comments are permanent).
- **Voting:** `PUT /api/comments/{id}/vote` with click semantics — clicking ▲ when ▲ is active removes it; ▲ then ▼ switches.
  The UI updates from the response (`upCount`, `downCount`, `myVote`), never by guessing. Display: ▲ *net* ▼ with the
  up/down breakdown in the tooltip. Votes are display-only (design §5.4).
- **`+`:** `POST …/interest` toggles; the box shows `interestCount` and is highlighted when `interested`.

### 4.9 Notifications — the bell
- **Data:** `GET /api/notifications/unread-count` on every page load and then every **60 s** while the tab is visible
  (no sockets); `GET /api/notifications?limit=10` when the dropdown opens.
- Text (from the design): `COMMENT_ON_QUESTION` → "*<actorName>* commented on your question *<questionTitle>*";
  `REPLY_TO_COMMENT` → "*<actorName>* replied to your comment on *<questionTitle>*". Both fields are in the notification itself (no extra requests).
- Click → `PUT /api/notifications/{id}/read` then go to `question.html?id=<questionId>#comment-<commentId>`.
  Unread items have a dot and bold text.
- **States:** empty ("No notifications"), error ("Could not load notifications").

### 4.10 Profile → My Questions — `profile.html`
- **Data:** `GET /api/me/questions?page=&size=20`. Same question rows and pager as the homepage; empty state offers **Ask Question**.

---

## 5. Frontend Architecture

### 5.1 Repository layout

```text
fix-it/
├── backend/                       (existing)
└── frontend/
    ├── index.html  login.html  search.html  question.html  ask.html  profile.html  styleguide.html
    ├── css/
    │   └── fixit.css              all styling, tokens at the top
    ├── js/
    │   ├── api.js                 the only place that calls fetch()
    │   ├── dom.js                 safe element builder h(), text helpers
    │   ├── time.js                "3 hours ago" formatting
    │   ├── session.js             current user, 401 → login redirect
    │   ├── header.js              builds header, bell, profile dropdown
    │   ├── components/            question-row.js, tag-chip.js, pager.js, message-bar.js, tag-picker.js, post.js, comment-tree.js
    │   └── pages/                 home.js, search.js, question.js, ask.js, profile.js
    ├── img/                       favicon only
    └── test/                      node --test unit tests (pure helpers) and e2e/ (Playwright)
```

How it is served: a Maven resources rule copies `frontend/` into the app's `static/` folder at build time. During
development the `dev` profile points Spring at the `frontend/` folder directly so edits show on browser refresh.

### 5.2 API client (`api.js`)
- One function per backend call; all return parsed JSON or throw `ApiError {status, message}`.
- Sends `credentials: "same-origin"`, `Accept: application/json`; JSON bodies only.
- Central handling: `401` → clear state and go to `/login.html`; `503` and network errors → throw a friendly `ApiError`.
- Never retries automatically, except a single retry on `409` (the backend's "please retry" for racing duplicate clicks).

### 5.3 State and rendering
- No global store. Each page reads its URL, loads data, renders, and re-renders only the affected section after an action.
- After any mutation the page re-renders from the **backend's response** (or re-fetches the section), so the screen
  never shows something the server did not accept.
- Buttons disable while their request is in flight (prevents double-clicks; the backend is also idempotent where it matters).

### 5.4 Safety rules (checked in F11)
1. `h(tag, props, ...children)` builds elements; user text is only ever added as text nodes.
2. `innerHTML`, `outerHTML`, `insertAdjacentHTML`, `document.write` and `eval` are **banned** in `frontend/js/` (a test greps for them).
3. Links built from data use only `question.html?id=<number>`; ids are validated as integers before use.
4. Body text keeps its line breaks via CSS `white-space: pre-wrap`, not by converting to HTML.
5. Backend adds a Content-Security-Policy (B7) so inline scripts cannot run even if a bug slipped through.

### 5.5 Time
Relative text ("asked 3 hours ago", "modified 2 days ago"), absolute time in the hover tooltip, dates older than 30 days
shown as e.g. `Oct 4 '26`. Timestamps arrive with an explicit `+05:30` offset, so plain `new Date(text)` is correct; show absolute times in IST.

---

## 6. Backend Touch-ups the Frontend Depends On

The backend changes the website needed are **all done** (B1, B4, B6, B7); B2/B3 were dropped now that password login exists.

| ID | Change | Why | Status |
|---|---|---|---|
| ~~B1~~ | ~~Serve `frontend/` as static files~~ | **Done:** `frontend/` is packaged into the app and served without login (`/`, `/*.html`, `/css`, `/js`, `/img`); placeholder `index.html`/`login.html`/`css`/`js` exist. `./run-local.sh dev` serves it live from disk | **Done** |
| ~~B2~~ | ~~Same-origin Google redirects~~ | Not needed while Google is off: login is a JSON call and the page navigates itself | **Dropped** |
| ~~B3~~ | ~~Dev-only login~~ | Not needed: real sign-up/login works without Google. (`db/dev_seed.sql` sample data is still worthwhile, with a seed user that has a password hash) | **Dropped** |
| ~~B4~~ | ~~`authorName`~~ | **Done:** question, answer, comment and list responses carry `authorName` (the username, or `user<id>`) | **Done** |
| ~~B5~~ | ~~Timestamps carry no time zone~~ | **Done:** the backend runs on IST and every timestamp carries the `+05:30` offset (decision: keep IST) | **Done** |
| ~~B6~~ | ~~`questionTitle` in notifications~~ | **Done:** notifications carry `questionTitle` and `actorName` | **Done** |
| ~~B7~~ | ~~Security headers~~ | **Done:** `Content-Security-Policy: default-src 'self'; style-src 'self' 'unsafe-inline'; …` plus nosniff and frame denial. **Frontend rule: no inline `<script>`, no `eval`, no CDN** (they would be blocked) | **Done** |

**Dev recipe:** the backend now ships a configured embedding provider (Jina) and provisional search values, so the default
(hybrid) search works as soon as `EMBEDDING_API_KEY` is set (`backend/run-local.sh`). Without a key, set `SEARCH_SEMANTIC_WEIGHT=0` to run
search with lexical ranking only.

---

## 7. Phases and Checkpoints

**Rule at every checkpoint:** BUILD → TEST → VERIFY → PASSED? If not, fix and re-test. Do not build on a broken layer.

---

### Phase F0 — Foundations

**Build**
- B1 from Section 6; the `frontend/` folder with a bare `index.html` and `login.html`.
- `db/dev_seed.sql`: ~5 users, ~10 tags, ~15 questions (some resolved, various `+` counts), answers, nested comments, votes, so
  every later screen has something to show.
- `api.js` skeleton and the unit-test runner (`node --test`).

**Checkpoint F0**
- `/` and `/login.html` load in a browser without logging in; `/api/me` still returns 401 JSON.
- `POST /api/auth/register` then `POST /api/auth/login` from the page create a session and `/api/me` returns the user; a wrong password shows the error bar.
- Editing a file in `frontend/` shows after a browser refresh (dev profile); a normal build packages the files.

**Do not proceed until** the pages are served and a developer can sign up and log in.

---

### Phase F1 — Design system and style guide

**Build**
- `css/fixit.css`: tokens, typography, page frame, header, tabs, buttons, forms, tag chips, stat boxes, question row, post layout,
  signature box, comment block, message bars, pager, empty state, focus styles.
- `styleguide.html`: every component once, in every state (normal, hover, focus, disabled, error, resolved, long text, very long
  unbroken word, 10 tags, empty).

**Checkpoint F1**
- Side by side with the wireframes in Section 2: header, row, post, comment and pager match the spec.
- Contrast check (a checker tool) passes AA for every token pair used for text.
- Keyboard: Tab reaches every interactive element in order with a visible focus ring.
- A 600-character unbroken word and a 255-character title do not break the layout.
- Width 980 px centred; at 600 px it stacks without horizontal scrolling.

**Do not proceed until** the style guide looks right — later phases only reuse these components.

---

### Phase F2 — App shell and authentication

**Build**
- `session.js`, `header.js`: header on every page, profile dropdown (**User ID**, smail, **My Questions**, Log out), bell placeholder.
- Login page with the error messages from Section 4.1; redirect rules (logged in → `/`, not logged in → `/login.html`).
- Logout via `POST /api/logout`.

**Checkpoint F2** (design test: *User & Authentication*)
- Not logged in → any page except login redirects to `/login.html`.
- Sign up with a valid username/smail/password → logged in, header shows the user; profile dropdown shows the User ID and **My Questions**.
- Wrong password, taken username/smail, non-IITM smail, weak password, and 5 wrong attempts (lock-out message) each show the right message.
- Log out → back on the login page; the Back button does not reveal data (pages refetch and get 401).
- Google sign-in is off; nothing on the site mentions it.

---

### Phase F3 — Homepage / question discovery

**Build**
- `question-row`, `tag-chip`, `pager` components; tag picker fed by `/api/tags`; `home.js`.
- URL-driven state (`?tags=…&page=…`).

**Checkpoint F3** (design test: *Homepage*)
- Select tags A and B → every listed question has A **or** B (union, not both), a question with both appears once.
- Order is by `+` count, highest first (check against the seed data).
- Reloading or sharing the URL gives the same list; removing a tag updates the list.
- Empty selection lists all questions; unknown tag shows the empty state; 21+ questions show a working pager.
- Each row shows title, body lines, tags, resolved status and `+` count — nothing else.

---

### Phase F4 — Question page (read-only)

**Build**
- `question.js`: question, tags, status line, `+` count (read-only), answers (accepted first), full comment tree with replies,
  signature boxes, relative times with tooltips, anchors.

**Checkpoint F4** (design tests: *Question Details*, *Question Fields*)
- Opening a row from the homepage shows the **complete** question: title, full body (line breaks kept), tags, author, modified time,
  resolved status, answers with their own modified times, the correct answer when resolved, and the nested discussion.
- A comment containing `<script>alert(1)</script>` or `<img onerror=…>` is shown as plain text and nothing runs.
- `#comment-<id>` scrolls to and highlights that comment. Unknown id → "This question does not exist."
- Answers show **no** comment box (design rule).

---

### Phase F5 — Search

**Build**
- Header search box → `search.html`; result list with the Section 2.4 row; empty/503/"ask a new question" states; the hidden `debug=1` tuning view.

**Checkpoint F5** (design tests: *Search*, *Search Threshold*, *Search Ordering*, *Search Result Display*)
- Query + tags → only relevant results, restricted to the selected tags, in the backend's order; a nonsense query → the empty state with the **Ask a new question** box.
- Empty query → inline message, no request sent.
- Results show only title, body lines, tags, resolved status and `+` count (no scores unless `debug=1`).
- Backend 503 → the "temporarily unavailable" message; the rest of the site keeps working.
- `debug=1` shows mode switch and the three scores, so the hybrid weight can be tuned.
- Clicking a result opens the question page.

---

### Phase F6 — Ask and edit question

**Build**
- `ask.html`, `tag-picker` (the four fixed tags from `/api/tags`, pick 1–4), validation, prefill from `?title=&tags=`, edit mode.

**Checkpoint F6** (design tests: *Question Management*, *Question Fields*; design §5.5)
- Create with title, body and tags → lands on the new question page showing everything correctly (`+ 0`, open, modified time).
- Missing title/body, **no tag chosen**, over-long title → clear inline errors and no request that is known to fail.
- "Ask a new question" from search arrives with the title and tags prefilled.
- Author can edit title, body and tags; another logged-in user opening `?edit=` is refused with a clear message (and the backend's 403 is handled if reached anyway).
- The Edit link appears only on the author's own questions.

---

### Phase F7 — Answers and resolution

**Build**
- Answer form, inline edit of own answer, "✓ select as correct" for the question author, live update of the resolved state.

**Checkpoint F7** (design tests: *Answer*, *Correct Answer / Resolution*)
- User B answers user A's question; the answer appears with its time. B can edit it; A sees no edit link on B's answer.
- A selects it → green ✓ on that answer, listed first, question shows **resolved** (status line and row box after reload).
- A can switch to another answer. B (the answerer) and bystanders never see the select control.
- Empty answer → inline error; a 403 from the backend is shown as a message, not a blank page.

---

### Phase F8 — Comments, replies and voting

**Build**
- `comment-tree`: add comment, reply (nested), edit own comment, up/down votes with click semantics.

**Checkpoint F8** (design tests: *Comments*, *Comment Voting*)
- Add a comment and reply to it → nested display, correct order, correct author and time.
- ▲ → counted; ▲ again → removed; ▲ then ▼ → switched; counts always match what a reload shows.
- Only a comment's author sees *edit*. There is **no** delete control anywhere.
- Voting changes nothing else on the page: `+` count and answer order stay the same (design rule).

---

### Phase F9 — `+` interest and notification bell

**Build**
- The `+` control on the question page (toggle, highlighted when mine). Bell with unread badge, 60-second refresh, dropdown, click-to-open + mark read.

**Checkpoint F9** (design tests: *Notifications*, *Notification Redirection*)
- `+` adds one, clicking again removes it; the count matches on reload and on the homepage ordering.
- User B comments on A's question → A's bell shows 1 within a minute (or on next page load); the text reads "Someone commented on your question …".
- A opens it → goes to the question **and scrolls to that comment**; the notification becomes read; the badge decreases.
- B replies to A's comment → A gets a *reply* notification; own actions create none.
- Polling stops while the tab is hidden.

---

### Phase F10 — Profile → My Questions

**Build**
- `profile.html` list with pager and empty state.

**Checkpoint F10** (design test: *Profile*)
- The dropdown shows the User ID and **My Questions**; the page lists only my questions, newest first, with the same rows as elsewhere.
- A user with no questions sees the empty state with an **Ask Question** button.

---

### Phase F11 — Errors, empty states, hardening, accessibility, responsive

**Build**
- Consistent message bars for every failure in Section 8's table; loading indicators; empty states everywhere.
- Content-Security-Policy (B7); the "no `innerHTML`" test; keyboard and screen-reader pass; narrow-width layout.

**Checkpoint F11** (design §5.5)
- Simulated 400/401/403/404/409/500/503/offline on every action (browser tools or Playwright route interception) → a friendly message each time, no blank screen, no technical text.
- The grep test finds no banned DOM APIs; with the CSP active every page still works with the console free of CSP violations.
- Every form field has a label; all actions work by keyboard; headings are in order; page title changes per screen.
- 360 px-wide screen: no horizontal scrolling, all actions reachable.
- Latest Chrome, Firefox, Safari, Edge: the end-to-end flow (F12) passes in each.

---

### Phase F12 — End-to-end test and release

**Build**
- Playwright end-to-end tests of the full design flow; the release checklist.

**Checkpoint F12 — the frontend is done only when all of these work** (design test: *End-to-end*):

```text
Login → pick tags on homepage → search with tags + query → inspect results → open a question
→ (not satisfied) ask a new question → another user answers and comments → reply → vote
→ the author marks "+" and selects the correct answer → question shows resolved
→ notifications received and opened → the resolved question is found by a later search
```

- [ ] Every row of the traceability table (Section 9) is checked off
- [ ] Style guide still matches after all phases
- [ ] No console errors on any page during the end-to-end run
- [ ] Sign-up verification decided (Google back on, or email verification) before real users
- [ ] Search verified against the real embedding provider with the tuned weight/thresholds (until then, lexical-only)
- [ ] Production setup: HTTPS, `Secure` cookie flag, placeholders replaced, provisional search values tuned

---

## 8. Error Handling Matrix (design §5.5)

| Situation | Backend | What the user sees |
|---|---|---|
| Wrong username / password | 401 | "Invalid username or password" (same for every cause) |
| Too many failed logins | 429 + `Retry-After` | "Too many failed attempts. Try again in N minutes." |
| Non-IITM smail / weak password / bad username at sign-up | 400 | The backend's message under the form |
| Username or smail already used | 409 | "That username is taken" / "An account with that smail already exists" |
| Session ended | 401 | Redirect to login (message "Please sign in again" on arrival) |
| Missing title / body / tags | client check, backend 400 | Inline message under the field |
| Edit something not yours | 403 | Bar: "You can only edit your own content." |
| Question / answer not found | 404 | "This question does not exist." / refresh the section |
| Empty search query | client check, backend 400 | Inline: "Type something to search." |
| Search unavailable | 503 | "Search is temporarily unavailable…" with links to browse/ask |
| Double-click race | 409 | Silent single retry, then "Please try again." |
| Anything else / offline | 500 / network | "Something went wrong. Please try again." |

---

## 9. Traceability — Design Document → Frontend

| Design requirement | Where built | Checkpoint |
|---|---|---|
| Login page and the allowed-account (IITM smail) rule (§1.4) — now username/password sign-up + login | F2 | F2 |
| Homepage: union of selected tags, sorted by `+` (§3) | F3 | F3 |
| Search within selected tags, threshold, order (§1.5) | F5 | F5 |
| Result shows title, few body lines, tags, resolved, `+` (§1.6) | F1 (row), F3, F5 | F3, F5 |
| Full question view: title, body, tags, author, modified time, status, answers + times, correct answer, discussion, comment vote controls (§1.7) | F4 | F4 |
| Raise a new question if unsatisfied; fields tags/title/body (§1.5 step 11, §1.7) | F5 (CTA), F6 | F5, F6 |
| Create / view / edit own question; title, body, tags; only author edits (§5.2) | F6 | F6 |
| Add / view / edit own answer; author selects correct answer (§5.3, §2.2) | F7 | F7 |
| Comments, replies, edit own, up/down, repeat removes vote (§5.4) | F8 | F8 |
| Votes do not affect ranking (§1.3, §5.4) | F8 (UI separation) | F8 |
| `+` count and interest (§1.2, DB §10) | F9 | F9 |
| Notifications via bell; click opens question/comment (§4) | F9 | F9 |
| Profile dropdown: User ID, My Questions; list (§4.2) | F2, F10 | F2, F10 |
| Clear errors without internals (§5.5) | F11 | F11 |
| Old-school Stack Overflow–like list/question pages (§1.5 step 8) | F1 | F1 |
| End-to-end workflow (§6, §7) | F12 | F12 |

---

## 10. Test Strategy

| Layer | Tool | What it covers |
|---|---|---|
| Pure helpers | Node's built-in `node --test` (no install needed) | `time.js`, `dom.js` escaping, URL/tag handling, vote-state logic, the banned-API grep |
| Browser end to end | Playwright against the backend in `dev` profile with seed data | Each phase's checkpoint scripted; the F12 full flow; failure injection by intercepting `/api/**` responses |
| Visual | `styleguide.html` plus screenshots compared by eye to Section 2 | F1 and after every CSS change |
| Contract | Backend's own 164 tests + `API_REFERENCE.md` | The API is already verified; frontend tests assume it |

Each phase's checkpoint list above is both the manual checklist and the list of automated tests to write for that phase.

---

## 11. Risks and Open Questions

| # | Item | Impact | Suggested handling |
|---|---|---|---|
| R1 | ~~Author names not exposed~~ | Fixed: `authorName` everywhere (username, or `user<id>`) | Done |
| R2 | ~~Time zones~~ **Decided: IST.** Every timestamp the API returns ends in `+05:30` | None — parse as ISO-8601 and format in the viewer's zone or IST | Done in the backend |
| R3 | **Google sign-in is switched off**; the smail typed at sign-up is **not verified** | Anyone can sign up as any untaken IITM smail | Fine for development; turn Google on (or add email verification) before real use |
| R4 | **No embedding provider yet** | Semantic search unavailable | Dev recipe uses lexical-only hybrid (weight 0); UI handles 503 |
| R5 | ~~Notification text lacked titles / names~~ | Fixed: notifications carry `questionTitle` and `actorName` | Done |
| R6 | CSRF protection relies on same-origin hosting (D14) | Breaks if frontend moves to another site | Keep same-origin; otherwise add CORS + CSRF tokens first |
| R7 | Old-school fixed 980 px layout on phones | Cramped | Single-column stack below 720 px (F11) |
| R8 | Plain-text bodies only | No code blocks/formatting | Matches the backend (no markdown); a later design change if wanted |

---

## 13. Build Status and What Differs From This Document

**All phases F0-F12 are built and passed their checkpoints** (2026-10-05): 60 unit tests and 75 browser tests (real Chrome, real backend)
pass; the complete workflow of F12 runs through the screens with three people.

Deliberate differences from the text above:

| Item | Document said | Built | Why |
|---|---|---|---|
| Button / tag / resolved colours | `#0095FF`, `#39739D`, `#5EBA7D` | `#0077CC`, `#2D6A94`, `#2A7048` (+ `--muted-strong` `#55606A`, `--input-border` `#8C959D`) | The first values failed WCAG AA contrast (3.1:1, 4.3:1, 2.4:1); `test/unit/contrast.test.mjs` now guards every pair |
| Resolved status box | "green outline, solid when resolved" | open = gray outline with `?`, resolved = solid green with `✓` | Clearer reading of the same idea |
| Homepage tag choice | "Selected tags [x] + Add tag dropdown" | The four tags as toggle buttons (pressed = selected) | Only four tags exist, so a dropdown adds nothing |
| Sample data | `db/dev_seed.sql` | `frontend/test/seed.mjs` (uses the real API; password hashes need no SQL) | Same purpose, no hand-made password hashes |
| Dev-only login (B3), Google redirects (B2) | required | not needed | Username/password login exists |
| Question-row titles | h3 | h2 | Heading levels must not skip (checked on every page) |

**Release checklist (F12)**
- [x] Every phase checkpoint passed (browser tests per phase: `test/e2e/f0` ... `f12`)
- [x] Style guide matches (`styleguide.html`; colours contrast-tested)
- [x] No console or CSP errors on any page during the run
- [x] Error sweep: every action x (400, 401, 403, 404, 409, 429, 500, 502, 503, offline) shows a friendly message, never technical text
- [x] Accessibility basics on every page: one h1, ordered headings, every field labelled, distinct titles, skip link, focus ring, keyboard-only use
- [x] 360px width: no sideways scrolling on any page
- [x] Pages are packaged into the app (`mvn package`) and test tooling is excluded
- [ ] **Not verified:** Firefox and Safari (only Chrome is installed for the test runner); the live Jina semantic search through the screens (tests run lexical-only, no paid calls); a real phone
- [ ] **Not applicable now:** real Google sign-in (parked); sign-up smail verification (decision D19)
- [ ] Production setup: HTTPS + `Secure` cookie, a dedicated database user, rotate the Jina key, tune the search values

---

## 12. Checkpoint Philosophy

```text
BUILD → TEST → VERIFY → CHECKPOINT PASSED?
                            │
                            ├── NO → FIX → TEST AGAIN
                            └── YES → NEXT PHASE
```

The most important checkpoints: **F0** (pages served, sign-up and login work) · **F1** (the look is right) · **F4** (a question displays
safely and completely) · **F6** (create/edit works with ownership) · **F8** (discussion works) · **F11** (errors, safety,
accessibility) · **F12** (the whole flow works).

Only after **F12** is the project ready for real users.

# Fix It — UI Migration (Stack Overflow look → modern workspace look)

**Scope:** visual redesign of `frontend/` only. **No functional change, no backend change.**
**Companion to:** `Fix_It_Frontend_Design_Document.md` (left unchanged; this document supersedes only its Section 2 "Visual Design").

---

## 1. Why

The first frontend deliberately copied the 2010–2014 Stack Overflow look (orange stripe, gray tabs, 13px Helvetica, stat
boxes, blue signature box). The goal now is an original, modern design. Every page, URL, API call, validation rule, error
message, element id and accessibility property stays exactly as it is.

## 2. Invariants (checked at the end of every phase)

| # | Invariant | How it is checked |
|---|---|---|
| I1 | Backend untouched (`backend/` has no diff) | `git status backend/` |
| I2 | No change to `api.js`, `session.js`, `validate.js`, `util.js`, `time.js`, `highlight.js`, or to the logic in `form.js` | `git diff --stat` |
| I3 | Every element `id` and ARIA hook the scripts and tests rely on still exists (`#plus-button`, `#bell-button`, `#notice-panel`, `#profile-button`, `#logout-button`, `#login-form`, `#signup-form`, `#question-form`, `#answer-text`, `#add-comment-button`, `#comment-N`, `#answer-N`, `#status-line`, `#result-count`, …) | e2e script |
| I4 | CSP-safe: no inline scripts, no CDN/web fonts, all assets local | e2e fails on any console/CSP error |
| I5 | No `innerHTML` / `outerHTML` / `insertAdjacentHTML` / `document.write` / `eval` in `frontend/js` | grep |
| I6 | WCAG AA: text pairs ≥ 4.5:1, visible focus ring, skip link, one `h1` per page, labelled fields | contrast script + e2e |
| I7 | No horizontal scrolling at 360px | e2e at 360px |
| I8 | Nothing is committed, pushed or stashed; all changes stay local | `git status` / `git log` |

## 3. Design language

| Aspect | Before | After |
|---|---|---|
| Type | Helvetica 13px | System UI stack, 15px base, scale 12 / 13 / 15 / 17 / 20 / 28 |
| Surfaces | Flat white, 1px lines | Neutral page bg `#f6f7fb`, white cards, 12px radius, hairline border, soft shadow |
| Brand | Orange stripe `#f48024` | Indigo primary `#4f46e5` (AA on white), indigo→violet gradient logo mark |
| Header | Gray bar, underlined tabs | Sticky translucent bar, pill nav, search field with icon, avatar with initials |
| Question row | Two stat boxes + text | Card: status pill + tags → title → excerpt → footer (`+N`, author, time) |
| Question page | SO gutter + blue signature box | Question card with author row, `+` pill in the card footer, answer cards, accepted answer gets an emerald border and badge |
| Comments | Dotted rows | Thread with guide lines, compact ▲ score ▼ vote pill |
| Login | Single bordered box | Split layout: brand panel + form card (stacks on mobile) |
| Alerts / empty states | Yellow and pink strips | Rounded alerts; centred empty-state cards with an icon |

Light theme only (dark mode is listed as "not built" in the frontend design doc §1.3). Colours are tokens on `:root`, so a theme
can be added later without touching components.

## 4. Implementation workflow

Every phase follows the same gate:

```text
ENTRY CRITERIA MET? ──no──► finish the previous phase
      │yes
      ▼
   BUILD  ──►  CHECK (phase exit criteria + invariants I1–I8)  ──fail──► FIX ──┐
                         │pass                                               │
                         ▼                                       ◄───────────┘
                  PHASE DONE → next phase
```

### M0 — Baseline (before any change)
- **Entry:** clean working tree on `feature/new-ui`; Postgres running; Chrome installed.
- **Work:** create an isolated database `fix_it_ui_e2e` from `backend/db/schema.sql` (the dev DB `fix_it` is not touched). Run the
  backend in the `dev` profile against it, lexical-only search (`SEARCH_SEMANTIC_WEIGHT=0`, no embedding key). Write an e2e
  script (puppeteer-core in a scratch folder, outside the repo) that covers the full design flow (§5). Run it on the **old** UI.
  Run `mvn test`.
- **Exit:** the e2e script passes on the old UI; baseline screenshots taken; the backend test result recorded.

### M1 — Design tokens and base components
- **Entry:** M0 done.
- **Work:** rewrite `css/fixit.css`: tokens, reset, typography, buttons, form fields, tags, alerts, pager, empty/loading states.
  Update the token list in `js/pages/styleguide.js`. Run the contrast check over every text pair.
- **Exit:** `styleguide.html` renders every component cleanly; all text pairs ≥ 4.5:1.

### M2 — App shell: header, footer, login
- **Entry:** M1 done.
- **Work:** `js/header.js` (logo mark, search icon, avatar initials; menus, ids and polling unchanged), footer, `login.html` brand
  panel, login card styling. New icons in `js/components/icons.js` (built with `createElementNS`).
- **Exit:** sign-up, login, the "already logged in" notice, logout, the bell dropdown and the profile dropdown all work; the
  header wraps cleanly at 360px.

### M3 — Lists: home, search, My Questions
- **Entry:** M2 done.
- **Work:** `components/question-row.js` → card layout; tag filter pills; page heads with subtitles; empty/loading states; pager;
  search CTA box and the `debug=1` tuning view.
- **Exit:** tag filtering, paging, search with tags, the "Ask a new question" CTA (pre-filled title/tags) and the 503 message all behave as before.

### M4 — Question page
- **Entry:** M3 done.
- **Work:** `components/post.js` (card with header/body/footer, author row instead of the signature box, `+` pill),
  `components/answers.js` (accepted badge and border, accept button), `components/comment-tree.js` (thread lines, vote pill),
  `pages/question.js` layout.
- **Exit:** `+` toggle, comment / reply / edit / vote, answer / edit / accept, the resolved status and highlight-from-notification all work for
  both the question's author and other users.

### M5 — Ask/edit form and polish
- **Entry:** M4 done.
- **Work:** ask/edit form card, tag picker chips, counter; responsive pass at 360 / 768 / 1280px; focus states, reduced motion;
  favicon; style guide refreshed with the new components.
- **Exit:** no layout breakage at any width; the style guide matches the pages.

### M6 — End-to-end verification and release
- **Entry:** M1–M5 done.
- **Work:** re-run the **unchanged** M0 e2e script on the new UI at 1280px and 360px; screenshots of every page; `mvn test`;
  banned-API grep; `git status` review. Then stop the server and drop `fix_it_ui_e2e`.
- **Exit:** every check in §5 passes, with no console or CSP errors; the only changes are under `frontend/` plus this document.

## 5. End-to-end checklist (run in M0 and M6)

```text
Sign up A and B (separate browser contexts) → A asks a question (title, body, tags)
→ home: tag filter narrows the list; pager → search with query + tag finds it
→ B opens it → presses "+" (count goes up, pressed) → comments → answers
→ A replies to B's comment, votes on it, edits the question
→ A accepts B's answer → status shows resolved; the answer is marked accepted and listed first
→ B's bell shows an unread notification → clicking it opens the question and the comment
→ A's My Questions lists the question → profile menu shows the User ID → logout returns to login
```

Plus: no console errors or failed requests on any page; one `h1` per page; no sideways scroll at 360px; validation
messages still appear (empty question, wrong password).

## 6. Status

| Phase | Status | Notes |
|---|---|---|
| M0 | done | e2e passed on the old UI; baseline screenshots taken. Backend on an empty DB: 252 tests, 249 pass, 2 skipped, 1 fail (`DatabaseConnectionTest` asserts the DB is literally named `fix_it`; expected when the run uses an isolated DB). The backend suite needs an **empty** database: on one that already has data, about 30 tests fail. |
| M1 | done | New tokens and base components in `css/fixit.css`; 32 text/background pairs checked, all ≥ 4.5:1 (input edge ≥ 3:1) |
| M2 | done | Header (logo mark, pill nav, search icon, avatar menu), footer, split login page. New `components/avatar.js` and icons |
| M3 | done | Question cards with status pill, tags, `+N`, author row; tag filter toolbar; empty/loading states; pager pills |
| M4 | done | Question card, `+` pill, answer cards with the "Accepted solution" badge, accept button, threaded comments with a vote pill |
| M5 | done | Ask/edit form card with a tips aside; responsive pass at 360 and 1280px; reduced motion; style guide and favicon refreshed |
| M6 | done | The same e2e script passes on the new UI at 1280 and 360px, with no console, CSP or HTTP errors, one h1 per page and no sideways scroll. The backend suite result is identical to M0. Banned-API grep is clean. `backend/`, `api.js` and the logic modules are unchanged. Nothing committed |

### Fixed along the way
- `main.container` vertical padding: before, `.container` overrode `main`'s padding, so content sat flush under the header (this was already wrong in the old CSS).
- Header search field: the generic `input[type=search]` rule overrode the field's icon padding; the header rule is now more specific.

### Still open / not verified
- Only Chrome was tested (no Firefox or Safari); live Jina semantic search was not exercised (the runs used lexical-only search, which goes through the same UI path).
- Dark mode was not built; it is out of scope per the frontend design doc §1.3. All colours are tokens, so it can be added later.

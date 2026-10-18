# Fix It: Prompt Sets


## Prompt 1 — Backend: Phases 1–2 of the Backend Implementation Roadmap

**Objective / Instruction:** Implement Phases 1–2 of the Backend
Implementation Roadmap.

**Repository Context:** Inspect the existing repository and refer to the
roadmap for setup requirements.

**Constraints / Invariants:** Follow the specified Java/Maven/Spring
Boot stack. Keep credentials secure. Do not implement business features.

**Examples / Edge Cases:** Handle database connection failures, invalid
credentials, and missing pgvector support.

**Implementation Approach:** Implement setup step by step, briefly
explain decisions, and fix issues before proceeding.

**Acceptance Criteria:** Backend builds and starts, the test endpoint
works, PostgreSQL connects, and pgvector is available.

**Verification / Reporting:** Run build/tests, report changed files,
actual results, and unresolved issues.

––––––––––––––––––––––––––––––––––––

## Prompt 2 — Backend: Phases 3–4 using the Backend Implementation Roadmap and Database Design as references

**Objective / Instruction:** Implement Phases 3–4 using the Backend
Implementation Roadmap and Database Design as references.

**Repository Context:** Inspect existing schema files, entities,
repositories, configuration, and tests.

**Constraints / Invariants:** Preserve all database design rules and
relationships. Do not introduce prohibited tables or features.

**Examples / Edge Cases:** Verify tags, accepted answers, nested
comments, votes, interests, notifications, and foreign-key constraints.

**Implementation Approach:** Map relationships before implementation,
explain key decisions briefly, and resolve persistence issues
systematically.

**Acceptance Criteria:** All nine tables and JPA mappings work
correctly; users and questions can be read, and questions can be created
and retrieved.

**Verification / Reporting:** Run build/tests, verify database
operations, and report changed files, actual results, and unresolved
issues.

––––––––––––––––––––––––––––––––––––

## Prompt 3 — Backend: Phase 5 of the Backend Implementation Roadmap

**Objective / Instruction:** Implement Phase 5 of the Backend
Implementation Roadmap.

**Repository Context:** Inspect existing authentication and user
modules; refer to the roadmap and Database Design.

**Constraints / Invariants:** Use Google Sign-In with IITM smail
validation. Do not implement username/password authentication or store
passwords.

**Examples / Edge Cases:** Test existing/new users, non-IITM identities,
failed callbacks, and unauthenticated requests.

**Implementation Approach:** Inspect the current flow, implement
authentication systematically, and briefly explain key decisions.

**Acceptance Criteria:** Google login, smail validation, user
creation/retrieval, and session protection work.

**Verification / Reporting:** Test successful and failed authentication
flows. Report changed files, actual results, and unresolved issues.

––––––––––––––––––––––––––––––––––––

## Prompt 4 — Backend: Phases 6–9 of the Backend Implementation Roadmap

**Objective / Instruction:** Implement Phases 6–9 of the Backend
Implementation Roadmap.

**Repository Context:** Inspect existing modules and refer to the
roadmap and Database Design for requirements.

**Constraints / Invariants:** Enforce ownership checks. Preserve
question-tag relationships and keep answers separate from comments.

**Examples / Edge Cases:** Test missing questions, multiple tags,
unauthorized edits, and accepting an answer belonging to another
question.

**Implementation Approach:** Implement each phase in order and briefly
explain key decisions.

**Acceptance Criteria:** Question CRUD, tag management, answer
management, and accepted-answer resolution work correctly.

**Verification / Reporting:** Run relevant tests and report changed
files, actual results, and unresolved issues.

––––––––––––––––––––––––––––––––––––

## Prompt 5 — Backend: Phases 10–13 of the Backend Implementation Roadmap

**Objective / Instruction:** Implement Phases 10–13 of the Backend
Implementation Roadmap.

**Repository Context:** Inspect existing modules and refer to the
roadmap and Database Design.

**Constraints / Invariants:** Comments belong only to questions and
cannot be deleted. Comment votes must not affect search ranking or
question interest counts.

**Examples / Edge Cases:** Test nested replies, vote removal/switching,
duplicate interests, correct interest counts, and notification
recipients.

**Implementation Approach:** Implement each feature systematically,
preserving existing relationships and briefly explaining key decisions.

**Acceptance Criteria:** Comments, replies, voting, question interest
toggling, and notifications work correctly.

**Verification / Reporting:** Test normal and edge cases. Report changed
files, actual results, and unresolved issues.

––––––––––––––––––––––––––––––––––––

## Prompt 6 — Backend: Phases 14–15 of the Backend Implementation Roadmap

**Objective / Instruction:** Implement Phases 14–15 of the Backend
Implementation Roadmap.

**Repository Context:** Inspect existing question, database, and
search-related modules. Refer to the roadmap for embedding requirements.

**Constraints / Invariants:** Keep the embedding provider configurable.
Do not assume a model, API, key, or dimension before these are supplied.
Never hard-code secrets.

**Examples / Edge Cases:** Handle unavailable APIs, invalid vectors,
dimension mismatches, and question updates.

**Implementation Approach:** Build the provider abstraction first;
complete API integration only when details are available. Briefly
explain key decisions.

**Acceptance Criteria:** The integration supports generating,
validating, and storing embeddings, including regenerating them when
searchable question content changes. If provider details are
unavailable, retain the placeholder and do not claim completion.

**Verification / Reporting:** Run relevant tests and report changed
files, actual results, and unresolved dependencies.

––––––––––––––––––––––––––––––––––––

## Prompt 7 — Backend: Phase 16 of the Backend Implementation Roadmap

**Objective / Instruction:** Implement Phase 16 of the Backend
Implementation Roadmap.

**Repository Context:** Inspect the embedding service, question
repository, and search modules. Refer to the roadmap and Database
Design.

**Constraints / Invariants:** Respect selected-tag filtering,
configurable relevance thresholds, and descending similarity ranking.

**Examples / Edge Cases:** Test similar, unrelated, duplicate, and
no-match queries.

**Implementation Approach:** Inspect the existing implementation,
implement the search flow, and briefly explain key decisions.

**Acceptance Criteria:** Return structured question results with
relevant metadata; exclude below-threshold results and rank relevant
matches correctly.

**Verification / Reporting:** Run search tests and report changed files,
actual results, and unresolved issues.

––––––––––––––––––––––––––––––––––––

## Prompt 8 — Backend: Phases 17–18 of the roadmap, adding lexical/BM25 and hybrid ranking only if selected for the project

**Objective / Instruction:** Implement Phases 17–18 of the roadmap,
adding lexical/BM25 and hybrid ranking only if selected for the project.

**Repository Context:** Inspect the semantic search implementation and
refer to the roadmap and existing search configuration.

**Constraints / Invariants:** Keep search methods independently testable
and ranking weights configurable. Do not include comment votes in
relevance scores.

**Examples / Edge Cases:** Test individual search methods, different
weights, threshold filtering, and result ordering.

**Implementation Approach:** Implement and verify each selected method
independently before combining them.

**Acceptance Criteria:** Selected ranking methods produce correct scores
and descending results with configurable weights and thresholds.

**Verification / Reporting:** Run relevant tests and report changed
files, actual results, and unresolved decisions.

––––––––––––––––––––––––––––––––––––

## Prompt 9 — Backend: Phases 19–20 of the roadmap: expose the search API and build homepage question discovery

**Objective / Instruction:** Implement Phases 19–20 of the roadmap:
expose the search API and build homepage question discovery.

**Repository Context:** Inspect existing search services, controllers,
question/tag repositories, and Database Design.

**Constraints / Invariants:** Return structured API responses, not HTML.
Keep homepage discovery separate from search ranking.

**Examples / Edge Cases:** Test empty queries, selected tags, multiple
matching tags, and questions with equal interest counts.

**Implementation Approach:** Reuse existing services and implement each
API systematically.

**Acceptance Criteria:** Search returns correctly filtered and ranked
results. Homepage discovery returns the union of questions matching
selected tags, ordered by `interest_count` descending.

**Verification / Reporting:** Test both APIs and report changed files,
actual results, and unresolved issues.

––––––––––––––––––––––––––––––––––––

## Prompt 10 — Backend: Phases 21–22 of the roadmap by auditing authorization and standardizing backend error handling

**Objective / Instruction:** Implement Phases 21–22 of the roadmap by
auditing authorization and standardizing backend error handling.

**Repository Context:** Inspect all controllers, services, security
configuration, and relevant database relationships. Refer to the
Database Design.

**Constraints / Invariants:** Enforce ownership and authentication
checks on the backend. Preserve existing valid behaviour and avoid
exposing internal errors.

**Examples / Edge Cases:** Test unauthorized edits, unauthenticated
requests, missing resources, invalid inputs, and invalid accepted
answers or votes.

**Implementation Approach:** Audit each protected operation, fix gaps,
and briefly explain important changes.

**Acceptance Criteria:** Protected operations enforce authorization and
APIs return consistent, appropriate error responses.

**Verification / Reporting:** Run authorization and error-handling
tests; report changed files, actual results, and remaining
vulnerabilities or issues.

––––––––––––––––––––––––––––––––––––

## Prompt 11 — Backend: Phase 23 by verifying the entire backend workflow against the Backend Implementation Roadmap

**Objective / Instruction:** Complete Phase 23 by verifying the entire
backend workflow against the Backend Implementation Roadmap.

**Repository Context:** Inspect existing automated tests and all
implemented backend modules. Use the Database Design to validate
relational behaviour.

**Constraints / Invariants:** Do not skip earlier checkpoints or modify
intended behaviour merely to make tests pass.

**Examples / Edge Cases:** Test the complete
user-to-question-to-search-to-answer/comment-to-resolution/notification
workflow, including authorization failures.

**Implementation Approach:** Execute the workflow systematically,
identify failures, fix them, and rerun affected tests.

**Acceptance Criteria:** All applicable checkpoints pass, including
database connectivity, authentication, question management,
interactions, embeddings, selected search methods, discovery,
authorization, and error handling.

**Verification / Reporting:** Run the full build and test suite. Report
actual results, changed files, failed tests, and unresolved issues.
Declare the backend ready for frontend development only when the
required checkpoints pass.

Building Frontend:

––––––––––––––––––––––––––––––––––––

## Prompt 12 — Frontend: frontend phases F0–F5 from the Frontend Design Document

**Objective / Instruction:** Implement frontend phases F0–F5 from the
Frontend Design Document.

**Repository Context:** Inspect the existing frontend/backend structure
and API reference. Use the Frontend Design Document as the primary
specification and consult the original Design Document and Database
Design where needed.

**Constraints / Invariants:** Use plain HTML, CSS, and JavaScript
without frameworks or build steps. Follow the specified old-school Q&A
design, multi-page structure, same-origin hosting, and username/password
authentication. Do not invent features.

**Examples / Edge Cases:** Handle login failures, empty question lists,
unavailable search, and responsive layouts.

**Implementation Approach:** Implement foundations, styling,
authentication, homepage discovery, read-only question pages, and search
in the specified order. Briefly explain key decisions.

**Acceptance Criteria:** Shared layout and styling work; login/signup,
homepage, question display, and search pages function using the
documented APIs.

**Verification / Reporting:** Test each checkpoint, report changed
files, actual results, and unresolved issues.

––––––––––––––––––––––––––––––––––––

## Prompt 13 — Frontend: frontend phases F6–F12 from the Frontend Design Document

**Objective / Instruction:** Implement frontend phases F6–F12 from the
Frontend Design Document.

**Repository Context:** Inspect the existing pages, shared
JavaScript/CSS, and `API_REFERENCE.md`. Refer to the Design Document and
Database Design for expected behaviour.

**Constraints / Invariants:** Preserve question/answer/comment
separation, enforce UI ownership rules while relying on backend
authorization, safely render user content using DOM APIs and
`textContent`, and never expose internal errors or secrets.

**Examples / Edge Cases:** Test unauthorized edits, nested replies, vote
toggling, interest toggling, empty notifications, failed API requests,
and unavailable resources.

**Implementation Approach:** Implement question editing, answers,
comments, voting, interests, notifications, My Questions, error states,
accessibility, and responsive improvements in the specified order.

**Acceptance Criteria:** All specified interactions work through the
backend APIs, security and accessibility requirements are met, and the
complete frontend workflow passes.

**Verification / Reporting:** Run automated and end-to-end tests, report
changed files, actual results, remaining issues, and release readiness.

––––––––––––––––––––––––––––––––––––

## Prompt 14 — Frontend: frontend phases F0–F5 and Sections 3, 4.1–4.6 from `Fix_It_Frontend_Design_Document.md`

**Objective / Instruction:**

Implement frontend phases **F0–F5** and Sections **3, 4.1–4.6** from
`Fix_It_Frontend_Design_Document.md`.

**Repository Context:**

Inspect the existing repository and follow
`Fix_It_Frontend_Design_Document.md` as the primary specification. Refer
to `API_REFERENCE.md`, `Fix_It_Design_Document.md`,
`Fix_It_Database_Design.md`, and
`Fix_It_Backend_Implementation_Roadmap.md` wherever necessary.

**Constraints / Invariants:**

-   Follow the design document's routes, APIs, layouts, and behaviours.

-   Use plain HTML, CSS, and JavaScript; no frontend frameworks or build
    tools.

-   Use username/password authentication as specified.

-   All application data must come from `/api/**`; redirect to
    `/login.html` on `401`.

-   Safely render user-generated content using DOM APIs and
    `textContent`.

-   Do not introduce features outside the specification.

**Examples / Edge Cases:**

Handle authentication errors, validation, empty states, loading states,
pagination, unavailable search (`503`), missing questions (`404`), and
URL-based tag/query state as documented.

**Implementation Approach:**

Implement shared utilities and authentication first, followed by the
header, homepage, search page, and read-only question page. Reuse
components and verify each stage before proceeding.

**Acceptance Criteria:**

All routes and features specified in Sections 3 and 4.1–4.6 work with
the documented backend APIs, including search tuning through `debug=1`.

**Verification / Reporting:**

Run relevant tests and Playwright checks if available. Report changed
files, actual test results, and remaining issues. Do not claim
unexecuted tests passed.

––––––––––––––––––––––––––––––––––––

## Prompt 15 — Frontend: frontend phases F6–F12 and Sections 4.7–4.10 from `Fix_It_Frontend_Design_Document.md`, including final integration and verification

**Objective / Instruction:**

Implement frontend phases **F6–F12** and Sections **4.7–4.10** from
`Fix_It_Frontend_Design_Document.md`, including final integration and
verification.

**Repository Context:**

Inspect the current implementation and reuse the components built in the
previous phase. Follow `Fix_It_Frontend_Design_Document.md` and consult
`API_REFERENCE.md`, `Fix_It_Design_Document.md`,
`Fix_It_Database_Design.md`, and
`Fix_It_Backend_Implementation_Roadmap.md` as needed.

**Constraints / Invariants:**

-   Follow documented API contracts and existing project conventions.

-   Keep questions, answers, comments, votes, and question interests
    distinct.

-   Comments cannot be deleted; do not add answer comments.

-   The backend remains authoritative for permissions and data.

-   Render user content safely; redirect on `401`.

-   Do not invent endpoints, response fields, or features.

**Examples / Edge Cases:**

Handle validation failures, unauthorized edits, nested replies, vote
toggling, interest counts, notification errors, empty profiles, and
failed API requests as specified in the design document.

**Implementation Approach:**

Implement question creation/editing, answers, comments/replies, voting,
`+` interest, notifications, and My Questions. Finish with
accessibility, responsive behaviour, and end-to-end verification.

**Acceptance Criteria:**

All functionality in Sections 4.7–4.10 works with the documented APIs
and preserves the specified UI behaviour.

**Verification / Reporting:**

Run available tests and Playwright checks. Report modified files, actual
test results, API mismatches, remaining issues, and release readiness.

––––––––––––––––––––––––––––––––––––

## Prompt 16 — Frontend: Sections 5.1–5.5 of `Fix_It_Frontend_Design_Document.md`

**Objective / Instruction:**

Implement **Sections 5.1–5.5** of `Fix_It_Frontend_Design_Document.md`.

**Repository Context:**

Inspect the existing repository and follow the design document for the
frontend structure, API client, rendering, safety, and time formatting.

**Constraints / Invariants:**

-   Follow the specified `frontend/` directory structure and plain
    HTML/CSS/JS architecture.

-   Centralize all `fetch()` calls in `js/api.js`.

-   Implement shared DOM helpers, session handling, header, components,
    and time utilities.

-   Follow the documented authentication, error-handling, and
    backend-response rules.

-   Never use `innerHTML`, `outerHTML`, `insertAdjacentHTML`,
    `document.write`, or `eval` in `frontend/js/`.

-   No inline scripts or CDN dependencies.

**Examples / Edge Cases:**

Handle `401`, network failures, `503`, duplicate requests, malformed
IDs, and timestamps with the correct IST offset.

**Implementation Approach:**

Build shared utilities first, following the design document. Re-render
affected sections using backend responses and disable buttons while
requests are in flight.

**Acceptance Criteria:**

The architecture matches Section 5, user content is rendered safely, and
shared utilities are reusable across all pages.

**Verification / Reporting:**

Run unit tests and available Playwright tests. Report modified files,
actual test results, and remaining issues.

––––––––––––––––––––––––––––––––––––

## Prompt 17 — Frontend: the frontend dependencies described in Section 6 of `Fix_It_Frontend_Design_Document.md` and complete any necessary frontend integration

**Objective / Instruction:**

Verify the frontend dependencies described in **Section 6** of
`Fix_It_Frontend_Design_Document.md` and complete any necessary frontend
integration.

**Repository Context:**

Inspect the actual backend and frontend implementation before changing
anything. Treat the design document's backend status table as the
expected reference, not proof that every change exists in the current
repository.

**Constraints / Invariants:**

-   Verify static-file serving, `authorName`, IST timestamps,
    notification fields, and security headers.

-   Google authentication and dev-only login are dropped; retain
    username/password authentication.

-   Do not modify working backend code unnecessarily or invent API
    contracts.

-   Follow the documented CSP restrictions: no inline scripts, `eval`,
    or CDN dependencies.

**Examples / Edge Cases:**

Check missing response fields, incorrect timestamp offsets, inaccessible
static resources, and frontend behaviour when the embedding API key is
absent.

**Implementation Approach:**

Compare the implementation against Section 6, fix only confirmed gaps,
and follow the documented local development recipe.

**Acceptance Criteria:**

Frontend/backend integration matches the documented contracts, and any
discrepancies are fixed or clearly reported.

**Verification / Reporting:**

Run relevant tests and verify local startup. Report confirmed fixes,
test results, unresolved mismatches, and required environment variables.

––––––––––––––––––––––––––––––––––––

## Prompt 18 — Frontend: Frontend Phases F0–F6 from `Fix_It_Frontend_Design_Document.md`, following the phase descriptions and checkpoints in Section 7

**Objective / Instruction:**

Implement **Frontend Phases F0–F6** from
`Fix_It_Frontend_Design_Document.md`, following the phase descriptions
and checkpoints in Section 7.

**Repository Context:**

Inspect the existing repository before making changes. Use
`Fix_It_Frontend_Design_Document.md` as the primary specification and
refer to `API_REFERENCE.md`, `Fix_It_Database_Design.md`,
`Fix_It_Backend_Implementation_Roadmap.md`, and
`Fix_It_Design_Document.md` wherever necessary.

**Constraints / Invariants:**

-   Follow the specified repository structure and use plain HTML, CSS,
    and JavaScript without frontend frameworks or build tools.

-   Implement F0–F6: foundations and seed data, design system and style
    guide, authentication and shared header, homepage, read-only
    question page, search, and question creation/editing.

-   Follow all documented routes, API contracts, UI behaviours,
    validation rules, security requirements, accessibility standards,
    and responsive-design rules.

-   Preserve URL-driven tag filtering, union semantics, question
    ordering, accepted-answer ordering, nested comments, IST timestamps,
    and backend-controlled search ranking.

-   Use safe DOM rendering; never use `innerHTML`, `outerHTML`,
    `insertAdjacentHTML`, `document.write`, or `eval` in `frontend/js/`.

-   Do not introduce undocumented features or fabricate data or API
    responses.

**Examples / Edge Cases:**

Handle authentication and validation failures, empty states, pagination,
long text, malicious user content, missing questions, empty search
queries, search `503` responses, prefilled forms, and unauthorized
editing as specified in the design document.

**Implementation Approach:**

Implement phases sequentially from **F0 through F6**, reusing shared
components and utilities. At every checkpoint, follow **BUILD → TEST →
VERIFY**. If a checkpoint fails, fix the issue and retest before
proceeding. Do not build on a broken phase.

**Acceptance Criteria:**

All F0–F6 checkpoints in Section 7 must pass, including authentication,
styling, homepage discovery, question display, search and debug mode,
and question creation/editing.

**Verification / Reporting:**

Run available unit tests and Playwright/browser tests. Report modified
files, features implemented, actual test results, failed checkpoints,
API mismatches, and remaining issues. Never claim that unexecuted tests
passed.

––––––––––––––––––––––––––––––––––––

## Prompt 19 — Frontend: Phase F7 — Answers and Resolution from `Fix_It_Frontend_Design_Document.md`

**Objective / Instruction:**

Implement **Phase F7 — Answers and Resolution** from
`Fix_It_Frontend_Design_Document.md`.

**Repository Context:**

Inspect the existing implementation and refer to the frontend design
document, `API_REFERENCE.md`, and database design for requirements and
API contracts.

**Constraints / Invariants:**

Follow the documented design and APIs. Only the question author can
select the correct answer; only an answer's author can edit their
answer. The backend remains authoritative for permissions.

**Examples / Edge Cases:**

Handle empty answers, unauthorized requests (`403`), answer editing,
switching the accepted answer, and updating resolved status and answer
ordering.

**Implementation Approach:**

Implement answer submission, inline editing, correct-answer selection,
and live updates. Follow **BUILD → TEST → VERIFY** before proceeding.

**Acceptance Criteria:**

All F7 checkpoints pass: answers display correctly with author and
timestamp, ownership controls are correct, the accepted answer appears
first, and resolved status persists after reload.

**Verification / Reporting:**

Run relevant tests and report modified files, actual results, and
remaining issues. Do not claim unexecuted tests passed.

––––––––––––––––––––––––––––––––––––

## Prompt 20 — Frontend: Phase F8 — Comments, Replies and Voting from `Fix_It_Frontend_Design_Document.md`

**Objective / Instruction:**

Implement **Phase F8 — Comments, Replies and Voting** from
`Fix_It_Frontend_Design_Document.md`.

**Repository Context:**

Inspect existing components and reuse the current `comment-tree`
implementation where possible. Refer to the design document,
`API_REFERENCE.md`, and database design.

**Constraints / Invariants:**

Follow documented API contracts. Support nested replies and editing
one's own comments. Comments are permanent: **never add a delete
control**. Comment voting must not affect question interest counts or
answer ordering.

**Examples / Edge Cases:**

Handle nested display, correct author/timestamps, vote removal,
switching between upvotes and downvotes, unauthorized edits, and failed
requests.

**Implementation Approach:**

Implement comment creation, nested replies, editing, and vote toggling.
Update vote counts from backend responses, not guessed local values.
Follow **BUILD → TEST → VERIFY**.

**Acceptance Criteria:**

All F8 checkpoints pass: comment hierarchy and ordering are correct,
voting matches backend state after reload, edit controls respect
ownership, and no delete controls exist.

**Verification / Reporting:**

Run relevant unit and browser tests. Report changed files, actual test
results, and remaining issues.

––––––––––––––––––––––––––––––––––––

## Prompt 21 — Frontend: Phase F9 from `Fix_It_Frontend_Design_Document.md`

**Objective / Instruction:**

Implement **Phase F9** from `Fix_It_Frontend_Design_Document.md`.

**Repository Context:**

Inspect the existing implementation and refer to the frontend design
document, `API_REFERENCE.md`, and database design for requirements and
API contracts.

**Constraints / Invariants:**

Follow the documented APIs and UI behaviour. The `+` interest control is
separate from comment voting. Use backend responses for counts and
state.

**Examples / Edge Cases:**

Handle duplicate clicks, toggling interest on/off, unread notifications,
comment/reply notifications, notification redirection, and hidden
browser tabs.

**Implementation Approach:**

Implement the `+` toggle, highlighted state, bell badge, dropdown,
60-second unread-count polling, mark-as-read action, and navigation to
the relevant comment. Stop polling while the tab is hidden.

**Acceptance Criteria:**

All F9 checkpoints pass: interest counts persist and match homepage
ordering; notifications display correctly, exclude the user's own
actions, redirect to and highlight the relevant comment, and update
unread counts.

**Verification / Reporting:**

Test interest toggling, comment/reply notifications, redirection, read
state, and visibility-based polling. Report actual test results and
remaining issues.

––––––––––––––––––––––––––––––––––––

## Prompt 22 — Frontend: Phases F10–F11 from `Fix_It_Frontend_Design_Document.md`

**Objective / Instruction:**

Implement **Phases F10–F11** from `Fix_It_Frontend_Design_Document.md`.

**Repository Context:**

Inspect existing pages and shared components. Use the frontend design
document as the primary specification and consult `API_REFERENCE.md`
where needed.

**Constraints / Invariants:**

Follow the specified profile behaviour, error messages, security rules,
accessibility requirements, and responsive design. Never use
`innerHTML`, `outerHTML`, `insertAdjacentHTML`, `document.write`, or
`eval` in `frontend/js/`.

**Examples / Edge Cases:**

Handle empty profiles, pagination, HTTP errors
(400/401/403/404/409/500/503), offline requests, loading states,
keyboard navigation, and narrow screens.

**Implementation Approach:**

Implement My Questions using the documented API, shared question rows,
pagination, and the empty state. Then audit error handling, safe DOM
rendering, CSP compatibility, accessibility, and responsiveness.

**Acceptance Criteria:**

All F10–F11 checkpoints pass: only the user's questions appear, error
states remain user-friendly, banned DOM APIs are absent, forms are
accessible, and layouts work at 360px without horizontal scrolling.

**Verification / Reporting:**

Run unit tests, security checks, and Playwright tests. Verify the
end-to-end flow across Chrome, Firefox, Safari, and Edge where
available. Report actual results, unresolved issues, and release
readiness without claiming unexecuted tests passed.

––––––––––––––––––––––––––––––––––––

## Prompt 23 — Frontend: and verify Phase F12 from `Fix_It_Frontend_Design_Document.md`

**Objective / Instruction:**

Implement and verify **Phase F12** from
`Fix_It_Frontend_Design_Document.md`.

**Repository Context:**

Inspect the existing frontend, backend, and test suites. Follow Sections
7, 9, 10, 12, and 13 of the design document.

**Constraints / Invariants:**

-   Use the existing Playwright setup, seed data, and backend APIs.

-   Do not assume previous checkpoints pass; verify the current
    implementation.

-   Do not replace real API integration with mocked success responses.

**Examples / Edge Cases:**

Cover authentication, search, question creation, answers, comments,
replies, voting, `+` interest, resolution, notifications, and subsequent
search.

**Implementation Approach:**

Implement missing end-to-end tests for the complete F12 workflow. Fix
failures before proceeding and maintain the release checklist.

**Acceptance Criteria:**

The complete documented workflow passes, traceability requirements are
checked, the style guide remains consistent, and no browser-console
errors occur during testing.

**Verification / Reporting:**

Run Node unit tests and Playwright tests against the real backend.
Report actual results, failed checkpoints, and remaining release
blockers.

––––––––––––––––––––––––––––––––––––

## Prompt 24 — Frontend: Audit and complete Phase F11, the error-handling matrix, and design traceability requirements in `Fix_It_Frontend_Design_Document.md`

**Objective / Instruction:**

Audit and complete **Phase F11**, the error-handling matrix, and design
traceability requirements in `Fix_It_Frontend_Design_Document.md`.

**Repository Context:**

Inspect existing API utilities, page components, tests, and backend
responses. Refer to Sections 5, 8, 9, and 10.

**Constraints / Invariants:**

-   Use consistent, user-friendly error messages without exposing
    technical details.

-   Preserve safe DOM rendering and the Content-Security-Policy
    requirements.

-   Follow existing API contracts and authorization rules.

-   Do not use banned DOM APIs or introduce unnecessary dependencies.

**Examples / Edge Cases:**

Test HTTP 400, 401, 403, 404, 409, 429, 500, 502, 503, and offline
scenarios. Handle loading states, empty states, expired sessions, and
failed mutations.

**Implementation Approach:**

Audit every page and action, fix confirmed issues, verify keyboard and
screen-reader accessibility, and test responsive layouts.

**Acceptance Criteria:**

All error cases show appropriate messages; no blank screens or unsafe
rendering occur; headings, labels, focus indicators, page titles, and
360px layouts meet the documented requirements.

**Verification / Reporting:**

Run unit tests, the banned-API grep test, accessibility checks, and
Playwright error-injection tests. Report actual results and unresolved
issues.

––––––––––––––––––––––––––––––––––––

## Prompt 25 — Frontend: the final project audit using Sections 9–13 of `Fix_It_Frontend_Design_Document.md`. Verify the implementation and prepare the release checklist

**Objective / Instruction:**

Perform the final project audit using Sections 9–13 of
`Fix_It_Frontend_Design_Document.md`. Verify the implementation and
prepare the release checklist.

**Repository Context:**

Inspect the current code, tests, build configuration, local startup
scripts, search configuration, and existing documentation.

**Constraints / Invariants:**

-   Treat the document's reported build status as historical
    information, not proof of the current state.

-   Preserve working implementations and existing design decisions.

-   Do not claim unverified functionality is complete.

-   Keep the frontend and backend same-origin as specified.

**Examples / Edge Cases:**

Check missing environment variables, unavailable embedding services,
incorrect search weights or thresholds, insecure production cookie
settings, and unsupported browsers.

**Implementation Approach:**

Verify every traceability-table row and release checklist item. Confirm
packaging, environment configuration, authentication limitations, and
search-provider integration. Fix confirmed defects and document anything
that requires external configuration or a deliberate decision.

**Acceptance Criteria:**

The project has an evidence-based release checklist distinguishing
completed, failed, and unverified items. Lexical-only search remains
available when semantic search is not configured.

**Verification / Reporting:**

Run the complete test suite and production build where possible.
Explicitly report whether real Jina semantic search, Firefox/Safari,
HTTPS, the `Secure` cookie flag, sign-up verification, and production
search tuning have actually been verified. Do not enable real-user
production use while critical security or verification requirements
remain unresolved.

––––––––––––––––––––––––––––––––––––

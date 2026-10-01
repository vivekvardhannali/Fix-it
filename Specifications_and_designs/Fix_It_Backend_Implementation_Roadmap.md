# Fix It — Backend Implementation Roadmap

**Project:** Fix It  
**Current focus:** Backend only  
**Frontend:** Not included in this phase

---

## 0. Target Backend Flow

The backend should be built in this order:

```text
Project Setup
    ↓
PostgreSQL + pgvector Setup
    ↓
Database Schema
    ↓
Spring Boot + JPA Database Connection
    ↓
User Authentication (Google Sign-In / IITM smail)
    ↓
User / Session Protection
    ↓
Question CRUD
    ↓
Tags + Question/Tag Mapping
    ↓
Answers + Accepted Answer
    ↓
Comments + Replies
    ↓
Comment Voting
    ↓
Question Interest (+)
    ↓
Notifications
    ↓
Search Infrastructure
    ↓
Embedding Generation
    ↓
Semantic Search
    ↓
Lexical Search / BM25 (if selected)
    ↓
Hybrid Ranking (if selected)
    ↓
Threshold + Ranking
    ↓
Homepage / Discovery API
    ↓
End-to-End Backend Testing
```

**Important:** Do not start with semantic search. First make the normal question/answer system work correctly. Search is built on top of the working question database.

---

# Phase 1 — Create the Backend Project

## Step 1. Create Spring Boot Project

Set up the backend using:

- Java
- Maven
- Spring Boot
- Spring Web
- Spring Data JPA
- PostgreSQL JDBC Driver
- PostgreSQL + pgvector support

### Build

Create the basic project structure:

```text
fix-it/
└── backend/
    ├── pom.xml
    └── src/
        ├── main/
        │   ├── java/
        │   └── resources/
        └── test/
```

At this stage, do not build any business functionality.

### Checkpoint 1

Verify:

- Project builds successfully.
- Spring Boot starts.
- No database functionality yet.
- A basic test endpoint can return a response.

**Do not proceed until the application starts cleanly.**

---

# Phase 2 — PostgreSQL Database

## Step 2. Create the PostgreSQL Database

Create the Fix It PostgreSQL database.

Then enable the required `pgvector` extension.

The database is intended to support:

- Normal relational data.
- Vector similarity search.

### Checkpoint 2

Verify from PostgreSQL that:

```text
Database exists
        ↓
PostgreSQL connection works
        ↓
pgvector extension is available
```

**Do not continue until the backend can connect to PostgreSQL.**

---

# Phase 3 — Build the Database Schema

## Step 3. Create the Tables

Create the 9 tables defined by the current database design:

```text
1. users
2. questions
3. tags
4. question_tags
5. answers
6. question_comments
7. question_comment_votes
8. question_interests
9. notifications
```

Important relationships:

```text
users
  │
  ├── questions
  │      ├── question_tags ── tags
  │      ├── answers
  │      ├── question_comments
  │      │       └── question_comment_votes
  │      └── question_interests
  │
  └── notifications
```

Keep these design rules:

- No `answer_comments`.
- No `answer_comment_votes`.
- Comments belong to questions.
- `parent_comment_id` supports comment replies.
- `accepted_answer_id` identifies the accepted answer.
- `question_interests` stores the `+` action.
- `interest_count` is maintained for question discovery.
- Comment votes do not affect search ranking.
- No comment deletion.

### Checkpoint 3

Insert a small amount of manual test data.

Verify:

- Users can exist.
- Questions reference users.
- Questions can have multiple tags.
- Questions can have answers.
- Questions can have comments/replies.
- Comment votes reference users/comments.
- Question interests reference users/questions.
- Notifications reference the relevant user/question/comment.

**Do not start API development until the relational structure works.**

---

# Phase 4 — Connect Spring Boot to PostgreSQL

## Step 4. Configure JPA

Configure:

```text
Spring Boot
     ↓
Spring Data JPA
     ↓
PostgreSQL JDBC
     ↓
PostgreSQL
```

Create the JPA entities corresponding to the database tables.

Start with:

```text
User
Question
Tag
QuestionTag
Answer
QuestionComment
QuestionCommentVote
QuestionInterest
Notification
```

Map the relationships carefully.

### Checkpoint 4

Test:

```text
Spring Boot
    ↓
JPA
    ↓
PostgreSQL
```

Verify that the application can:

- Read a user.
- Read a question.
- Create a question.
- Read the created question back.

**If this fails, fix the database/JPA layer before building services.**

---

# Phase 5 — Google Authentication

## Step 5. Implement Google Sign-In

Authentication is **not** username/password authentication.

The intended flow is:

```text
User
  ↓
Fix It Login
  ↓
Continue with Google
  ↓
Google Authentication
  ↓
IITM smail identity
  ↓
Redirect back to Fix It
  ↓
Backend verifies authenticated identity
  ↓
Find user in users table
  │
  ├── Existing → Login
  │
  └── New → Create user
  ↓
Authenticated application session
```

The backend must not store:

```text
password
password_hash
```

The `users` table stores the IITM smail used to identify the Fix It account.

### Backend responsibilities

Implement:

- Google authentication configuration.
- Callback handling.
- Authenticated-user extraction.
- IITM smail validation.
- Find-or-create user.
- Application authentication/session handling.
- Reject unauthenticated access to protected operations.

### Checkpoint 5

Verify the complete backend authentication flow.

Test:

```text
Google login
    ↓
Successful callback
    ↓
Correct smail obtained
    ↓
User created/retrieved
    ↓
Authenticated request works
```

Also test an unauthorized/non-IITM identity according to the project's allowed-account rule.

**Do not proceed until authentication works independently of the rest of the application.**

---

# Phase 6 — Question Management

## Step 6. Implement Question CRUD

Build the question backend.

A question contains:

```text
question_id
author_id
title
body
is_resolved
accepted_answer_id
interest_count
created_at
updated_at
```

Implement:

```text
Create question
Get question
Update own question
```

Only the question author can modify:

```text
title
body
tags
```

### Checkpoint 6

Test:

```text
Authenticated user
      ↓
Create question
      ↓
Question stored
      ↓
Get question
      ↓
Update own question
```

Then test:

```text
Different user
      ↓
Attempts to edit question
      ↓
Request rejected
```

---

# Phase 7 — Tags

## Step 7. Implement Tags and Question-Tag Mapping

Build:

```text
Tag
QuestionTag
```

Implement:

- Create/use available tags.
- Assign tags to questions.
- Retrieve question tags.
- Update question tags by the question owner.

Do not hard-code the final tag categories because the exact tag set is not finalized in the database design.

### Checkpoint 7

Test:

```text
Question
   ↓
Tag A
Tag B
Tag C
```

Verify that:

- Multiple tags can belong to one question.
- A tag can belong to multiple questions.
- Only the question owner can change the question's tags.

---

# Phase 8 — Answers

## Step 8. Implement Answer Management

Create the answer functionality.

An answer contains:

```text
answer_id
question_id
author_id
body
created_at
updated_at
```

Implement:

```text
Add answer
Get answers for question
Update own answer
```

Only the answer author can edit the answer.

### Checkpoint 8

Test:

```text
User A
  ↓
Question
  ↓
User B adds answer
  ↓
Answer appears
```

Then:

```text
User B → can edit
User A → cannot edit User B's answer
```

---

# Phase 9 — Accepted Answer / Resolution

## Step 9. Implement Correct Answer Selection

The question author can select an answer as correct.

Backend operation:

```text
Question
   ↓
Question author selects Answer X
   ↓
accepted_answer_id = X
   ↓
is_resolved = true
```

The exact consistency rule should be enforced so that the accepted answer belongs to the same question.

### Checkpoint 9

Test:

```text
Question author
      ↓
Select answer
      ↓
accepted_answer_id updated
      ↓
Question marked resolved
```

Also test:

```text
Different user
      ↓
Attempts to select correct answer
      ↓
Rejected
```

---

# Phase 10 — Comments and Replies

## Step 10. Implement Question Comments

Comments are attached **only to questions**.

Implement:

```text
Add comment
Reply to comment
Edit own comment
Get comments
```

Use:

```text
parent_comment_id
```

for replies.

Example:

```text
Question
 ├── Comment A
 │    ├── Reply A1
 │    └── Reply A2
 │
 └── Comment B
```

There is no answer-comment functionality.

Comments have no delete operation.

### Checkpoint 10

Test:

```text
Question
  ↓
Comment
  ↓
Reply
  ↓
Nested discussion retrieved correctly
```

Test ownership:

```text
Comment author → can edit
Other user → cannot edit
```

---

# Phase 11 — Comment Voting

## Step 11. Implement UP/DOWN Comment Votes

Implement:

```text
UP
DOWN
REMOVE
SWITCH
```

Required behaviour:

```text
No vote → UP
UP → click UP → No vote

No vote → DOWN
DOWN → click DOWN → No vote

UP → DOWN
DOWN → UP
```

One user can have at most one active vote on a comment.

### Checkpoint 11

Test all four transitions.

Also verify that comment voting does **not** modify:

```text
question interest_count
search score
homepage ordering
semantic similarity
```

---

# Phase 12 — Question Interest (+)

## Step 12. Implement the `+` Feature

The `+` action means:

> This user is interested in this question / wants it answered.

Use:

```text
question_interests
```

and maintain:

```text
questions.interest_count
```

Behaviour:

```text
No interest
    ↓
+ clicked
    ↓
question_interests row created
    ↓
interest_count + 1
```

Again:

```text
+ clicked again
    ↓
interest removed
    ↓
interest_count - 1
```

### Checkpoint 12

Test:

- User can add interest.
- Same user cannot create duplicate active interest.
- Clicking again removes it.
- Count changes correctly.
- Different users can independently mark interest.

---

# Phase 13 — Notifications

## Step 13. Implement Website Notifications

Implement notifications for the interactions already defined in the design.

Examples:

```text
Someone comments on your question
        ↓
Notification

Someone replies to your comment
        ↓
Notification
```

Notification contains:

```text
notification_id
user_id
type
question_id
comment_id
created_at
is_read
```

Implement:

```text
Get notifications
Mark notification as read
```

### Checkpoint 13

Test:

```text
User A owns Question
        ↓
User B comments
        ↓
User A receives notification
        ↓
User A opens notification
        ↓
Correct question/comment is identified
```

---

# Phase 14 — Build the Search Foundation

## Step 14. Decide Where Question Embeddings Are Stored

Before implementing semantic search, decide the exact embedding storage representation using the existing PostgreSQL + pgvector requirement.

The search system ultimately needs:

```text
Question
   ↓
Embedding
   ↓
pgvector
```

The current database design does not yet specify the exact embedding column/table structure.

**This is a checkpoint where the exact storage design must be finalized before coding semantic search.**

### Checkpoint 14

Finalize:

- Embedding model.
- Embedding dimension.
- Which question text is embedded.
- Where the vector is stored.
- How vectors are updated when a question changes.
- Which questions participate in search.

Do not implement the final search query before these decisions are fixed.

---

# Phase 15 — Embedding Generation

## Step 15. Create the Embedding Service

Build a backend service responsible for converting question text into an embedding.

Conceptually:

```text
Question text
     ↓
Embedding Service
     ↓
Vector
     ↓
PostgreSQL / pgvector
```

When a question is created:

```text
Create Question
      ↓
Generate embedding
      ↓
Store embedding
```

When searchable question content changes:

```text
Update Question
      ↓
Regenerate embedding
      ↓
Update stored vector
```

### Checkpoint 15

Create several questions with clearly different meanings.

Generate embeddings and verify:

- A vector is produced.
- The vector dimension is correct.
- The vector is stored successfully.
- Updating the relevant question data updates its embedding.

---

# Phase 16 — Semantic Search

## Step 16. Implement Semantic Search

Input:

```text
query
selected tags
```

Flow:

```text
User query
    ↓
Generate query embedding
    ↓
Filter candidate questions by selected tags
    ↓
Calculate vector similarity
    ↓
Apply relevance threshold
    ↓
Sort descending by similarity
    ↓
Return question results
```

The result should contain the information needed by the search-result UI later:

```text
question_id
title
body preview
tags
is_resolved
interest_count
relevance/similarity
```

### Checkpoint 16

Create a test set containing:

- Similar questions.
- Unrelated questions.
- Duplicate/nearly duplicate questions.
- Queries with no suitable match.

Verify:

```text
Relevant → returned
Irrelevant → filtered
Higher similarity → appears earlier
Below threshold → not returned
```

---

# Phase 17 — Lexical Search / BM25

## Step 17. Implement Lexical Search Only If Selected

The design currently allows:

```text
Cosine similarity
OR
BM25
OR
Combination
```

The exact approach is to be decided during implementation.

If BM25/lexical search is selected, build it after semantic search so that each component can be tested independently.

### Checkpoint 17

Verify that lexical search independently produces sensible relevance scores.

Do not combine scores yet.

---

# Phase 18 — Hybrid Search

## Step 18. Implement Hybrid Ranking If Selected

If the final approach is hybrid:

```text
SEMANTIC_WEIGHT = configurable
LEXICAL_WEIGHT  = 1 - SEMANTIC_WEIGHT

HYBRID_SCORE =
    SEMANTIC_WEIGHT × SEMANTIC_SCORE
    +
    LEXICAL_WEIGHT × LEXICAL_SCORE
```

The weights should be configurable rather than hard-coded into business logic.

Then:

```text
Candidate questions
      ↓
Hybrid score
      ↓
Threshold
      ↓
Descending score
      ↓
Final results
```

### Checkpoint 18

Test:

- Semantic-only behaviour.
- Lexical-only behaviour.
- Hybrid behaviour.
- Different configurable weights.
- Threshold filtering.
- Final descending ordering.

Also verify that:

```text
comment votes
```

do not enter the score.

---

# Phase 19 — Search API

## Step 19. Expose the Search Endpoint

Once search works internally, expose it through the backend API.

Conceptually:

```text
Search Request
    ├── query
    └── selected tags
          ↓
SearchService
          ↓
Ranking
          ↓
Search Results
```

The backend should return structured data rather than HTML.

### Checkpoint 19

Test the API directly using a tool such as Postman/curl.

Verify:

```text
Request
  ↓
Correct candidate filtering
  ↓
Correct scoring
  ↓
Threshold
  ↓
Correct ordering
  ↓
Correct response
```

---

# Phase 20 — Homepage / Discovery API

## Step 20. Implement Homepage Question Discovery

The homepage is different from semantic search.

Given selected tags:

```text
Selected tags
      ↓
Union of matching questions
      ↓
Sort by interest_count DESC
      ↓
Return questions
```

Important:

```text
Homepage ordering ≠ Search ranking
```

The `+` count is used here for discovery ordering.

### Checkpoint 20

Test:

```text
Selected Tag A + Tag B
        ↓
Questions matching A OR B
        ↓
Sorted by + count descending
```

Verify that this is a **union**, not an intersection.

---

# Phase 21 — Authorization Audit

## Step 21. Test Ownership and Protected Operations

Before considering the backend complete, systematically test authorization.

### Question

```text
Author → edit
Other user → reject
```

### Answer

```text
Author → edit
Other user → reject
```

### Comment

```text
Author → edit
Other user → reject
```

### Accepted answer

```text
Question author → select
Other user → reject
```

### Protected actions

```text
Unauthenticated user → reject
Authenticated user → allowed where applicable
```

### Checkpoint 21

No ownership rule should be enforced only by the frontend.

The backend must perform the authorization check.

---

# Phase 22 — Error Handling

## Step 22. Add Basic Backend Error Handling

Handle cases such as:

```text
Unauthenticated request
Unauthorized operation
Question not found
Answer not found
Comment not found
Invalid question data
Invalid tag data
Invalid vote operation
Invalid accepted answer
Invalid search request
Authentication failure
```

Return consistent HTTP responses and clear error messages without exposing internal implementation details.

### Checkpoint 22

For each major API, test at least:

```text
Successful request
Invalid request
Unauthorized request
Missing resource
```

---

# Phase 23 — Complete Backend Integration Test

## Step 23. Run the Full Backend Flow

Now test the complete system without a frontend.

```text
Google Authentication
        ↓
User created/retrieved
        ↓
Create question
        ↓
Add tags
        ↓
Generate/store embedding
        ↓
Another user searches
        ↓
Search returns relevant question
        ↓
Open question
        ↓
Add answer
        ↓
Add comment
        ↓
Reply to comment
        ↓
Vote on comment
        ↓
Question author marks +
        ↓
Question author selects correct answer
        ↓
Question becomes resolved
        ↓
Notification generated
        ↓
Resolved question becomes part of searchable knowledge base
```

### Checkpoint 23 — Final Backend Check

The backend is ready for frontend development only when all of these work:

- [ ] Spring Boot starts.
- [ ] PostgreSQL connection works.
- [ ] pgvector is available.
- [ ] All 9 database tables work.
- [ ] Google authentication works.
- [ ] IITM smail identity is handled correctly.
- [ ] User creation/retrieval works.
- [ ] Question CRUD works.
- [ ] Tags work.
- [ ] Answers work.
- [ ] Accepted-answer workflow works.
- [ ] Comments/replies work.
- [ ] Comment voting works.
- [ ] Question `+` works.
- [ ] Notifications work.
- [ ] Embeddings work.
- [ ] Semantic search works.
- [ ] Threshold works.
- [ ] Ranking works.
- [ ] Lexical/hybrid search works if selected.
- [ ] Homepage discovery works.
- [ ] Ownership checks work.
- [ ] Error handling works.
- [ ] Full end-to-end backend flow works.

---

# Recommended Build Order — Short Version

If you are actually coding this now, follow this exact sequence:

```text
1. Spring Boot project
        ↓
2. PostgreSQL + pgvector
        ↓
3. Database schema
        ↓
4. JPA entities + DB connection
        ↓
5. Google authentication
        ↓
6. User management
        ↓
7. Question CRUD
        ↓
8. Tags
        ↓
9. Answers
        ↓
10. Accepted answer / resolution
        ↓
11. Comments + replies
        ↓
12. Comment voting
        ↓
13. Question +
        ↓
14. Notifications
        ↓
15. Finalize embedding storage
        ↓
16. Embedding service
        ↓
17. Semantic search
        ↓
18. Lexical/BM25 (if selected)
        ↓
19. Hybrid ranking (if selected)
        ↓
20. Search API
        ↓
21. Homepage/discovery API
        ↓
22. Authorization audit
        ↓
23. Error handling
        ↓
24. Full backend integration test
        ↓
25. BACKEND COMPLETE
```

---

# Checkpoint Philosophy

At every checkpoint, use this rule:

```text
BUILD
  ↓
TEST
  ↓
VERIFY
  ↓
CHECKPOINT PASSED?
  │
  ├── NO → FIX → TEST AGAIN
  │
  └── YES
        ↓
     NEXT STEP
```

**Do not build the next layer on top of a broken previous layer.**

The most important checkpoints are:

```text
CP1 → Spring Boot works
CP2 → PostgreSQL + pgvector works
CP3 → Schema works
CP4 → JPA ↔ PostgreSQL works
CP5 → Google authentication works
CP6 → Question CRUD works
CP9 → Resolution works
CP13 → Notifications work
CP14 → Embedding design finalized
CP16 → Semantic search works
CP18 → Final ranking works
CP21 → Authorization is secure
CP23 → Entire backend works
```

Only after **CP23** should you start the frontend.

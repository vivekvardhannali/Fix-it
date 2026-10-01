# Fix It — Database Design

## 1. Database Overview

The Fix It interface is designed around a question page containing:

1. The **Question**
2. The **Answer** section
3. The **Comments** section underneath

The Answer and Comments are completely separate.

The question author can read the comments and use useful information from them to create or update the answer. They may copy-paste a comment or write the answer themselves. However, the database does **not** create any relationship between an answer and the comments used to formulate it.

There is also **no separate comment section for answers**.

---

## 2. Database Entities

The database consists of the following main tables:

1. `users`
2. `questions`
3. `tags`
4. `question_tags`
5. `answers`
6. `question_comments`
7. `question_comment_votes`
8. `question_interests`
9. `notifications`

---

## 3. Users

The `users` table stores account information.

| Column | Type | Description |
|---|---|---|
| `user_id` | PK | Unique user ID |
| `smail` | VARCHAR | IITM email used as the user's authenticated identity |
| `created_at` | TIMESTAMP DEFAULT CURRENT_TIMESTAMP | Account creation time |


### Authentication

Fix It uses Google Sign-In for authentication rather than maintaining a separate Fix It password.

The authentication flow is:

```text
User
  ↓
Fix It Login
  ↓
Google Sign-In
  ↓
IITM smail authentication
  ↓
Redirect back to Fix It
  ↓
Find existing user or create new user
  ↓
Logged-in application session
```

The `users` table therefore does not store a `password` or `password_hash`. The Google account handles authentication credentials; Fix It stores the IITM smail needed to identify the application user.

Only authenticated users can perform actions that require a logged-in account, such as creating questions, answering, commenting, voting and using other protected functionality.

---

## 4. Questions

The `questions` table stores the actual question.

| Column | Type | Description |
|---|---|---|
| `question_id` | PK | Unique question ID |
| `author_id` | FK → `users` | Person who created the question |
| `title` | VARCHAR | Question title |
| `body` | TEXT | Question description |
| `is_resolved` | BOOLEAN | Whether the question has been resolved |
| `accepted_answer_id` | FK → `answers` | The answer selected by the question author as the correct answer; nullable until an answer is accepted |
| `interest_count` | INT | Number of users who marked `+` |
| `created_at` | TIMESTAMP | Question creation time |
| `updated_at` | TIMESTAMP | Last modification time of the question |

The question table does not store the answer text.

---

## 5. Tags

The `tags` table stores the available question categories/tags.

| Column | Type | Description |
|---|---|---|
| `tag_id` | PK | Unique tag ID |
| `name` | VARCHAR | Tag name |

A question can have multiple tags.

The exact set of tag categories can be finalized separately. Tags are stored as separate records rather than hard-coded into the question table.

---

## 6. Question Tags

The `question_tags` table implements the many-to-many relationship between questions and tags.

| Column | Type | Description |
|---|---|---|
| `question_id` | FK → `questions` | Question |
| `tag_id` | FK → `tags` | Tag |

Primary key:

```text
(question_id, tag_id)
```

Example:

```text
Question 101
    ├── Networking
    ├── WiFi
    └── Hostel
```

---

## 7. Answers

The `answers` table stores the actual answer shown in the Answer section of a question.

| Column | Type | Description |
|---|---|---|
| `answer_id` | PK | Unique answer ID |
| `question_id` | FK → `questions` | Question being answered |
| `author_id` | FK → `users` | Person who wrote the answer |
| `body` | TEXT | Actual answer |
| `created_at` | TIMESTAMP | Answer creation time |
| `updated_at` | TIMESTAMP | Last modification time of the answer |

### Answer and Comment Separation

There is deliberately **no `comment_id` in the `answers` table**.

The workflow is:

```text
User reads comments
        ↓
Identifies useful information
        ↓
Copies/rephrases it OR writes an answer themselves
        ↓
Updates the Answer box
```

The resulting answer is stored independently.

The database does not record which comments were used to create the answer.

### Accepted Answer

When the question author selects an answer as the correct answer, the question stores that answer through `accepted_answer_id`.

```text
QUESTION
    │
    └── accepted_answer_id ──────> ANSWER
```

`accepted_answer_id` is nullable while the question is unresolved. Once the author selects an answer, it identifies the single accepted answer for that question.

---

## 8. Question Comments

The `question_comments` table stores discussion related to the question.

Comments are displayed below the question/answer area.

| Column | Type | Description |
|---|---|---|
| `comment_id` | PK | Unique comment ID |
| `question_id` | FK → `questions` | Question being discussed |
| `author_id` | FK → `users` | Person who wrote the comment |
| `parent_comment_id` | FK → `question_comments` | Parent comment for a reply |
| `body` | TEXT | Comment content |
| `created_at` | TIMESTAMP | Comment creation time |
| `updated_at` | TIMESTAMP | Last modification time |

Comments can therefore support replies:

```text
Question
│
├── Comment A
│   ├── Reply A1
│   └── Reply A2
│
├── Comment B
│
└── Comment C
```

All of these comments belong to the **question**, not to the answer.

There is no `answer_comments` table.

### Comment Deletion

Comments do not have a delete operation in the system. Posting a comment is a one-way action, so comments remain stored in the database.

Therefore, the current schema does not require an `is_deleted` field or a soft-delete mechanism for comments.

---

## 9. Question Comment Votes

The `question_comment_votes` table stores upvotes/downvotes on comments.

| Column | Type | Description |
|---|---|---|
| `comment_id` | FK → `question_comments` | Comment being voted on |
| `user_id` | FK → `users` | User who voted |
| `vote_type` | VARCHAR(4) | `UP` or `DOWN`; constrained to these values |
| `created_at` | TIMESTAMP | Vote creation time |
| `updated_at` | TIMESTAMP | Last vote modification time |

Primary key:

```text
(comment_id, user_id)
```

A database CHECK constraint should restrict `vote_type` to `UP` or `DOWN`. This ensures that a user has at most one active vote on a comment.

### Vote Behaviour

First UP vote:

```text
No vote → UP
```

Click UP again:

```text
UP → removed
```

Change vote:

```text
UP → DOWN
```

or:

```text
DOWN → UP
```

Comment votes are only for user interaction. They do not affect:

- semantic search relevance
- lexical search relevance
- hybrid search score
- question `+` count
- homepage ordering

---

## 10. Question Interests

The `question_interests` table stores the `+` action on a question.

| Column | Type | Description |
|---|---|---|
| `question_id` | FK → `questions` | Question |
| `user_id` | FK → `users` | User who marked interest |
| `created_at` | TIMESTAMP | Time of interest |

Primary key:

```text
(question_id, user_id)
```

### `+` Behaviour

When a user clicks `+`:

```text
User
  ↓
question_interests row inserted
  ↓
interest_count increases
```

When the same user clicks `+` again:

```text
question_interests row removed
  ↓
interest_count decreases
```

The `+` count is separate from comment votes and is used for the question's interest/discovery behaviour.

---

## 11. Notifications

The `notifications` table stores website notifications shown through the notification bell.

| Column | Type | Description |
|---|---|---|
| `notification_id` | PK | Unique notification ID |
| `user_id` | FK → `users` | Notification recipient |
| `type` | VARCHAR | Type of notification |
| `question_id` | FK → `questions` | Related question |
| `comment_id` | FK → `question_comments` | Related comment, if applicable |
| `created_at` | TIMESTAMP | Notification creation time |
| `is_read` | BOOLEAN | Whether the notification has been opened |

Example:

```text
Someone commented on your question
        ↓
Notification created
        ↓
Bell icon shows notification
        ↓
User clicks notification
        ↓
Corresponding question/comment is opened
```

---

# 12. Complete Relationship Structure

The main relationships are:

```text
                         USERS
                           │
          ┌────────────────┼─────────────────┐
          │                │                 │
          ▼                ▼                 ▼
      QUESTIONS         ANSWERS          COMMENTS
          │                │                 │
          │                │                 │
          │                │                 ▼
          │                │           COMMENT VOTES
          │                │
          │                │
          ▼                │
     QUESTION_TAGS         │
          │                │
          ▼                │
         TAGS              │
```

More explicitly:

```text
USER
 │
 ├──────── creates ────────> QUESTION
 │                              │
 │                              ├── QUESTION_TAGS ──> TAGS
 │                              │
 │                              ├── ANSWER
 │                              │
 │                              └── QUESTION_COMMENTS
 │                                       │
 │                                       └── QUESTION_COMMENT_VOTES
 │
 └──────── receives ───────> NOTIFICATIONS
```

---

# 13. Question Page Data Model

The UI for a question can be represented as:

```text
┌──────────────────────────────────────────────┐
│ QUESTION                                     │
│                                              │
│ Title                                        │
│                                              │
│ Question description                         │
│                                              │
│ [Tag 1] [Tag 2] [Tag 3]                     │
│                                              │
│ Author • Modified date                       │
│                                              │
│ + 15                                         │
└──────────────────────────────────────────────┘


┌──────────────────────────────────────────────┐
│ ANSWER                                       │
│                                              │
│ Actual answer/solution                       │
│                                              │
│ Answer author • Modified date                │
└──────────────────────────────────────────────┘


COMMENTS
────────────────────────────────────────────────

User 1:
Try restarting the network service.

    └── User 2:
        I tried that, but the issue remains.

User 3:
Check the DNS configuration.

────────────────────────────────────────────────
[ Add a comment... ]
```

The database relationship is:

```text
                 QUESTION
                    │
          ┌─────────┴─────────┐
          │                   │
          ▼                   ▼
       ANSWER             COMMENTS
          │                   │
          │                   ├── Comment
          │                   ├── Reply
          │                   └── Comment
          │
          └── completely independent
```

---

# 14. Important Separation Rules

The database follows these rules:

### Rule 1 — Answer is separate from comments

An answer is stored in `answers`.

Comments are stored in `question_comments`.

They are separate entities.

### Rule 2 — No comments for answers

There is no:

```text
answer_comments
```

table.

There is also no:

```text
answer_comment_votes
```

table.

### Rule 3 — Comments can be used to formulate an answer

The question author may:

- copy a comment into the answer
- rewrite information from comments
- combine information from multiple comments
- write a completely new answer

But the final answer is stored independently.

### Rule 4 — Comment votes do not influence the answer

Voting on a comment does not automatically modify or promote the answer.

### Rule 5 — Question interest is different from comment voting

The `+` action on a question is stored through `question_interests`.

Comment UP/DOWN votes are stored through `question_comment_votes`.

These serve different purposes.

---

# 15. Final Table List

| # | Table | Purpose |
|---|---|---|
| 1 | `users` | User accounts |
| 2 | `questions` | Questions posted by users, including the selected accepted answer |
| 3 | `tags` | Available question tags/categories |
| 4 | `question_tags` | Maps questions to tags |
| 5 | `answers` | Actual answer/solution for a question |
| 6 | `question_comments` | Discussion/comments on a question |
| 7 | `question_comment_votes` | UP/DOWN votes on question comments |
| 8 | `question_interests` | Users marking `+` on questions |
| 9 | `notifications` | Website notifications |

This structure keeps the **Question, Answer, and Comments as three clearly separate parts of the UI and database**, while still allowing the question author to use information from comments when writing or updating the answer.


---


## 16. Timestamp Handling

Creation timestamps use PostgreSQL `TIMESTAMP` values with `CURRENT_TIMESTAMP` defaults where appropriate. Modification timestamps are updated by the application whenever the corresponding record is edited. No database trigger is required for the current design.

---

# 17. Database Optimization

Indexes and other database optimizations are intentionally not specified as part of the current functional database design.

The initial implementation will focus on correctness and functionality. Indexing, query optimization, and other scalability improvements can be introduced later based on the actual query patterns and performance measurements.

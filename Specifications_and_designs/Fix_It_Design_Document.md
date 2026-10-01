# Fix It — Design Document

**Vivek — CS23B043**  
**Murali — CS23B024**

## 1. Architecture — Modules, Interfaces and Complete Query Flow

Fix It is designed with the interaction and question-resolution model of Stack Overflow as the reference, while keeping the functionality limited to the requirements defined for this project.

### 1.1 Modules

| Module | Responsibility | Interface / Operations |
|---|---|---|
| User & Authentication | Authenticate users through Google Sign-In using their IITM smail identity, create/retrieve the corresponding Fix It account, and manage the logged-in user. | `UserService: authenticateWithGoogle(), getUser()` |
| Question Management | Create, view, edit and manage questions containing title, description/body, tags, interested-user count, modified details and resolved status. | `QuestionService: createQuestion(), getQuestion(), updateQuestion()` |
| Answer Management | Add answers to questions, view answers, edit own answers, and allow the question author to identify the correct answer. | `AnswerService: addAnswer(), updateAnswer(), getAnswers(), markCorrect()` |
| Comment & Discussion | Discussion on questions using comments and replies. Users can upvote/downvote comments for interaction. Comments are not attached to answers. | `CommentService: addComment(), replyToComment(), updateComment(), voteComment()` |
| Search & Semantic Ranking | Search questions within the selected tags and retrieve semantically relevant questions using the selected similarity approach. | `SearchService: searchQuestions(query, tags)` |
| Homepage / Question Discovery | Display questions to logged-in users so that they can discover questions and participate by answering/commenting. | `DiscoveryService: getQuestions()` |
| Notification | Store and display website notifications for relevant user interactions. | `NotificationService: getNotifications(), markAsRead()` |
| Profile | Display the logged-in user's profile dropdown, user ID and their questions. | `ProfileService: getProfile(), getMyQuestions()` |
| Frontend | Provides question search, question details, homepage/discovery, notifications, profile and interaction screens. | User-facing interface |

### 1.2 Question Data

Each question contains:

- Tags representing the genre/category of the question.
- Title.
- Body/description.
- Number of users who also want the question answered (`+` count).
- Last modified date/time for the question.
- Resolved or unresolved status.
- Answers associated with the question.
- A correct-answer indication selected by the question author.
- Discussion through comments and replies.

Answers have their own last-modified date/time.

### 1.3 Comment and Discussion Data

The discussion section supports comments and replies to comments.

- A user can add a comment to the relevant question.
- A user can reply to an existing comment.
- Comments can be upvoted or downvoted.
- Comment voting is only for user interaction.
- Comment votes do **not** affect question ranking, semantic search, similarity scores, or any search hot path.

### 1.4 Authentication Flow

The authentication flow uses Google Sign-In rather than a separate Fix It password.

| Step | What will be done | Result |
|---|---|---|
| 1. Open Fix It | User opens the Fix It website. | Login page is displayed. |
| 2. Continue with Google | User selects the Google Sign-In option. | User is redirected to Google's authentication page. |
| 3. Authenticate with IITM account | User signs in using their IITM Google/smail account. | Google authenticates the user. |
| 4. Return to Fix It | Google redirects the user back to Fix It after successful authentication. | Fix It receives the authenticated identity. |
| 5. Validate account identity | Fix It verifies that the authenticated identity is an allowed IITM smail account. | Unauthorized identities are rejected. |
| 6. Find or create account | Fix It finds the corresponding `users` record or creates one for a first-time user. | A Fix It user account is available. |
| 7. Enter application | The authenticated user is taken to the homepage. | The user can use Fix It features. |

Fix It does not store the user's Google password. Authentication is handled through Google, while the Fix It database stores the user's IITM smail and application-specific account information.

### 1.5 Complete Query Flow

| Step | What will be done | Result |
|---|---|---|
| 1. Login | User authenticates through Google Sign-In using their IITM smail account. | Authenticated user reaches the homepage. |
| 2. Select tags | User selects tags representing the category/genre of the question. | Search is restricted to questions belonging to the selected tags. |
| 3. Enter question | User types the question/query. | Query is sent for search. |
| 4. Search database | The system searches the database for questions within the selected tags. | Candidate questions are retrieved. |
| 5. Calculate relevance | Semantic similarity is used to compare the query with existing questions. The implementation may use cosine similarity of embeddings, BM25, or a combination; the exact approach will be decided during implementation. | Each candidate receives a similarity/relevance value. |
| 6. Apply threshold | Only questions whose similarity/relevance is above the selected threshold are retained. | Irrelevant questions are removed. |
| 7. Sort results | Remaining questions are ordered by similarity/relevance in decreasing order. | Most similar questions appear first. |
| 8. Display results | Search results are displayed in a Stack Overflow-like question-list format. | User sees the relevant questions. |
| 9. Open question | User selects a search result. | Complete question details are displayed. |
| 10. Inspect question | The user can view the title, tags, modified details, author, answer/correct-answer information when resolved, and discussion. | User can determine whether the existing question answers their need. |
| 11. Raise new question | If the user is not satisfied with the results, they can raise a new question with the required fields filled. | A new question is created. |

### 1.6 Search Result Display

Each search result contains only a compact preview of the question:

- Main title.
- A few lines from the question body/description.
- Tags.
- Resolved status when applicable.
- Number of `+` users.

The results are displayed in decreasing similarity/relevance order.

### 1.7 Complete Question View

When a user opens a question, the page displays:

- Question title.
- Full question body/description.
- Tags.
- Question author.
- Question last-modified date/time.
- Resolved/unresolved status.
- Answers.
- Answer last-modified date/time.
- Correct answer when the question has been resolved.
- Discussion/comments and replies.
- Upvote/downvote controls for comments.

If the user is not satisfied with the available search results, the user can raise a new question with:

- Tags.
- Title.
- Body/description.


---

## 2. Technology Stack

The implementation follows the technology stack defined in the project proposal:

| Technology | Purpose |
|---|---|
| Java | Primary implementation language |
| Maven | Build and dependency management |
| Spring Boot | Backend application framework |
| HTML/CSS/JavaScript | Frontend |
| Spring Web | Web/API layer |
| Spring Data JPA | Database access and persistence |
| PostgreSQL JDBC Driver | Java connectivity to PostgreSQL |
| PostgreSQL + pgvector | Database and vector similarity search support |
| Google Sign-In / OAuth | User authentication using IITM smail |

The application is intended to run as a hosted web application, such as on a department or CFI server.

## 3. Homepage and Question Discovery

### 2.1 What a User Sees After Login

After login, the user reaches the Fix It homepage.

The homepage displays questions for discovery based on the tags selected by the user.

If the user selects multiple tags, the homepage considers the **union of all selected tags**: questions matching any of the selected tags can be displayed. The resulting questions are sorted in decreasing order of the number of `+` users.

The purpose is to allow logged-in users to discover existing questions and participate in discussions or provide answers.

### 2.2 How Questions Get Answers

Any logged-in user who discovers a question can participate by providing an answer.

The homepage provides one way for users to discover questions they may be able to answer. Users can also reach questions through search.

The question author can select the answer that correctly solves the question. Once a correct answer is selected, the question can be shown as resolved.

The answer author can edit their own answer.

---

## 4. Notifications

Fix It contains a notification section inside the website.

Notifications are not phone notifications. They are displayed after the user logs in.

A notification is generated for relevant interactions such as:

- Someone comments on the user's question.
- Someone replies to the user's comment.
- Other relevant interactions connected to the user's question/comment.

The notification section is accessed through a bell icon.

When the user opens the bell icon:

1. The list of notifications is displayed.
2. The user selects a notification.
3. The system redirects the user to the corresponding question, comment, or relevant location.

## 4.2 Profile

The logged-in user can access the profile section from the website.

When the user presses the profile section, a dropdown is displayed containing:

- User ID.
- My Questions.

Selecting **My Questions** displays the questions created by the logged-in user.

---

## 5. Basic CRUD and Ownership

Basic CRUD operations are supported for the corresponding entities.

### 5.1 User

- Authenticate using Google Sign-In with an IITM smail account.
- Create a Fix It account automatically on the first successful authentication.
- Log in through Google on subsequent visits.
- View account information required by the system.

Fix It does not store a separate user password.

### 5.2 Question

A question author can:

- Create a question.
- View the question.
- Edit their own question.
- Edit the question title, body/description and tags.
- View the question's modified date/time.
- View its resolved status.

Only the author of a question can edit that question and its fields.

### 5.3 Answer

A user can:

- Add an answer to a question.
- View answers.
- Edit their own answer.

Only the author of an answer can edit that answer.

The question author can select the correct answer.

### 5.4 Comments

Users can:

- Add comments.
- Reply to comments.
- Edit their own comments.
- Upvote comments.
- Downvote comments.
- If a user upvotes an already-upvoted comment again, the upvote is removed. Similarly, a repeated downvote removes the user's downvote.

Comment voting is only for user interaction and does not affect question search, similarity ranking or the search hot path.

---


### 5.5 Basic Error Handling

The application should return clear user-facing errors for common failures such as:

- Unsuccessful Google authentication.
- Unauthorized/non-IITM authentication.
- Missing required question fields.
- Invalid or missing tags.
- Attempts by users to edit content they do not own.
- Search requests with invalid or empty required input.

Errors should be handled without exposing internal implementation details.

---

## 6. Test Plan

Each major functionality has a corresponding test.

| Module / Functionality | Test | Expected Result |
|---|---|---|
| User & Authentication | Authenticate using Google Sign-In with an allowed IITM smail account and attempt authentication with a non-allowed identity. | Valid IITM authentication succeeds and creates/retrieves the Fix It account; non-allowed identity is rejected. |
| Question Management | Create a question with tags, title and body, then edit it using the question author and a different user. | Author can edit the question; another user cannot edit it. |
| Question Fields | Create a question and verify tags, title, body, `+` count, modified date/time and resolved status. | All required question information is stored and displayed correctly. |
| Search | Select tags and submit a query similar to an existing question. | Search is performed within the selected tags and relevant questions are returned. |
| Search Threshold | Submit a query that has no sufficiently similar question. | Questions below the similarity/relevance threshold are not displayed. |
| Search Ordering | Submit a query matching multiple questions with different similarity values. | Results are displayed in decreasing similarity/relevance order. |
| Search Result Display | Search for a question and inspect the result list. | Each result shows title, a few body lines, tags, resolved status and `+` count. |
| Question Details | Open a search result. | Complete question details, answers and discussion are displayed. |
| Answer | Add an answer to a question and edit it using the answer author and another user. | Answer is added; only its author can edit it. |
| Correct Answer / Resolution | Question author selects an answer as correct. | Correct answer is identified and the question becomes resolved. |
| Comments | Add a comment and reply to it. | Comment and nested reply are displayed in the discussion. |
| Comment Voting | Upvote/downvote a comment and repeat the same vote. | The vote is recorded on the first action and is removed when the same user repeats that action; question/search ranking is unchanged. |
| Homepage | Login, select multiple tags and open the homepage. | Questions matching the union of the selected tags are displayed and sorted by decreasing `+` count. |
| Notifications | Comment on another user's question or reply to their comment. | The relevant notification appears in the recipient's notification section. |
| Profile | Open the profile section and select My Questions. | The dropdown displays the user ID and My Questions, and My Questions displays the user's questions. |
| Notification Redirection | Click a notification. | User is redirected to the corresponding question/comment or relevant location. |
| End-to-end | Login → search using tags/query → inspect results → open question → raise a new question if unsatisfied → receive answer/comment → select correct answer → resolve. | Complete question search and resolution workflow works correctly. |

---

## 7. Scope of the Current Design

The design is limited to the following core workflow:

**Google Sign-In → Homepage/Question Discovery → Union of Selected Tags + `+` Ordering → Tag + Query Search → Similarity/Relevant Questions → Threshold Filtering → Decreasing Similarity Order → Question Details → Existing Answer or New Question → Answers/Discussion → Correct Answer → Resolved Question → Future Search Reuse**

The design does not introduce additional functionality beyond the question, answer, discussion, search, discovery, authentication, notification, CRUD and resolution functionality described above.

// Style guide: every component in every state, with made-up data (no login, no network).

import { h, setChildren } from "../dom.js";
import { renderHeader, mountFooter } from "../header.js";
import { messageBar } from "../components/message-bar.js";
import { tagChip, tagList } from "../components/tag-chip.js";
import { tagFilter, tagPicker } from "../components/tag-picker.js";
import { questionRow } from "../components/question-row.js";
import { pager } from "../components/pager.js";
import { post, signature, plusButton, acceptedMark, acceptButton } from "../components/post.js";
import { commentItem } from "../components/comment-tree.js";
import { answerPost } from "../components/answers.js";

const ago = (minutes) => new Date(Date.now() - minutes * 60_000).toISOString();
const section = (title, note, ...content) => h("section", { class: "sg-section" }, h("h2", {}, title), note ? h("p", { class: "sg-note" }, note) : null, ...content);

renderHeader(document.getElementById("site-header"), {
  user: { userId: 12, username: "cs23b043", smail: "cs23b043@smail.iitm.ac.in" }, page: "home", live: false, unread: 2,
  notices: [
    { notificationId: 1, type: "COMMENT_ON_QUESTION", questionTitle: "WiFi keeps disconnecting in hostel", actorName: "cs23b024", isRead: false, createdAt: ago(2) },
    { notificationId: 2, type: "REPLY_TO_COMMENT", questionTitle: "Mess food quality", actorName: "ee22b101", isRead: true, createdAt: ago(300) },
  ],
});
mountFooter();

// ---- tokens (read from the stylesheet, so this page can never drift from it)
const tokens = ["--bg", "--bg-header", "--accent", "--text", "--muted", "--muted-strong", "--link", "--link-hover", "--border", "--row-border", "--input-border",
  "--tag-bg", "--tag-text", "--btn-primary", "--btn-primary-hover", "--ok", "--ok-dark", "--ok-solid", "--sig-bg", "--warn-bg", "--warn-border", "--error-bg", "--error-border", "--error-text", "--badge-bg", "--active-bg"];
const rootStyle = getComputedStyle(document.documentElement);
const swatches = tokens.map((name) => h("div", { class: "swatch" },
  h("div", { class: "chip", style: `background:${rootStyle.getPropertyValue(name).trim()}` }), h("div", { class: "name" }, name, h("br"), rootStyle.getPropertyValue(name).trim())));

const longTitle = "A very long question title that goes on and on to check how the row wraps when somebody writes far more than the usual one line of text in the title field " + "x".repeat(60);
const unbroken = "W".repeat(600);
const base = { questionId: 1, title: "WiFi keeps disconnecting in hostel", bodyPreview: "my laptop loses the wifi connection every hour in hostel block\nstarted last week after the update\nand restarting the router does not help", tags: ["tech"], isResolved: false, interestCount: 3, createdAt: ago(180), authorName: "cs23b043" };

const comments = [
  { commentId: 1, authorId: 1, authorName: "cs23b024", body: "Try changing the DNS to 8.8.8.8.", upCount: 3, downCount: 1, myVote: "UP", createdAt: ago(120), updatedAt: ago(120) },
  { commentId: 2, authorId: 2, authorName: "cs23b043", body: "Tried that, no luck.", upCount: 0, downCount: 0, myVote: null, createdAt: ago(60), updatedAt: ago(60) },
  { commentId: 3, authorId: 3, authorName: "ee22b101", body: "Did you update the driver?\nSecond line of the same comment.", upCount: 0, downCount: 2, myVote: "DOWN", createdAt: ago(40), updatedAt: ago(10) },
  { commentId: 4, authorId: 4, authorName: "me20b017", body: "Deep reply (depth 7 stays at indentation level 6).", upCount: 1, downCount: 0, myVote: null, createdAt: ago(5), updatedAt: ago(5) },
];

setChildren(document.getElementById("guide"),
  section("Colour tokens", "Defined once in css/fixit.css. Every pair used for text passes WCAG AA (checked by test/unit/contrast.test.mjs).", h("div", { class: "sg-swatches" }, swatches)),

  section("Typography", "Body 13px/1.4 · list title 17px · page title 27px · meta 12px.",
    h("div", { class: "q-title-page" }, "Question page title 27px"),
    h("p", {}, "Body text 13px: The quick brown fox jumps over the lazy dog. ", h("a", { href: "#" }, "A link"), " and ", h("code", {}, "inline code"), "."),
    h("p", { class: "subtle" }, "Muted meta text 12px: asked 3 hours ago")),

  section("Buttons", "Primary, secondary, link-style, disabled, busy.",
    h("div", { class: "sg-row" },
      h("button", { class: "btn btn-primary", type: "button" }, "Post Your Question"),
      h("button", { class: "btn btn-secondary", type: "button" }, "Cancel"),
      h("button", { class: "btn-link", type: "button" }, "link-style button"),
      h("button", { class: "btn btn-primary", type: "button", disabled: true }, "Disabled"),
      h("button", { class: "btn btn-primary", type: "button", disabled: true, "aria-busy": "true" }, "Posting…"))),

  section("Forms", "Field, hint, character counter, error state.",
    h("div", { class: "field" }, h("label", { for: "sg-t" }, "Title"), h("input", { id: "sg-t", type: "text", value: "WiFi keeps disconnecting" }), h("div", { class: "hint" }, "Be specific (max 255 characters).")),
    h("div", { class: "field has-error" }, h("label", { for: "sg-e" }, "Title (error)"), h("input", { id: "sg-e", type: "text", value: "" }), h("div", { class: "field-error" }, "Enter a title.")),
    h("div", { class: "field" }, h("label", { for: "sg-b" }, "Description"), h("textarea", { id: "sg-b" }, "Multi-line\ntext area"), h("div", { class: "counter" }, "12 / 20000"))),

  section("Tags", "Chip · ten chips wrapping · filter toggles (pressed / not pressed) · checkbox picker for the ask form.",
    h("div", { class: "sg-row" }, tagChip("tech"), tagChip("math"), tagChip("code"), tagChip("others")),
    h("div", { class: "sg-row" }, tagList(["tag-1", "tag-2", "tag-3", "tag-4", "tag-5", "tag-6", "tag-7", "tag-8", "tag-9", "tag-10"])),
    tagFilter({ tags: [{ name: "tech" }, { name: "math" }, { name: "code" }, { name: "others" }], selected: ["tech", "code"], onToggle: () => {} }),
    h("div", { class: "sg-box" }, tagPicker({ tags: [{ name: "tech" }, { name: "math" }, { name: "code" }, { name: "others" }], selected: ["math"] }).element)),

  section("Message bars", "Info (yellow) and error (pink).",
    messageBar("info", "Search is temporarily unavailable. You can still browse questions or ask a new one."),
    messageBar("error", "Invalid username or password"),
    messageBar("error", "Could not load the discussion.", h("button", { class: "btn-link", type: "button" }, "Try again"))),

  section("Question rows", "Normal · resolved · zero plus / open · long title · 600-character word · ten tags · search result (no author/time) · tuning view.",
    h("div", { class: "q-list" },
      questionRow(base),
      questionRow({ ...base, questionId: 2, isResolved: true, interestCount: 15, tags: ["tech", "code"], title: "Resolved: how to register for elective courses" }),
      questionRow({ ...base, questionId: 3, interestCount: 0, title: "Zero plus, still open" }),
      questionRow({ ...base, questionId: 4, title: longTitle, tags: ["tech", "math", "code", "others"] }),
      questionRow({ ...base, questionId: 5, title: unbroken, bodyPreview: unbroken }),
      questionRow({ ...base, questionId: 6, tags: ["t1", "t2", "t3", "t4", "t5", "t6", "t7", "t8", "t9", "t10"] }),
      questionRow({ questionId: 7, title: "A search result has no author or time", bodyPreview: "just the body lines", tags: ["math"], isResolved: true, interestCount: 4 }),
      questionRow({ questionId: 8, title: "Tuning view shows the scores", bodyPreview: "debug=1", tags: ["code"], isResolved: false, interestCount: 1, relevance: 0.712, semanticScore: 0.802, lexicalScore: 0.511 }, { debug: true }))),

  section("Pager", "First page · middle · last · many pages.",
    pager({ page: 0, totalPages: 3, hrefFor: (p) => "#p" + p }),
    pager({ page: 4, totalPages: 9, hrefFor: (p) => "#p" + p }),
    pager({ page: 19, totalPages: 20, hrefFor: (p) => "#p" + p })),

  section("Posts", "Question post with the + box (off / on) · answer with the select-as-correct check · accepted answer · signature with edited time.",
    post({ gutter: plusButton({ count: 3, pressed: false, onClick: () => {} }), text: "Full question body,\nline breaks are kept.", extra: h("div", { class: "sg-row" }, tagList(["tech", "code"])), actions: h("a", { href: "#" }, "edit"), sign: signature({ verb: "asked", createdAt: ago(180), updatedAt: ago(60), authorName: "cs23b043" }) }),
    post({ gutter: plusButton({ count: 4, pressed: true, onClick: () => {} }), text: "The + box is highlighted when it is mine.", sign: signature({ verb: "asked", createdAt: ago(20), updatedAt: ago(20), authorName: "cs23b024" }) }),
    answerPost({ answerId: 1, body: "Update the router firmware.", isAccepted: true, authorName: "ee22b101", createdAt: ago(90), updatedAt: ago(20) }, { isQuestionAuthor: true }),
    answerPost({ answerId: 2, body: "Seen by the question author: the check selects this one.", isAccepted: false, authorName: "me20b017", createdAt: ago(50), updatedAt: ago(50) }, { isQuestionAuthor: true, onAccept: () => {}, mine: false }),
    answerPost({ answerId: 3, body: "Mine: an edit link shows, and no check (I am not the question's author).", isAccepted: false, authorName: "cs23b043", createdAt: ago(30), updatedAt: ago(30) }, { mine: true, onEdit: () => {} }),
    h("div", { class: "sg-row" }, acceptedMark(), acceptButton(() => {}))),

  section("Comments", "Votes (mine pressed) · reply/edit links · edited marker · nesting capped at level 6.",
    h("div", { class: "comments" },
      commentItem(comments[0], { depth: 0, onVote: () => {}, onReply: () => {} }),
      commentItem(comments[1], { depth: 1, mine: true, onVote: () => {}, onReply: () => {}, onEdit: () => {} }),
      commentItem(comments[2], { depth: 2, onVote: () => {}, onReply: () => {} }),
      commentItem(comments[3], { depth: 7, onVote: () => {}, onReply: () => {} }))),

  section("Empty and loading states", null,
    h("div", { class: "empty" }, "No questions match these tags.", h("br"), h("a", { class: "btn btn-primary", href: "#" }, "Ask a question")),
    h("div", { class: "loading" }, "Loading…"),
    h("div", { class: "cta-box" }, h("span", {}, "Not what you were looking for?"), h("a", { class: "btn btn-primary", href: "#" }, "Ask a new question"))),

  section("Header", "The header above is live-styled: click the bell and the profile menu to see the dropdowns. Press Tab to see the focus ring on every control.", null));

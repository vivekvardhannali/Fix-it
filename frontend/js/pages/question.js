// The full question page: title, status, the question with its "+" box, the discussion (comments + replies),
// the answers (accepted first) and the "Your answer" form. Each section loads on its own, so one failure does not
// blank the page.

import { h, setChildren, ensureHeading } from "../dom.js";
import { api } from "../api.js";
import { mountHeader, mountFooter } from "../header.js";
import { parseQuery, toId } from "../util.js";
import { differs } from "../time.js";
import { post, signature, plusButton } from "../components/post.js";
import { tagList } from "../components/tag-chip.js";
import { timeEl } from "../components/time-el.js";
import { showError, clearMessage } from "../components/message-bar.js";
import { mountComments } from "../components/comment-tree.js";
import { mountAnswers } from "../components/answers.js";
import { questionHref } from "../components/question-row.js";
import { highlightFromHash } from "../highlight.js";

const user = await mountHeader("question");
mountFooter();

const messages = document.getElementById("messages");
const content = document.getElementById("content");
const id = toId(parseQuery(location.search).get("id"));

function notFound() {
  document.title = "Question not found - Fix It";
  setChildren(content, h("h1", { class: "page-title" }, "Question not found"),
    h("div", { class: "empty", id: "not-found" }, "This question does not exist.", h("br"), h("a", { href: "/" }, "Back to questions")));
}

async function start() {
  if (!id) return notFound();
  clearMessage(messages);
  let question;
  try {
    question = await api.getQuestion(id);
  } catch (e) {
    if (e.status === 404) return notFound();
    ensureHeading("Question");
    return showError(messages, e, { onRetry: start });
  }
  document.title = `${question.title} - Fix It`;
  const mine = question.authorId === user.userId;

  // ---- status line: asked / modified / resolved. Rebuilt whenever the question changes (e.g. an answer is accepted).
  const status = h("p", { class: "status-line", id: "status-line" });
  const renderStatus = () => {
    setChildren(status, "asked ", timeEl(question.createdAt),
      differs(question.createdAt, question.updatedAt) ? [" · modified ", timeEl(question.updatedAt)] : null,
      " · ", question.isResolved ? h("span", { class: "resolved-mark" }, "✓ resolved") : "open");
  };
  renderStatus();

  // ---- the "+" box: shows the server's count, and whether I already pressed it
  const gutter = h("div", { class: "plus-slot" });
  let plus = { count: question.interestCount, pressed: false };
  const renderPlus = ({ focus = false } = {}) => {
    const button = plusButton({ count: plus.count, pressed: plus.pressed, onClick: togglePlus });
    setChildren(gutter, button);
    if (focus) button.focus();
  };
  async function togglePlus() {
    const button = gutter.querySelector("button");
    button.disabled = true;
    try {
      const result = await api.toggleInterest(question.questionId);
      plus = { count: result.interestCount, pressed: result.interested };
      clearMessage(messages);
      renderPlus({ focus: true });
    } catch (e) {
      showError(messages, e);
      button.disabled = false;
    }
  }
  renderPlus();
  api.interest(question.questionId).then((state) => { plus = { count: state.interestCount, pressed: state.interested }; renderPlus(); }).catch(() => { /* keep the count we have */ });

  const commentsSection = h("section", { id: "comments-section", "aria-label": "Discussion" });
  const answersSection = h("section", { id: "answers-section", "aria-label": "Answers" });

  setChildren(content,
    h("div", { class: "page-head" }, h("h1", { class: "q-title-page", id: "question-title" }, question.title), h("a", { class: "btn btn-primary", href: "/ask.html" }, "Ask Question")),
    status,
    post({
      id: "question-post", gutter, text: question.body,
      extra: h("div", { class: "post-extra" }, tagList(question.tags)),
      actions: mine ? h("a", { href: "/ask.html?edit=" + toId(String(question.questionId)), id: "edit-question" }, "edit") : null,
      sign: signature({ verb: "asked", createdAt: question.createdAt, updatedAt: question.updatedAt, authorName: question.authorName }),
    }),
    commentsSection, answersSection);

  const comments = mountComments(commentsSection, { questionId: question.questionId, me: user });
  const answers = mountAnswers(answersSection, {
    question, me: user,
    onQuestionChange: (updated) => { Object.assign(question, updated); renderStatus(); },
  });
  await Promise.allSettled([comments.reload(), answers.load()]);   // independent: a failure in one leaves the other working
  highlightFromHash();                                              // arriving from a notification: scroll to the comment
}

await start();

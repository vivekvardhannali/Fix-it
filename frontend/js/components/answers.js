// The answers under a question. The accepted answer is listed first; only the question's author sees the "select as
// correct" check; only an answer's author sees its "edit". Answers never have comments.

import { h, setChildren, linkButton, whileBusy } from "../dom.js";
import { api } from "../api.js";
import { plural } from "../util.js";
import { validateAnswer } from "../validate.js";
import { showError } from "./message-bar.js";
import { post, signature, acceptedMark, acceptButton, anchorId } from "./post.js";
import { highlight } from "../highlight.js";

/** One answer as a post. Handlers are optional (the style guide renders it without a server). */
export function answerPost(a, { isQuestionAuthor = false, mine = false, onAccept, onEdit } = {}) {
  const gutter = a.isAccepted ? acceptedMark() : isQuestionAuthor && onAccept ? acceptButton(() => onAccept(a)) : null;
  return post({
    id: anchorId("answer", a.answerId),
    className: a.isAccepted ? "post--accepted" : "",
    gutter,
    text: a.body,
    actions: mine && onEdit ? linkButton("edit", () => onEdit(a), { "aria-label": "Edit your answer" }) : null,
    sign: signature({ verb: "answered", createdAt: a.createdAt, updatedAt: a.updatedAt, authorName: a.authorName }),
  });
}

/** accepted answer first, then oldest first (the backend already returns oldest first) */
export function orderAnswers(answers) {
  return [...answers].sort((x, y) => Number(y.isAccepted) - Number(x.isAccepted));
}

function answerForm({ label, initial = "", submitLabel, onSubmit, onCancel }) {
  const error = h("div", { class: "field-error", role: "alert", hidden: true });
  const area = h("textarea", { "aria-label": label, maxlength: 20000, rows: 6 }, initial);
  const submit = h("button", { type: "submit", class: "btn btn-primary" }, submitLabel);
  const form = h("form", { class: "inline-form", novalidate: true }, area, error,
    h("div", { class: "button-row" }, submit, onCancel ? h("button", { type: "button", class: "btn btn-secondary", onClick: onCancel }, "Cancel") : null));
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const problems = validateAnswer(area.value);
    error.hidden = !problems.body;
    error.textContent = problems.body || "";
    if (problems.body) { area.focus(); return; }
    await whileBusy(submit, async () => {
      try {
        await onSubmit(area.value.trim());
      } catch (e) {
        error.hidden = false;
        error.textContent = e.message;
      }
    });
  });
  return { form, area };
}

/**
 * @param question the question as returned by the API (needs questionId, authorId, acceptedAnswerId)
 * @param onQuestionChange called with the updated question after an answer is accepted (the page updates its status line)
 */
export function mountAnswers(container, { question, me, onQuestionChange }) {
  let answers = [];
  const isQuestionAuthor = question.authorId === me.userId;
  const heading = h("h2", { class: "section-title", id: "answers-heading" }, "Answers");
  const status = h("div", { "aria-live": "polite" });
  const list = h("div", { class: "answers", "aria-labelledby": "answers-heading" });
  const formArea = h("div", { class: "answer-form" });
  setChildren(container, heading, status, list, formArea);

  async function onAccept(answer) {
    try {
      const updated = await api.acceptAnswer(question.questionId, answer.answerId);
      answers.forEach((a) => { a.isAccepted = a.answerId === updated.acceptedAnswerId; });
      Object.assign(question, updated);
      status.replaceChildren();
      renderList();
      onQuestionChange?.(updated);
      highlight(list.querySelector("#" + anchorId("answer", answer.answerId)));
    } catch (e) {
      showError(status, e);
    }
  }

  function onEdit(answer, el) {
    const { form, area } = answerForm({
      label: "Edit your answer", initial: answer.body, submitLabel: "Save", onCancel: renderList,
      onSubmit: async (body) => {
        const updated = await api.updateAnswer(answer.answerId, body);
        Object.assign(answer, updated);
        renderList();
        highlight(list.querySelector("#" + anchorId("answer", answer.answerId)));
      },
    });
    el.replaceChildren(h("div", { class: "post-body" }, form));
    area.focus();
  }

  function renderList() {
    heading.textContent = answers.length ? plural(answers.length, "Answer") : "Answers";
    if (answers.length === 0) {
      setChildren(list, h("p", { class: "subtle" }, "No answers yet. Be the first to answer."));
      return;
    }
    setChildren(list, orderAnswers(answers).map((a) => {
      const el = answerPost(a, { isQuestionAuthor, mine: a.authorId === me.userId, onAccept, onEdit: (x) => onEdit(x, el) });
      return el;
    }));
  }

  function renderForm() {
    const { form, area } = answerForm({
      label: "Your answer", submitLabel: "Post Your Answer",
      onSubmit: async (body) => {
        const created = await api.addAnswer(question.questionId, body);
        answers.push(created);
        area.value = "";
        renderList();
        highlight(list.querySelector("#" + anchorId("answer", created.answerId)));
      },
    });
    area.id = "answer-text";
    setChildren(formArea, h("h2", { class: "section-title" }, "Your Answer"), form);
  }

  async function load() {
    try {
      answers = await api.listAnswers(question.questionId);
      status.replaceChildren();
      renderList();
    } catch (e) {
      showError(status, e, { onRetry: load });
    }
  }

  renderForm();
  return { load, element: container };
}

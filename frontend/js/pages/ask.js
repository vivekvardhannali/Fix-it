// Ask a new question, or edit your own (?edit=<id>). Tags: 1-4 of the fixed set (tech, math, code, others).

import { h, setChildren, whileBusy, ensureHeading } from "../dom.js";
import { api } from "../api.js";
import { mountHeader, mountFooter } from "../header.js";
import { parseQuery, toId } from "../util.js";
import { LIMITS, validateQuestion } from "../validate.js";
import { textField, textAreaField, applyErrors } from "../components/form.js";
import { tagPicker } from "../components/tag-picker.js";
import { showError, showMessage, clearMessage } from "../components/message-bar.js";
import { questionHref } from "../components/question-row.js";

const user = await mountHeader("ask");
mountFooter();

const messages = document.getElementById("messages");
const content = document.getElementById("content");
const params = parseQuery(location.search);
const editRaw = params.get("edit");
const editId = toId(editRaw);
const editing = editRaw !== null;

function stop(text, ...links) {
  setChildren(content, h("h1", { class: "page-title" }, editing ? "Edit your question" : "Ask a question"), h("div", { class: "empty" }, text, links.length ? h("br") : null, links));
}

async function start() {
  if (editing && !editId) { document.title = "Edit question - Fix It"; return stop("This question does not exist.", h("a", { href: "/" }, "Back to questions")); }

  let allTags, existing = null;
  try {
    allTags = await api.tags();
    if (editing) existing = await api.getQuestion(editId);
  } catch (e) {
    if (editing && e.status === 404) return stop("This question does not exist.", h("a", { href: "/" }, "Back to questions"));
    ensureHeading(editing ? "Edit your question" : "Ask a question");
    return showError(messages, e, { onRetry: start });
  }
  if (existing && existing.authorId !== user.userId) {
    document.title = "Edit question - Fix It";
    return stop("You can only edit your own questions.", h("a", { href: questionHref(existing.questionId) }, "Back to the question"));
  }

  document.title = (existing ? "Edit question" : "Ask a question") + " - Fix It";
  const known = new Set(allTags.map((t) => t.name.toLowerCase()));
  const prefillTags = existing ? existing.tags : params.getAll("tags").filter((t) => known.has(t.trim().toLowerCase()));

  const title = textField({ id: "title", label: "Title", maxlength: LIMITS.titleMax, value: existing ? existing.title : (params.get("title") || "").slice(0, LIMITS.titleMax),
    hint: "Be specific: what exactly goes wrong? (up to 255 characters)" });
  const picker = tagPicker({ tags: allTags, selected: prefillTags, max: allTags.length });
  const body = textAreaField({ id: "body", label: "Description", maxlength: LIMITS.bodyMax, rows: 12, counter: true, value: existing ? existing.body : "",
    hint: "Say what you tried and what you expected." });
  const submit = h("button", { type: "submit", class: "btn btn-primary", id: "submit-question" }, existing ? "Save Edits" : "Post Your Question");
  const cancel = h("a", { class: "btn btn-secondary", href: existing ? questionHref(existing.questionId) : "/" }, "Cancel");
  const form = h("form", { id: "question-form", novalidate: true }, title.element, picker.element, body.element, h("div", { class: "button-row" }, submit, cancel));

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    clearMessage(messages);
    const values = { title: title.input.value, body: body.input.value, tags: picker.getSelected() };
    const errors = validateQuestion(values, { maxTags: allTags.length });
    picker.setError(errors.tags || "");
    const ok = applyErrors({ title, body }, errors);
    if (errors.tags && ok) picker.focus();                      // only the tags are wrong: focus them
    if (errors.title || errors.body || errors.tags) return;
    await whileBusy(submit, async () => {
      try {
        const saved = existing
          ? await api.updateQuestion(existing.questionId, values.title.trim(), values.body.trim(), values.tags)
          : await api.createQuestion(values.title.trim(), values.body.trim(), values.tags);
        location.assign(questionHref(saved.questionId));
      } catch (e) {
        showError(messages, e);
      }
    });
  });

  setChildren(content, h("div", { class: "page-head" }, h("h1", {}, existing ? "Edit your question" : "Ask a question")), form);
  title.input.focus();
}

await start();

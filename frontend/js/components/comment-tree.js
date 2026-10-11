// The discussion under a question: comments, replies (any depth, indentation capped at 6), edit own, up/down votes.
// There is no delete anywhere (comments are permanent) and answers never have comments (design rule).

import { h, setChildren, linkButton, whileBusy } from "../dom.js";
import { api } from "../api.js";
import { differs } from "../time.js";
import { indentLevel, netScore, plural } from "../util.js";
import { validateComment } from "../validate.js";
import { timeEl } from "./time-el.js";
import { messageBar, showError } from "./message-bar.js";
import { anchorId } from "./post.js";
import { highlight } from "../highlight.js";

/** ▲ score ▼ - pressed arrow = my vote. Display only: votes never change ranking or the "+" count. */
export function votesEl(c, onVote) {
  const up = c.upCount || 0, down = c.downCount || 0;
  const tip = `${up} up · ${down} down`;
  const arrow = (type, glyph, label) => h("button", {
    type: "button", class: "vote-btn", "aria-pressed": c.myVote === type ? "true" : "false", "aria-label": label, title: tip,
    onClick: () => onVote?.(c, type),
  }, glyph);
  return h("div", { class: "comment-votes" }, arrow("UP", "▲", "Upvote this comment"), h("span", { class: "vote-score", title: tip }, String(netScore(up, down))), arrow("DOWN", "▼", "Downvote this comment"));
}

/** One comment (no children). Handlers are optional so the style guide can render it without any server. */
export function commentItem(c, { depth = 0, mine = false, onVote, onReply, onEdit } = {}) {
  const level = indentLevel(depth);
  return h("div", { class: `comment depth-${level}`, id: anchorId("comment", c.commentId), dataset: { depth: String(depth) } },
    votesEl(c, onVote),
    h("div", { class: "comment-main" },
      h("div", { class: "comment-body" }, c.body),
      h("div", { class: "comment-meta" },
        "— ", c.authorName || "unknown", ", ", timeEl(c.createdAt), differs(c.createdAt, c.updatedAt) ? " (edited)" : null,
        onReply ? linkButton("reply", () => onReply(c), { "aria-label": `Reply to ${c.authorName || "this comment"}` }) : null,
        mine && onEdit ? linkButton("edit", () => onEdit(c), { "aria-label": "Edit your comment" }) : null),
      h("div", { class: "comment-slot" })));
}

/** A small form with a textarea; used for new comments, replies and edits. */
function commentForm({ label, initial = "", submitLabel, onSubmit, onCancel }) {
  const error = h("div", { class: "field-error", role: "alert", hidden: true });
  const area = h("textarea", { class: "short", "aria-label": label, maxlength: 5000, rows: 3 }, initial);
  const submit = h("button", { type: "submit", class: "btn btn-primary" }, submitLabel);
  const form = h("form", { class: "inline-form", novalidate: true },
    area, error,
    h("div", { class: "button-row" }, submit, onCancel ? h("button", { type: "button", class: "btn btn-secondary", onClick: onCancel }, "Cancel") : null));
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const problems = validateComment(area.value);
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
 * Loads and manages the whole discussion inside `container`.
 * @param me the logged-in user ({userId, ...}); only the author of a comment gets its "edit" link
 */
export function mountComments(container, { questionId, me }) {
  let tree = [];
  const heading = h("h2", { class: "section-title", id: "comments-heading" }, "Comments");
  const list = h("div", { class: "comments", "aria-labelledby": "comments-heading" });
  const status = h("div", { "aria-live": "polite" });
  const addArea = h("div", { class: "add-comment" });
  setChildren(container, heading, status, list, addArea);

  const flat = () => {
    const out = [];
    const walk = (nodes, depth) => nodes.forEach((n) => { out.push([n, depth]); walk(n.replies || [], depth + 1); });
    walk(tree, 0);
    return out;
  };

  function closeSlots() {
    list.querySelectorAll(".comment-slot").forEach((slot) => slot.replaceChildren());
  }

  async function onVote(comment, type) {
    try {
      const result = await api.vote(comment.commentId, type);
      Object.assign(comment, { upCount: result.upCount, downCount: result.downCount, myVote: result.myVote ?? null });
      const el = list.querySelector("#" + anchorId("comment", comment.commentId));
      const fresh = votesEl(comment, onVote);
      el?.querySelector(".comment-votes")?.replaceWith(fresh);
      fresh.querySelectorAll(".vote-btn")[type === "UP" ? 0 : 1]?.focus();     // keyboard users keep their place
    } catch (e) {
      showError(status, e);
    }
  }

  function openReply(comment, itemEl) {
    closeSlots();
    const { form, area } = commentForm({
      label: `Reply to ${comment.authorName}`, submitLabel: "Add reply", onCancel: closeSlots,
      onSubmit: async (body) => { const created = await api.replyToComment(comment.commentId, body); await reload(created.commentId); },
    });
    itemEl.querySelector(".comment-slot").replaceChildren(form);
    area.focus();
  }

  function openEdit(comment, itemEl) {
    closeSlots();
    const { form, area } = commentForm({
      label: "Edit your comment", initial: comment.body, submitLabel: "Save", onCancel: closeSlots,
      onSubmit: async (body) => { await api.updateComment(comment.commentId, body); await reload(comment.commentId); },
    });
    itemEl.querySelector(".comment-slot").replaceChildren(form);
    area.focus();
    area.setSelectionRange(area.value.length, area.value.length);
  }

  function renderList() {
    const items = flat();
    heading.textContent = items.length ? plural(items.length, "Comment") : "Comments";
    if (items.length === 0) {
      setChildren(list, h("p", { class: "subtle" }, "No comments yet. Start the discussion."));
      return;
    }
    setChildren(list, items.map(([c, depth]) => {
      const el = commentItem(c, { depth, mine: c.authorId === me.userId, onVote, onReply: (x) => openReply(x, el), onEdit: (x) => openEdit(x, el) });
      return el;
    }));
  }

  function renderAdd() {
    const open = h("button", { type: "button", class: "btn btn-secondary", id: "add-comment-button" }, "Add a comment…");
    open.addEventListener("click", () => {
      const { form, area } = commentForm({
        label: "Add a comment", submitLabel: "Add comment", onCancel: renderAdd,
        onSubmit: async (body) => { const created = await api.addComment(questionId, body); renderAdd(); await reload(created.commentId); },
      });
      setChildren(addArea, form);
      area.focus();
    });
    setChildren(addArea, open);
  }

  async function reload(highlightId) {
    try {
      tree = await api.listComments(questionId);
      status.replaceChildren();
      renderList();
      if (highlightId) highlight(list.querySelector("#" + anchorId("comment", highlightId)));
    } catch (e) {
      showError(status, e, { onRetry: () => reload(highlightId) });
    }
  }

  renderAdd();
  return { reload, element: container };
}

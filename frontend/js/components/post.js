import { h } from "../dom.js";
import { toId } from "../util.js";
import { differs } from "../time.js";
import { timeEl } from "./time-el.js";

/**
 * The light-blue box at the bottom of a post: "asked 3 hours ago", optional "edited 20 mins ago", and the author.
 * verb: "asked" | "answered".
 */
export function signature({ verb, createdAt, updatedAt, authorName }) {
  return h("div", { class: "signature" },
    h("span", { class: "when" }, verb + " ", timeEl(createdAt)),
    differs(createdAt, updatedAt) ? h("span", { class: "when" }, "edited ", timeEl(updatedAt)) : null,
    h("span", { class: "author" }, authorName || "unknown"));
}

/** Layout of one post: gutter (interaction column) | body text, then a footer with actions and the signature. */
export function post({ id, gutter, text, extra, actions, sign, className = "" }) {
  return h("div", { class: ("post " + className).trim(), id },
    h("div", { class: "post-gutter" }, gutter),
    h("div", { class: "post-body" },
      h("div", { class: "post-text" }, text),
      extra,
      h("div", { class: "post-foot" }, h("div", { class: "post-actions" }, actions), sign)));
}

/** The big "+" box in a question's gutter: "I want this answered too". Pressed state comes from the server's answer. */
export function plusButton({ count, pressed, onClick, disabled = false }) {
  return h("button", { type: "button", class: "plus-btn", id: "plus-button", "aria-pressed": pressed ? "true" : "false", disabled: disabled || undefined,
    "aria-label": `Plus: ${count} people want this answered${pressed ? ", including you" : ""}`, title: "I want this answered too", onClick },
    h("span", { class: "glyph", "aria-hidden": "true" }, "+"), h("span", { class: "count" }, String(count)), h("span", { class: "label" }, "plus"));
}

/** Green check shown on the accepted answer. */
export function acceptedMark() {
  return h("div", { class: "accepted-mark", role: "img", "aria-label": "Accepted answer", title: "The question author accepted this answer" }, "✓");
}

/** Check button only the question's author sees on an answer. */
export function acceptButton(onClick) {
  return h("button", { type: "button", class: "accept-btn", "aria-label": "Select as the correct answer", title: "Select as the correct answer", onClick }, "✓");
}

/** Anchor id of an item ("comment-12"), or undefined when the id is not a valid number. */
export function anchorId(prefix, id) {
  const safe = toId(id);
  return safe ? `${prefix}-${safe}` : undefined;
}

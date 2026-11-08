import { h } from "../dom.js";
import { toId } from "../util.js";
import { differs } from "../time.js";
import { timeEl } from "./time-el.js";
import { avatar } from "./avatar.js";

/**
 * Author row at the bottom of a post: avatar, the author, "asked 3 hours ago" and an optional "edited 20 mins ago".
 * verb: "asked" | "answered".
 */
export function signature({ verb, createdAt, updatedAt, authorName }) {
  const name = authorName || "unknown";
  return h("div", { class: "signature" },
    avatar(name),
    h("div", { class: "sig-text" },
      h("span", { class: "author" }, name),
      h("span", { class: "when" }, verb + " ", timeEl(createdAt),
        differs(createdAt, updatedAt) ? [" · edited ", timeEl(updatedAt)] : null)));
}

/** Layout of one post (a card): body text and extras, then a footer with the interaction control, actions and the author row. */
export function post({ id, head, gutter, text, extra, actions, sign, className = "" }) {
  return h("div", { class: ("post " + className).trim(), id },
    head ? h("div", { class: "post-head" }, head) : null,
    h("div", { class: "post-body" },
      h("div", { class: "post-text" }, text),
      extra),
    h("div", { class: "post-foot" },
      h("div", { class: "post-actions" }, h("div", { class: "post-gutter" }, gutter), actions),
      sign));
}

/** The "+" pill on a question: "I want this answered too". Pressed state comes from the server's answer. */
export function plusButton({ count, pressed, onClick, disabled = false }) {
  return h("button", { type: "button", class: "plus-btn", id: "plus-button", "aria-pressed": pressed ? "true" : "false", disabled: disabled || undefined,
    "aria-label": `Plus: ${count} people want this answered${pressed ? ", including you" : ""}`, title: "I want this answered too", onClick },
    h("span", { class: "glyph", "aria-hidden": "true" }, pressed ? "✓" : "+"), h("span", { class: "count" }, String(count)),
    h("span", { class: "label" }, pressed ? "You want this answered" : "I want this answered too"));
}

/** Green badge shown on the accepted answer. */
export function acceptedMark() {
  return h("div", { class: "accepted-mark", role: "img", "aria-label": "Accepted answer", title: "The question author accepted this answer" },
    h("span", { class: "tick", "aria-hidden": "true" }, "✓"), h("span", { "aria-hidden": "true" }, "Accepted solution"));
}

/** Button only the question's author sees on an answer. */
export function acceptButton(onClick) {
  return h("button", { type: "button", class: "accept-btn", "aria-label": "Select as the correct answer", title: "Select as the correct answer", onClick },
    h("span", { class: "tick", "aria-hidden": "true" }, "✓"), "Accept as solution");
}

/** Anchor id of an item ("comment-12"), or undefined when the id is not a valid number. */
export function anchorId(prefix, id) {
  const safe = toId(id);
  return safe ? `${prefix}-${safe}` : undefined;
}
